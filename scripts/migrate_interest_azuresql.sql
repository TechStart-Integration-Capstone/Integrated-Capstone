-- Additive migration; use this for an existing ledger (not the destructive bootstrap).
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF COL_LENGTH('dbo.ACCOUNT', 'interest_rate') IS NULL
    ALTER TABLE dbo.ACCOUNT ADD interest_rate DECIMAL(7,4) NOT NULL
        CONSTRAINT DF_ACCOUNT_INTEREST_RATE DEFAULT 0.0000
        CONSTRAINT CK_ACCOUNT_INTEREST_RATE CHECK (interest_rate >= 0);

IF OBJECT_ID('dbo.EOD_JOB_RUN', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.EOD_JOB_RUN (
        job_run_id BIGINT IDENTITY(1,1) PRIMARY KEY,
        business_date DATE NOT NULL,
        job_name NVARCHAR(50) NOT NULL,
        status NVARCHAR(20) NOT NULL CHECK (status IN ('RUNNING', 'SUCCESS')),
        started_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        ended_at DATETIME2 NULL,
        CONSTRAINT uq_eod_job_date UNIQUE (job_name, business_date)
    );
END;
-- Interest postings use the existing LEDGER_TRANSACTION reference and PostgreSQL ledger.
-- Refuse to discard historical GL data during migration.
IF OBJECT_ID('dbo.GL_ENTRY', 'U') IS NOT NULL
BEGIN
    IF EXISTS (SELECT 1 FROM dbo.GL_ENTRY)
        THROW 51000, 'GL_ENTRY contains historical entries; reconcile them before retiring this table.', 1;
    DROP TABLE dbo.GL_ENTRY;
END;
COMMIT;
