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

@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final TransactionRepository transactionRepository;
    private final LedgerMutationAuditRepository auditRepository;
    private final ReconciliationLogRepository reconLogRepository;
    private final ObjectMapper objectMapper;

    public ReconciliationService(TransactionRepository transactionRepository,
                                 LedgerMutationAuditRepository auditRepository,
                                 ReconciliationLogRepository reconLogRepository,
                                 ObjectMapper objectMapper) {
        this.transactionRepository = transactionRepository;
        this.auditRepository = auditRepository;
        this.reconLogRepository = reconLogRepository;
        this.objectMapper = objectMapper;
    }

    // Real-time reconciliation triggered by Kafka event
    @KafkaListener(topics = "ledger.transaction.events", groupId = "reconciliation-service-group")
    public void onTransactionEvent(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            Long transactionId = node.has("transactionId") ? node.get("transactionId").asLong() : null;
            if (transactionId != null) {
                transactionRepository.findById(transactionId).ifPresent(tx -> saveReconLog(tx));
            }
        } catch (Exception e) {
            log.error("[reconciliation-service] Error processing Kafka event: {}", e.getMessage());
        }
    }

    // Scheduled sweep every 15 minutes
    @Scheduled(cron = "${app.reconciliation.cron:0 */15 * * * *}")
    public void scheduledReconciliation() {
        log.info("[reconciliation-service] Running scheduled 15-minute reconciliation sweep...");
        List<TransactionRecord> transactions = transactionRepository.findTop50ByOrderByTransactionDateDesc();
        transactions.forEach(tx -> saveReconLog(tx));
        log.info("[reconciliation-service] Reconciliation sweep completed for {} transactions.", transactions.size());
    }

    @Transactional("postgresTransactionManager")
    public ReconciliationLog reconcile(TransactionRecord tx) {
        return saveReconLog(tx);
    }

    // Internal method — called both from @Transactional wrapper and directly (safe either way)
    private ReconciliationLog saveReconLog(TransactionRecord tx) {
        Optional<LedgerMutationAudit> auditOpt = auditRepository.findByTransactionId(tx.getTransactionId());
        String oracleStatus = tx.getStatus();
        String postgresStatus;
        String reconStatus;

        if (auditOpt.isPresent()) {
            LedgerMutationAudit audit = auditOpt.get();
            if (audit.getAmount().compareTo(tx.getAmount()) == 0) {
                postgresStatus = "COMMITTED";
                reconStatus = "MATCHED";
            } else {
                postgresStatus = "AMOUNT_MISMATCH";
                reconStatus = "DRIFT_DETECTED";
            }
        } else {
            if ("SUCCESS".equalsIgnoreCase(oracleStatus)) {
                postgresStatus = "MISSING_AUDIT";
                reconStatus = "DRIFT_DETECTED";
            } else {
                postgresStatus = "NOT_APPLICABLE";
                reconStatus = "MATCHED";
            }
        }

        ReconciliationLog recon = reconLogRepository.save(
                new ReconciliationLog(tx.getTransactionId(), oracleStatus, postgresStatus, reconStatus));
        log.info("[reconciliation-service] txId={} oracle={} postgres={} recon={}",
                tx.getTransactionId(), oracleStatus, postgresStatus, reconStatus);
        return recon;
    }

    public List<ReconciliationLog> getRecentLogs() {
        return reconLogRepository.findTop50ByOrderByReconDateDesc();
    }

    public void runFullSweep() {
        scheduledReconciliation();
    }
}
