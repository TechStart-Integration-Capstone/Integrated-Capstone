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
IF OBJECT_ID('dbo.GL_ENTRY', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.GL_ENTRY (
        gl_entry_id BIGINT IDENTITY(1,1) PRIMARY KEY,
        account_id BIGINT NOT NULL REFERENCES dbo.ACCOUNT(account_id),
        amount DECIMAL(18,2) NOT NULL CHECK (amount >= 0),
        entry_type NVARCHAR(10) NOT NULL CHECK (entry_type IN ('CREDIT', 'DEBIT')),
        posting_type NVARCHAR(40) NOT NULL,
        description NVARCHAR(255) NOT NULL,
        business_date DATE NOT NULL,
        period_start DATE NOT NULL,
        period_end DATE NOT NULL,
        job_run_id BIGINT NOT NULL REFERENCES dbo.EOD_JOB_RUN(job_run_id),
        transaction_id BIGINT NULL REFERENCES dbo.LEDGER_TRANSACTION(transaction_id),
        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT ck_gl_period CHECK (period_start <= period_end AND business_date = period_end),
        CONSTRAINT uq_gl_interest_period UNIQUE (account_id, posting_type, period_end)
    );
END;
COMMIT;
