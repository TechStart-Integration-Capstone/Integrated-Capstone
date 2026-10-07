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
