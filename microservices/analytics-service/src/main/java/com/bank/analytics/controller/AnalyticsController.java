package com.bank.analytics.controller;

import com.bank.analytics.service.AnalyticsAggregator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for the analytics-service.
 *
 * All endpoints return point-in-time snapshots of the in-memory
 * aggregation built by AnalyticsAggregator from Kafka events.
 *
 * Routes (proxied through API Gateway → analytics-service:8088):
 *
 *   GET /api/v1/analytics/summary
 *     Overall ledger stats: total mutations, debit/credit split,
 *     PHP volume, active accounts, TPS, uptime.
 *
 *   GET /api/v1/analytics/accounts
 *     Per-account breakdown sorted by most active, including
 *     individual debit/credit counts and last known balance.
 *
 *   GET /api/v1/analytics/recent
 *     Last 50 processed events in reverse-chronological order.
 *     Useful for the frontend live feed panel.
 */
@RestController
@RequestMapping("/api/v1/analytics")
@CrossOrigin(origins = "*")
public class AnalyticsController {

    private final AnalyticsAggregator aggregator;

    public AnalyticsController(AnalyticsAggregator aggregator) {
        this.aggregator = aggregator;
    }

    /**
     * Overall ledger analytics summary.
     *
     * Example response:
     * {
     *   "totalEventsProcessed": 142,
     *   "totalDebits": 118,
     *   "totalCredits": 24,
     *   "totalDebitAmountPHP": 354200.0000,
     *   "totalCreditAmountPHP": 72000.0000,
     *   "totalVolumePhp": 426200.0000,
     *   "uniqueAccountsActive": 3,
     *   "uniqueCustomersActive": 2,
     *   "currentTps": 2.37,
     *   "uptimeSeconds": 3612,
     *   "service": "analytics-service",
     *   "status": "UP"
     * }
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        return ResponseEntity.ok(aggregator.getSummary());
    }

    /**
     * Per-account breakdown — sorted by total transaction count descending.
     *
     * Example entry:
     * {
     *   "accountId": 1,
     *   "totalTransactions": 98,
     *   "totalDebits": 82,
     *   "totalCredits": 16,
     *   "totalDebitAmount": 245600.0000,
     *   "totalCreditAmount": 48000.0000,
     *   "lastKnownBalance": 125450.0000,
     *   "lastTransactionRef": "TX-PH-1718000000000-9A3B1C",
     *   "lastTransactionTime": "2026-09-25T14:33:12.004"
     * }
     */
    @GetMapping("/accounts")
    public ResponseEntity<List<Map<String, Object>>> getAccountBreakdown() {
        return ResponseEntity.ok(aggregator.getAccountBreakdown());
    }

    /**
     * Last 50 processed events in reverse-chronological order.
     */
    @GetMapping("/recent")
    public ResponseEntity<List<Map<String, Object>>> getRecentEvents() {
        return ResponseEntity.ok(aggregator.getRecentEvents());
    }
}
