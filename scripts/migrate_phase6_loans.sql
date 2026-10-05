-- ============================================================================
-- PayPink 2.0 — Azure SQL Phase 6 Loans Migration Script
-- Safe to re-run: every change is guarded by an existence check.
-- Run after schema-azuresql.sql and migrate_phase5_hardening.sql.
-- ============================================================================

-- 1. Hardcoded credit score + income on CUSTOMER (income drives the affordability check)
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.CUSTOMER') AND name = 'credit_score')
BEGIN
    ALTER TABLE dbo.CUSTOMER ADD credit_score INT NOT NULL CONSTRAINT DF_CUSTOMER_CS DEFAULT 650
        CONSTRAINT CK_CUSTOMER_CS CHECK (credit_score BETWEEN 300 AND 850);
END
GO

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.CUSTOMER') AND name = 'monthly_income')
BEGIN
    ALTER TABLE dbo.CUSTOMER ADD monthly_income DECIMAL(18,4) NOT NULL CONSTRAINT DF_CUSTOMER_INC DEFAULT 30000;
END
GO

-- 2. Track loan money movement on the remittance record
--    Allowed values: TRANSFER | LOAN_DISBURSEMENT | LOAN_REPAYMENT
--    (LEDGER_TRANSACTION.transaction_type has no CHECK constraint, so no change is needed there.)
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.REMITTANCE') AND name = 'transaction_type')
BEGIN
    ALTER TABLE dbo.REMITTANCE ADD transaction_type NVARCHAR(30) NOT NULL
        CONSTRAINT DF_REMITTANCE_TYPE DEFAULT 'TRANSFER';
END
GO

-- 3. Loan events have no ledger transaction, so OUTBOX_EVENT.transaction_id becomes optional.
--    SQL Server will not alter a column used by a FOREIGN KEY, so the FK is dropped and re-added.
IF EXISTS (SELECT * FROM sys.columns
           WHERE object_id = OBJECT_ID('dbo.OUTBOX_EVENT') AND name = 'transaction_id' AND is_nullable = 0)
BEGIN
    IF EXISTS (SELECT * FROM sys.foreign_keys WHERE name = 'fk_outbox_transaction')
        ALTER TABLE dbo.OUTBOX_EVENT DROP CONSTRAINT fk_outbox_transaction;
    ALTER TABLE dbo.OUTBOX_EVENT ALTER COLUMN transaction_id BIGINT NULL;
    ALTER TABLE dbo.OUTBOX_EVENT ADD CONSTRAINT fk_outbox_transaction
        FOREIGN KEY (transaction_id) REFERENCES dbo.LEDGER_TRANSACTION(transaction_id);
END
GO

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.OUTBOX_EVENT') AND name = 'aggregate_id')
BEGIN
    ALTER TABLE dbo.OUTBOX_EVENT ADD aggregate_id NVARCHAR(40) NULL;   -- e.g. LOAN reference_no
END
GO

-- 4. Loan tables
IF OBJECT_ID('dbo.LOAN_APPLICATION', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.LOAN_APPLICATION (
        application_id      BIGINT IDENTITY(1,1) PRIMARY KEY,
        reference_no        NVARCHAR(30)  NOT NULL UNIQUE,   -- LAP-20261005-000001
        idempotency_key     NVARCHAR(64)  NOT NULL UNIQUE,
        customer_id         BIGINT        NOT NULL REFERENCES dbo.CUSTOMER(customer_id),
        account_id          BIGINT        NOT NULL REFERENCES dbo.ACCOUNT(account_id),  -- disbursement + repayment account
        requested_amount    DECIMAL(18,4) NOT NULL,
        requested_term      INT           NOT NULL,
        credit_score        INT           NOT NULL,          -- copied at decision time
        decision            NVARCHAR(15)  NOT NULL,          -- APPROVED | COUNTER_OFFER | DECLINED
        offered_amount      DECIMAL(18,4) NULL,
        offered_term        INT           NULL,
        annual_rate         DECIMAL(6,3)  NULL,
        monthly_installment DECIMAL(18,4) NULL,
        decline_reason      NVARCHAR(50)  NULL,
        status              NVARCHAR(15)  NOT NULL,          -- DECIDED | ACCEPTED | EXPIRED
        expires_at          DATETIME2     NOT NULL,          -- created + 7 days
        created_date        DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME()
    );
    CREATE INDEX idx_loan_app_customer ON dbo.LOAN_APPLICATION(customer_id);
END
GO

IF OBJECT_ID('dbo.LOAN', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.LOAN (
        loan_id               BIGINT IDENTITY(1,1) PRIMARY KEY,
        reference_no          NVARCHAR(30)  NOT NULL UNIQUE,   -- LN-20261005-000001
        application_id        BIGINT        NOT NULL UNIQUE REFERENCES dbo.LOAN_APPLICATION(application_id),
        customer_id           BIGINT        NOT NULL REFERENCES dbo.CUSTOMER(customer_id),
        account_id            BIGINT        NOT NULL REFERENCES dbo.ACCOUNT(account_id),
        principal             DECIMAL(18,4) NOT NULL,
        annual_rate           DECIMAL(6,3)  NOT NULL,
        term_months           INT           NOT NULL,
        monthly_installment   DECIMAL(18,4) NOT NULL,
        outstanding_principal DECIMAL(18,4) NOT NULL CHECK (outstanding_principal >= 0),
        penalty_due           DECIMAL(18,4) NOT NULL DEFAULT 0,
        status                NVARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE | OVERDUE | CLOSED
        disbursement_txn_id   BIGINT        NULL REFERENCES dbo.LEDGER_TRANSACTION(transaction_id),
        ft_reference          NVARCHAR(20)  NULL,               -- T24 FT reference
        disbursed_date        DATE          NOT NULL,
        maturity_date         DATE          NOT NULL
    );
    CREATE INDEX idx_loan_customer ON dbo.LOAN(customer_id, status);
END
GO

IF OBJECT_ID('dbo.LOAN_SCHEDULE', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.LOAN_SCHEDULE (
        schedule_id     BIGINT IDENTITY(1,1) PRIMARY KEY,
        loan_id         BIGINT        NOT NULL REFERENCES dbo.LOAN(loan_id),
        installment_no  INT           NOT NULL,
        due_date        DATE          NOT NULL,
        principal_due   DECIMAL(18,4) NOT NULL,
        interest_due    DECIMAL(18,4) NOT NULL,
        amount_paid     DECIMAL(18,4) NOT NULL DEFAULT 0,
        penalty_charged BIT           NOT NULL DEFAULT 0,      -- makes the EOD penalty a one-time charge
        status          NVARCHAR(10)  NOT NULL DEFAULT 'PENDING',  -- PENDING | PAID | OVERDUE
        CONSTRAINT UQ_LOAN_SCHEDULE UNIQUE (loan_id, installment_no)
    );
    CREATE INDEX idx_loan_schedule_due ON dbo.LOAN_SCHEDULE(status, due_date);
END
GO

IF OBJECT_ID('dbo.LOAN_REPAYMENT', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.LOAN_REPAYMENT (
        repayment_id    BIGINT IDENTITY(1,1) PRIMARY KEY,
        loan_id         BIGINT        NOT NULL REFERENCES dbo.LOAN(loan_id),
        reference_no    NVARCHAR(30)  NOT NULL UNIQUE,      -- LRP-20261105-000001
        idempotency_key NVARCHAR(64)  NOT NULL UNIQUE,
        amount          DECIMAL(18,4) NOT NULL CHECK (amount > 0),
        transaction_id  BIGINT        NULL REFERENCES dbo.LEDGER_TRANSACTION(transaction_id),
        created_date    DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME()
    );
END
GO
