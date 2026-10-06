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
