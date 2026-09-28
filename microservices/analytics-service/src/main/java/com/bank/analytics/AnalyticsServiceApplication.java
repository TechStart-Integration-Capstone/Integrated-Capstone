package com.bank.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Analytics Service
 *
 * Consumes ledger.transaction.events from Kafka and maintains a
 * thread-safe in-memory aggregation of transaction metrics.
 *
 * No database dependency — all stats are held in memory and reset
 * on restart (appropriate for a real-time dashboard feed).
 *
 * Architecture layer: Event Layer (Layer 5) — Kafka consumer.
 * Exposes aggregated insights via REST for the frontend Telemetry tab
 * and for Grafana dashboards.
 *
 * Endpoints (proxied through API Gateway):
 *   GET /api/v1/analytics/summary   — overall ledger statistics
 *   GET /api/v1/analytics/accounts  — per-account breakdown
 *   GET /api/v1/analytics/recent    — last N processed events
 */
@SpringBootApplication
public class AnalyticsServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AnalyticsServiceApplication.class, args);
    }
}
