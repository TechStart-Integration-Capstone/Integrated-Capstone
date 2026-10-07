package com.bank.transaction.orchestrator.interest;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class InterestEodService {
    public record Result(LocalDate businessDate, int accounts, boolean replayed) {}
    public record RecoveryResult(Map<LocalDate, List<LocalDate>> blockedPeriods) {}
    public record PeriodOverview(LocalDate startDate, LocalDate businessDate, LocalDate periodStart,
                                 LocalDate periodEnd, String postingStatus, List<LocalDate> missingDays,
                                 java.util.Set<LocalDate> completedDays,
                                 List<InterestAccrualStore.ProposalSummary> proposals) {}
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
                if (!date.equals(LocalDate.now(clock)))
                    throw new IllegalStateException("EOD capture crossed midnight; recover the historical snapshot for " + date);
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

    public void runBusinessDate(LocalDate businessDate) {
        if (businessDate.isBefore(startDate)) return;
        accrue(businessDate);
        if (businessDate.getDayOfMonth() == businessDate.lengthOfMonth()) postMonth(businessDate);
    }

    /** Retry closed months after a crash between the daily commit and monthly posting. */
    public RecoveryResult recoverClosedMonths() {
        LocalDate today = LocalDate.now(clock);
        Map<LocalDate, List<LocalDate>> blocked = new LinkedHashMap<>();
        for (LocalDate end = startDate.withDayOfMonth(startDate.lengthOfMonth()); end.isBefore(today);
             end = end.plusMonths(1).withDayOfMonth(end.plusMonths(1).lengthOfMonth())) {
            if (!ledger.postingComplete(end)) {
                List<LocalDate> missing = missingDays(end);
                if (missing.isEmpty()) postMonth(end);
                else blocked.put(end, missing);
            }
        }
        return new RecoveryResult(Map.copyOf(blocked));
    }

    /** Only elapsed days are missing; future days and today's unfinished cutoff are not outages. */
    public List<LocalDate> missingDays(LocalDate periodEnd) {
        if (periodEnd == null || periodEnd.getDayOfMonth() != periodEnd.lengthOfMonth() || periodEnd.isBefore(startDate))
            throw new IllegalArgumentException("Select a calendar month-end on or after the interest start date");
        LocalDate first = periodEnd.withDayOfMonth(1).isBefore(startDate) ? startDate : periodEnd.withDayOfMonth(1);
        LocalDate today = LocalDate.now(clock);
        LocalDate last = periodEnd.isBefore(today) ? periodEnd : today.minusDays(1);
        if (last.isBefore(first)) return List.of();
        var complete = audit.completedDates(first, last);
        return first.datesUntil(last.plusDays(1)).filter(date -> !complete.contains(date)).toList();
    }

    public InterestAccrualStore.Proposal prepareBackfill(LocalDate date, InterestRecoveryRequest request, String actor) {
        validateDate(date);
        if (!date.isBefore(LocalDate.now(clock)))
            throw new IllegalArgumentException("Recovery is only for past business dates");
        String preparer = adminIdentity(actor);
        var rows = recoveryRows(request);
        String hash = recoveryHash(date, request, rows);
        return ledger.locked(() -> {
            var existing = audit.resolution(date);
            if (existing != null && (!"BACKFILL".equals(existing.mode()) || !hash.equals(existing.requestHash())))
                throw new IllegalStateException("That date is already sealed with a different snapshot or resolution");
            if (existing == null) {
                ensureUnposted(date);
                validateAccounts(date, request);
            }
            return audit.prepare(date, request, preparer, hash);
        });
    }

    public InterestAccrualStore.Proposal backfillProposal(java.util.UUID id) { return audit.proposal(id); }

    public PeriodOverview overview(LocalDate requestedEnd) {
        LocalDate today = LocalDate.now(clock);
        LocalDate end = requestedEnd == null ? today.withDayOfMonth(today.lengthOfMonth()) : requestedEnd;
        var missing = missingDays(end);
        LocalDate first = end.withDayOfMonth(1).isBefore(startDate) ? startDate : end.withDayOfMonth(1);
        var completed = audit.completedDates(first, end);
        String status = ledger.postingComplete(end) ? "POSTED" : !missing.isEmpty() ? "BLOCKED"
                : end.isBefore(today) || (end.equals(today) && completed.contains(end)) ? "READY" : "ACCRUING";
        return new PeriodOverview(startDate, today, first, end, status, missing, completed, audit.proposals(first, end));
    }

    public Result approveBackfill(java.util.UUID id, InterestRecoveryRequest.Approval approval, String actor) {
        String checker = adminIdentity(actor);
        if (approval == null || !approval.confirmed() || approval.reason() == null
                || approval.reason().isBlank() || approval.reason().length() > 1000)
            throw new IllegalArgumentException("Review confirmation and an approval reason are required");
        return ledger.locked(() -> {
            var proposal = audit.proposal(id);
            // Simulation workflow: preparation and approval remain separate actions, but may share an admin.
            LocalDate date = proposal.businessDate();
            validateDate(date);
            var rows = recoveryRows(proposal.request());
            if (!proposal.requestHash().equals(recoveryHash(date, proposal.request(), rows)))
                throw new IllegalStateException("Backfill proposal content does not match its audit hash");
            var existing = audit.resolution(date);
            boolean replay = existing != null;
            if (replay && (!"BACKFILL".equals(existing.mode()) || !proposal.requestHash().equals(existing.requestHash())
                    || proposal.approvedBy() == null || !audit.completed(date)))
                throw new IllegalStateException("That date is already sealed with a different snapshot or resolution");
            if (!replay) {
                ensureUnposted(date);
                validateAccounts(date, proposal.request());
            }
            long job = ledger.startJob(date, "EOD_INTEREST_ACCRUAL");
            if (!replay) audit.approve(proposal, rows, checker, approval.reason());
            ledger.finishJob(job);
            return new Result(date, replay ? 0 : rows.size(), replay);
        });
    }

    private void ensureUnposted(LocalDate date) {
        if (ledger.periodHasPostings(date.withDayOfMonth(date.lengthOfMonth())))
            throw new IllegalStateException("Cannot change an already posted interest period");
    }

    private void validateAccounts(LocalDate date, InterestRecoveryRequest request) {
        request.accounts().stream().sorted(Comparator.comparingLong(InterestRecoveryRequest.HistoricalAccount::accountId))
                .forEach(account -> ledger.validateHistoricalAccount(account, date, clock.getZone()));
    }

    private static String adminIdentity(String actor) {
        if (actor == null || actor.isBlank() || actor.length() > 120)
            throw new IllegalArgumentException("An authenticated administrator identity is required");
        return actor.strip().toLowerCase(java.util.Locale.ROOT);
    }

    private static List<InterestAccrualStore.Accrual> recoveryRows(InterestRecoveryRequest request) {
        if (request == null || request.mode() == null || !request.confirmed() || request.accounts() == null
                || request.reason() == null || request.reason().isBlank() || request.reason().length() > 1000
                || request.sourceReference() == null || request.sourceReference().isBlank() || request.sourceReference().length() > 255)
            throw new IllegalArgumentException("Recovery requires a mode, reason, source reference, accounts and explicit confirmation");
        if (request.accounts().size() > 10000) throw new IllegalArgumentException("Backfill manifest is too large");
        var ids = new HashSet<Long>();
        return request.accounts().stream().map(account -> {
            if (account == null || account.accountId() <= 0 || !ids.add(account.accountId()))
                throw new IllegalArgumentException("Historical accounts must have distinct positive IDs");
            BigDecimal balance = checkedDecimal(account.eodBalance(), 18, 4);
            BigDecimal rate = InterestPolicy.rate(account.accountType(), balance, account.annualRate());
            rate = checkedDecimal(rate, 7, 4);
            if (InterestPolicy.isSavings(account.accountType()) && account.annualRate() != null
                    && account.annualRate().compareTo(rate) != 0)
                throw new IllegalArgumentException("Savings rate does not match the balance tier");
            return new InterestAccrualStore.Accrual(account.accountId(), balance, rate,
                    checkedDecimal(InterestPolicy.daily(balance, rate), 18, 6));
        }).sorted(Comparator.comparingLong(InterestAccrualStore.Accrual::accountId)).toList();
    }

    private static BigDecimal checkedDecimal(BigDecimal value, int precision, int scale) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException("Invalid historical balance or rate");
        try {
            BigDecimal normalized = value.setScale(scale, java.math.RoundingMode.UNNECESSARY);
            if (normalized.precision() > precision) throw new IllegalArgumentException("Historical value exceeds database precision");
            return normalized;
        } catch (ArithmeticException ex) { throw new IllegalArgumentException("Historical value has too many decimals", ex); }
    }

    private static String recoveryHash(LocalDate date, InterestRecoveryRequest request, List<InterestAccrualStore.Accrual> rows) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : List.of(date.toString(), request.mode().name(), request.reason(), request.sourceReference(), rows.toString())) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    private void validateDate(LocalDate date) {
        if (date == null || date.isBefore(startDate) || date.isAfter(LocalDate.now(clock)))
            throw new IllegalArgumentException("Business date must be between " + startDate + " and today");
    }
}
