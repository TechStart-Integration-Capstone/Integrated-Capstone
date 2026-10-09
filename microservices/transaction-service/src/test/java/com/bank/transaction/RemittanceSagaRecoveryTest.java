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
import java.util.Map;

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
    @DisplayName("Committing a core-posted transfer preserves unrelated savings and transfer holds")
    void corePostedCommit_doesNotReleaseOtherReservations() {
        Remittance rem = remittance("T24_POSTED", Remittance.STEP_POSTED, LocalDateTime.now());
        when(jdbcTemplate.queryForObject(contains("FROM dbo.REMITTANCE WITH (UPDLOCK"), eq(String.class), eq(7L)))
                .thenReturn("T24_POSTED");
        when(jdbcTemplate.queryForList(anyString(), eq("10"), eq("10")))
                .thenReturn(List.of(Map.of("account_id", 10L, "customer_id", 1L,
                        "account_number", "ACC-SOURCE", "current_balance", new BigDecimal("129014.59"),
                        "held_balance", new BigDecimal("21100.00"))));
        when(jdbcTemplate.queryForList(anyString(), eq("20"), eq("20")))
                .thenReturn(List.of(Map.of("account_id", 20L, "customer_id", 2L,
                        "account_number", "ACC-TARGET", "current_balance", new BigDecimal("250000.00"),
                        "held_balance", BigDecimal.ZERO)));
        when(transactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RemittanceResponse response = ledgerService.commitLedgerMutation(rem, request(), 10L, 20L,
                new BigDecimal("250000.00"), null, "FT202610041001");

        assertThat(response.getStatus()).isEqualTo("POSTED");
        assertThat(ledgerService.resolveAccount("10").availableBalance()).isEqualByComparingTo("107914.59");
        verify(jdbcTemplate, never()).update(contains("UPDATE dbo.ACCOUNT"), any(Object[].class));
        verify(transactionRepository).save(any());
        verify(remittanceRepository).save(rem);
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

    private Remittance heldInWindow() {
        Remittance r = remittance(Remittance.STATUS_RESERVED, Remittance.INTERNAL_CLIENT_CANCEL_WINDOW, LocalDateTime.now());
        r.setFtReference(null);
        r.setCallerCustomerId(42L);
        r.setCancelUntil(LocalDateTime.now().plusSeconds(10));
        return r;
    }

    @Test
    @DisplayName("Send now: claims the cancellation window and posts to core banking immediately")
    void sendNow_dispatchesImmediately() {
        Remittance held = heldInWindow();
        when(remittanceRepository.findByReferenceNo("TX-PH-LOAN1")).thenReturn(java.util.Optional.of(held));
        RemittanceLedgerService ledger = mock(RemittanceLedgerService.class);
        when(ledger.claimCancelWindow(7L, Remittance.INTERNAL_WINDOW_CLOSED)).thenReturn(true);
        when(ledger.resolveAccount(anyString())).thenReturn(
                new RemittanceLedgerService.AccountInfo(10L, 42L, "001100000001", BigDecimal.TEN, BigDecimal.ZERO));
        when(t24AdapterClient.executeTransfer(any(), any(), any(), any(), any(), any()))
                .thenReturn(new T24Result("POSTED", "FT202610041003", null, null, false));

        new RemittanceSagaWorker(remittanceRepository, ledger, t24AdapterClient).sendNow("TX-PH-LOAN1", 42L);

        verify(ledger).recordT24Posted(held, "FT202610041003");
        verify(ledger).commitLedgerMutation(eq(held), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Send now after the window was already claimed (cancelled or sweeping) never sends a second time")
    void sendNow_lostClaim_doesNotDispatch() {
        Remittance held = heldInWindow();
        when(remittanceRepository.findByReferenceNo("TX-PH-LOAN1")).thenReturn(java.util.Optional.of(held));
        RemittanceLedgerService ledger = mock(RemittanceLedgerService.class);
        when(ledger.claimCancelWindow(anyLong(), anyString())).thenReturn(false);

        new RemittanceSagaWorker(remittanceRepository, ledger, t24AdapterClient).sendNow("TX-PH-LOAN1", 42L);

        verifyNoInteractions(t24AdapterClient);
        verify(ledger, never()).commitLedgerMutation(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Send now on someone else's transfer is forbidden")
    void sendNow_otherCustomer_forbidden() {
        when(remittanceRepository.findByReferenceNo("TX-PH-LOAN1")).thenReturn(java.util.Optional.of(heldInWindow()));
        RemittanceLedgerService ledger = mock(RemittanceLedgerService.class);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        new RemittanceSagaWorker(remittanceRepository, ledger, t24AdapterClient).sendNow("TX-PH-LOAN1", 99L))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verify(ledger, never()).claimCancelWindow(anyLong(), anyString());
    }

    @Test
    @DisplayName("Sweeper skips a matured window that Cancel or Send now already claimed")
    void sweeper_skipsClaimedWindow() {
        Remittance held = heldInWindow();
        when(remittanceRepository.findByStatusAndInternalStatusAndCancelUntilBefore(
                eq(Remittance.STATUS_RESERVED), eq(Remittance.INTERNAL_CLIENT_CANCEL_WINDOW), any())).thenReturn(List.of(held));
        RemittanceLedgerService ledger = mock(RemittanceLedgerService.class);
        when(ledger.claimCancelWindow(anyLong(), anyString())).thenReturn(false);

        new RemittanceSagaWorker(remittanceRepository, ledger, t24AdapterClient).resolvePendingSagas();

        verifyNoInteractions(t24AdapterClient);
    }
}
