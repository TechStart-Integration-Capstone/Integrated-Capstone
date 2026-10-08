-- ============================================================================
-- CAPSTONE FSE: Core Retail Ledger & Balance Mutation Engine
-- PostgreSQL 15+ Database Schema (Immutable Audit & Downstream Store)
-- ============================================================================

DROP TABLE IF EXISTS NOTIFICATION CASCADE;
DROP TABLE IF EXISTS RECONCILIATION_LOG CASCADE;
DROP TABLE IF EXISTS LEDGER_MUTATION_AUDIT CASCADE;

-- 1. LEDGER_MUTATION_AUDIT TABLE (Immutable Append-Only Financial Audit Trail)
CREATE TABLE LEDGER_MUTATION_AUDIT (
    audit_id         BIGSERIAL PRIMARY KEY,
    transaction_id   BIGINT NOT NULL,
    account_id       BIGINT NOT NULL,
    operation        VARCHAR(20) NOT NULL CHECK (operation IN ('DEBIT', 'CREDIT')),
    amount           NUMERIC(18,4) NOT NULL CHECK (amount > 0.0000),
    currency         VARCHAR(10) DEFAULT 'PHP' NOT NULL,
    before_balance   NUMERIC(18,4) NOT NULL,
    after_balance    NUMERIC(18,4) NOT NULL,
    created_date     TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 2. RECONCILIATION_LOG TABLE (Cross-Database Integrity & Drift Detection)
CREATE TABLE RECONCILIATION_LOG (
    recon_id         BIGSERIAL PRIMARY KEY,
    transaction_id   BIGINT NOT NULL,
    oracle_status    VARCHAR(30) NOT NULL,
    postgres_status  VARCHAR(30) NOT NULL,
    recon_status     VARCHAR(30) NOT NULL, -- 'MATCHED', 'DRIFT_DETECTED'
    recon_date       TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 3. NOTIFICATION TABLE (Customer Alert Dispatch History)
CREATE TABLE NOTIFICATION (
    notification_id  BIGSERIAL PRIMARY KEY,
    customer_id      BIGINT NOT NULL,
    message          TEXT NOT NULL,
    status           VARCHAR(20) NOT NULL CHECK (status IN ('SENT', 'FAILED', 'RETRY')),
    created_date     TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Performance & Query Optimization Indexes
CREATE INDEX idx_audit_tx_id ON LEDGER_MUTATION_AUDIT(transaction_id);
CREATE INDEX idx_audit_acc_id ON LEDGER_MUTATION_AUDIT(account_id);
CREATE INDEX idx_audit_created ON LEDGER_MUTATION_AUDIT(created_date);
CREATE INDEX idx_recon_status ON RECONCILIATION_LOG(recon_status, recon_date);
CREATE INDEX idx_notif_cust_status ON NOTIFICATION(customer_id, status);

-- Seed Initial Mock Audit Records
INSERT INTO LEDGER_MUTATION_AUDIT (transaction_id, account_id, operation, amount, currency, before_balance, after_balance)
VALUES (101, 1, 'CREDIT', 25000.0000, 'PHP', 100450.0000, 125450.0000);

INSERT INTO RECONCILIATION_LOG (transaction_id, oracle_status, postgres_status, recon_status)
VALUES (101, 'SUCCESS', 'COMMITTED', 'MATCHED');

INSERT INTO NOTIFICATION (customer_id, message, status)
VALUES (1, 'PayPink Alert: ₱25,000.0000 credited to Account ACC-PH-1001-8842 via InstaPay.', 'SENT');
