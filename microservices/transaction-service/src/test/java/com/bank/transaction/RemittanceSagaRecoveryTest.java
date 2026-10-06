package com.bank.transaction;

import com.bank.transaction.client.T24AdapterClient;
import com.bank.transaction.dto.RemittanceRequest;
import com.bank.transaction.dto.RemittanceResponse;
import com.bank.transaction.dto.T24Result;
import com.bank.transaction.model.Remittance;
import com.bank.transaction.repository.OutboxEventRepository;
import com.bank.transaction.repository.RemittanceRepository;
import com.bank.transaction.repository.TransactionRepository;
import com.bank.transaction.service.RemittanceLedgerService;
import com.bank.transaction.service.RemittanceSagaWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** A remittance must reach the ledger exactly once, however many times the orchestrator and saga worker commit it. */
class RemittanceSagaRecoveryTest {

    private RemittanceRepository remittanceRepository;
    private TransactionRepository transactionRepository;
    private JdbcTemplate jdbcTemplate;
    private T24AdapterClient t24AdapterClient;
    private RemittanceLedgerService ledgerService;

    @BeforeEach
    void setUp() {
        remittanceRepository = mock(RemittanceRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        t24AdapterClient = mock(T24AdapterClient.class);
        ledgerService = new RemittanceLedgerService(remittanceRepository, transactionRepository,
                mock(OutboxEventRepository.class), jdbcTemplate);
    }

    private static Remittance remittance(String status, String internalStatus, LocalDateTime updatedAt) {
        Remittance r = new Remittance("TX-PH-LOAN1", 10L, 20L, new BigDecimal("250000.00"), "PHP", status);
        r.setRemittanceId(7L);
        r.setTransactionType("LOAN_DISBURSEMENT");
        r.setInternalStatus(internalStatus);
        r.setFtReference("FT202610041001");
        ReflectionTestUtils.setField(r, "updatedAt", updatedAt);
        return r;
    }

    private static RemittanceRequest request() {
        RemittanceRequest request = new RemittanceRequest();
        request.setSourceAccountId("10");
        request.setTargetAccountId("20");
        request.setAmount(new BigDecimal("250000.00"));
        request.setCurrency("PHP");
        return request;
    }

    @Test
    @DisplayName("Commit when the ledger row already exists → replay, no second debit/credit or LEDGER_TRANSACTION row")
    void commit_isIdempotent() {
        Remittance rem = remittance(Remittance.STATUS_PROCESSING, Remittance.STEP_POSTED, LocalDateTime.now());
        when(jdbcTemplate.queryForObject(contains("FROM dbo.REMITTANCE WITH (UPDLOCK"), eq(String.class), eq(7L)))
                .thenReturn("Processing");
        when(jdbcTemplate.queryForList(contains("FROM dbo.LEDGER_TRANSACTION"), eq(Long.class), eq("TX-PH-LOAN1")))
                .thenReturn(List.of(501L));

        RemittanceResponse response = ledgerService.commitLedgerMutation(rem, request(), 10L, 20L,
                new BigDecimal("250000.00"), null, "FT202610041001");

        assertThat(response.getStatus()).isEqualTo("POSTED");
        assertThat(response.getTransactionId()).isEqualTo(501L);
        verify(jdbcTemplate, never()).update(contains("UPDATE dbo.ACCOUNT"), any(), any(), any(), any()); // debit source
        verify(jdbcTemplate, never()).update(contains("UPDATE dbo.ACCOUNT"), any(), any());               // credit target
        verify(transactionRepository, never()).save(any());
        // The status left behind is healed so the saga worker stops picking the row up.
        assertThat(rem.getStatus()).isEqualTo(Remittance.STATUS_POSTED);
        verify(remittanceRepository).save(rem);
    }

    @Test
    @DisplayName("Saga worker: T24-posted Processing row is committed; a row still being handled by the orchestrator is left alone")
    void sagaWorker_commitsSettledT24PostedRowsOnly() {
        Remittance stuck = remittance(Remittance.STATUS_PROCESSING, Remittance.STEP_POSTED, LocalDateTime.now().minusMinutes(5));
        Remittance inFlight = remittance(Remittance.STATUS_PROCESSING, Remittance.STEP_POSTED, LocalDateTime.now());
        inFlight.setRemittanceId(8L);
        ReflectionTestUtils.setField(inFlight, "updatedAt", LocalDateTime.now());
        when(remittanceRepository.findByStatus(Remittance.STATUS_PROCESSING)).thenReturn(List.of(stuck, inFlight));

        RemittanceLedgerService ledger = mock(RemittanceLedgerService.class);
        new RemittanceSagaWorker(remittanceRepository, ledger, t24AdapterClient).resolvePendingSagas();

        verify(ledger).commitLedgerMutation(eq(stuck), any(), eq(10L), eq(20L), eq(new BigDecimal("250000.00")), isNull(), eq("FT202610041001"));
        verify(ledger, never()).commitLedgerMutation(eq(inFlight), any(), any(), any(), any(), any(), any());
        // Already posted by T24: no second T24 call.
        verifyNoInteractions(t24AdapterClient);
    }

    @Test
    @DisplayName("Saga worker: Processing row still waiting on T24 is inquired, then committed once T24 reports POSTED")
    void sagaWorker_inquiresThenCommits() {
        Remittance waiting = remittance(Remittance.STATUS_PROCESSING, Remittance.STEP_AUTHORIZED, LocalDateTime.now().minusMinutes(5));
        waiting.setFtReference(null);
        ReflectionTestUtils.setField(waiting, "updatedAt", LocalDateTime.now().minusMinutes(5));
        when(remittanceRepository.findByStatus(Remittance.STATUS_PROCESSING)).thenReturn(List.of(waiting));
        RemittanceLedgerService ledger = mock(RemittanceLedgerService.class);
        when(ledger.resolveAccount(anyString())).thenReturn(
                new RemittanceLedgerService.AccountInfo(10L, 1L, "PH1000000LOAN", BigDecimal.ZERO, BigDecimal.ZERO));
        when(t24AdapterClient.executeTransfer(any(), any(), any(), any(), any(), any()))
                .thenReturn(new T24Result("POSTED", "FT202610041002", null, null, true));

        new RemittanceSagaWorker(remittanceRepository, ledger, t24AdapterClient).resolvePendingSagas();

        verify(ledger).recordT24Posted(waiting, "FT202610041002");
        verify(ledger).commitLedgerMutation(eq(waiting), any(), any(), any(), any(), any(), any());
    }
}
