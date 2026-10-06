package com.bank.reconciliation;

import com.bank.reconciliation.model.oracle.TransactionRecord;
import com.bank.reconciliation.model.postgres.LedgerMutationAudit;
import com.bank.reconciliation.model.postgres.ReconciliationLog;
import com.bank.reconciliation.repository.oracle.TransactionRepository;
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
 * Both Oracle and PostgreSQL repositories are mocked — no DB connections needed.
 */
@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock private TransactionRepository        transactionRepository;
    @Mock private LedgerMutationAuditRepository auditRepository;
    @Mock private ReconciliationLogRepository   reconLogRepository;
    private ReconciliationService               service;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach void setUp() {
        service = new ReconciliationService(transactionRepository, auditRepository, reconLogRepository, objectMapper);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private TransactionRecord oracleTx(long id, String status, BigDecimal amount) {
        TransactionRecord tx = new TransactionRecord();
        sf(tx,"transactionId",id); sf(tx,"status",status); sf(tx,"amount",amount);
        sf(tx,"referenceNo","REF-"+id); return tx;
    }

    private LedgerMutationAudit pgAudit(long txId, BigDecimal amount) {
        LedgerMutationAudit a = new LedgerMutationAudit();
        sf(a,"transactionId",txId); sf(a,"amount",amount); return a;
    }

    private void sf(Object o,String n,Object v){try{var x=o.getClass().getDeclaredField(n);x.setAccessible(true);x.set(o,v);}catch(Exception e){throw new RuntimeException(e);}}

    // ── MATCHED: Oracle SUCCESS + Postgres amount matches ────────────────────
    @Test @DisplayName("reconcile: Oracle SUCCESS + matching Postgres audit = MATCHED")
    void reconcile_oracleSuccessMatchingAudit_returnsMatched() {
        TransactionRecord tx = oracleTx(1L,"SUCCESS",new BigDecimal("500.0000"));
        LedgerMutationAudit audit = pgAudit(1L,new BigDecimal("500.0000"));
        when(auditRepository.findByTransactionId(1L)).thenReturn(Optional.of(audit));
        when(reconLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReconciliationLog log = service.reconcile(tx);

        assertThat(log.getReconStatus()).isEqualTo("MATCHED");
        assertThat(log.getOracleStatus()).isEqualTo("SUCCESS");
        assertThat(log.getPostgresStatus()).isEqualTo("COMMITTED");
        verify(reconLogRepository,times(1)).save(any(ReconciliationLog.class));
    }

    // ── DRIFT: Oracle SUCCESS + Postgres amount differs ──────────────────────
    @Test @DisplayName("reconcile: Oracle SUCCESS + mismatched Postgres amount = DRIFT_DETECTED")
    void reconcile_amountMismatch_returnsDriftDetected() {
        TransactionRecord tx = oracleTx(2L,"SUCCESS",new BigDecimal("500.0000"));
        LedgerMutationAudit audit = pgAudit(2L,new BigDecimal("499.9999")); // wrong amount
        when(auditRepository.findByTransactionId(2L)).thenReturn(Optional.of(audit));
        when(reconLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReconciliationLog log = service.reconcile(tx);

        assertThat(log.getReconStatus()).isEqualTo("DRIFT_DETECTED");
        assertThat(log.getPostgresStatus()).isEqualTo("AMOUNT_MISMATCH");
    }

    // ── DRIFT: Oracle SUCCESS but no Postgres audit found ────────────────────
    @Test @DisplayName("reconcile: Oracle SUCCESS but Postgres audit missing = DRIFT_DETECTED")
    void reconcile_missingPostgresAudit_returnsDriftDetected() {
        TransactionRecord tx = oracleTx(3L,"SUCCESS",new BigDecimal("200.0000"));
        when(auditRepository.findByTransactionId(3L)).thenReturn(Optional.empty());
        when(reconLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReconciliationLog log = service.reconcile(tx);

        assertThat(log.getReconStatus()).isEqualTo("DRIFT_DETECTED");
        assertThat(log.getPostgresStatus()).isEqualTo("MISSING_AUDIT");
    }

    // ── MATCHED: Oracle FAILED + no Postgres audit (expected — not applicable) 
    @Test @DisplayName("reconcile: Oracle FAILED + no Postgres audit = MATCHED (not applicable)")
    void reconcile_oracleFailed_noAudit_returnsMatched() {
        TransactionRecord tx = oracleTx(4L,"FAILED",new BigDecimal("100.0000"));
        when(auditRepository.findByTransactionId(4L)).thenReturn(Optional.empty());
        when(reconLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReconciliationLog log = service.reconcile(tx);

        assertThat(log.getReconStatus()).isEqualTo("MATCHED");
        assertThat(log.getPostgresStatus()).isEqualTo("NOT_APPLICABLE");
    }

    // ── runFullSweep processes top-50 recent transactions ────────────────────
    @Test @DisplayName("runFullSweep: processes all recent Oracle transactions")
    void runFullSweep_processesAllTransactions() {
        TransactionRecord tx1 = oracleTx(10L,"SUCCESS",new BigDecimal("100.0000"));
        TransactionRecord tx2 = oracleTx(11L,"FAILED", new BigDecimal("200.0000"));
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
        TransactionRecord tx = oracleTx(20L,"SUCCESS",new BigDecimal("300.0000"));
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
