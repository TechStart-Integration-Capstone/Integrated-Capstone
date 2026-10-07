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
