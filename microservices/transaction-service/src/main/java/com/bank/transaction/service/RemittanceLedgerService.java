package com.bank.transaction.service;

import com.bank.transaction.dto.RemittanceRequest;
import com.bank.transaction.dto.RemittanceResponse;
import com.bank.transaction.dto.RiskResult;
import com.bank.transaction.model.OutboxEvent;
import com.bank.transaction.model.Remittance;
import com.bank.transaction.model.TransactionRecord;
import com.bank.transaction.repository.OutboxEventRepository;
import com.bank.transaction.repository.RemittanceRepository;
import com.bank.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * PayPink 2.0 — Short DB Transaction Manager for Remittance Ledger Mutations.
 * Separates DB operations into short, isolated transactions to avoid holding
 * database connections while calling external network services (Risk Engine, T24).
 */
@Service
public class RemittanceLedgerService {

    private static final Logger log = LoggerFactory.getLogger(RemittanceLedgerService.class);

    private final RemittanceRepository remittanceRepository;
    private final TransactionRepository transactionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final JdbcTemplate jdbcTemplate;

    public RemittanceLedgerService(
            RemittanceRepository remittanceRepository,
            TransactionRepository transactionRepository,
            OutboxEventRepository outboxEventRepository,
            JdbcTemplate jdbcTemplate) {

        this.remittanceRepository = remittanceRepository;
        this.transactionRepository = transactionRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public record AccountInfo(Long id, Long customerId, String number, BigDecimal balance, BigDecimal heldBalance) {
        public BigDecimal availableBalance() {
            BigDecimal b = balance != null ? balance : BigDecimal.ZERO;
            BigDecimal h = heldBalance != null ? heldBalance : BigDecimal.ZERO;
            return b.subtract(h);
        }
    }

    private Object getValue(Map<String, Object> map, String key) {
        if (map.containsKey(key)) return map.get(key);
        if (map.containsKey(key.toUpperCase())) return map.get(key.toUpperCase());
        if (map.containsKey(key.toLowerCase())) return map.get(key.toLowerCase());
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) return entry.getValue();
        }
        return null;
    }

    public AccountInfo resolveAccount(String accountIdOrNumber) {
        String sql = "SELECT account_id, customer_id, account_number, current_balance, held_balance FROM dbo.ACCOUNT WITH (UPDLOCK, ROWLOCK) WHERE account_number = ? OR CAST(account_id AS NVARCHAR(50)) = ?";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, accountIdOrNumber, accountIdOrNumber);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found in ledger: " + accountIdOrNumber);
        }
        Map<String, Object> r = rows.get(0);
        Object idObj = getValue(r, "account_id");
        Object custObj = getValue(r, "customer_id");
        Object numObj = getValue(r, "account_number");
        Object balObj = getValue(r, "current_balance");
        Object heldObj = getValue(r, "held_balance");

        Long id = idObj != null ? ((Number) idObj).longValue() : null;
        Long customerId = custObj != null ? ((Number) custObj).longValue() : null;
        String number = numObj != null ? numObj.toString() : null;
        BigDecimal balance = balObj != null ? new BigDecimal(balObj.toString()) : BigDecimal.ZERO;
        BigDecimal heldBalance = heldObj != null ? new BigDecimal(heldObj.toString()) : BigDecimal.ZERO;
        return new AccountInfo(id, customerId, number, balance, heldBalance);
    }

    @Transactional
    public Remittance holdFunds(RemittanceRequest request, String referenceNo, String idempotencyKey, Long callerCustomerId) {
        AccountInfo source = resolveAccount(request.getSourceAccountId());
        AccountInfo target = resolveAccount(request.getTargetAccountId());

        // 1. Ownership check: verify caller owns source account
        if (callerCustomerId != null && !source.customerId().equals(callerCustomerId)) {
            log.warn("[ledger-service] Account ownership mismatch! Caller customerId={} does not own source accountId={}",
                    callerCustomerId, source.id());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied: caller does not own source account " + request.getSourceAccountId());
        }

        // 2. Reject same-account self-transfers (#3)
        if (source.id().equals(target.id())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Source and target accounts cannot be the same account");
        }

        // 3. Check available balance (current_balance - held_balance)
        if (source.availableBalance().compareTo(request.getAmount()) < 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Insufficient available funds in source account (Available: " + source.availableBalance() + ")");
        }

        // 4. Atomic conditional UPDATE on ACCOUNT: hold funds without lock contention
        String updateSql = "UPDATE dbo.ACCOUNT SET held_balance = held_balance + ? " +
                           "WHERE account_id = ? AND (current_balance - held_balance) >= ?";
        int rowsUpdated = jdbcTemplate.update(updateSql, request.getAmount(), source.id(), request.getAmount());
        if (rowsUpdated == 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Insufficient available funds in source account at hold execution");
        }

        Remittance remittance = new Remittance(
                referenceNo,
                source.id(),
                target.id(),
                request.getAmount(),
                request.getCurrency(),
                Remittance.STATUS_RESERVED
        );
        remittance.setCallerCustomerId(callerCustomerId != null ? callerCustomerId : source.customerId());
        remittance.setIdempotencyKey(idempotencyKey);
        remittance.setTransactionType(request.getTransactionType());
        remittance.setInternalStatus(Remittance.STEP_AUTHORIZED);
        remittance.setCurrentService("transaction-service");
        return remittanceRepository.save(remittance);
    }

    public record CustomerLimits(BigDecimal dailyLimit, BigDecimal perTxLimit) {}

    public CustomerLimits getCustomerLimits(Long customerId) {
        BigDecimal daily = new BigDecimal("50000.0000");
        BigDecimal perTx = new BigDecimal("25000.0000");
        if (customerId == null) return new CustomerLimits(daily, perTx);
        try {
            String sql = "SELECT daily_transfer_limit, per_tx_limit FROM dbo.CUSTOMER WHERE customer_id = ?";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, customerId);
            if (!rows.isEmpty()) {
                Object d = getValue(rows.get(0), "daily_transfer_limit");
                Object p = getValue(rows.get(0), "per_tx_limit");
                if (d != null) daily = new BigDecimal(d.toString());
                if (p != null) perTx = new BigDecimal(p.toString());
            }
        } catch (Exception ex) {
            log.warn("[ledger-service] Could not fetch limits for customerId={}, using defaults: {}", customerId, ex.getMessage());
        }
        return new CustomerLimits(daily, perTx);
    }

    public BigDecimal getTodayCumulativeTransferAmount(Long customerId) {
        if (customerId == null) return BigDecimal.ZERO;
        try {
            String sql = """
                SELECT COALESCE(SUM(amount), 0)
                FROM dbo.REMITTANCE
                WHERE caller_customer_id = ?
                  AND created_at >= CAST(CAST(SWITCHOFFSET(GETUTCDATE(), '+08:00') AS DATE) AS DATETIME2)
                  AND status IN ('Posted', 'POSTED', 'Reserved', 'Authorized', 'Processing', 'PENDING_CORE', 'T24_POSTED')
            """;
            BigDecimal sum = jdbcTemplate.queryForObject(sql, BigDecimal.class, customerId);
            return sum != null ? sum : BigDecimal.ZERO;
        } catch (Exception ex) {
            log.warn("[ledger-service] Failed to query cumulative transfer amount: {}", ex.getMessage());
            return BigDecimal.ZERO;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Remittance recordFailedRemittance(
            RemittanceRequest request,
            String referenceNo,
            String idempotencyKey,
            Long callerCustomerId,
            String internalStep,
            String failureReason,
            RiskResult risk,
            String serviceName) {

        Long srcId = null;
        Long tgtId = null;
        try {
            srcId = resolveAccount(request.getSourceAccountId()).id();
        } catch (Exception ignored) {}
        try {
            tgtId = resolveAccount(request.getTargetAccountId()).id();
        } catch (Exception ignored) {}

        final Long resolvedSrcId = srcId;
        final Long resolvedTgtId = tgtId;
        Remittance remittance = remittanceRepository.findByReferenceNo(referenceNo).orElseGet(() -> {
            Remittance r = new Remittance();
            r.setReferenceNo(referenceNo);
            r.setCallerCustomerId(callerCustomerId != null ? callerCustomerId : 0L);
            r.setIdempotencyKey(idempotencyKey);
            r.setSourceAccountId(resolvedSrcId != null ? resolvedSrcId : 0L);
            r.setTargetAccountId(resolvedTgtId != null ? resolvedTgtId : 0L);
            r.setAmount(request.getAmount() != null ? request.getAmount() : BigDecimal.ZERO);
            r.setCurrency(request.getCurrency() != null ? request.getCurrency() : "PHP");
            r.setTransactionType(request.getTransactionType() != null ? request.getTransactionType() : RemittanceRequest.TYPE_TRANSFER);
            return r;
        });

        remittance.setStatus(Remittance.STATUS_FAILED);
        remittance.setInternalStatus(internalStep);
        remittance.setCurrentService(serviceName != null ? serviceName : "transaction-service");
        remittance.setReason(failureReason);
        if (risk != null) {
            remittance.setRiskScore(risk.score());
            remittance.setRiskDecision(risk.decision());
        }
        return remittanceRepository.save(remittance);
    }

    /** LEDGER_TRANSACTION id for a POSTED remittance, or null if the ledger row is not there yet. */
    public Long findTransactionId(String referenceNo) {
        // First row wins: older data can hold a duplicate ledger row for one remittance.
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT transaction_id FROM dbo.LEDGER_TRANSACTION WHERE reference_no = ? ORDER BY transaction_id",
                Long.class, referenceNo);
        return ids.isEmpty() ? null : ids.get(0);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRejection(Remittance remittance, String reason, RiskResult risk) {
        remittance.setStatus(Remittance.STATUS_FAILED);
        remittance.setReason(reason);
        remittance.setCurrentService("transaction-service");
        if (risk != null) {
            remittance.setRiskScore(risk.score());
            remittance.setRiskDecision(risk.decision());
        }
        remittanceRepository.save(remittance);
        log.info("[ledger-service] Remittance ref={} marked FAILED: {}", remittance.getReferenceNo(), reason);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordT24Posted(Remittance remittance, String ftReference) {
        remittance.setStatus(Remittance.STATUS_PROCESSING);
        remittance.setInternalStatus(Remittance.STEP_POSTED);
        remittance.setCurrentService("t24-adapter");
        remittance.setFtReference(ftReference);
        remittanceRepository.save(remittance);
        log.info("[ledger-service] Remittance ref={} state updated to Processing (T24_POSTED) with ftRef={}",
                remittance.getReferenceNo(), ftReference);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseHoldFunds(Remittance remittance, Long sourceAccountId, BigDecimal amount, String reason) {
        if (sourceAccountId != null && amount != null) {
            String releaseSql = "UPDATE dbo.ACCOUNT SET held_balance = CASE WHEN held_balance >= ? THEN held_balance - ? ELSE 0 END WHERE account_id = ?";
            jdbcTemplate.update(releaseSql, amount, amount, sourceAccountId);
        }
        remittance.setStatus(Remittance.STATUS_FAILED);
        remittance.setInternalStatus(Remittance.STEP_POSTED);
        remittance.setCurrentService("t24-adapter");
        remittance.setReason(reason);
        remittance.setUpdatedAt(LocalDateTime.now());
        remittanceRepository.save(remittance);

        // Record outbox event for automatic/bank-side reversal
        OutboxEvent event = new OutboxEvent();
        event.setTransactionId(null);
        event.setEventType("REMITTANCE_REVERSED");
        event.setPayload(String.format("{\"referenceNo\":\"%s\",\"callerCustomerId\":%d,\"amount\":%.4f,\"status\":\"FAILED\",\"reason\":\"%s\"}",
                remittance.getReferenceNo(), remittance.getCallerCustomerId(), remittance.getAmount(), reason != null ? reason.replace("\"", "'") : ""));
        event.setStatus("PENDING");
        event.setCreatedDate(LocalDateTime.now());
        outboxEventRepository.save(event);

        log.info("[ledger-service] Held funds released and reversed for sourceAcc={} ref={}", sourceAccountId, remittance.getReferenceNo());
    }

    /**
     * Atomically ends a transfer's cancellation window. Cancel, "Send now" and the saga sweeper all call this
     * first and only the caller that gets {@code true} may act, so a transfer is never both cancelled and sent.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claimCancelWindow(Long remittanceId, String newInternalStatus) {
        return jdbcTemplate.update("UPDATE dbo.REMITTANCE SET internal_status = ?, updated_at = ? "
                        + "WHERE remittance_id = ? AND status = ? AND internal_status = ?",
                newInternalStatus, LocalDateTime.now(), remittanceId,
                Remittance.STATUS_RESERVED, Remittance.INTERNAL_CLIENT_CANCEL_WINDOW) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelAndReleaseHold(Remittance remittance, String reason) {
        if (remittance.getSourceAccountId() != null && remittance.getAmount() != null) {
            String releaseSql = "UPDATE dbo.ACCOUNT SET held_balance = CASE WHEN held_balance >= ? THEN held_balance - ? ELSE 0 END WHERE account_id = ?";
            jdbcTemplate.update(releaseSql, remittance.getAmount(), remittance.getAmount(), remittance.getSourceAccountId());
        }
        remittance.setStatus(Remittance.STATUS_CANCELLED);
        remittance.setInternalStatus(Remittance.INTERNAL_CANCELLED_BY_USER);
        remittance.setCurrentService("transaction-service");
        remittance.setReason(reason);
        remittance.setUpdatedAt(LocalDateTime.now());
        remittanceRepository.save(remittance);

        // Record outbox event for user cancellation
        OutboxEvent event = new OutboxEvent();
        event.setTransactionId(null);
        event.setEventType("REMITTANCE_CANCELLED");
        event.setPayload(String.format("{\"referenceNo\":\"%s\",\"callerCustomerId\":%d,\"amount\":%.4f,\"status\":\"CANCELLED\",\"reason\":\"%s\"}",
                remittance.getReferenceNo(), remittance.getCallerCustomerId(), remittance.getAmount(), reason != null ? reason.replace("\"", "'") : ""));
        event.setStatus("PENDING");
        event.setCreatedDate(LocalDateTime.now());
        outboxEventRepository.save(event);

        log.info("[ledger-service] User cancelled remittance ref={} and released held funds.", remittance.getReferenceNo());
    }

    @Transactional
    public RemittanceResponse commitLedgerMutation(Remittance remittance, RemittanceRequest request,
                                                       Long sourceAccId, Long targetAccId,
                                                       BigDecimal amount, RiskResult risk, String ftReference) {

        // The orchestrator and the saga worker can both try to commit the same remittance. Lock the REMITTANCE
        // row first so they serialize, then skip the debit/credit if the ledger row already exists — otherwise
        // the second caller would post the same transfer twice (two LEDGER_TRANSACTION rows, double credit).
        String currentStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM dbo.REMITTANCE WITH (UPDLOCK, ROWLOCK) WHERE remittance_id = ?",
                String.class, remittance.getRemittanceId());
        List<Long> existingTx = jdbcTemplate.queryForList(
                "SELECT transaction_id FROM dbo.LEDGER_TRANSACTION WHERE reference_no = ? ORDER BY transaction_id",
                Long.class, remittance.getReferenceNo());
        if (Remittance.STATUS_POSTED.equalsIgnoreCase(currentStatus) || !existingTx.isEmpty()) {
            Long transactionId = existingTx.isEmpty() ? null : existingTx.get(0);
            if (!Remittance.STATUS_POSTED.equalsIgnoreCase(currentStatus)) {
                // Ledger row exists but the status was left behind (e.g. overwritten by a stale saga update).
                remittance.setStatus(Remittance.STATUS_POSTED);
                remittance.setInternalStatus(Remittance.STEP_LEDGER_UPDATE);
                remittance.setCurrentService("transaction-service");
                if (ftReference != null) remittance.setFtReference(ftReference);
                remittanceRepository.save(remittance);
            }
            log.warn("[ledger-service] Ledger mutation for ref={} already committed (txId={}); not posting again",
                    remittance.getReferenceNo(), transactionId);
            RemittanceResponse replay = new RemittanceResponse("POSTED", remittance.getReferenceNo(),
                    ftReference != null ? ftReference : remittance.getFtReference(),
                    request.getSourceAccountId(), request.getTargetAccountId(), amount,
                    BigDecimal.ZERO, BigDecimal.ZERO, risk != null ? risk.score() : null,
                    risk != null ? risk.decision() : null, null, true);
            replay.setTransactionId(transactionId);
            return replay;
        }

        AccountInfo currentSource = resolveAccount(String.valueOf(sourceAccId));
        AccountInfo currentTarget = resolveAccount(String.valueOf(targetAccId));

        BigDecimal sourceBefore = currentSource.balance();
        BigDecimal sourceAfter = sourceBefore.subtract(amount);

        BigDecimal targetBefore = currentTarget.balance();
        BigDecimal targetAfter = (targetBefore != null ? targetBefore : BigDecimal.ZERO).add(amount);

        // 1. Update balances: debit source & release hold; credit target
        String updateSourceSql = "UPDATE dbo.ACCOUNT SET current_balance = ?, " +
                                 "held_balance = CASE WHEN held_balance >= ? THEN held_balance - ? ELSE 0 END " +
                                 "WHERE account_id = ?";
        jdbcTemplate.update(updateSourceSql, sourceAfter, amount, amount, currentSource.id());

        String updateTargetSql = "UPDATE dbo.ACCOUNT SET current_balance = ? WHERE account_id = ?";
        jdbcTemplate.update(updateTargetSql, targetAfter, currentTarget.id());

        // 2. Save LEDGER_TRANSACTION row (plain transfers keep their existing P2P_REMITTANCE type)
        String remittanceType = remittance.getTransactionType();
        String ledgerType = remittanceType == null || RemittanceRequest.TYPE_TRANSFER.equals(remittanceType)
                ? "P2P_REMITTANCE" : remittanceType;
        TransactionRecord tx = new TransactionRecord(
                currentSource.id(),
                currentTarget.id(),
                amount,
                request.getCurrency(),
                request.getCurrency(),
                ledgerType,
                remittance.getReferenceNo(),
                "SUCCESS",
                null
        );
        TransactionRecord savedTx = transactionRepository.save(tx);

        // 3. Save OUTBOX_EVENT row. transactionId/accountId/operation/before/afterBalance describe the
        //    debit leg so audit-service writes LEDGER_MUTATION_AUDIT and reconciliation can match it.
        String payloadJson = String.format(
                "{\"remittanceId\":%d,\"referenceNo\":\"%s\",\"ftReference\":\"%s\",\"sourceAccountId\":%d,\"targetAccountId\":%d,"
                        + "\"amount\":%s,\"status\":\"POSTED\",\"transactionId\":%d,\"transactionType\":\"%s\","
                        + "\"accountId\":%d,\"customerId\":%d,\"operation\":\"DEBIT\",\"currency\":\"%s\","
                        + "\"beforeBalance\":%s,\"afterBalance\":%s}",
                remittance.getRemittanceId(), remittance.getReferenceNo(), ftReference,
                currentSource.id(), currentTarget.id(), amount.toPlainString(),
                savedTx.getTransactionId(), ledgerType,
                currentSource.id(), currentSource.customerId(), request.getCurrency(),
                sourceBefore.toPlainString(), sourceAfter.toPlainString()
        );

        OutboxEvent event = new OutboxEvent();
        event.setTransactionId(savedTx.getTransactionId());
        event.setEventType("REMITTANCE_COMPLETED");
        event.setPayload(payloadJson);
        event.setStatus("PENDING");
        event.setCreatedDate(LocalDateTime.now());
        outboxEventRepository.save(event);

        // 4. Update REMITTANCE status to POSTED
        remittance.setStatus(Remittance.STATUS_POSTED);
        remittance.setInternalStatus(Remittance.STEP_LEDGER_UPDATE);
        remittance.setCurrentService("transaction-service");
        remittance.setFtReference(ftReference);
        remittanceRepository.save(remittance);

        log.info("[ledger-service] Ledger mutation committed ref={} sourceAfter={} targetAfter={}",
                remittance.getReferenceNo(), sourceAfter, targetAfter);

        BigDecimal riskScore = risk != null ? risk.score() : null;
        String riskDecision = risk != null ? risk.decision() : null;

        RemittanceResponse response = new RemittanceResponse(
                "POSTED",
                remittance.getReferenceNo(),
                ftReference,
                request.getSourceAccountId(),
                request.getTargetAccountId(),
                amount,
                sourceBefore,
                sourceAfter,
                riskScore,
                riskDecision,
                null,
                false
        );
        response.setTransactionId(savedTx.getTransactionId());
        return response;
    }
}
