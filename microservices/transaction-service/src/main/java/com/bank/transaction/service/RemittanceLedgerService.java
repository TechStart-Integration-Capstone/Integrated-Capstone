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

    public AccountInfo resolveAccount(String accountIdOrNumber) {
        String sql = "SELECT account_id, customer_id, account_number, current_balance, held_balance FROM dbo.ACCOUNT WITH (UPDLOCK, ROWLOCK) WHERE account_number = ? OR CAST(account_id AS NVARCHAR(50)) = ?";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, accountIdOrNumber, accountIdOrNumber);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found in ledger: " + accountIdOrNumber);
        }
        Map<String, Object> r = rows.get(0);
        Long id = ((Number) r.get("account_id")).longValue();
        Long customerId = ((Number) r.get("customer_id")).longValue();
        String number = (String) r.get("account_number");
        BigDecimal balance = (BigDecimal) r.get("current_balance");
        BigDecimal heldBalance = r.get("held_balance") != null ? (BigDecimal) r.get("held_balance") : BigDecimal.ZERO;
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
                "PENDING_CORE"
        );
        remittance.setCallerCustomerId(callerCustomerId != null ? callerCustomerId : source.customerId());
        remittance.setIdempotencyKey(idempotencyKey);
        return remittanceRepository.save(remittance);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRejection(Remittance remittance, String reason, RiskResult risk) {
        remittance.setStatus("REJECTED");
        remittance.setReason(reason);
        if (risk != null) {
            remittance.setRiskScore(risk.score());
            remittance.setRiskDecision(risk.decision());
        }
        remittanceRepository.save(remittance);
        log.info("[ledger-service] Remittance ref={} marked REJECTED: {}", remittance.getReferenceNo(), reason);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordT24Posted(Remittance remittance, String ftReference) {
        remittance.setStatus("T24_POSTED");
        remittance.setFtReference(ftReference);
        remittanceRepository.save(remittance);
        log.info("[ledger-service] Remittance ref={} state updated to T24_POSTED with ftRef={}",
                remittance.getReferenceNo(), ftReference);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseHoldFunds(Remittance remittance, Long sourceAccountId, BigDecimal amount, String reason) {
        if (sourceAccountId != null && amount != null) {
            String releaseSql = "UPDATE dbo.ACCOUNT SET held_balance = CASE WHEN held_balance >= ? THEN held_balance - ? ELSE 0 END WHERE account_id = ?";
            jdbcTemplate.update(releaseSql, amount, amount, sourceAccountId);
        }
        remittance.setStatus("REJECTED");
        remittance.setReason(reason);
        remittanceRepository.save(remittance);
        log.info("[ledger-service] Held funds released for sourceAcc={} ref={}", sourceAccountId, remittance.getReferenceNo());
    }

    @Transactional
    public RemittanceResponse commitLedgerMutation(Remittance remittance, RemittanceRequest request,
                                                       Long sourceAccId, Long targetAccId,
                                                       BigDecimal amount, RiskResult risk, String ftReference) {

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

        // 2. Save LEDGER_TRANSACTION row
        TransactionRecord tx = new TransactionRecord(
                currentSource.id(),
                currentTarget.id(),
                amount,
                request.getCurrency(),
                request.getCurrency(),
                "P2P_REMITTANCE",
                remittance.getReferenceNo(),
                "SUCCESS",
                null
        );
        TransactionRecord savedTx = transactionRepository.save(tx);

        // 3. Save OUTBOX_EVENT row
        String payloadJson = String.format(
                "{\"remittanceId\":%d,\"referenceNo\":\"%s\",\"ftReference\":\"%s\",\"sourceAccountId\":%d,\"targetAccountId\":%d,\"amount\":%.4f,\"status\":\"POSTED\"}",
                remittance.getRemittanceId(), remittance.getReferenceNo(), ftReference,
                currentSource.id(), currentTarget.id(), amount
        );

        OutboxEvent event = new OutboxEvent();
        event.setTransactionId(savedTx.getTransactionId());
        event.setEventType("REMITTANCE_COMPLETED");
        event.setPayload(payloadJson);
        event.setStatus("PENDING");
        event.setCreatedDate(LocalDateTime.now());
        outboxEventRepository.save(event);

        // 4. Update REMITTANCE status to POSTED
        remittance.setStatus("POSTED");
        remittance.setFtReference(ftReference);
        remittanceRepository.save(remittance);

        log.info("[ledger-service] Ledger mutation committed ref={} sourceAfter={} targetAfter={}",
                remittance.getReferenceNo(), sourceAfter, targetAfter);

        BigDecimal riskScore = risk != null ? risk.score() : null;
        String riskDecision = risk != null ? risk.decision() : null;

        return new RemittanceResponse(
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
    }
}
