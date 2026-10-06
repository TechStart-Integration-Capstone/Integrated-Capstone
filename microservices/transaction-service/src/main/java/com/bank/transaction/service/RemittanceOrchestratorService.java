package com.bank.transaction.service;

import com.bank.transaction.client.RiskEngineClient;
import com.bank.transaction.client.T24AdapterClient;
import com.bank.transaction.dto.InternalTransferRequest;
import com.bank.transaction.dto.InternalTransferResponse;
import com.bank.transaction.dto.RemittanceRequest;
import com.bank.transaction.dto.RemittanceResponse;
import com.bank.transaction.dto.RiskResult;
import com.bank.transaction.dto.T24Result;
import com.bank.transaction.model.Remittance;
import com.bank.transaction.repository.RemittanceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * PayPink 2.0 — Remittance Saga Orchestrator.
 *
 * Implements non-blocking saga workflow with short DB transactions:
 *   1. Redis Atomic Idempotency Check + DB idempotency fallback (409 Conflict if in-progress)
 *   2. Risk Engine Fraud Screening FIRST (reject before acquiring DB lock)
 *   3. Atomic Fund Reservation SECOND (held_balance update + REMITTANCE = PENDING_CORE)
 *   4. T24 Core Adapter Posting THIRD (Temenos OFS string -> FT reference)
 *   5. Forward Recovery / Ledger Commit (Save T24_POSTED -> Atomic DB Debit & Credit + Outbox)
 *   6. Auto-release hold guardrail on unhandled post-hold exceptions.
 */
@Service
public class RemittanceOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(RemittanceOrchestratorService.class);
    private static final String IDEMP_PREFIX = "idemp:remittance:";
    private static final String LOCK_PREFIX = "idemp:lock:";

    private final RemittanceRepository remittanceRepository;
    private final RemittanceLedgerService ledgerService;
    private final RiskEngineClient riskEngineClient;
    private final T24AdapterClient t24AdapterClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RiskDecisionPublisher riskDecisionPublisher;

    public RemittanceOrchestratorService(
            RemittanceRepository remittanceRepository,
            RemittanceLedgerService ledgerService,
            RiskEngineClient riskEngineClient,
            T24AdapterClient t24AdapterClient,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            RiskDecisionPublisher riskDecisionPublisher) {

        this.remittanceRepository   = remittanceRepository;
        this.ledgerService          = ledgerService;
        this.riskEngineClient       = riskEngineClient;
        this.t24AdapterClient       = t24AdapterClient;
        this.redisTemplate          = redisTemplate;
        this.objectMapper           = objectMapper;
        this.riskDecisionPublisher  = riskDecisionPublisher;
    }

    /** Public customer transfer: always a plain TRANSFER with full risk screening and ownership check. */
    public RemittanceResponse processRemittance(
            RemittanceRequest request,
            String idempotencyKey,
            String correlationId,
            Long callerCustomerId) {
        request.setTransactionType(RemittanceRequest.TYPE_TRANSFER);
        return execute(request, idempotencyKey, correlationId, callerCustomerId);
    }

    /**
     * Internal service-to-service transfer (loan-service only).
     * The caller is the source account's owner, so the ownership check always passes — that is how
     * bank-funded disbursements from PH1000000LOAN get through. LOAN_DISBURSEMENT skips the risk engine;
     * LOAN_REPAYMENT runs the normal flow. Outcomes are reported in the body instead of as HTTP errors.
     */
    public InternalTransferResponse processInternalTransfer(InternalTransferRequest internal, String correlationId) {
        RemittanceRequest request = new RemittanceRequest(
                internal.getSourceAccountNo(), internal.getTargetAccountNo(), internal.getAmount(), "PHP");
        request.setTransactionType(internal.getTransactionType());
        try {
            Long sourceOwnerId = ledgerService.resolveAccount(internal.getSourceAccountNo()).customerId();
            RemittanceResponse response = execute(request, internal.getIdempotencyKey(), correlationId, sourceOwnerId);
            if ("POSTED".equalsIgnoreCase(response.getStatus())) {
                return InternalTransferResponse.posted(response.getTransactionId(), response.getFtReference());
            }
            return InternalTransferResponse.pendingCore(response.getFtReference(), response.getReason());
        } catch (ResponseStatusException e) {
            String reason = e.getReason() != null ? e.getReason() : e.getMessage();
            if (e.getStatusCode().value() == HttpStatus.CONFLICT.value() && reason.contains("in progress")) {
                return InternalTransferResponse.pendingCore(null, reason);
            }
            if (e.getStatusCode().value() == HttpStatus.UNPROCESSABLE_ENTITY.value() && reason.startsWith("Insufficient")) {
                reason = InternalTransferResponse.REASON_INSUFFICIENT_FUNDS;
            }
            log.warn("[remittance-orchestrator] Internal {} transfer rejected key={} status={} reason={}",
                    internal.getTransactionType(), internal.getIdempotencyKey(), e.getStatusCode().value(), reason);
            return InternalTransferResponse.rejected(reason);
        }
    }

    private RemittanceResponse execute(
            RemittanceRequest request,
            String idempotencyKey,
            String correlationId,
            Long callerCustomerId) {

        log.info("[remittance-orchestrator] Processing transfer callerCustomerId={} sourceAcc={} targetAcc={} amount={} idemp={} corrId={}",
                callerCustomerId, request.getSourceAccountId(), request.getTargetAccountId(), request.getAmount(), idempotencyKey, correlationId);

        String redisKey = (idempotencyKey != null && !idempotencyKey.isBlank()) ? IDEMP_PREFIX + callerCustomerId + ":" + idempotencyKey : null;
        String lockKey = (idempotencyKey != null && !idempotencyKey.isBlank()) ? LOCK_PREFIX + callerCustomerId + ":" + idempotencyKey : null;

        // ── Step 1: Redis & Database Idempotency Check (#2) ───────────────────────────
        if (redisKey != null) {
            // Check Redis cache first
            String cachedJson = redisTemplate.opsForValue().get(redisKey);
            if (cachedJson != null) {
                try {
                    log.info("[remittance-orchestrator] Redis idempotency cache hit for key={}", idempotencyKey);
                    RemittanceResponse cachedResp = objectMapper.readValue(cachedJson, RemittanceResponse.class);
                    cachedResp.setCachedIdempotentResponse(true);
                    return cachedResp;
                } catch (Exception e) {
                    log.warn("[remittance-orchestrator] Failed to parse cached idempotency response: {}", e.getMessage());
                }
            }

            // Check Database fallback
            Optional<Remittance> existingOpt = remittanceRepository.findByCallerCustomerIdAndIdempotencyKey(callerCustomerId, idempotencyKey);
            if (existingOpt.isPresent()) {
                Remittance existing = existingOpt.get();

                RemittanceLedgerService.AccountInfo srcAcc = ledgerService.resolveAccount(request.getSourceAccountId());
                RemittanceLedgerService.AccountInfo tgtAcc = ledgerService.resolveAccount(request.getTargetAccountId());

                // Validate request details match (comparing Long vs Long)
                if (!existing.getSourceAccountId().equals(srcAcc.id())
                        || !existing.getTargetAccountId().equals(tgtAcc.id())
                        || existing.getAmount().compareTo(request.getAmount()) != 0) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "This Idempotency-Key was already used for a transfer with different request details.");
                }

                if ("POSTED".equalsIgnoreCase(existing.getStatus())) {
                    log.info("[remittance-orchestrator] DB idempotency hit for key={} ref={}", idempotencyKey, existing.getReferenceNo());
                    RemittanceResponse replay = new RemittanceResponse(
                            existing.getStatus(),
                            existing.getReferenceNo(),
                            existing.getFtReference(),
                            existing.getSourceAccountId(),
                            existing.getTargetAccountId(),
                            existing.getAmount(),
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            existing.getRiskScore(),
                            existing.getRiskDecision(),
                            existing.getReason(),
                            true
                    );
                    replay.setTransactionId(ledgerService.findTransactionId(existing.getReferenceNo()));
                    return replay;
                } else if ("T24_POSTED".equalsIgnoreCase(existing.getStatus()) || "PROCESSING".equalsIgnoreCase(existing.getStatus())
                        || "PENDING_CORE".equalsIgnoreCase(existing.getStatus()) || "RESERVED".equalsIgnoreCase(existing.getStatus())
                        || "AUTHORIZED".equalsIgnoreCase(existing.getStatus())) {
                    boolean canCancel = "RESERVED".equalsIgnoreCase(existing.getStatus())
                            && Remittance.INTERNAL_CLIENT_CANCEL_WINDOW.equalsIgnoreCase(existing.getInternalStatus())
                            && existing.getCancelUntil() != null && java.time.LocalDateTime.now().isBefore(existing.getCancelUntil());
                    int remaining = canCancel ? (int) Math.max(0, java.time.Duration.between(java.time.LocalDateTime.now(), existing.getCancelUntil()).toSeconds()) : 0;
                    RemittanceResponse inProg = new RemittanceResponse(
                            existing.getStatus(),
                            existing.getReferenceNo(),
                            existing.getFtReference(),
                            existing.getSourceAccountId(),
                            existing.getTargetAccountId(),
                            existing.getAmount(),
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            existing.getRiskScore(),
                            existing.getRiskDecision(),
                            canCancel ? "Transfer is in 30-second cancellation window. You may cancel within the window." : "Transfer is currently processing. Status will update via saga worker.",
                            true
                    );
                    inProg.setCancelUntil(existing.getCancelUntil());
                    inProg.setCancelWindowSeconds(remaining);
                    inProg.setCanCancel(canCancel);
                    return inProg;
                } else if ("REJECTED".equalsIgnoreCase(existing.getStatus()) || "FAILED".equalsIgnoreCase(existing.getStatus())) {
                    String reason = existing.getReason() != null ? existing.getReason() : "Transfer was previously rejected.";
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, reason);
                } else if ("CANCELLED".equalsIgnoreCase(existing.getStatus())) {
                    String reason = existing.getReason() != null ? existing.getReason() : "Transfer was cancelled.";
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, reason);
                }
            }

            Boolean setLock = redisTemplate.opsForValue().setIfAbsent(lockKey, "IN_PROGRESS", Duration.ofMinutes(2));
            if (Boolean.FALSE.equals(setLock)) {
                log.warn("[remittance-orchestrator] Concurrent request in progress for idempotency key={}", idempotencyKey);
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "A transfer request with this Idempotency-Key is currently in progress. Please retry shortly.");
            }
        }

        try {
            // ── Step 1: Initiated ────────────────────────────────────────────────────
            String referenceNo = deriveReferenceNo(callerCustomerId, idempotencyKey);

            // ── Step 2 & 3: Validated & Authenticated ────────────────────────────────
            RemittanceLedgerService.AccountInfo sourceAcc = ledgerService.resolveAccount(request.getSourceAccountId());
            RemittanceLedgerService.AccountInfo targetAcc = ledgerService.resolveAccount(request.getTargetAccountId());

            if (callerCustomerId != null && !sourceAcc.customerId().equals(callerCustomerId)) {
                log.warn("[remittance-orchestrator] Account ownership mismatch! Caller customerId={} does not own source accountId={}",
                        callerCustomerId, sourceAcc.id());
                try {
                    ledgerService.recordFailedRemittance(request, referenceNo, idempotencyKey, callerCustomerId,
                            Remittance.STEP_AUTHENTICATED, "Caller does not own source account", null, "transaction-service");
                } catch (Exception ignored) {}
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Access denied: caller does not own source account " + request.getSourceAccountId());
            }

            if (sourceAcc.id().equals(targetAcc.id())) {
                try {
                    ledgerService.recordFailedRemittance(request, referenceNo, idempotencyKey, callerCustomerId,
                            Remittance.STEP_VALIDATED, "Source and target accounts cannot be the same account", null, "transaction-service");
                } catch (Exception ignored) {}
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Source and target accounts cannot be the same account");
            }

            // ── Step 4: Fraud Check (Risk Engine Screening) ──────────────────────────
            RiskResult risk = null;
            if (RemittanceRequest.TYPE_LOAN_DISBURSEMENT.equals(request.getTransactionType())) {
                log.info("[remittance-orchestrator] LOAN_DISBURSEMENT ref={} skips risk screening", referenceNo);
            } else {
                risk = riskEngineClient.evaluateRisk(
                        sourceAcc.id(),
                        targetAcc.id(),
                        request.getAmount(),
                        request.getCurrency(),
                        correlationId
                );

                if ("UNAVAILABLE".equalsIgnoreCase(risk.decision())) {
                    log.warn("[remittance-orchestrator] Risk screening UNAVAILABLE ref={}", referenceNo);
                    // Publish UNAVAILABLE decision to audit log before aborting
                    riskDecisionPublisher.publish(referenceNo, risk);
                    try {
                        ledgerService.recordFailedRemittance(request, referenceNo, idempotencyKey, callerCustomerId,
                                Remittance.STEP_FRAUD_CHECK, "Risk screening service unavailable", risk, "risk-engine");
                    } catch (Exception ignored) {}
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Risk screening service is temporarily unavailable. Transfer aborted for security.");
                }

                if ("REJECT".equalsIgnoreCase(risk.decision()) || (risk.score() != null && risk.score().doubleValue() > 0.85)) {
                    String reason = "Transfer rejected by fraud risk screening: " + String.join(", ", risk.reasons());
                    log.warn("[remittance-orchestrator] Transfer REJECTED by Risk Engine! ref={} score={}", referenceNo, risk.score());
                    // Publish REJECT decision to audit log before aborting — captures rejected transfers too
                    riskDecisionPublisher.publish(referenceNo, risk);
                    try {
                        ledgerService.recordFailedRemittance(request, referenceNo, idempotencyKey, callerCustomerId,
                                Remittance.STEP_FRAUD_CHECK, reason, risk, "risk-engine");
                    } catch (Exception ignored) {}
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, reason);
                }

                // APPROVE — publish to audit log and proceed with the saga
                riskDecisionPublisher.publish(referenceNo, risk);
            }

            // ── Step 5: Limit Check (For funds transfers within bank) ────────────────
            if (RemittanceRequest.TYPE_TRANSFER.equals(request.getTransactionType())) {
                RemittanceLedgerService.CustomerLimits limits = ledgerService.getCustomerLimits(callerCustomerId);
                if (request.getAmount().compareTo(limits.perTxLimit()) > 0) {
                    String reason = String.format("Transaction amount (₱%s) exceeds per-transaction limit of ₱%s",
                            request.getAmount().toPlainString(), limits.perTxLimit().toPlainString());
                    try {
                        ledgerService.recordFailedRemittance(request, referenceNo, idempotencyKey, callerCustomerId,
                                Remittance.STEP_LIMIT_CHECK, reason, risk, "transaction-service");
                    } catch (Exception ignored) {}
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, reason);
                }
                BigDecimal todaySum = ledgerService.getTodayCumulativeTransferAmount(callerCustomerId);
                if (todaySum.add(request.getAmount()).compareTo(limits.dailyLimit()) > 0) {
                    String reason = String.format("Daily transfer limit of ₱%s exceeded. Cumulative today: ₱%s, attempted: ₱%s",
                            limits.dailyLimit().toPlainString(), todaySum.toPlainString(), request.getAmount().toPlainString());
                    try {
                        ledgerService.recordFailedRemittance(request, referenceNo, idempotencyKey, callerCustomerId,
                                Remittance.STEP_LIMIT_CHECK, reason, risk, "transaction-service");
                    } catch (Exception ignored) {}
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, reason);
                }
            }

            // ── Step 6: Funds Check ──────────────────────────────────────────────────
            if (sourceAcc.availableBalance().compareTo(request.getAmount()) < 0) {
                String reason = "Insufficient available funds in source account (Available: " + sourceAcc.availableBalance() + ")";
                try {
                    ledgerService.recordFailedRemittance(request, referenceNo, idempotencyKey, callerCustomerId,
                            Remittance.STEP_FUNDS_CHECK, reason, risk, "transaction-service");
                } catch (Exception ignored) {}
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, reason);
            }

            // ── Step 7: Authorized & Reserved (Hold Funds) ───────────────────────────
            Remittance remittance;
            try {
                remittance = ledgerService.holdFunds(request, referenceNo, idempotencyKey, callerCustomerId);
            } catch (org.springframework.dao.DataIntegrityViolationException dive) {
                log.warn("[remittance-orchestrator] Concurrent duplicate idempotency key insertion detected for customerId={} key={}", callerCustomerId, idempotencyKey);
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "A transfer request with this Idempotency-Key is currently in progress. Please retry shortly.");
            }

            // Check if 30-second client cancellation window applies
            boolean applyClientWindow = request.getCancelWindowSeconds() != null
                    && request.getCancelWindowSeconds() > 0
                    && !Boolean.TRUE.equals(request.getSkipClientWindow())
                    && !RemittanceRequest.TYPE_LOAN_DISBURSEMENT.equals(request.getTransactionType());

            if (applyClientWindow) {
                int windowSeconds = request.getCancelWindowSeconds();
                remittance.setStatus(Remittance.STATUS_RESERVED);
                remittance.setInternalStatus(Remittance.INTERNAL_CLIENT_CANCEL_WINDOW);
                remittance.setCurrentService("transaction-service");
                remittance.setCancelUntil(LocalDateTime.now().plusSeconds(windowSeconds));
                remittance.setRetryCount(0);
                remittance.setMaxRetries(3);
                remittance.setReason("Cancellation window active. You can cancel within " + windowSeconds + " seconds.");
                remittance = remittanceRepository.save(remittance);

                RemittanceResponse windowResponse = new RemittanceResponse(
                        Remittance.STATUS_RESERVED,
                        referenceNo,
                        null,
                        sourceAcc.id(),
                        targetAcc.id(),
                        request.getAmount(),
                        sourceAcc.balance(),
                        sourceAcc.balance(),
                        risk != null ? risk.score() : null,
                        risk != null ? risk.decision() : null,
                        remittance.getReason(),
                        false
                );
                windowResponse.setCancelUntil(remittance.getCancelUntil());
                windowResponse.setCancelWindowSeconds(windowSeconds);
                windowResponse.setCanCancel(true);
                return windowResponse;
            }

            // ── Step 8 & 9: T24 Core Adapter & Ledger Commit ──────────────────────────
            return executeCoreBankingSagaInternal(remittance, request, sourceAcc, targetAcc, risk, correlationId, redisKey);

        } finally {
            if (lockKey != null) {
                redisTemplate.delete(lockKey);
            }
        }
    }

    public RemittanceResponse executeCoreBankingSaga(Remittance remittance, String correlationId) {
        RemittanceRequest request = new RemittanceRequest();
        request.setSourceAccountId(String.valueOf(remittance.getSourceAccountId()));
        request.setTargetAccountId(String.valueOf(remittance.getTargetAccountId()));
        request.setAmount(remittance.getAmount());
        request.setCurrency(remittance.getCurrency());
        request.setTransactionType(remittance.getTransactionType());

        RemittanceLedgerService.AccountInfo sourceAcc = ledgerService.resolveAccount(String.valueOf(remittance.getSourceAccountId()));
        RemittanceLedgerService.AccountInfo targetAcc = ledgerService.resolveAccount(String.valueOf(remittance.getTargetAccountId()));
        RiskResult risk = remittance.getRiskScore() != null
                ? new RiskResult(remittance.getRiskScore(), remittance.getRiskDecision(), java.util.List.of())
                : null;

        String redisKey = (remittance.getIdempotencyKey() != null && !remittance.getIdempotencyKey().isBlank())
                ? IDEMP_PREFIX + remittance.getCallerCustomerId() + ":" + remittance.getIdempotencyKey()
                : null;

        return executeCoreBankingSagaInternal(remittance, request, sourceAcc, targetAcc, risk, correlationId, redisKey);
    }

    private RemittanceResponse executeCoreBankingSagaInternal(
            Remittance remittance,
            RemittanceRequest request,
            RemittanceLedgerService.AccountInfo sourceAcc,
            RemittanceLedgerService.AccountInfo targetAcc,
            RiskResult risk,
            String correlationId,
            String redisKey) {

        String referenceNo = remittance.getReferenceNo();
        RemittanceResponse finalResponse;

        try {
            T24Result t24 = t24AdapterClient.executeTransfer(
                    referenceNo,
                    sourceAcc.number(),
                    targetAcc.number(),
                    request.getAmount(),
                    request.getCurrency(),
                    correlationId
            );

            if ("POSTED".equalsIgnoreCase(t24.status())) {
                ledgerService.recordT24Posted(remittance, t24.ftReference());

                try {
                    finalResponse = ledgerService.commitLedgerMutation(
                            remittance,
                            request,
                            sourceAcc.id(),
                            targetAcc.id(),
                            request.getAmount(),
                            risk,
                            t24.ftReference()
                    );
                } catch (Exception commitEx) {
                    log.error("[remittance-orchestrator] T24 POSTED for ref={} but local ledger commit failed: {}. Preserving T24_POSTED status for saga worker recovery.",
                            referenceNo, commitEx.getMessage());
                    finalResponse = new RemittanceResponse(
                            "PROCESSING",
                            referenceNo,
                            t24.ftReference(),
                            request.getSourceAccountId(),
                            request.getTargetAccountId(),
                            request.getAmount(),
                            BigDecimal.ZERO,
                            BigDecimal.ZERO,
                            risk != null ? risk.score() : null,
                            risk != null ? risk.decision() : null,
                            "T24 Core Banking posted transfer. Local ledger update will be finalized shortly by saga worker.",
                            false
                    );
                }
            } else if ("REJECTED".equalsIgnoreCase(t24.status())) {
                // Instant Reversal (0 Retries)
                String reason = "Core banking T24 rejected transfer: " + t24.reason();
                ledgerService.releaseHoldFunds(remittance, sourceAcc.id(), request.getAmount(), reason);
                remittance.setInternalStatus(Remittance.INTERNAL_T24_REJECTED);
                remittanceRepository.save(remittance);
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, reason);
            } else {
                // PROCESSING / Timeout SLA: Bounded Retry with Exponential Backoff
                remittance.setStatus("PROCESSING");
                remittance.setInternalStatus(Remittance.STEP_AUTHORIZED);
                remittance.setCurrentService("t24-adapter");
                remittance.setRetryCount(0);
                remittance.setMaxRetries(3);
                remittance.setNextRetryAt(LocalDateTime.now().plusSeconds(15));
                remittance.setReason("T24 Core Banking processing delay. Status will update via saga worker.");
                remittanceRepository.save(remittance);

                finalResponse = new RemittanceResponse(
                        "PROCESSING",
                        referenceNo,
                        t24.ftReference(),
                        request.getSourceAccountId(),
                        request.getTargetAccountId(),
                        request.getAmount(),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        risk != null ? risk.score() : null,
                        risk != null ? risk.decision() : null,
                        "T24 Core Banking processing delay. Status will update via saga worker.",
                        false
                );
            }
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception ex) {
            // Post-Hold Exception Guardrail: Auto-release held balance ONLY if failure happened before T24 POSTED
            log.error("[remittance-orchestrator] Unhandled error post-hold for ref={}. Releasing held balance: {}",
                    referenceNo, ex.getMessage());
            ledgerService.releaseHoldFunds(remittance, sourceAcc.id(), request.getAmount(),
                    "System error post-hold: " + ex.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Transfer process encountered an error after hold. Funds have been released.");
        }

        // Cache in Redis for Idempotency — final outcomes only.
        if (redisKey != null && "POSTED".equalsIgnoreCase(finalResponse.getStatus())) {
            try {
                String json = objectMapper.writeValueAsString(finalResponse);
                redisTemplate.opsForValue().set(redisKey, json, Duration.ofDays(30));
            } catch (Exception e) {
                log.warn("[remittance-orchestrator] Failed to cache response in Redis: {}", e.getMessage());
            }
        }

        return finalResponse;
    }

    public void cancelTransferByUser(String referenceNo, Long callerCustomerId) {
        Remittance remittance = remittanceRepository.findByReferenceNo(referenceNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Remittance reference not found: " + referenceNo));

        if (callerCustomerId != null && !remittance.getCallerCustomerId().equals(callerCustomerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: caller does not own this transfer");
        }

        if (Remittance.STATUS_CANCELLED.equalsIgnoreCase(remittance.getStatus())) {
            return;
        }

        boolean isCancelable = Remittance.STATUS_RESERVED.equalsIgnoreCase(remittance.getStatus())
                && Remittance.INTERNAL_CLIENT_CANCEL_WINDOW.equalsIgnoreCase(remittance.getInternalStatus());

        if (!isCancelable || (remittance.getCancelUntil() != null && LocalDateTime.now().isAfter(remittance.getCancelUntil()))
                || !ledgerService.claimCancelWindow(remittance.getRemittanceId(), Remittance.INTERNAL_CANCELLED_BY_USER)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The cancellation window has closed. Transfer is being processed and cannot be cancelled.");
        }

        ledgerService.cancelAndReleaseHold(remittance, "Cancelled by user within the cancellation window");
    }

    public RemittanceResponse getRemittanceStatus(String referenceNo, Long callerCustomerId) {
        Remittance remittance = remittanceRepository.findByReferenceNo(referenceNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Remittance reference not found: " + referenceNo));

        if (callerCustomerId != null && !remittance.getCallerCustomerId().equals(callerCustomerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: caller does not own this transfer");
        }

        boolean canCancel = Remittance.STATUS_RESERVED.equalsIgnoreCase(remittance.getStatus())
                && Remittance.INTERNAL_CLIENT_CANCEL_WINDOW.equalsIgnoreCase(remittance.getInternalStatus())
                && remittance.getCancelUntil() != null
                && LocalDateTime.now().isBefore(remittance.getCancelUntil());

        int remaining = 0;
        if (canCancel && remittance.getCancelUntil() != null) {
            remaining = (int) Math.max(0, Duration.between(LocalDateTime.now(), remittance.getCancelUntil()).toSeconds());
        }

        RemittanceResponse resp = new RemittanceResponse(
                remittance.getStatus(),
                remittance.getReferenceNo(),
                remittance.getFtReference(),
                remittance.getSourceAccountId(),
                remittance.getTargetAccountId(),
                remittance.getAmount(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                remittance.getRiskScore(),
                remittance.getRiskDecision(),
                remittance.getReason(),
                false
        );
        resp.setCancelUntil(remittance.getCancelUntil());
        resp.setCancelWindowSeconds(remaining);
        resp.setCanCancel(canCancel);
        resp.setTransactionId(ledgerService.findTransactionId(remittance.getReferenceNo()));
        return resp;
    }

    private String deriveReferenceNo(Long customerId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return "TX-PH-" + System.currentTimeMillis() + "-" + java.util.UUID.randomUUID().toString().substring(0, 6);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((customerId + ":" + idempotencyKey).getBytes(StandardCharsets.UTF_8));
            String base64 = Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 12);
            return "TX-PH-" + base64.toUpperCase();
        } catch (Exception e) {
            return "TX-PH-" + System.currentTimeMillis() + "-" + java.util.UUID.randomUUID().toString().substring(0, 6);
        }
    }
}
