package com.bank.analytics.dto;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Per-account aggregated stats — one instance per accountId in the
 * ConcurrentHashMap inside AnalyticsAggregator.
 *
 * All fields use atomic types so they can be updated lock-free from
 * the Kafka consumer thread without blocking the REST read path.
 */
public class AccountSummary {

    private final Long accountId;
    private final AtomicLong   totalTransactions = new AtomicLong(0);
    private final AtomicLong   totalDebits       = new AtomicLong(0);
    private final AtomicLong   totalCredits      = new AtomicLong(0);
    private final AtomicReference<BigDecimal> totalDebitAmount  =
            new AtomicReference<>(BigDecimal.ZERO);
    private final AtomicReference<BigDecimal> totalCreditAmount =
            new AtomicReference<>(BigDecimal.ZERO);
    private final AtomicReference<BigDecimal> lastKnownBalance  =
            new AtomicReference<>(BigDecimal.ZERO);
    private volatile String lastTransactionRef  = "";
    private volatile String lastTransactionTime = "";

    public AccountSummary(Long accountId) {
        this.accountId = accountId;
    }

    // ── Mutation (called from Kafka consumer thread) ──────────────────────────

    public void record(String operation, BigDecimal amount,
                       BigDecimal afterBalance, String referenceNo, String timestamp) {
        totalTransactions.incrementAndGet();
        if ("DEBIT".equalsIgnoreCase(operation)) {
            totalDebits.incrementAndGet();
            totalDebitAmount.updateAndGet(cur -> cur.add(amount));
        } else {
            totalCredits.incrementAndGet();
            totalCreditAmount.updateAndGet(cur -> cur.add(amount));
        }
        lastKnownBalance.set(afterBalance);
        lastTransactionRef  = referenceNo;
        lastTransactionTime = timestamp;
    }

    // ── Snapshot (called from REST thread) ───────────────────────────────────

    public java.util.Map<String, Object> toMap() {
        return java.util.Map.of(
                "accountId",            accountId,
                "totalTransactions",    totalTransactions.get(),
                "totalDebits",          totalDebits.get(),
                "totalCredits",         totalCredits.get(),
                "totalDebitAmount",     totalDebitAmount.get(),
                "totalCreditAmount",    totalCreditAmount.get(),
                "lastKnownBalance",     lastKnownBalance.get(),
                "lastTransactionRef",   lastTransactionRef,
                "lastTransactionTime",  lastTransactionTime
        );
    }

    public long getTotalTransactions() { return totalTransactions.get(); }
    public Long getAccountId()         { return accountId; }
}
