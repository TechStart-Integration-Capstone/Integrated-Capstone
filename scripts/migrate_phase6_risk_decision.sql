-- ============================================================================
-- PayPink 2.0 — Phase 6 Migration: Immutable Risk Decision Log
-- Database: PostgreSQL 15 (ledger_audit_db)
-- Run this on an EXISTING database that was already migrated through Phase 5b.
-- Safe to run multiple times — uses IF NOT EXISTS.
-- ============================================================================

-- RISK_DECISION — append-only fraud scoring audit log
CREATE TABLE IF NOT EXISTS RISK_DECISION (
    decision_id    BIGSERIAL    PRIMARY KEY,
    reference_no   VARCHAR(64)  NOT NULL,
    score          NUMERIC(5,4) NOT NULL,
    rule_score     NUMERIC(5,4),
    ml_score       NUMERIC(5,4),
    decision       VARCHAR(20)  NOT NULL,
    reasons        JSONB        NOT NULL,
    latency_ms     INT          NOT NULL DEFAULT 0,
    scored_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Indexes (also idempotent)
CREATE INDEX IF NOT EXISTS idx_risk_decision_ref    ON RISK_DECISION(reference_no);
CREATE INDEX IF NOT EXISTS idx_risk_decision_date   ON RISK_DECISION(scored_at DESC);
CREATE INDEX IF NOT EXISTS idx_risk_decision_score  ON RISK_DECISION(score, decision);
CREATE INDEX IF NOT EXISTS idx_risk_decision_reason ON RISK_DECISION USING GIN(reasons);
