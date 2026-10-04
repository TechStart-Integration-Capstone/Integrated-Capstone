package com.bank.transaction.service;

import com.bank.transaction.client.RiskEngineClient;
import com.bank.transaction.client.T24AdapterClient;
import com.bank.transaction.dto.RemittanceRequest;
import com.bank.transaction.dto.RemittanceResponse;
import com.bank.transaction.dto.RiskResult;
import com.bank.transaction.dto.T24Result;
import com.bank.transaction.model.OutboxEvent;
import com.bank.transaction.model.Remittance;
import com.bank.transaction.model.TransactionRecord;
import com.bank.transaction.repository.OutboxEventRepository;
import com.bank.transaction.repository.RemittanceRepository;
import com.bank.transaction.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * PayPink 2.0 — Remittance Orchestrator (Saga Engine)
 *
 * Implements the 4-step synchronous transfer saga:
 *   1. Redis Idempotency Check
 *   2. Hold Funds (REMITTANCE status = PENDING_CORE, pessimistic locking WITH UPDLOCK, ROWLOCK)
 *   3. Risk Engine Fraud Screening (Python FastAPI, score > 0.85 -> REJECT)
 *   4. T24 Core Adapter Posting (Temenos OFS string -> FT reference /1 or /-1)
 *   5. Final Ledger Balance Mutation + OUTBOX Row in ONE database transaction
 */
@Service
public class RemittanceOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(RemittanceOrchestratorService.class);
    private static final String IDEMP_PREFIX = "idemp:remittance:";

    private final RemittanceRepository remittanceRepository;
    private final TransactionRepository transactionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final RiskEngineClient riskEngineClient;
    private final T24AdapterClient t24AdapterClient;
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RemittanceOrchestratorService(
            RemittanceRepository remittanceRepository,
            TransactionRepository transactionRepository,
            OutboxEventRepository outboxEventRepository,
            RiskEngineClient riskEngineClient,
            T24AdapterClient t24AdapterClient,
            JdbcTemplate jdbcTemplate,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {

        this.remittanceRepository = remittanceRepository;
        this.transactionRepository = transactionRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.riskEngineClient = riskEngineClient;
        this.t24AdapterClient = t24AdapterClient;
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    private record AccountInfo(Long id, String number, BigDecimal balance) {}

    private AccountInfo resolveAccount(String accountIdOrNumber) {
        String queryVal = accountIdOrNumber;
        if ("SIM-REJECT".equalsIgnoreCase(accountIdOrNumber)) {
            queryVal = "2"; // Use target account 2 for DB row lock & foreign key constraint
        }
        String sql = "SELECT account_id, account_number, current_balance FROM dbo.ACCOUNT WITH (UPDLOCK, ROWLOCK) WHERE account_number = ? OR CAST(account_id AS NVARCHAR(50)) = ?";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, queryVal, queryVal);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found in ledger: " + accountIdOrNumber);
        }
        Map<String, Object> r = rows.get(0);
        Long id = ((Number) r.get("account_id")).longValue();
        String number = (String) r.get("account_number");
        BigDecimal balance = (BigDecimal) r.get("current_balance");
        return new AccountInfo(id, number, balance);
    }

    public RemittanceResponse processRemittance(RemittanceRequest request, String idempotencyKey, String correlationId) {
        log.info("[remittance-orchestrator] Processing remittance from acc={} to acc={} amount={} idemp={} corrId={}",
                request.getSourceAccountId(), request.getTargetAccountId(), request.getAmount(), idempotencyKey, correlationId);

        // ── Step 1: Redis Idempotency Check ──────────────────────────────────────────
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String redisKey = IDEMP_PREFIX + idempotencyKey;
            String cachedJson = redisTemplate.opsForValue().get(redisKey);
            if (cachedJson != null) {
                try {
                    log.info("[remittance-orchestrator] Redis idempotency hit for key={}", idempotencyKey);
                    RemittanceResponse cachedResp = objectMapper.readValue(cachedJson, RemittanceResponse.class);
                    cachedResp.setCachedIdempotentResponse(true);
                    return cachedResp;
                } catch (Exception e) {
                    log.warn("[remittance-orchestrator] Failed to parse cached idempotency JSON: {}", e.getMessage());
                }
            }
        }

        // Generate unique transaction reference
        String referenceNo = "TX-PH-" + Instant.now().toEpochMilli() + "-" +
                java.util.UUID.randomUUID().toString().substring(0, 8);

        // ── Step 2: Hold Funds (REMITTANCE = PENDING_CORE) ───────────────────────────
        Remittance remittance = executeHoldFunds(request, referenceNo);

        // ── Step 3: Risk Engine Screening ────────────────────────────────────────────
        RiskResult risk = riskEngineClient.evaluateRisk(
                request.getSourceAccountId(),
                request.getTargetAccountId(),
                request.getAmount(),
                request.getCurrency(),
                correlationId
        );

        remittance.setRiskScore(risk.score());
        remittance.setRiskDecision(risk.decision());

        if ("UNAVAILABLE".equalsIgnoreCase(risk.decision())) {
            remittance.setStatus("REJECTED");
            remittance.setReason("Risk engine service unavailable");
            remittanceRepository.save(remittance);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Risk screening service is temporarily unavailable. Transfer aborted for security.");
        }

        if ("REJECT".equalsIgnoreCase(risk.decision()) || (risk.score() != null && risk.score().doubleValue() > 0.85)) {
            remittance.setStatus("REJECTED");
            remittance.setReason("Transfer rejected by fraud risk screening: " + String.join(", ", risk.reasons()));
            remittanceRepository.save(remittance);
            log.warn("[remittance-orchestrator] Transfer REJECTED by Risk Engine! ref={} score={}", referenceNo, risk.score());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Transfer rejected by risk screening. Reason: " + remittance.getReason());
        }

        // ── Step 4: T24 Core Adapter Call ─────────────────────────────────────────────
        AccountInfo sourceAcc = resolveAccount(request.getSourceAccountId());
        AccountInfo targetAcc = resolveAccount(request.getTargetAccountId());
        String creditAccNo = "SIM-REJECT".equalsIgnoreCase(request.getTargetAccountId()) ? "SIM-REJECT" : targetAcc.number();

        T24Result t24 = t24AdapterClient.executeTransfer(
                referenceNo,
                sourceAcc.number(),
                creditAccNo,
                request.getAmount(),
                request.getCurrency(),
                correlationId
        );

        // ── Step 5: Final Balance Mutation + OUTBOX Row ──────────────────────────────
        RemittanceResponse finalResponse;
        if ("POSTED".equalsIgnoreCase(t24.status())) {
            remittance.setFtReference(t24.ftReference());
            finalResponse = commitLedgerMutation(remittance, request, sourceAcc, targetAcc, risk, t24);
        } else if ("REJECTED".equalsIgnoreCase(t24.status())) {
            remittance.setStatus("REJECTED");
            remittance.setReason("Core banking T24 rejected transfer: " + t24.reason());
            remittanceRepository.save(remittance);
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, remittance.getReason());
        } else {
            // PROCESSING / Timeout
            remittance.setStatus("PROCESSING");
            remittance.setReason("T24 Core Banking processing delay (2s SLA timeout)");
            remittanceRepository.save(remittance);

            finalResponse = new RemittanceResponse(
                    "PROCESSING",
                    referenceNo,
                    null,
                    request.getSourceAccountId(),
                    request.getTargetAccountId(),
                    request.getAmount(),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    risk.score(),
                    risk.decision(),
                    "T24 Core Banking processing. Status will update via reconciliation.",
                    false
            );
        }

        // Cache in Redis for Idempotency
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            try {
                String redisKey = IDEMP_PREFIX + idempotencyKey;
                String json = objectMapper.writeValueAsString(finalResponse);
                redisTemplate.opsForValue().set(redisKey, json, Duration.ofDays(30));
            } catch (Exception e) {
                log.warn("[remittance-orchestrator] Failed to cache response in Redis: {}", e.getMessage());
            }
        }

        return finalResponse;
    }

    @Transactional
    protected Remittance executeHoldFunds(RemittanceRequest request, String referenceNo) {
        AccountInfo source = resolveAccount(request.getSourceAccountId());
        AccountInfo target = resolveAccount(request.getTargetAccountId());

        if (source.balance() == null || source.balance().compareTo(request.getAmount()) < 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds in source account");
        }

        Remittance remittance = new Remittance(
                referenceNo,
                source.id(),
                target.id(),
                request.getAmount(),
                request.getCurrency(),
                "PENDING_CORE"
        );
        return remittanceRepository.save(remittance);
    }

    @Transactional
    protected RemittanceResponse commitLedgerMutation(Remittance remittance, RemittanceRequest request,
                                                       AccountInfo sourceAcc, AccountInfo targetAcc,
                                                       RiskResult risk, T24Result t24) {

        // 1. Lock and fetch current balances
        AccountInfo currentSource = resolveAccount(sourceAcc.number());
        AccountInfo currentTarget = resolveAccount(targetAcc.number());

        if (currentSource.balance() == null || currentSource.balance().compareTo(request.getAmount()) < 0) {
            remittance.setStatus("REJECTED");
            remittance.setReason("Insufficient funds at commit time");
            remittanceRepository.save(remittance);
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds in source account");
        }

        BigDecimal sourceBefore = currentSource.balance();
        BigDecimal sourceAfter = sourceBefore.subtract(request.getAmount());

        BigDecimal targetBefore = currentTarget.balance();
        BigDecimal targetAfter = (targetBefore != null ? targetBefore : BigDecimal.ZERO).add(request.getAmount());

        // 2. Update balances
        jdbcTemplate.update("UPDATE dbo.ACCOUNT SET current_balance = ? WHERE account_id = ?", sourceAfter, currentSource.id());
        jdbcTemplate.update("UPDATE dbo.ACCOUNT SET current_balance = ? WHERE account_id = ?", targetAfter, currentTarget.id());

        // 3. Save LEDGER_TRANSACTION row
        TransactionRecord tx = new TransactionRecord(
                currentSource.id(),
                currentTarget.id(),
                request.getAmount(),
                request.getCurrency(),
                request.getCurrency(),
                "P2P_REMITTANCE",
                remittance.getReferenceNo(),
                "SUCCESS",
                null
        );
        TransactionRecord savedTx = transactionRepository.save(tx);

        // 4. Save OUTBOX_EVENT row (topic: remittance.events, key: sourceAccountId)
        String payloadJson = String.format(
                "{\"remittanceId\":%d,\"referenceNo\":\"%s\",\"ftReference\":\"%s\",\"sourceAccountId\":%d,\"targetAccountId\":%d,\"amount\":%.4f,\"status\":\"POSTED\"}",
                remittance.getRemittanceId(), remittance.getReferenceNo(), t24.ftReference(),
                currentSource.id(), currentTarget.id(), request.getAmount()
        );

        OutboxEvent event = new OutboxEvent();
        event.setTransactionId(savedTx.getTransactionId());
        event.setEventType("REMITTANCE_COMPLETED");
        event.setPayload(payloadJson);
        event.setStatus("PENDING");
        event.setCreatedDate(LocalDateTime.now());
        outboxEventRepository.save(event);

        // 5. Update REMITTANCE status to POSTED
        remittance.setStatus("POSTED");
        remittance.setFtReference(t24.ftReference());
        remittanceRepository.save(remittance);

        return new RemittanceResponse(
                "POSTED",
                remittance.getReferenceNo(),
                t24.ftReference(),
                request.getSourceAccountId(),
                request.getTargetAccountId(),
                request.getAmount(),
                sourceBefore,
                sourceAfter,
                risk.score(),
                risk.decision(),
                null,
                false
        );
    }
}
