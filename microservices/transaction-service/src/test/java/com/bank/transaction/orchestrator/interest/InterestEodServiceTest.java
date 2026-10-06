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
        service.runToday();
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
}
