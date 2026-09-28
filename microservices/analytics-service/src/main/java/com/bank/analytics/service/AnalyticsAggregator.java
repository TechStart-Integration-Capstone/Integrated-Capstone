package com.bank.analytics.service;

import com.bank.analytics.dto.AccountSummary;
import com.bank.analytics.dto.AnalyticsEvent;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory aggregation engine for the analytics service.
 *
 * All write paths (from the Kafka consumer thread) use atomic operations
 * or ConcurrentHashMap.compute() — no synchronized blocks, no locks.
 * Read paths (from the REST controller thread) take point-in-time snapshots
 * of the atomic values, so they never block writers.
 *
 * Stats maintained:
 *   - Total events processed
 *   - Total DEBIT / CREDIT counts and PHP amounts
 *   - Unique accounts seen
 *   - Unique customers seen
 *   - Per-account breakdown (AccountSummary)
 *   - Rolling TPS (transactions per second) over a 60-second window
 *   - Recent 50 events ring buffer (for the /recent endpoint)
 *   - Service uptime and processing start time
 */
@Service
public class AnalyticsAggregator {

    // ── Global counters ───────────────────────────────────────────────────────
    private final AtomicLong totalEvents        = new AtomicLong(0);
    private final AtomicLong totalDebits        = new AtomicLong(0);
    private final AtomicLong totalCredits       = new AtomicLong(0);
    private final AtomicReference<BigDecimal> totalDebitAmount  =
            new AtomicReference<>(BigDecimal.ZERO);
    private final AtomicReference<BigDecimal> totalCreditAmount =
            new AtomicReference<>(BigDecimal.ZERO);

    // ── Per-entity maps ───────────────────────────────────────────────────────
    private final ConcurrentHashMap<Long, AccountSummary> accountStats =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Boolean> uniqueCustomers =
            new ConcurrentHashMap<>();

    // ── TPS tracking — timestamps of events in the last 60 seconds ───────────
    private final ConcurrentLinkedDeque<Long> tpsWindow = new ConcurrentLinkedDeque<>();
    private static final long TPS_WINDOW_MS = 60_000L;

    // ── Recent events ring buffer (last 50) ───────────────────────────────────
    private final ConcurrentLinkedDeque<Map<String, Object>> recentEvents =
            new ConcurrentLinkedDeque<>();
    private static final int MAX_RECENT = 50;

    // ── Service lifecycle ─────────────────────────────────────────────────────
    private final long startTimeMs = System.currentTimeMillis();

    // ── Main ingest method — called by AnalyticsKafkaConsumer ────────────────

    public void ingest(AnalyticsEvent event) {
        if (event == null) return;

        long nowMs = System.currentTimeMillis();

        // 1. Global counters
        totalEvents.incrementAndGet();
        String op = event.getOperation() != null
                ? event.getOperation().toUpperCase() : "UNKNOWN";

        BigDecimal amount = event.getAmount() != null
                ? event.getAmount() : BigDecimal.ZERO;

        if ("DEBIT".equals(op)) {
            totalDebits.incrementAndGet();
            totalDebitAmount.updateAndGet(cur -> cur.add(amount));
        } else if ("CREDIT".equals(op)) {
            totalCredits.incrementAndGet();
            totalCreditAmount.updateAndGet(cur -> cur.add(amount));
        }

        // 2. Per-account stats
        if (event.getAccountId() != null) {
            accountStats.compute(event.getAccountId(), (id, existing) -> {
                AccountSummary summary = (existing != null) ? existing : new AccountSummary(id);
                summary.record(
                        op, amount,
                        event.getAfterBalance() != null ? event.getAfterBalance() : BigDecimal.ZERO,
                        event.getReferenceNo() != null  ? event.getReferenceNo()  : "",
                        event.getTimestamp()   != null  ? event.getTimestamp()    : ""
                );
                return summary;
            });
        }

        // 3. Unique customer tracking
        if (event.getCustomerId() != null) {
            uniqueCustomers.put(event.getCustomerId(), Boolean.TRUE);
        }

        // 4. TPS window — add now, evict entries older than 60 s
        tpsWindow.addLast(nowMs);
        tpsWindow.removeIf(ts -> nowMs - ts > TPS_WINDOW_MS);

        // 5. Recent events ring buffer
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("transactionId", event.getTransactionId());
        snapshot.put("referenceNo",   event.getReferenceNo());
        snapshot.put("accountId",     event.getAccountId());
        snapshot.put("customerId",    event.getCustomerId());
        snapshot.put("operation",     op);
        snapshot.put("amount",        amount);
        snapshot.put("currency",      event.getCurrency() != null ? event.getCurrency() : "PHP");
        snapshot.put("afterBalance",  event.getAfterBalance());
        snapshot.put("timestamp",     event.getTimestamp());
        recentEvents.addFirst(snapshot);
        while (recentEvents.size() > MAX_RECENT) {
            recentEvents.removeLast();
        }
    }

    // ── Summary snapshot — called by AnalyticsController ─────────────────────

    public Map<String, Object> getSummary() {
        long nowMs    = System.currentTimeMillis();
        long uptimeSec = (nowMs - startTimeMs) / 1000;

        // Evict stale TPS entries before computing rate
        tpsWindow.removeIf(ts -> nowMs - ts > TPS_WINDOW_MS);
        double currentTps = tpsWindow.size() / (TPS_WINDOW_MS / 1000.0);

        BigDecimal totalVol = totalDebitAmount.get().add(totalCreditAmount.get());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalEventsProcessed",  totalEvents.get());
        summary.put("totalDebits",           totalDebits.get());
        summary.put("totalCredits",          totalCredits.get());
        summary.put("totalDebitAmountPHP",   totalDebitAmount.get());
        summary.put("totalCreditAmountPHP",  totalCreditAmount.get());
        summary.put("totalVolumePhp",        totalVol);
        summary.put("uniqueAccountsActive",  accountStats.size());
        summary.put("uniqueCustomersActive", uniqueCustomers.size());
        summary.put("currentTps",            round2(currentTps));
        summary.put("uptimeSeconds",         uptimeSec);
        summary.put("service",               "analytics-service");
        summary.put("status",                "UP");
        return summary;
    }

    // ── Per-account breakdown — sorted by most active ─────────────────────────

    public List<Map<String, Object>> getAccountBreakdown() {
        return accountStats.values().stream()
                .sorted(Comparator.comparingLong(AccountSummary::getTotalTransactions).reversed())
                .map(AccountSummary::toMap)
                .collect(Collectors.toList());
    }

    // ── Recent events ─────────────────────────────────────────────────────────

    public List<Map<String, Object>> getRecentEvents() {
        return new ArrayList<>(recentEvents);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private double round2(double val) {
        return BigDecimal.valueOf(val)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
