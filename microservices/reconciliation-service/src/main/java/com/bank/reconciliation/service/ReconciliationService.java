package com.bank.reconciliation.service;

import com.bank.reconciliation.model.oracle.TransactionRecord;
import com.bank.reconciliation.model.postgres.LedgerMutationAudit;
import com.bank.reconciliation.model.postgres.ReconciliationLog;
import com.bank.reconciliation.repository.oracle.TransactionRepository;
import com.bank.reconciliation.repository.postgres.LedgerMutationAuditRepository;
import com.bank.reconciliation.repository.postgres.ReconciliationLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Cross-database reconciliation service.
 *
 * Compares Azure SQL LEDGER_TRANSACTION records against PostgreSQL LEDGER_MUTATION_AUDIT
 * to detect and flag data drift (MATCHED vs DRIFT_DETECTED).
 *
 * Two triggers:
 *  1. Real-time — Kafka listener on remittance.events / ledger.transaction.events
 *  2. Scheduled — every 15 minutes (configurable via app.reconciliation.cron)
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final TransactionRepository         transactionRepository;
    private final LedgerMutationAuditRepository auditRepository;
    private final ReconciliationLogRepository   reconLogRepository;
    private final ObjectMapper                  objectMapper;

    public ReconciliationService(TransactionRepository transactionRepository,
                                 LedgerMutationAuditRepository auditRepository,
                                 ReconciliationLogRepository reconLogRepository,
                                 ObjectMapper objectMapper) {
        this.transactionRepository = transactionRepository;
        this.auditRepository       = auditRepository;
        this.reconLogRepository    = reconLogRepository;
        this.objectMapper          = objectMapper;
    }

    // ── Real-time reconciliation triggered by Kafka ───────────────────────────

    @KafkaListener(topics = {"remittance.events", "ledger.transaction.events"},
                   groupId = "reconciliation-service-group")
    public void onTransactionEvent(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);

            // loan.* events carry no ledger leg; the transfer itself is reconciled via LEDGER_TRANSACTION.
            if (node.path("eventType").asText("").startsWith("loan.")) return;

            Long transactionId = node.has("transactionId") ? node.get("transactionId").asLong() : null;
            if (transactionId != null) {
                transactionRepository.findById(transactionId)
                        .ifPresent(this::saveReconLog);
            }
        } catch (Exception e) {
            log.error("[reconciliation-service] Error processing Kafka event: {}", e.getMessage());
        }
    }

    // ── Scheduled sweep every 15 minutes ─────────────────────────────────────

    @Scheduled(cron = "${app.reconciliation.cron:0 */15 * * * *}")
    @Transactional("postgresTransactionManager")
    public void scheduledReconciliation() {
        log.info("[reconciliation-service] Running scheduled 15-minute reconciliation sweep...");
        List<TransactionRecord> transactions = transactionRepository.findTop50ByOrderByTransactionDateDesc();
        transactions.forEach(this::saveReconLog);
        log.info("[reconciliation-service] Reconciliation sweep completed for {} transactions.",
                transactions.size());
    }

    @Transactional("postgresTransactionManager")
    public ReconciliationLog reconcile(TransactionRecord tx) {
        return saveReconLog(tx);
    }

    // ── Core reconciliation logic ─────────────────────────────────────────────

    private ReconciliationLog saveReconLog(TransactionRecord tx) {
        Optional<LedgerMutationAudit> auditOpt = auditRepository.findByTransactionId(tx.getTransactionId());

        String azureSqlStatus = tx.getStatus();
        String postgresStatus;
        String reconStatus;

        // account_id is populated from the PostgreSQL audit row when one exists;
        // null for failed/missing transactions where no audit row was written.
        Long accountId = null;

        if (auditOpt.isPresent()) {
            LedgerMutationAudit audit = auditOpt.get();
            accountId = audit.getAccountId();   // populated from the audit row

            if (audit.getAmount().compareTo(tx.getAmount()) == 0) {
                postgresStatus = "COMMITTED";
                reconStatus    = "MATCHED";
            } else {
                postgresStatus = "AMOUNT_MISMATCH";
                reconStatus    = "DRIFT_DETECTED";
            }
        } else {
            if ("SUCCESS".equalsIgnoreCase(azureSqlStatus)) {
                postgresStatus = "MISSING_AUDIT";
                reconStatus    = "DRIFT_DETECTED";
            } else {
                postgresStatus = "NOT_APPLICABLE";
                reconStatus    = "MATCHED";
            }
        }

        Optional<ReconciliationLog> existingOpt = reconLogRepository.findByTransactionId(tx.getTransactionId());
        ReconciliationLog recon;
        if (existingOpt.isPresent()) {
            recon = existingOpt.get();
            recon.setAccountId(accountId);
            recon.setAzureSqlStatus(azureSqlStatus);
            recon.setPostgresStatus(postgresStatus);
            recon.setReconStatus(reconStatus);
            recon.setReconDate(java.time.LocalDateTime.now());
        } else {
            recon = new ReconciliationLog(tx.getTransactionId(), accountId,
                    azureSqlStatus, postgresStatus, reconStatus);
        }

        recon = reconLogRepository.save(recon);

        log.info("[reconciliation-service] txId={} azureSql={} postgres={} recon={}",
                tx.getTransactionId(), azureSqlStatus, postgresStatus, reconStatus);

        return recon;
    }

    public List<ReconciliationLog> getRecentLogs() {
        return reconLogRepository.findTop50ByOrderByReconDateDesc();
    }

    @Transactional("postgresTransactionManager")
    public void runFullSweep() {
        scheduledReconciliation();
    }
}
