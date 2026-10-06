-- ============================================================================
-- PayPink 2.0 â€” Azure SQL (SQL Server 2022) Complete Unified Schema
-- Single Source of Truth for Database Setup & Migrations
-- ============================================================================

-- Filtered indexes (uq_remittance_customer_idemp) require QUOTED_IDENTIFIER ON; sqlcmd defaults it to OFF.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

-- Drop tables in reverse dependency order (safe re-run)
IF OBJECT_ID('dbo.GL_ENTRY',         'U') IS NOT NULL DROP TABLE dbo.GL_ENTRY;
IF OBJECT_ID('dbo.EOD_JOB_RUN',      'U') IS NOT NULL DROP TABLE dbo.EOD_JOB_RUN;
IF OBJECT_ID('dbo.LOAN_REPAYMENT',   'U') IS NOT NULL DROP TABLE dbo.LOAN_REPAYMENT;
IF OBJECT_ID('dbo.LOAN_SCHEDULE',    'U') IS NOT NULL DROP TABLE dbo.LOAN_SCHEDULE;
IF OBJECT_ID('dbo.LOAN',             'U') IS NOT NULL DROP TABLE dbo.LOAN;
IF OBJECT_ID('dbo.LOAN_APPLICATION', 'U') IS NOT NULL DROP TABLE dbo.LOAN_APPLICATION;
IF OBJECT_ID('dbo.REMITTANCE',       'U') IS NOT NULL DROP TABLE dbo.REMITTANCE;
IF OBJECT_ID('dbo.OUTBOX_EVENT',     'U') IS NOT NULL DROP TABLE dbo.OUTBOX_EVENT;
IF OBJECT_ID('dbo.LEDGER_TRANSACTION','U') IS NOT NULL DROP TABLE dbo.LEDGER_TRANSACTION;
IF OBJECT_ID('dbo.AUDIT_LOG',        'U') IS NOT NULL DROP TABLE dbo.AUDIT_LOG;
IF OBJECT_ID('dbo.BANKING_FAVORITE', 'U') IS NOT NULL DROP TABLE dbo.BANKING_FAVORITE;
IF OBJECT_ID('dbo.ACCOUNT',          'U') IS NOT NULL DROP TABLE dbo.ACCOUNT;
IF OBJECT_ID('dbo.CUSTOMER',         'U') IS NOT NULL DROP TABLE dbo.CUSTOMER;
GO

-- 1. CUSTOMER TABLE
CREATE TABLE dbo.CUSTOMER (
    customer_id      BIGINT IDENTITY(1,1) PRIMARY KEY,
    username         NVARCHAR(50)  NOT NULL UNIQUE,
    password_hash    NVARCHAR(255) NOT NULL,
    first_name       NVARCHAR(100) NOT NULL,
    last_name        NVARCHAR(100) NOT NULL,
    email            NVARCHAR(150) NOT NULL UNIQUE,
    contact_no       NVARCHAR(30)  NOT NULL,
    status           NVARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_date     DATETIME2     NOT NULL DEFAULT GETUTCDATE(),
    -- Phase 6 Loans: hardcoded credit score + monthly income for affordability
    credit_score         INT           NOT NULL CONSTRAINT DF_CUSTOMER_CS DEFAULT 650
                                       CONSTRAINT CK_CUSTOMER_CS CHECK (credit_score BETWEEN 300 AND 850),
    monthly_income       DECIMAL(18,4) NOT NULL CONSTRAINT DF_CUSTOMER_INC DEFAULT 30000,
    daily_transfer_limit DECIMAL(18,4) NOT NULL CONSTRAINT DF_CUSTOMER_DAILY_LIMIT DEFAULT 50000.0000,
    per_tx_limit         DECIMAL(18,4) NOT NULL CONSTRAINT DF_CUSTOMER_PER_TX_LIMIT DEFAULT 25000.0000
);
GO

-- 2. ACCOUNT TABLE (customer_balance_master)
CREATE TABLE dbo.ACCOUNT (
    account_id       BIGINT IDENTITY(1,1) PRIMARY KEY,
    customer_id      BIGINT        NOT NULL,
    account_number   NVARCHAR(30)  NOT NULL UNIQUE,
    account_type     NVARCHAR(30)  NOT NULL DEFAULT 'SAVINGS',
    currency         NVARCHAR(10)  NOT NULL DEFAULT 'PHP',
    current_balance  DECIMAL(18,4) NOT NULL DEFAULT 0.0000,
    held_balance     DECIMAL(18,4) NOT NULL DEFAULT 0.0000,
    status           NVARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_date     DATETIME2     NOT NULL DEFAULT GETUTCDATE(),
    CONSTRAINT fk_account_customer FOREIGN KEY (customer_id) REFERENCES dbo.CUSTOMER(customer_id),
    CONSTRAINT chk_account_balance_positive CHECK (current_balance >= 0.0000),
    CONSTRAINT chk_account_held_positive CHECK (held_balance >= 0.0000),
    CONSTRAINT chk_account_held_le_balance CHECK (held_balance <= current_balance)
);
GO

-- 3. AUDIT_LOG TABLE (Synchronous Security & Operational Audit)
CREATE TABLE dbo.AUDIT_LOG (
    audit_id         BIGINT IDENTITY(1,1) PRIMARY KEY,
    customer_id      BIGINT         NOT NULL,
    action           NVARCHAR(100)  NOT NULL,
    entity           NVARCHAR(50)   NOT NULL,
    details          NVARCHAR(4000) NOT NULL,
    timestamp        DATETIME2      NOT NULL DEFAULT GETUTCDATE(),
    CONSTRAINT fk_audit_customer FOREIGN KEY (customer_id) REFERENCES dbo.CUSTOMER(customer_id)
);
GO

-- 4. BANKING_FAVORITE TABLE (transfer recipient favourites)
CREATE TABLE dbo.BANKING_FAVORITE (
    favorite_id  BIGINT IDENTITY(1,1) PRIMARY KEY,
    customer_id  BIGINT    NOT NULL,
    account_id   BIGINT    NOT NULL,
    created_date DATETIME2 NOT NULL DEFAULT GETUTCDATE(),
    CONSTRAINT fk_fav_customer FOREIGN KEY (customer_id) REFERENCES dbo.CUSTOMER(customer_id),
    CONSTRAINT fk_fav_account  FOREIGN KEY (account_id)  REFERENCES dbo.ACCOUNT(account_id),
    CONSTRAINT uq_fav_customer_account UNIQUE (customer_id, account_id)
);
GO

-- 5. LEDGER_TRANSACTION TABLE
CREATE TABLE dbo.LEDGER_TRANSACTION (
    transaction_id   BIGINT IDENTITY(1,1) PRIMARY KEY,
    from_account_id  BIGINT        NOT NULL,
    to_account_id    BIGINT,
    amount           DECIMAL(18,4) NOT NULL,
    source_currency  NVARCHAR(10)  NOT NULL DEFAULT 'PHP',
    target_currency  NVARCHAR(10)  NOT NULL DEFAULT 'PHP',
    transaction_type NVARCHAR(30)  NOT NULL,
    reference_no     NVARCHAR(64)  NOT NULL UNIQUE,
    status           NVARCHAR(20)  NOT NULL,
    failure_reason   NVARCHAR(255),
    transaction_date DATETIME2     NOT NULL DEFAULT GETUTCDATE(),
    CONSTRAINT fk_tx_from_account FOREIGN KEY (from_account_id) REFERENCES dbo.ACCOUNT(account_id),
    CONSTRAINT fk_tx_to_account   FOREIGN KEY (to_account_id)   REFERENCES dbo.ACCOUNT(account_id),
    CONSTRAINT chk_tx_amount_positive CHECK (amount > 0.0000)
);
GO

-- 6. OUTBOX_EVENT TABLE (Transactional Outbox Pattern for Kafka Streaming)
CREATE TABLE dbo.OUTBOX_EVENT (
    event_id         BIGINT IDENTITY(1,1) PRIMARY KEY,
    transaction_id   BIGINT        NULL,             -- NULL for loan.* events (no ledger transaction)
    event_type       NVARCHAR(50)  NOT NULL,
    payload          NVARCHAR(MAX) NOT NULL,
    status           NVARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_date     DATETIME2     NOT NULL DEFAULT GETUTCDATE(),
    processed_date   DATETIME2,
    aggregate_id     NVARCHAR(40)  NULL,             -- e.g. LOAN reference_no; Kafka key when transaction_id is NULL
    CONSTRAINT fk_outbox_transaction FOREIGN KEY (transaction_id) REFERENCES dbo.LEDGER_TRANSACTION(transaction_id)
);
GO

-- 7. REMITTANCE TABLE (PayPink 2.0 Synchronous Saga Orchestration)
CREATE TABLE dbo.REMITTANCE (
    remittance_id     BIGINT IDENTITY(1,1) PRIMARY KEY,
    reference_no      NVARCHAR(64)  NOT NULL UNIQUE,
    caller_customer_id BIGINT        NOT NULL,
    idempotency_key   NVARCHAR(80)  NULL,
    source_account_id BIGINT        NOT NULL,
    target_account_id BIGINT        NOT NULL,
    amount            DECIMAL(18,4) NOT NULL,
    currency          NVARCHAR(10)  NOT NULL DEFAULT 'PHP',
    status            NVARCHAR(30)  NOT NULL, -- PENDING_CORE, POSTED, REJECTED, PROCESSING, Initiated, Authorized
    internal_status   NVARCHAR(40)  NULL,     -- Granular lifecycle status
    current_service   NVARCHAR(40)  NULL,
    risk_score        DECIMAL(5,4)  NULL,
    risk_decision     NVARCHAR(20)  NULL,
    ft_reference      NVARCHAR(64)  NULL,
    reason            NVARCHAR(255) NULL,
    created_at        DATETIME2     NOT NULL DEFAULT GETUTCDATE(),
    updated_at        DATETIME2     NOT NULL DEFAULT GETUTCDATE(),
    transaction_type  NVARCHAR(30)  NOT NULL CONSTRAINT DF_REMITTANCE_TYPE DEFAULT 'TRANSFER', -- TRANSFER | LOAN_DISBURSEMENT | LOAN_REPAYMENT
    cancel_until      DATETIME2     NULL,
    retry_count       INT           NOT NULL CONSTRAINT DF_REMITTANCE_RETRY_COUNT DEFAULT 0,
    max_retries       INT           NOT NULL CONSTRAINT DF_REMITTANCE_MAX_RETRIES DEFAULT 3,
    next_retry_at     DATETIME2     NULL,
    CONSTRAINT fk_remittance_src_account FOREIGN KEY (source_account_id) REFERENCES dbo.ACCOUNT(account_id),
    CONSTRAINT fk_remittance_tgt_account FOREIGN KEY (target_account_id) REFERENCES dbo.ACCOUNT(account_id)
);
GO

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_remittance_status_retry')
BEGIN
    CREATE INDEX idx_remittance_status_retry ON dbo.REMITTANCE(status, next_retry_at, cancel_until);
END
GO

-- 8. LOAN_APPLICATION TABLE (Phase 6 Loans: instant decision from credit score)
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
    status              NVARCHAR(15)  NOT NULL,          -- DECIDED | DISBURSING | ACCEPTED | FAILED | EXPIRED
    expires_at          DATETIME2     NOT NULL,          -- created + 7 days
    created_date        DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME()
);
GO

-- 9. LOAN TABLE
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
    maturity_date         DATE          NOT NULL,
    last_autodebit_date   DATE          NULL,               -- EOD auto-debit: last attempt
    last_autodebit_status NVARCHAR(20)  NULL,               -- PAID | INSUFFICIENT_FUNDS | FAILED
    last_autodebit_amount DECIMAL(18,4) NULL
);
GO

-- 10. LOAN_SCHEDULE TABLE
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
GO

-- 11. LOAN_REPAYMENT TABLE
CREATE TABLE dbo.LOAN_REPAYMENT (
    repayment_id    BIGINT IDENTITY(1,1) PRIMARY KEY,
    loan_id         BIGINT        NOT NULL REFERENCES dbo.LOAN(loan_id),
    reference_no    NVARCHAR(30)  NOT NULL UNIQUE,      -- LRP-20261105-000001
    idempotency_key NVARCHAR(64)  NOT NULL UNIQUE,
    amount          DECIMAL(18,4) NOT NULL CHECK (amount > 0),
    transaction_id  BIGINT        NULL REFERENCES dbo.LEDGER_TRANSACTION(transaction_id),
    created_date    DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME()
);
GO

-- Indexes for high-throughput concurrency
CREATE INDEX idx_account_cust_id  ON dbo.ACCOUNT(customer_id);
CREATE INDEX idx_account_num      ON dbo.ACCOUNT(account_number);
CREATE INDEX idx_tx_from_acc      ON dbo.LEDGER_TRANSACTION(from_account_id);
CREATE INDEX idx_tx_ref_no        ON dbo.LEDGER_TRANSACTION(reference_no);
CREATE INDEX idx_outbox_status    ON dbo.OUTBOX_EVENT(status, created_date);
CREATE INDEX idx_audit_cust_id    ON dbo.AUDIT_LOG(customer_id, timestamp);
CREATE INDEX idx_remittance_ref   ON dbo.REMITTANCE(reference_no);
CREATE INDEX idx_remittance_stat  ON dbo.REMITTANCE(status);
CREATE UNIQUE INDEX uq_remittance_customer_idemp ON dbo.REMITTANCE(caller_customer_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_loan_app_customer    ON dbo.LOAN_APPLICATION(customer_id);
CREATE INDEX idx_loan_customer        ON dbo.LOAN(customer_id, status);
CREATE INDEX idx_loan_schedule_due    ON dbo.LOAN_SCHEDULE(status, due_date);
GO

-- Seed data â€” demo users
INSERT INTO dbo.CUSTOMER (username, password_hash, first_name, last_name, email, contact_no, status)
VALUES ('lviernes', '$2a$10$wN3WpZgJ4g7N8dC5lRzPfeYk4GqU1xL8e9m3K7b0yU6r5T1w9P8a2', 'Levi', 'Viernes', 'jonlevi.jlv@gmail.com', '+63 922 758 4285', 'ACTIVE');
INSERT INTO dbo.CUSTOMER (username, password_hash, first_name, last_name, email, contact_no, status)
VALUES ('arosales', '$2a$10$wN3WpZgJ4g7N8dC5lRzPfeYk4GqU1xL8e9m3K7b0yU6r5T1w9P8a2', 'Aly', 'Rosales', 'aly.rosales@paypink.ph', '+63 918 555 6789', 'ACTIVE');
INSERT INTO dbo.CUSTOMER (username, password_hash, first_name, last_name, email, contact_no, status)
VALUES ('glim', '$2a$10$wN3WpZgJ4g7N8dC5lRzPfeYk4GqU1xL8e9m3K7b0yU6r5T1w9P8a2', 'Gill', 'Lim', 'gill.lim@paypink.ph', '+63 920 333 4567', 'ACTIVE');
GO

-- Accounts with initial balances (12-digit Luhn standard)
INSERT INTO dbo.ACCOUNT (customer_id, account_number, account_type, currency, current_balance, status)
VALUES (1, '001181233469', 'SAVINGS_ACCOUNT',   'PHP', 125450.0000, 'ACTIVE');
INSERT INTO dbo.ACCOUNT (customer_id, account_number, account_type, currency, current_balance, status)
VALUES (1, '001381233467', 'CHECKING_ACCOUNT',  'PHP',  50000.0000, 'ACTIVE');
INSERT INTO dbo.ACCOUNT (customer_id, account_number, account_type, currency, current_balance, status)
VALUES (1, '001981233461', 'STRESS_TEST_ACCOUNT','PHP',     60.0000, 'ACTIVE');
INSERT INTO dbo.ACCOUNT (customer_id, account_number, account_type, currency, current_balance, status)
VALUES (2, '001133218709', 'SAVINGS_ACCOUNT',   'PHP',  84320.5000, 'ACTIVE');
INSERT INTO dbo.ACCOUNT (customer_id, account_number, account_type, currency, current_balance, status)
VALUES (3, '001428928483', 'TIME_DEPOSIT',      'PHP', 350000.0000, 'ACTIVE');
GO

-- Legacy account number aliases for backward compatibility
INSERT INTO dbo.AUDIT_LOG (customer_id, action, entity, details)
VALUES
(1, 'ACCOUNT_RENUMBERED', 'ACCOUNT:1', 'ACC-PH-1001-8842'),
(1, 'ACCOUNT_RENUMBERED', 'ACCOUNT:2', 'ACC-PH-1001-9921'),
(1, 'ACCOUNT_RENUMBERED', 'ACCOUNT:3', 'ACC-PH-1001-7714'),
(2, 'ACCOUNT_RENUMBERED', 'ACCOUNT:4', 'ACC-PH-2002-3311'),
(3, 'ACCOUNT_RENUMBERED', 'ACCOUNT:5', 'ACC-PH-3003-4422');
GO

-- Phase 6 Loans: hardcoded credit scores per demo user (LOW / NORMAL / HIGH bands)
UPDATE dbo.CUSTOMER SET credit_score = 520, monthly_income = 20000.0000  WHERE username = 'lviernes';
UPDATE dbo.CUSTOMER SET credit_score = 670, monthly_income = 45000.0000  WHERE username = 'arosales';
UPDATE dbo.CUSTOMER SET credit_score = 800, monthly_income = 150000.0000 WHERE username = 'glim';
GO

-- Phase 6 Loans: the bank's own loan account. paypink_bank cannot log in (invalid BCrypt hash).
INSERT INTO dbo.CUSTOMER (username, password_hash, first_name, last_name, email, contact_no, status, credit_score, monthly_income)
VALUES ('paypink_bank', '!no-login', 'PayPink', 'Bank', 'loans@paypink.example.test', '+630000000000', 'ACTIVE', 850, 0);
INSERT INTO dbo.ACCOUNT (customer_id, account_number, account_type, currency, current_balance, status)
SELECT customer_id, 'PH1000000LOAN', 'INTERNAL', 'PHP', 50000000.0000, 'ACTIVE' FROM dbo.CUSTOMER WHERE username = 'paypink_bank';
GO

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
