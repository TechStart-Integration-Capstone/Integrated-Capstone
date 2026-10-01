package com.bank.ledger.service;

import com.bank.ledger.model.oracle.TransactionRecord;
import com.bank.ledger.model.postgres.LedgerMutationAudit;
import com.bank.ledger.model.postgres.ReconciliationLog;
import com.bank.ledger.repository.oracle.TransactionRepository;
import com.bank.ledger.repository.postgres.LedgerMutationAuditRepository;
import com.bank.ledger.repository.postgres.ReconciliationLogRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final TransactionRepository transactionRepository;
    private final LedgerMutationAuditRepository ledgerMutationAuditRepository;
    private final ReconciliationLogRepository reconciliationLogRepository;
    private final Counter reconDriftCounter;

    public ReconciliationService(TransactionRepository transactionRepository,
                                 LedgerMutationAuditRepository ledgerMutationAuditRepository,
                                 ReconciliationLogRepository reconciliationLogRepository,
                                 @Autowired(required = false) MeterRegistry meterRegistry) {
        this.transactionRepository = transactionRepository;
        this.ledgerMutationAuditRepository = ledgerMutationAuditRepository;
        this.reconciliationLogRepository = reconciliationLogRepository;
        this.reconDriftCounter = meterRegistry != null 
                ? Counter.builder("ledger.recon.drift")
                         .description("Count of cross-datastore reconciliation drift anomalies detected")
                         .register(meterRegistry)
                : null;
    }

    /**
     * Requirement: @Scheduled Reconciliation Sweep every 15 minutes (lagged window: now-20min -> now-2min).
     * Prevents false-positive drift alarms on in-flight Kafka events.
     */
    @Scheduled(cron = "${app.reconciliation.cron:0 */15 * * * *}")
    @Transactional
    public void runScheduledSystemWideReconciliation() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minusMinutes(20);
        LocalDateTime windowEnd = now.minusMinutes(2);

        List<TransactionRecord> transactions = transactionRepository.findByStatusAndTransactionDateBetween(
                "SUCCESS", windowStart, windowEnd
        );

        if (transactions.isEmpty()) {
            return;
        }

        Set<Long> txIds = transactions.stream().map(TransactionRecord::getTransactionId).collect(Collectors.toSet());
        List<LedgerMutationAudit> audits = ledgerMutationAuditRepository.findByTransactionIdIn(txIds);
        Map<Long, List<LedgerMutationAudit>> auditsByTxId = audits.stream()
                .collect(Collectors.groupingBy(LedgerMutationAudit::getTransactionId));

        for (TransactionRecord tx : transactions) {
            List<LedgerMutationAudit> txAudits = auditsByTxId.getOrDefault(tx.getTransactionId(), Collections.emptyList());
            reconcileTransactionLegs(tx, txAudits);
        }
    }

    /**
     * Near-Real-Time verification triggered by Kafka Event Stream
     */
    @Transactional
    public void validateEventRealTime(Long transactionId) {
        transactionRepository.findById(transactionId).ifPresent(tx -> {
            List<LedgerMutationAudit> txAudits = ledgerMutationAuditRepository.findByTransactionId(tx.getTransactionId());
            reconcileTransactionLegs(tx, txAudits);
        });
    }

    private void reconcileTransactionLegs(TransactionRecord tx, List<LedgerMutationAudit> audits) {
        // Source leg follows the operation: DEBIT for debits and transfers, CREDIT for deposits
        String sourceEntryType = "CREDIT".equalsIgnoreCase(tx.getOperation()) ? "CREDIT" : "DEBIT";
        reconcileLeg(tx, tx.getFromAccountId(), sourceEntryType, audits);

        // Internal transfer: target leg is always a CREDIT
        if (tx.getToAccountId() != null) {
            reconcileLeg(tx, tx.getToAccountId(), "CREDIT", audits);
        }
    }

    private void reconcileLeg(TransactionRecord tx, Long accountId, String expectedEntryType, List<LedgerMutationAudit> audits) {
        Optional<LedgerMutationAudit> match = audits.stream()
                .filter(a -> accountId.equals(a.getAccountId()))
                .findFirst();

        String oracleStatus = tx.getStatus();
        String postgresStatus;
        String reconStatus;
        List<String> mismatches = new ArrayList<>();

        if (match.isPresent()) {
            LedgerMutationAudit audit = match.get();
            if (audit.getAmount().compareTo(tx.getAmount()) != 0) {
                mismatches.add("AMOUNT_MISMATCH");
            }
            if (audit.getEntryType() != null && !audit.getEntryType().equalsIgnoreCase(expectedEntryType)) {
                mismatches.add("ENTRY_TYPE_MISMATCH");
            }
            if (audit.getCurrency() != null && !audit.getCurrency().equalsIgnoreCase(tx.getSourceCurrency())) {
                mismatches.add("CURRENCY_MISMATCH");
            }
            if (mismatches.isEmpty()) {
                postgresStatus = "COMMITTED";
                reconStatus = "MATCHED";
            } else {
                postgresStatus = "MISMATCH";
                reconStatus = "DRIFT_DETECTED";
            }
        } else {
            if ("SUCCESS".equalsIgnoreCase(oracleStatus)) {
                postgresStatus = "MISSING_AUDIT";
                reconStatus = "DRIFT_DETECTED";
                mismatches.add("MISSING_AUDIT_LEG");
            } else {
                postgresStatus = "NOT_APPLICABLE";
                reconStatus = "MATCHED";
            }
        }

        if ("DRIFT_DETECTED".equals(reconStatus)) {
            if (reconDriftCounter != null) {
                reconDriftCounter.increment();
            }
            log.error("DRIFT DETECTED: txId={}, accountId={}, mismatches={}", tx.getTransactionId(), accountId, mismatches);
        }

        String mismatchFields = String.join(", ", mismatches);
        upsertReconciliationLog(tx.getTransactionId(), accountId, oracleStatus, postgresStatus, reconStatus, mismatchFields);
    }

    private void upsertReconciliationLog(Long txId, Long accountId, String oracleStatus, String postgresStatus,
                                        String reconStatus, String mismatchFields) {
        Optional<ReconciliationLog> existing = reconciliationLogRepository.findByTransactionIdAndAccountId(txId, accountId);
        if (existing.isPresent()) {
            ReconciliationLog logEntry = existing.get();
            logEntry.setOracleStatus(oracleStatus);
            logEntry.setPostgresStatus(postgresStatus);
            logEntry.setReconStatus(reconStatus);
            logEntry.setMismatchFields(mismatchFields);
            logEntry.setCheckCount(logEntry.getCheckCount() + 1);
            logEntry.setLastCheckedAt(LocalDateTime.now());
            reconciliationLogRepository.save(logEntry);
        } else {
            ReconciliationLog newEntry = new ReconciliationLog(
                    txId, accountId, oracleStatus, postgresStatus, reconStatus, mismatchFields
            );
            reconciliationLogRepository.save(newEntry);
        }
    }

    public List<ReconciliationLog> getRecentReconciliationLogs() {
        return reconciliationLogRepository.findTop50ByOrderByReconDateDesc();
    }
}
