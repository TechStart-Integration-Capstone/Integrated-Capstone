-- ============================================================================
-- PayPink 2.0 — Reconciliation Fix Migration
-- Database: PostgreSQL 15 (ledger_audit_db)
-- Run this on an EXISTING database to apply the reconciliation schema fixes.
-- Safe to run multiple times — all operations are idempotent.
--
-- What this fixes:
--   1. account_id — was NOT NULL, now nullable (entity never populated it → crash)
--   2. oracle_status — column renamed to azure_sql_status (Oracle XE → Azure SQL migration)
--   3. UNIQUE constraint — was (transaction_id, account_id), now (transaction_id) alone
--   4. New indexes for transaction_id and partial index on account_id
-- ============================================================================

-- 1. Drop the old unique constraint (transaction_id, account_id)
--    Name may differ if schema was created manually — use DO block to be safe.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uq_recon_tx_account'
          AND conrelid = 'RECONCILIATION_LOG'::regclass
    ) THEN
        ALTER TABLE RECONCILIATION_LOG DROP CONSTRAINT uq_recon_tx_account;
    END IF;
END;
$$;

-- 2. Make account_id nullable (was NOT NULL — the root cause of the insert crash)
ALTER TABLE RECONCILIATION_LOG
    ALTER COLUMN account_id DROP NOT NULL;

-- 3. Rename oracle_status → azure_sql_status (primary DB is now Azure SQL, not Oracle XE)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'reconciliation_log'
          AND column_name = 'oracle_status'
    ) THEN
        ALTER TABLE RECONCILIATION_LOG RENAME COLUMN oracle_status TO azure_sql_status;
    END IF;
END;
$$;

-- 4. Add new unique constraint on transaction_id alone
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uq_recon_tx'
          AND conrelid = 'RECONCILIATION_LOG'::regclass
    ) THEN
        ALTER TABLE RECONCILIATION_LOG
            ADD CONSTRAINT uq_recon_tx UNIQUE (transaction_id);
    END IF;
END;
$$;

-- 5. Add index on transaction_id (for fast lookups by the reconciliation service)
CREATE INDEX IF NOT EXISTS idx_recon_tx_id
    ON RECONCILIATION_LOG(transaction_id);

-- 6. Add partial index on account_id (only where populated)
CREATE INDEX IF NOT EXISTS idx_recon_acct
    ON RECONCILIATION_LOG(account_id)
    WHERE account_id IS NOT NULL;

-- Verify
SELECT
    column_name,
    is_nullable,
    data_type
FROM information_schema.columns
WHERE table_name = 'reconciliation_log'
ORDER BY ordinal_position;
