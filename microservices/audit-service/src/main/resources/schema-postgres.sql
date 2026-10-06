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
--    oracle_status renamed to azure_sql_status (Phase 1 migration renamed the primary DB from Oracle XE to Azure SQL).
--    account_id is nullable — reconciliation is done at transaction level; account_id is populated from
--    LEDGER_MUTATION_AUDIT when available, but absent for failed/missing transactions.
CREATE TABLE RECONCILIATION_LOG (
    recon_id          BIGSERIAL PRIMARY KEY,
    transaction_id    BIGINT       NOT NULL,
    account_id        BIGINT,                        -- nullable: populated from audit row when present
    azure_sql_status  VARCHAR(30)  NOT NULL,         -- was oracle_status; maps to Azure SQL LEDGER_TRANSACTION.status
    postgres_status   VARCHAR(30)  NOT NULL,
    recon_status      VARCHAR(30)  NOT NULL,         -- 'MATCHED', 'DRIFT_DETECTED'
    mismatch_fields   VARCHAR(200),
    check_count       INT          NOT NULL DEFAULT 1,
    last_checked_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    recon_date        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_recon_tx UNIQUE (transaction_id)   -- one recon row per transaction (account_id no longer in key)
);

-- Indexes
CREATE INDEX idx_audit_tx_id   ON LEDGER_MUTATION_AUDIT(transaction_id);
CREATE INDEX idx_audit_acc_id  ON LEDGER_MUTATION_AUDIT(account_id);
CREATE INDEX idx_audit_created ON LEDGER_MUTATION_AUDIT(created_date);
CREATE INDEX idx_recon_status  ON RECONCILIATION_LOG(recon_status, recon_date);
CREATE INDEX idx_recon_tx_id   ON RECONCILIATION_LOG(transaction_id);
CREATE INDEX idx_recon_acct    ON RECONCILIATION_LOG(account_id) WHERE account_id IS NOT NULL;

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

-- Additive migration; run against ledger_audit_db before enabling interest EOD.
BEGIN;
CREATE TABLE IF NOT EXISTS interest_accrual (
    accrual_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id BIGINT NOT NULL,
    business_date DATE NOT NULL,
    eod_balance NUMERIC(18,4) NOT NULL CHECK (eod_balance >= 0),
    rate NUMERIC(7,4) NOT NULL CHECK (rate >= 0), -- annual fraction: 0.0250 = 2.5%
    interest_amount NUMERIC(18,6) NOT NULL CHECK (interest_amount >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_account_business_date UNIQUE (account_id, business_date)
);
CREATE INDEX IF NOT EXISTS idx_interest_accrual_date ON interest_accrual(business_date, account_id);

-- Committed atomically with all daily rows, including days with no eligible accounts.
-- This is a snapshot-completion record, never a posting flag.
CREATE TABLE IF NOT EXISTS interest_accrual_batch (
    business_date DATE PRIMARY KEY,
    account_count INTEGER NOT NULL CHECK (account_count >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE OR REPLACE RULE no_update_interest_accrual AS ON UPDATE TO interest_accrual DO INSTEAD NOTHING;
CREATE OR REPLACE RULE no_delete_interest_accrual AS ON DELETE TO interest_accrual DO INSTEAD NOTHING;
CREATE OR REPLACE RULE no_update_interest_batch AS ON UPDATE TO interest_accrual_batch DO INSTEAD NOTHING;
CREATE OR REPLACE RULE no_delete_interest_batch AS ON DELETE TO interest_accrual_batch DO INSTEAD NOTHING;

CREATE OR REPLACE FUNCTION reject_interest_truncate() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Interest audit tables are append-only';
END;
$$;
DROP TRIGGER IF EXISTS interest_accrual_no_truncate ON interest_accrual;
CREATE TRIGGER interest_accrual_no_truncate BEFORE TRUNCATE ON interest_accrual
    FOR EACH STATEMENT EXECUTE FUNCTION reject_interest_truncate();
DROP TRIGGER IF EXISTS interest_batch_no_truncate ON interest_accrual_batch;
CREATE TRIGGER interest_batch_no_truncate BEFORE TRUNCATE ON interest_accrual_batch
    FOR EACH STATEMENT EXECUTE FUNCTION reject_interest_truncate();

-- A completed day's totals cannot change through later inserts either.
CREATE OR REPLACE FUNCTION guard_interest_batch() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    -- Serializes sealing and appending, including clients outside the worker.
    PERFORM pg_advisory_xact_lock(72419, NEW.business_date - DATE '2000-01-01');
    IF TG_TABLE_NAME = 'interest_accrual' THEN
        IF EXISTS (SELECT 1 FROM interest_accrual_batch WHERE business_date = NEW.business_date) THEN
            RAISE EXCEPTION 'Interest snapshot for % is already complete', NEW.business_date;
        END IF;
    ELSE
        IF NEW.account_count <> (SELECT COUNT(*) FROM interest_accrual WHERE business_date = NEW.business_date) THEN
            RAISE EXCEPTION 'Interest snapshot row count does not match batch';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS interest_accrual_guard ON interest_accrual;
CREATE TRIGGER interest_accrual_guard BEFORE INSERT ON interest_accrual
    FOR EACH ROW EXECUTE FUNCTION guard_interest_batch();
DROP TRIGGER IF EXISTS interest_batch_guard ON interest_accrual_batch;
CREATE TRIGGER interest_batch_guard BEFORE INSERT ON interest_accrual_batch
    FOR EACH ROW EXECUTE FUNCTION guard_interest_batch();
COMMIT;
