package com.bank.transaction.orchestrator.interest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InterestEodServiceTest {
    private final InterestLedger ledger = mock(InterestLedger.class);
    private final InterestAccrualStore audit = mock(InterestAccrualStore.class);
    private final LocalDate end = LocalDate.of(2026, 10, 31);
    private InterestEodService service;

    @BeforeEach void setup() {
        when(ledger.locked(any())).thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        when(ledger.startJob(any(), any())).thenReturn(7L);
        service = new InterestEodService(ledger, audit, end.withDayOfMonth(1),
                Clock.fixed(Instant.parse("2026-10-31T15:59:59Z"), ZoneId.of("Asia/Manila")));
    }

    @Test void missingHistoricalSnapshotIsNeverCalculatedFromLiveBalances() {
        assertThatIllegalStateException().isThrownBy(() -> service.accrue(end.minusDays(1)));
        verify(ledger, never()).activeAccounts();
        verify(audit, never()).append(any(), any());
    }

    @Test void retryAfterPostgresCommitNeverReadsChangedBalances() {
        when(audit.completed(end.minusDays(1))).thenReturn(true);
        assertThat(service.accrue(end.minusDays(1)).replayed()).isTrue();
        verify(ledger, never()).activeAccounts();
        verify(audit, never()).append(any(), any());
        verify(ledger).finishJob(7L);
    }

    @Test void incompleteMonthCannotMoveMoney() {
        when(audit.completedDates(end.withDayOfMonth(1), end)).thenReturn(
                end.withDayOfMonth(1).datesUntil(end).collect(Collectors.toSet()));
        assertThatIllegalStateException().isThrownBy(() -> service.postMonth(end)).withMessageContaining("2026-10-31");
        verify(ledger, never()).post(any(), any(), any(), anyLong());
    }

    @Test void nightlyAccruesClosingDayBeforePosting() {
        when(ledger.activeAccounts()).thenReturn(List.of(new InterestLedger.Account(1, 1, "SAVINGS", "PHP",
                new BigDecimal("10000"), BigDecimal.ZERO)));
        when(audit.completedDates(end.withDayOfMonth(1), end)).thenReturn(
                end.withDayOfMonth(1).datesUntil(end.plusDays(1)).collect(Collectors.toSet()));
        when(audit.monthlyTotals(any(), any())).thenReturn(List.of(new InterestAccrualStore.MonthlyTotal(1, new BigDecimal("33.97"))));
        service.runBusinessDate(end);
        var order = inOrder(audit, ledger);
        order.verify(audit).append(eq(end), argThat(rows -> rows.get(0).amount().compareTo(new BigDecimal("1.095890")) == 0));
        order.verify(ledger).post(any(), eq(end.withDayOfMonth(1)), eq(end), eq(7L));
    }

    @Test void rejectsFutureAndNonMonthEndDates() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.accrue(end.plusDays(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> service.postMonth(end.minusDays(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> service.accrue(end.minusMonths(1)));
    }

    @Test void controllerRequiresAdminEvenInsidePerimeter() {
        var controller = new InterestEodController(service);
        assertThatThrownBy(() -> controller.accrue("ROLE_CUSTOMER", end))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("403");
        verifyNoInteractions(ledger, audit);
    }

    @Test void delayedRunKeepsPreviousBusinessDateAndNeverCapturesToday() {
        var delayed = new InterestEodService(ledger, audit, end.withDayOfMonth(1),
                Clock.fixed(Instant.parse("2026-10-31T16:00:01Z"), ZoneId.of("Asia/Manila")));
        assertThatThrownBy(() -> delayed.runBusinessDate(end)).hasMessageContaining("Historical EOD snapshot");
        verify(audit).completed(end);
        verify(audit, never()).completed(end.plusDays(1));
        verify(ledger, never()).activeAccounts();
    }

    @Test void sourceReadCrossingMidnightCannotSealWrongSnapshot() {
        Clock clock = mock(Clock.class);
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Manila"));
        when(clock.instant()).thenReturn(Instant.parse("2026-10-31T15:59:59Z"),
                Instant.parse("2026-10-31T15:59:59Z"), Instant.parse("2026-10-31T16:00:00Z"));
        var crossing = new InterestEodService(ledger, audit, end.withDayOfMonth(1), clock);
        when(ledger.activeAccounts()).thenReturn(List.of());
        assertThatThrownBy(() -> crossing.accrue(end)).hasMessageContaining("crossed midnight");
        verify(audit, never()).append(any(), any());
    }

    @Test void recoveryRejectsUnconfirmedBackfillAndDuplicateAccounts() {
        var row = new InterestRecoveryRequest.HistoricalAccount(1, "SAVINGS", BigDecimal.TEN, null);
        var unconfirmed = new InterestRecoveryRequest(InterestRecoveryRequest.Mode.BACKFILL, "reason", "case-1", false, List.of());
        assertThatIllegalArgumentException().isThrownBy(() -> service.prepareBackfill(end.minusDays(1), unconfirmed, "admin"));
        var duplicates = new InterestRecoveryRequest(InterestRecoveryRequest.Mode.BACKFILL, "reason", "case-1", true, List.of(row, row));
        assertThatThrownBy(() -> service.prepareBackfill(end.minusDays(1), duplicates, "admin")).hasMessageContaining("distinct");
        verifyNoInteractions(ledger, audit);
    }

    @Test void recoveryEndpointsRequireAdminAndAuthenticatedActor() {
        var controller = new InterestEodController(service);
        var request = new InterestRecoveryRequest(InterestRecoveryRequest.Mode.BACKFILL, "reason", "case-1", true, List.of());
        assertThatThrownBy(() -> controller.resolve("ROLE_CUSTOMER", "customer", end.minusDays(1), request)).hasMessageContaining("403");
        assertThatThrownBy(() -> controller.missing("ROLE_CUSTOMER", end)).hasMessageContaining("403");
        assertThatThrownBy(() -> controller.resolve("ROLE_ADMIN", null, end.minusDays(1), request)).hasMessageContaining("401");
        verifyNoInteractions(ledger, audit);
    }

    @Test void missingDayListingExcludesFutureDatesAndDaysBeforeActivation() {
        var first = end.minusDays(2);
        var recent = new InterestEodService(ledger, audit, first,
                Clock.fixed(Instant.parse("2026-10-31T04:00:00Z"), ZoneId.of("Asia/Manila")));
        when(audit.completedDates(first, end.minusDays(1))).thenReturn(java.util.Set.of(first));
        assertThat(recent.missingDays(end)).containsExactly(end.minusDays(1));
    }
}
