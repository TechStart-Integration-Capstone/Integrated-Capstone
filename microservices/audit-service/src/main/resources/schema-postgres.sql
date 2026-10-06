-- ============================================================================
-- CAPSTONE FSE: Core Retail Ledger & Balance Mutation Engine
-- PostgreSQL 15+ schema OWNED BY event-consumers / audit-service
--   LEDGER_MUTATION_AUDIT  (immutable financial audit, one row per ledger leg)
--   RECONCILIATION_LOG     (Oracle/AzureSQL vs PostgreSQL drift detection, upserted per leg)
-- ============================================================================

DROP TABLE IF EXISTS RECONCILIATION_LOG CASCADE;
DROP TABLE IF EXISTS LEDGER_MUTATION_AUDIT CASCADE;

-- 1. LEDGER_MUTATION_AUDIT (Immutable Append-Only Financial Audit Trail)
CREATE TABLE LEDGER_MUTATION_AUDIT (
    audit_id         BIGSERIAL PRIMARY KEY,
    transaction_id   BIGINT NOT NULL,
    account_id       BIGINT NOT NULL,
    entry_type       VARCHAR(10) NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount           NUMERIC(18,4) NOT NULL CHECK (amount > 0.0000),
    currency         VARCHAR(10) DEFAULT 'PHP' NOT NULL,
    before_balance   NUMERIC(18,4) NOT NULL,
    after_balance    NUMERIC(18,4) NOT NULL,
    created_date     TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uq_audit_tx_account UNIQUE (transaction_id, account_id)
);

-- 2. RECONCILIATION_LOG (Cross-Database Integrity & Drift Detection)
CREATE TABLE RECONCILIATION_LOG (
    recon_id         BIGSERIAL PRIMARY KEY,
    transaction_id   BIGINT NOT NULL,
    account_id       BIGINT NOT NULL,
    oracle_status    VARCHAR(30) NOT NULL,
    postgres_status  VARCHAR(30) NOT NULL,
    recon_status     VARCHAR(30) NOT NULL,          -- 'MATCHED', 'DRIFT_DETECTED'
    mismatch_fields  VARCHAR(200),
    check_count      INT DEFAULT 1 NOT NULL,
    last_checked_at  TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    recon_date       TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uq_recon_tx_account UNIQUE (transaction_id, account_id)
);

-- Indexes
CREATE INDEX idx_audit_tx_id   ON LEDGER_MUTATION_AUDIT(transaction_id);
CREATE INDEX idx_audit_acc_id  ON LEDGER_MUTATION_AUDIT(account_id);
CREATE INDEX idx_audit_created ON LEDGER_MUTATION_AUDIT(created_date);
CREATE INDEX idx_recon_status  ON RECONCILIATION_LOG(recon_status, recon_date);

-- 3. RISK_DECISION (Phase 6 — Immutable Risk Decision Log)
--    Append-only. One row per risk-engine scoring call (APPROVE and REJECT both recorded).
--    Populated by audit-service consuming the risk.decisions Kafka topic.
--    Published by transaction-service RemittanceOrchestratorService after every /score call.
DROP TABLE IF EXISTS RISK_DECISION CASCADE;
CREATE TABLE RISK_DECISION (
    decision_id    BIGSERIAL    PRIMARY KEY,
    reference_no   VARCHAR(64)  NOT NULL,          -- matches REMITTANCE.reference_no
    score          NUMERIC(5,4) NOT NULL,           -- combined score 0.0000–1.0000
    rule_score     NUMERIC(5,4),                   -- rule-only score (Layer 1)
    ml_score       NUMERIC(5,4),                   -- Isolation Forest score (Layer 2)
    decision       VARCHAR(20)  NOT NULL,           -- APPROVE | REJECT | UNAVAILABLE
    reasons        JSONB        NOT NULL,           -- e.g. ["amount_above_50k","anomaly_off_hours_02h"]
    latency_ms     INT          NOT NULL DEFAULT 0, -- end-to-end scoring latency
    scored_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_risk_decision_ref    ON RISK_DECISION(reference_no);
CREATE INDEX idx_risk_decision_date   ON RISK_DECISION(scored_at DESC);
CREATE INDEX idx_risk_decision_score  ON RISK_DECISION(score, decision);
CREATE INDEX idx_risk_decision_reason ON RISK_DECISION USING GIN(reasons);
