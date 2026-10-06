package com.bank.transaction.orchestrator.interest;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

public class InterestEodService {
    public record Result(LocalDate businessDate, int accounts, boolean replayed) {}
    private final InterestLedger ledger;
    private final InterestAccrualStore audit;
    private final LocalDate startDate;
    private final Clock clock;

    public InterestEodService(InterestLedger ledger, InterestAccrualStore audit, LocalDate startDate, Clock clock) {
        this.ledger = ledger;
        this.audit = audit;
        this.startDate = startDate;
        this.clock = clock;
    }

    public Result accrue(LocalDate date) {
        validateDate(date);
        return ledger.locked(() -> {
            boolean replay = audit.completed(date);
            // Never fabricate historical snapshots from today's balance. Committed snapshots can be replayed.
            if (!replay && !date.equals(LocalDate.now(clock)))
                throw new IllegalStateException("Historical EOD snapshot is missing for " + date);
            long job = ledger.startJob(date, "EOD_INTEREST_ACCRUAL");
            int count = 0;
            if (!replay) {
                List<InterestAccrualStore.Accrual> rows = ledger.activeAccounts().stream().map(account -> {
                    var rate = InterestPolicy.rate(account.type(), account.balance(), account.contractRate());
                    return new InterestAccrualStore.Accrual(account.id(), account.balance(), rate,
                            InterestPolicy.daily(account.balance(), rate));
                }).toList();
                audit.append(date, rows);
                count = rows.size();
            }
            ledger.finishJob(job);
            return new Result(date, count, replay);
        });
    }

    public Result postMonth(LocalDate end) {
        validateDate(end);
        if (end.getDayOfMonth() != end.lengthOfMonth())
            throw new IllegalArgumentException("Posting date must be the final calendar day of the month");
        LocalDate start = end.withDayOfMonth(1).isBefore(startDate) ? startDate : end.withDayOfMonth(1);
        return ledger.locked(() -> {
            var complete = audit.completedDates(start, end);
            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                if (!complete.contains(date))
                    throw new IllegalStateException("Cannot post an incomplete interest period; missing " + date);
            }
            long job = ledger.startJob(end, "EOD_INTEREST_POSTING");
            int count = 0;
            for (var total : audit.monthlyTotals(start, end)) {
                if (ledger.post(total, start, end, job)) count++;
            }
            ledger.finishJob(job);
            return new Result(end, count, count == 0);
        });
    }

    public void runToday() {
        LocalDate today = LocalDate.now(clock);
        if (today.isBefore(startDate)) return;
        accrue(today);
        if (today.getDayOfMonth() == today.lengthOfMonth()) postMonth(today);
    }

    /** Retry closed months after a crash between the daily commit and monthly posting. */
    public void recoverClosedMonths() {
        LocalDate today = LocalDate.now(clock);
        for (LocalDate end = startDate.withDayOfMonth(startDate.lengthOfMonth()); end.isBefore(today);
             end = end.plusMonths(1).withDayOfMonth(end.plusMonths(1).lengthOfMonth())) {
            if (!ledger.postingComplete(end)) postMonth(end);
        }
    }

    private void validateDate(LocalDate date) {
        if (date == null || date.isBefore(startDate) || date.isAfter(LocalDate.now(clock)))
            throw new IllegalArgumentException("Business date must be between " + startDate + " and today");
    }
}
