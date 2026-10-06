package com.bank.transaction.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Result returned by the risk engine for a single transfer scoring call.
 *
 * Fields
 * ------
 *  score     — combined score (0.0000–1.0000). This is what the orchestrator uses for the REJECT gate.
 *  decision  — APPROVE | REJECT | UNAVAILABLE
 *  reasons   — list of human-readable signal strings, e.g. ["amount_above_50k","anomaly_off_hours_02h"]
 *  ruleScore — Layer 1 rule-based score (populated by risk engine v3.0.0+; null for older responses)
 *  mlScore   — Layer 2 Isolation Forest score (populated by risk engine v3.0.0+; null for older responses)
 *  latencyMs — end-to-end scoring latency in milliseconds as reported by the risk engine
 *
 * ruleScore and mlScore are optional (null-safe) — the orchestrator only gates on score+decision.
 * They are written to the RISK_DECISION audit log in PostgreSQL via the risk.decisions Kafka topic.
 */
public record RiskResult(
        BigDecimal score,
        String     decision,
        List<String> reasons,
        BigDecimal ruleScore,
        BigDecimal mlScore,
        Double     latencyMs
) {
    /** Convenience constructor for callers that only have the combined score (fallback, tests). */
    public RiskResult(BigDecimal score, String decision, List<String> reasons) {
        this(score, decision, reasons, null, null, null);
    }
}
