-- ============================================================================
-- PayPink 2.0: Notification Service Schema (PostgreSQL 15+)
-- Database: ledger_audit_db
-- Table: NOTIFICATION (One alert per ledger leg / loan event)
-- ============================================================================

CREATE TABLE IF NOT EXISTS NOTIFICATION (
    notification_id  BIGSERIAL PRIMARY KEY,
    customer_id      BIGINT NOT NULL,
    account_id       BIGINT NOT NULL,
    reference_no     VARCHAR(64) NOT NULL,
    message          TEXT NOT NULL,
    status           VARCHAR(20) NOT NULL,
    created_date     TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_date     TIMESTAMPTZ,
    CONSTRAINT uq_notification_ref_account UNIQUE (reference_no, account_id)
);

CREATE INDEX IF NOT EXISTS idx_notification_ref ON NOTIFICATION(reference_no);
CREATE INDEX IF NOT EXISTS idx_notification_customer ON NOTIFICATION(customer_id);
CREATE INDEX IF NOT EXISTS idx_notification_created ON NOTIFICATION(created_date);
