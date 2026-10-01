-- ============================================================================
-- CAPSTONE FSE: Core Retail Ledger & Balance Mutation Engine
-- PostgreSQL 15+ schema OWNED BY event-consumers
--   LEDGER_MUTATION_AUDIT  (immutable financial audit, one row per ledger leg)
--   RECONCILIATION_LOG     (Oracle vs PostgreSQL drift detection, upserted per leg)
--
-- NOTIFICATION is owned by notification-service (see its own schema-postgres.sql).
-- Oracle tables are owned by ledger-core (schema-oracle.sql); this service only READS them.
-- No seed rows: audit and reconciliation data only ever come from real Kafka events.
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
