package com.bank.transaction.service;

import com.bank.transaction.client.RiskEngineClient;
import com.bank.transaction.client.T24AdapterClient;
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

    public RemittanceOrchestratorService(
            RemittanceRepository remittanceRepository,
            RemittanceLedgerService ledgerService,
            RiskEngineClient riskEngineClient,
            T24AdapterClient t24AdapterClient,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {

        this.remittanceRepository = remittanceRepository;
        this.ledgerService = ledgerService;
        this.riskEngineClient = riskEngineClient;
        this.t24AdapterClient = t24AdapterClient;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public RemittanceResponse processRemittance(
            RemittanceRequest request,
            String idempotencyKey,
            String correlationId,
            Long callerCustomerId) {

        log.info("[remittance-orchestrator] Processing transfer callerCustomerId={} sourceAcc={} targetAcc={} amount={} idemp={} corrId={}",
                callerCustomerId, request.getSourceAccountId(), request.getTargetAccountId(), request.getAmount(), idempotencyKey, correlationId);

        String redisKey = (idempotencyKey != null && !idempotencyKey.isBlank()) ? IDEMP_PREFIX + idempotencyKey : null;
        String lockKey = (idempotencyKey != null && !idempotencyKey.isBlank()) ? LOCK_PREFIX + idempotencyKey : null;

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
            Optional<Remittance> existingOpt = remittanceRepository.findByIdempotencyKey(idempotencyKey);
            if (existingOpt.isPresent()) {
                Remittance existing = existingOpt.get();
                if ("POSTED".equalsIgnoreCase(existing.getStatus()) || "PROCESSING".equalsIgnoreCase(existing.getStatus())) {
                    log.info("[remittance-orchestrator] DB idempotency hit for key={} ref={}", idempotencyKey, existing.getReferenceNo());
                    RemittanceResponse resp = new RemittanceResponse(
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
                    return resp;
                } else if ("PENDING_CORE".equalsIgnoreCase(existing.getStatus())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "A transfer request with this Idempotency-Key is currently in progress. Please retry shortly.");
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
            // Generate deterministic reference number from idempotency key
            String referenceNo = deriveReferenceNo(callerCustomerId, idempotencyKey);

            // ── Step 2: Risk Engine Screening FIRST ──────────────────────────────────
            RiskResult risk = riskEngineClient.evaluateRisk(
                    request.getSourceAccountId(),
                    request.getTargetAccountId(),
                    request.getAmount(),
                    request.getCurrency(),
                    correlationId
            );

            if ("UNAVAILABLE".equalsIgnoreCase(risk.decision())) {
                log.warn("[remittance-orchestrator] Risk screening UNAVAILABLE ref={}", referenceNo);
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Risk screening service is temporarily unavailable. Transfer aborted for security.");
            }

            if ("REJECT".equalsIgnoreCase(risk.decision()) || (risk.score() != null && risk.score().doubleValue() > 0.85)) {
                String reason = "Transfer rejected by fraud risk screening: " + String.join(", ", risk.reasons());
                log.warn("[remittance-orchestrator] Transfer REJECTED by Risk Engine! ref={} score={}", referenceNo, risk.score());
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, reason);
            }

            // ── Step 3: Hold Funds SECOND (Ownership Check + Same-Account Check + Atomic held_balance) ──────
            Remittance remittance = ledgerService.holdFunds(request, referenceNo, idempotencyKey, callerCustomerId);

            // ── Step 4 & 5: T24 Core Adapter & Ledger Commit (With Exception Guardrail) ──────
            RemittanceResponse finalResponse;
            RemittanceLedgerService.AccountInfo sourceAcc = ledgerService.resolveAccount(request.getSourceAccountId());
            RemittanceLedgerService.AccountInfo targetAcc = ledgerService.resolveAccount(request.getTargetAccountId());

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

                    finalResponse = ledgerService.commitLedgerMutation(
                            remittance,
                            request,
                            sourceAcc.id(),
                            targetAcc.id(),
                            request.getAmount(),
                            risk,
                            t24.ftReference()
                    );
                } else if ("REJECTED".equalsIgnoreCase(t24.status())) {
                    String reason = "Core banking T24 rejected transfer: " + t24.reason();
                    ledgerService.releaseHoldFunds(remittance, sourceAcc.id(), request.getAmount(), reason);
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, reason);
                } else {
                    // PROCESSING / Timeout SLA (#1)
                    remittance.setStatus("PROCESSING");
                    remittance.setReason("T24 Core Banking processing. Status will update via saga worker.");
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
                            risk.score(),
                            risk.decision(),
                            "T24 Core Banking processing delay. Status will update via saga worker.",
                            false
                    );
                }
            } catch (ResponseStatusException rse) {
                throw rse;
            } catch (Exception ex) {
                // Post-Hold Exception Guardrail (#1): Auto-release held balance on unhandled errors
                log.error("[remittance-orchestrator] Unhandled error post-hold for ref={}. Releasing held balance: {}",
                        referenceNo, ex.getMessage());
                ledgerService.releaseHoldFunds(remittance, sourceAcc.id(), request.getAmount(),
                        "System error post-hold: " + ex.getMessage());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Transfer process encountered an error after hold. Funds have been released.");
            }

            // Cache in Redis for Idempotency
            if (redisKey != null) {
                try {
                    String json = objectMapper.writeValueAsString(finalResponse);
                    redisTemplate.opsForValue().set(redisKey, json, Duration.ofDays(30));
                } catch (Exception e) {
                    log.warn("[remittance-orchestrator] Failed to cache response in Redis: {}", e.getMessage());
                }
            }

            return finalResponse;

        } finally {
            if (lockKey != null) {
                redisTemplate.delete(lockKey);
            }
        }
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
