-- ============================================================================
-- CAPSTONE FSE: Core Retail Ledger & Balance Mutation Engine
-- PostgreSQL 15+ schema OWNED BY notification-service
--   NOTIFICATION (customer alert dispatch history, one row per ledger leg)
--
-- LEDGER_MUTATION_AUDIT / RECONCILIATION_LOG are owned by event-consumers.
-- No seed rows: notifications only ever come from real Kafka events.
-- ============================================================================

DROP TABLE IF EXISTS NOTIFICATION CASCADE;

CREATE TABLE NOTIFICATION (
    notification_id  BIGSERIAL PRIMARY KEY,
    customer_id      BIGINT NOT NULL,
    account_id       BIGINT NOT NULL,
    reference_no     VARCHAR(64) NOT NULL,
    message          TEXT NOT NULL,
    status           VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'RETRY')),
    created_date     TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_date     TIMESTAMPTZ,
    -- One alert per ledger leg: a transfer gives the sender a DEBIT alert and the receiver a CREDIT alert.
    CONSTRAINT uq_notification_ref_account UNIQUE (reference_no, account_id)
);

CREATE INDEX idx_notif_cust_status ON NOTIFICATION(customer_id, status);
