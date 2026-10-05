package com.bank.audit.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * PayPink 2.0 — Phase 6 Risk Decision Audit Record.
 *
 * Append-only. One row per risk-engine scoring call (APPROVE and REJECT both recorded).
 * Populated by RiskDecisionConsumer consuming the {@code risk.decisions} Kafka topic.
 * Published by transaction-service RiskDecisionPublisher after every /score call.
 *
 * Maps to the RISK_DECISION table in PostgreSQL (ledger_audit_db).
 */
@Entity
@Table(name = "RISK_DECISION")
public class RiskDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "decision_id")
    private Long decisionId;

    /** Remittance reference number — logical link to REMITTANCE in Azure SQL. */
    @Column(name = "reference_no", nullable = false, length = 64)
    private String referenceNo;

    /** Combined score: max(rule_score, ml_score * 0.90). Range 0.0000–1.0000. */
    @Column(name = "score", nullable = false, precision = 5, scale = 4)
    private BigDecimal score;

    /** Rule-based layer score (Layer 1). Null if risk engine < v3.0.0. */
    @Column(name = "rule_score", precision = 5, scale = 4)
    private BigDecimal ruleScore;

    /** Isolation Forest ML layer score (Layer 2). Null if risk engine < v3.0.0. */
    @Column(name = "ml_score", precision = 5, scale = 4)
    private BigDecimal mlScore;

    /** APPROVE | REJECT | UNAVAILABLE */
    @Column(name = "decision", nullable = false, length = 20)
    private String decision;

    /**
     * JSON array of human-readable signal strings.
     * Stored as TEXT in PostgreSQL (JSONB with GIN index applied by schema-postgres.sql).
     * Example: ["amount_above_50k","anomaly_off_hours_02h"]
     */
    @Column(name = "reasons", nullable = false, columnDefinition = "TEXT")
    private String reasons;

    /** End-to-end scoring latency in milliseconds as reported by the risk engine. */
    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    /** Timestamp when the risk engine scored this transfer. */
    @Column(name = "scored_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMPTZ DEFAULT NOW()")
    private OffsetDateTime scoredAt;

    public RiskDecision() {}

    public RiskDecision(String referenceNo, BigDecimal score, BigDecimal ruleScore,
                        BigDecimal mlScore, String decision, String reasons, int latencyMs) {
        this.referenceNo = referenceNo;
        this.score       = score;
        this.ruleScore   = ruleScore;
        this.mlScore     = mlScore;
        this.decision    = decision;
        this.reasons     = reasons;
        this.latencyMs   = latencyMs;
        this.scoredAt    = OffsetDateTime.now();
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public Long          getDecisionId()  { return decisionId; }
    public String        getReferenceNo() { return referenceNo; }
    public BigDecimal    getScore()       { return score; }
    public BigDecimal    getRuleScore()   { return ruleScore; }
    public BigDecimal    getMlScore()     { return mlScore; }
    public String        getDecision()    { return decision; }
    public String        getReasons()     { return reasons; }
    public int           getLatencyMs()   { return latencyMs; }
    public OffsetDateTime getScoredAt()   { return scoredAt; }
}
