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

-- Apply after migrate_interest_postgres.sql on existing databases, before deploying the recovery API.
BEGIN;
ALTER TABLE interest_accrual_batch
    ADD COLUMN IF NOT EXISTS resolution_type VARCHAR(16) NOT NULL DEFAULT 'SNAPSHOT',
    ADD COLUMN IF NOT EXISTS resolved_by VARCHAR(120),
    ADD COLUMN IF NOT EXISTS resolution_reason VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS source_reference VARCHAR(255),
    ADD COLUMN IF NOT EXISTS request_hash CHAR(64);
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_interest_resolution'
                   AND conrelid = 'interest_accrual_batch'::regclass) THEN
        ALTER TABLE interest_accrual_batch ADD CONSTRAINT ck_interest_resolution CHECK (
            (resolution_type = 'SNAPSHOT' AND resolved_by IS NULL AND resolution_reason IS NULL
                AND source_reference IS NULL AND request_hash IS NULL)
            OR
            (resolution_type IN ('BACKFILL', 'WAIVER') AND resolved_by IS NOT NULL AND length(trim(resolved_by)) > 0
                AND resolution_reason IS NOT NULL AND length(trim(resolution_reason)) > 0
                AND source_reference IS NOT NULL AND length(trim(source_reference)) > 0
                AND request_hash IS NOT NULL AND request_hash ~ '^[0-9a-f]{64}$'
                AND (resolution_type <> 'WAIVER' OR account_count = 0))
        );
    END IF;
END;
$$;
COMMIT;

-- Apply after the interest recovery migration. Preserve all historical records, including legacy waivers.
BEGIN;
CREATE TABLE IF NOT EXISTS interest_backfill_proposal (
    proposal_id UUID PRIMARY KEY,
    business_date DATE NOT NULL,
    prepared_by VARCHAR(120) NOT NULL CHECK (length(trim(prepared_by)) > 0),
    request_hash CHAR(64) NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    request_json JSONB NOT NULL CHECK (request_json->>'mode' = 'BACKFILL'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (business_date, request_hash),
    UNIQUE (proposal_id, business_date)
);
CREATE TABLE IF NOT EXISTS interest_backfill_approval (
    proposal_id UUID PRIMARY KEY,
    business_date DATE NOT NULL UNIQUE,
    approved_by VARCHAR(120) NOT NULL CHECK (length(trim(approved_by)) > 0),
    approval_reason VARCHAR(1000) NOT NULL CHECK (length(trim(approval_reason)) > 0),
    approved_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (proposal_id, business_date) REFERENCES interest_backfill_proposal(proposal_id, business_date)
);
ALTER TABLE interest_accrual_batch ADD COLUMN IF NOT EXISTS proposal_id UUID
    REFERENCES interest_backfill_proposal(proposal_id);

CREATE OR REPLACE RULE no_update_interest_proposal AS ON UPDATE TO interest_backfill_proposal DO INSTEAD NOTHING;
CREATE OR REPLACE RULE no_delete_interest_proposal AS ON DELETE TO interest_backfill_proposal DO INSTEAD NOTHING;
CREATE OR REPLACE RULE no_update_interest_approval AS ON UPDATE TO interest_backfill_approval DO INSTEAD NOTHING;
CREATE OR REPLACE RULE no_delete_interest_approval AS ON DELETE TO interest_backfill_approval DO INSTEAD NOTHING;
DROP TRIGGER IF EXISTS interest_proposal_no_truncate ON interest_backfill_proposal;
CREATE TRIGGER interest_proposal_no_truncate BEFORE TRUNCATE ON interest_backfill_proposal
    FOR EACH STATEMENT EXECUTE FUNCTION reject_interest_truncate();
DROP TRIGGER IF EXISTS interest_approval_no_truncate ON interest_backfill_approval;
CREATE TRIGGER interest_approval_no_truncate BEFORE TRUNCATE ON interest_backfill_approval
    FOR EACH STATEMENT EXECUTE FUNCTION reject_interest_truncate();

CREATE OR REPLACE FUNCTION guard_interest_approval() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM interest_backfill_proposal WHERE proposal_id = NEW.proposal_id
               AND lower(trim(prepared_by)) = lower(trim(NEW.approved_by))) THEN
        RAISE EXCEPTION 'A different administrator must approve the backfill';
    END IF;
    RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS interest_approval_guard ON interest_backfill_approval;
CREATE TRIGGER interest_approval_guard BEFORE INSERT ON interest_backfill_approval
    FOR EACH ROW EXECUTE FUNCTION guard_interest_approval();

CREATE OR REPLACE FUNCTION guard_interest_resolution() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.resolution_type = 'WAIVER' THEN
        RAISE EXCEPTION 'Interest waivers are not permitted; missing days remain unresolved';
    ELSIF NEW.resolution_type = 'BACKFILL' THEN
        IF NOT EXISTS (
            SELECT 1 FROM interest_backfill_proposal p JOIN interest_backfill_approval a USING (proposal_id, business_date)
            WHERE p.proposal_id = NEW.proposal_id AND p.business_date = NEW.business_date
                AND p.request_hash = NEW.request_hash AND p.prepared_by = NEW.resolved_by
                AND p.request_json->>'reason' = NEW.resolution_reason
                AND p.request_json->>'sourceReference' = NEW.source_reference
                AND jsonb_array_length(p.request_json->'accounts') = NEW.account_count
        ) THEN
            RAISE EXCEPTION 'Backfill requires an independently approved matching proposal';
        END IF;
    ELSIF NEW.resolution_type <> 'SNAPSHOT' OR NEW.proposal_id IS NOT NULL THEN
        RAISE EXCEPTION 'Invalid interest resolution';
    END IF;
    RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS interest_resolution_guard ON interest_accrual_batch;
CREATE TRIGGER interest_resolution_guard BEFORE INSERT ON interest_accrual_batch
    FOR EACH ROW EXECUTE FUNCTION guard_interest_resolution();

-- Existing restricted runtime role keeps append-only access on the new audit records.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'interest_eod_writer') THEN
        GRANT SELECT, INSERT ON interest_backfill_proposal, interest_backfill_approval TO interest_eod_writer;
    END IF;
END;
$$;
COMMIT;

-- Simulation policy: one admin may prepare and later approve the same proposal.
-- Preserve separate immutable records and all approval/completion integrity checks.
BEGIN;
DROP TRIGGER IF EXISTS interest_approval_guard ON interest_backfill_approval;
DROP FUNCTION IF EXISTS guard_interest_approval();
COMMIT;
