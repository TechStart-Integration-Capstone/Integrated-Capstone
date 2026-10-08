-- =========================================================================
-- PayPink 2.0: Phase 2 — Stateful T24 Core Banking Schema
-- Target: SQL Server 2022 / Azure SQL
-- Creates t24.LOCKED_AMOUNT and t24.POSTING_JOURNAL
-- Idempotent: Can be run safely multiple times
-- =========================================================================

SET NOCOUNT ON;
PRINT 'Beginning Phase 2 T24 Core Schema Migration...';

-- 1. Ensure t24 schema exists
IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 't24')
BEGIN
    EXEC('CREATE SCHEMA t24');
    PRINT 'Created schema: t24';
END

-- 2. Create t24.LOCKED_AMOUNT table
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'LOCKED_AMOUNT' AND schema_id = SCHEMA_ID('t24'))
BEGIN
    CREATE TABLE t24.LOCKED_AMOUNT (
        hold_id BIGINT IDENTITY(1,1) PRIMARY KEY,
        account_id BIGINT NOT NULL,
        amount DECIMAL(18,4) NOT NULL,
        currency NVARCHAR(3) NOT NULL CONSTRAINT df_locked_currency DEFAULT 'PHP',
        reference_no NVARCHAR(64) NOT NULL,
        status NVARCHAR(20) NOT NULL CONSTRAINT df_locked_status DEFAULT 'ACTIVE',
        created_at DATETIME2 NOT NULL CONSTRAINT df_locked_created DEFAULT GETDATE(),
        expires_at DATETIME2 NULL,
        CONSTRAINT uq_locked_reference UNIQUE (reference_no),
        CONSTRAINT fk_locked_account FOREIGN KEY (account_id) REFERENCES t24.ACCOUNT(account_id)
    );
    CREATE INDEX idx_locked_account_status ON t24.LOCKED_AMOUNT (account_id, status);
    PRINT 'Created table: t24.LOCKED_AMOUNT';
END
ELSE
BEGIN
    PRINT 'Table t24.LOCKED_AMOUNT already exists.';
END

-- 3. Create t24.POSTING_JOURNAL table
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'POSTING_JOURNAL' AND schema_id = SCHEMA_ID('t24'))
BEGIN
    CREATE TABLE t24.POSTING_JOURNAL (
        journal_id BIGINT IDENTITY(1,1) PRIMARY KEY,
        reference_no NVARCHAR(64) NOT NULL,
        debit_account_id BIGINT NOT NULL,
        credit_account_id BIGINT NOT NULL,
        amount DECIMAL(18,4) NOT NULL,
        currency NVARCHAR(3) NOT NULL CONSTRAINT df_journal_currency DEFAULT 'PHP',
        debit_balance_before DECIMAL(18,4) NOT NULL,
        debit_balance_after DECIMAL(18,4) NOT NULL,
        credit_balance_before DECIMAL(18,4) NOT NULL,
        credit_balance_after DECIMAL(18,4) NOT NULL,
        status NVARCHAR(20) NOT NULL CONSTRAINT df_journal_status DEFAULT 'POSTED',
        created_at DATETIME2 NOT NULL CONSTRAINT df_journal_created DEFAULT GETDATE(),
        CONSTRAINT uq_journal_reference UNIQUE (reference_no),
        CONSTRAINT fk_journal_debit_account FOREIGN KEY (debit_account_id) REFERENCES t24.ACCOUNT(account_id),
        CONSTRAINT fk_journal_credit_account FOREIGN KEY (credit_account_id) REFERENCES t24.ACCOUNT(account_id)
    );
    CREATE INDEX idx_journal_reference ON t24.POSTING_JOURNAL (reference_no);
    CREATE INDEX idx_journal_debit ON t24.POSTING_JOURNAL (debit_account_id);
    CREATE INDEX idx_journal_credit ON t24.POSTING_JOURNAL (credit_account_id);
    PRINT 'Created table: t24.POSTING_JOURNAL';
END
ELSE
BEGIN
    PRINT 'Table t24.POSTING_JOURNAL already exists.';
END

-- 4. Create dbo.* synonyms for backward compatibility and cross-module reads
IF EXISTS (SELECT 1 FROM sys.synonyms WHERE name = 'LOCKED_AMOUNT' AND schema_id = SCHEMA_ID('dbo'))
    DROP SYNONYM dbo.LOCKED_AMOUNT;

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'LOCKED_AMOUNT' AND schema_id = SCHEMA_ID('dbo'))
BEGIN
    CREATE SYNONYM dbo.LOCKED_AMOUNT FOR t24.LOCKED_AMOUNT;
    PRINT 'Created synonym dbo.LOCKED_AMOUNT -> t24.LOCKED_AMOUNT';
END

IF EXISTS (SELECT 1 FROM sys.synonyms WHERE name = 'POSTING_JOURNAL' AND schema_id = SCHEMA_ID('dbo'))
    DROP SYNONYM dbo.POSTING_JOURNAL;

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'POSTING_JOURNAL' AND schema_id = SCHEMA_ID('dbo'))
BEGIN
    CREATE SYNONYM dbo.POSTING_JOURNAL FOR t24.POSTING_JOURNAL;
    PRINT 'Created synonym dbo.POSTING_JOURNAL -> t24.POSTING_JOURNAL';
END

PRINT 'Phase 2 T24 Core Schema Migration completed successfully!';
