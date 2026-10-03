package com.bank.reconciliation;

import com.bank.reconciliation.model.sqlserver.TransactionRecord;
import com.bank.reconciliation.model.postgres.LedgerMutationAudit;
import com.bank.reconciliation.model.postgres.ReconciliationLog;
import com.bank.reconciliation.repository.sqlserver.TransactionRepository;
import com.bank.reconciliation.repository.postgres.LedgerMutationAuditRepository;
import com.bank.reconciliation.repository.postgres.ReconciliationLogRepository;
import com.bank.reconciliation.service.ReconciliationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReconciliationService.
 * Both Azure SQL and PostgreSQL repositories are mocked — no DB connections needed.
 */
@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock private TransactionRepository        transactionRepository;
    @Mock private LedgerMutationAuditRepository auditRepository;
    @Mock private ReconciliationLogRepository   reconLogRepository;
    @InjectMocks private ReconciliationService  service;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach void setUp() {
        try {
            var f = ReconciliationService.class.getDeclaredField("objectMapper");
            f.setAccessible(true); f.set(service, objectMapper);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private TransactionRecord ledgerTx(long id, String status, BigDecimal amount) {
        TransactionRecord tx = new TransactionRecord();
        sf(tx,"transactionId",id); sf(tx,"status",status); sf(tx,"amount",amount);
        sf(tx,"referenceNo","REF-"+id); return tx;
    }

    private LedgerMutationAudit pgAudit(long txId, BigDecimal amount) {
        LedgerMutationAudit a = new LedgerMutationAudit();
        sf(a,"transactionId",txId); sf(a,"amount",amount); return a;
    }

    private void sf(Object o,String n,Object v){try{var x=o.getClass().getDeclaredField(n);x.setAccessible(true);x.set(o,v);}catch(Exception e){throw new RuntimeException(e);}}

    // ── MATCHED: Azure SQL SUCCESS + Postgres amount matches ─────────────────
    @Test @DisplayName("reconcile: Azure SQL SUCCESS + matching Postgres audit = MATCHED")
    void reconcile_azureSqlSuccessMatchingAudit_returnsMatched() {
        TransactionRecord tx = ledgerTx(1L,"SUCCESS",new BigDecimal("500.0000"));
        LedgerMutationAudit audit = pgAudit(1L,new BigDecimal("500.0000"));
        when(auditRepository.findByTransactionId(1L)).thenReturn(Optional.of(audit));
        when(reconLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReconciliationLog log = service.reconcile(tx);

        assertThat(log.getReconStatus()).isEqualTo("MATCHED");
        assertThat(log.getLedgerStatus()).isEqualTo("SUCCESS");
        assertThat(log.getPostgresStatus()).isEqualTo("COMMITTED");
        verify(reconLogRepository,times(1)).save(any(ReconciliationLog.class));
    }

    // ── DRIFT: Azure SQL SUCCESS + Postgres amount differs ───────────────────
    @Test @DisplayName("reconcile: Azure SQL SUCCESS + mismatched Postgres amount = DRIFT_DETECTED")
    void reconcile_amountMismatch_returnsDriftDetected() {
        TransactionRecord tx = ledgerTx(2L,"SUCCESS",new BigDecimal("500.0000"));
        LedgerMutationAudit audit = pgAudit(2L,new BigDecimal("499.9999")); // wrong amount
        when(auditRepository.findByTransactionId(2L)).thenReturn(Optional.of(audit));
        when(reconLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReconciliationLog log = service.reconcile(tx);

        assertThat(log.getReconStatus()).isEqualTo("DRIFT_DETECTED");
        assertThat(log.getPostgresStatus()).isEqualTo("AMOUNT_MISMATCH");
    }

    // ── DRIFT: Azure SQL SUCCESS but no Postgres audit found ─────────────────
    @Test @DisplayName("reconcile: Azure SQL SUCCESS but Postgres audit missing = DRIFT_DETECTED")
    void reconcile_missingPostgresAudit_returnsDriftDetected() {
        TransactionRecord tx = ledgerTx(3L,"SUCCESS",new BigDecimal("200.0000"));
        when(auditRepository.findByTransactionId(3L)).thenReturn(Optional.empty());
        when(reconLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReconciliationLog log = service.reconcile(tx);

        assertThat(log.getReconStatus()).isEqualTo("DRIFT_DETECTED");
        assertThat(log.getPostgresStatus()).isEqualTo("MISSING_AUDIT");
    }

    // ── MATCHED: Azure SQL FAILED + no Postgres audit (not applicable)
    @Test @DisplayName("reconcile: Azure SQL FAILED + no Postgres audit = MATCHED (not applicable)")
    void reconcile_azureSqlFailed_noAudit_returnsMatched() {
        TransactionRecord tx = ledgerTx(4L,"FAILED",new BigDecimal("100.0000"));
        when(auditRepository.findByTransactionId(4L)).thenReturn(Optional.empty());
        when(reconLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReconciliationLog log = service.reconcile(tx);

        assertThat(log.getReconStatus()).isEqualTo("MATCHED");
        assertThat(log.getPostgresStatus()).isEqualTo("NOT_APPLICABLE");
    }

    // ── runFullSweep processes top-50 recent transactions ────────────────────
    @Test @DisplayName("runFullSweep: processes all recent Azure SQL transactions")
    void runFullSweep_processesAllTransactions() {
        TransactionRecord tx1 = ledgerTx(10L,"SUCCESS",new BigDecimal("100.0000"));
        TransactionRecord tx2 = ledgerTx(11L,"FAILED", new BigDecimal("200.0000"));
        when(transactionRepository.findTop50ByOrderByTransactionDateDesc()).thenReturn(List.of(tx1,tx2));
        when(auditRepository.findByTransactionId(10L)).thenReturn(Optional.of(pgAudit(10L,new BigDecimal("100.0000"))));
        when(auditRepository.findByTransactionId(11L)).thenReturn(Optional.empty());
        when(reconLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.runFullSweep();

        verify(reconLogRepository,times(2)).save(any(ReconciliationLog.class));
    }

    // ── getRecentLogs returns from repository ─────────────────────────────────
    @Test @DisplayName("getRecentLogs: delegates to repository findTop50")
    void getRecentLogs_delegatesToRepository() {
        ReconciliationLog rl1 = new ReconciliationLog(1L,"SUCCESS","COMMITTED","MATCHED");
        ReconciliationLog rl2 = new ReconciliationLog(2L,"SUCCESS","AMOUNT_MISMATCH","DRIFT_DETECTED");
        when(reconLogRepository.findTop50ByOrderByReconDateDesc()).thenReturn(List.of(rl1,rl2));

        List<ReconciliationLog> result = service.getRecentLogs();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getReconStatus()).isEqualTo("MATCHED");
        assertThat(result.get(1).getReconStatus()).isEqualTo("DRIFT_DETECTED");
    }

    // ── Kafka consumer: valid event triggers reconcile ───────────────────────
    @Test @DisplayName("onTransactionEvent: valid Kafka message triggers reconcile for that txId")
    void onTransactionEvent_validMessage_triggersReconcile() {
        TransactionRecord tx = ledgerTx(20L,"SUCCESS",new BigDecimal("300.0000"));
        when(transactionRepository.findById(20L)).thenReturn(Optional.of(tx));
        when(auditRepository.findByTransactionId(20L)).thenReturn(Optional.of(pgAudit(20L,new BigDecimal("300.0000"))));
        when(reconLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.onTransactionEvent("{\"transactionId\":20}");

        verify(reconLogRepository,times(1)).save(any());
    }

    // ── Kafka consumer: malformed JSON is swallowed ──────────────────────────
    @Test @DisplayName("onTransactionEvent: malformed Kafka message is swallowed gracefully")
    void onTransactionEvent_malformedJson_noException() {
        assertThatNoException().isThrownBy(() -> service.onTransactionEvent("BAD{{{"));
        verify(reconLogRepository,never()).save(any());
    }
}
