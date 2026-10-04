-- ============================================================================
-- PayPink 2.0 — Azure SQL (SQL Server 2022) Schema
-- Translated from schema-oracle.sql for Phase 1 migration
-- ============================================================================

-- Drop tables in reverse dependency order (safe re-run)
IF OBJECT_ID('dbo.OUTBOX_EVENT',  'U') IS NOT NULL DROP TABLE dbo.OUTBOX_EVENT;
IF OBJECT_ID('dbo.LEDGER_TRANSACTION', 'U') IS NOT NULL DROP TABLE dbo.LEDGER_TRANSACTION;
IF OBJECT_ID('dbo.AUDIT_LOG',     'U') IS NOT NULL DROP TABLE dbo.AUDIT_LOG;
IF OBJECT_ID('dbo.BANKING_FAVORITE','U') IS NOT NULL DROP TABLE dbo.BANKING_FAVORITE;
IF OBJECT_ID('dbo.ACCOUNT',       'U') IS NOT NULL DROP TABLE dbo.ACCOUNT;
IF OBJECT_ID('dbo.CUSTOMER',      'U') IS NOT NULL DROP TABLE dbo.CUSTOMER;
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
    created_date     DATETIME2     NOT NULL DEFAULT GETUTCDATE()
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
    status           NVARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_date     DATETIME2     NOT NULL DEFAULT GETUTCDATE(),
    CONSTRAINT fk_account_customer FOREIGN KEY (customer_id) REFERENCES dbo.CUSTOMER(customer_id),
    CONSTRAINT chk_account_balance_positive CHECK (current_balance >= 0.0000)
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
    transaction_id   BIGINT        NOT NULL,
    event_type       NVARCHAR(50)  NOT NULL,
    payload          NVARCHAR(MAX) NOT NULL,
    status           NVARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_date     DATETIME2     NOT NULL DEFAULT GETUTCDATE(),
    processed_date   DATETIME2,
    CONSTRAINT fk_outbox_transaction FOREIGN KEY (transaction_id) REFERENCES dbo.LEDGER_TRANSACTION(transaction_id)
);
GO

-- 7. REMITTANCE TABLE (PayPink 2.0 Synchronous Saga Orchestration)
CREATE TABLE dbo.REMITTANCE (
    remittance_id     BIGINT IDENTITY(1,1) PRIMARY KEY,
    reference_no      NVARCHAR(64)  NOT NULL UNIQUE,
    source_account_id BIGINT        NOT NULL,
    target_account_id BIGINT        NOT NULL,
    amount            DECIMAL(18,4) NOT NULL,
    currency          NVARCHAR(10)  NOT NULL DEFAULT 'PHP',
    status            NVARCHAR(30)  NOT NULL, -- PENDING_CORE, POSTED, REJECTED, PROCESSING
    risk_score        DECIMAL(5,4)  NULL,
    risk_decision     NVARCHAR(20)  NULL,
    ft_reference      NVARCHAR(64)  NULL,
    reason            NVARCHAR(255) NULL,
    created_at        DATETIME2     NOT NULL DEFAULT GETUTCDATE(),
    updated_at        DATETIME2     NOT NULL DEFAULT GETUTCDATE(),
    CONSTRAINT fk_remittance_src_account FOREIGN KEY (source_account_id) REFERENCES dbo.ACCOUNT(account_id),
    CONSTRAINT fk_remittance_tgt_account FOREIGN KEY (target_account_id) REFERENCES dbo.ACCOUNT(account_id)
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
GO

-- Seed data — same demo users as Oracle schema
INSERT INTO dbo.CUSTOMER (username, password_hash, first_name, last_name, email, contact_no, status)
VALUES ('lviernes', '$2a$10$wN3WpZgJ4g7N8dC5lRzPfeYk4GqU1xL8e9m3K7b0yU6r5T1w9P8a2', 'Levi', 'Viernes', 'jonlevi.jlv@gmail.com', '+63 922 758 4285', 'ACTIVE');
INSERT INTO dbo.CUSTOMER (username, password_hash, first_name, last_name, email, contact_no, status)
VALUES ('arosales', '$2a$10$wN3WpZgJ4g7N8dC5lRzPfeYk4GqU1xL8e9m3K7b0yU6r5T1w9P8a2', 'Aly', 'Rosales', 'aly.rosales@paypink.ph', '+63 918 555 6789', 'ACTIVE');
INSERT INTO dbo.CUSTOMER (username, password_hash, first_name, last_name, email, contact_no, status)
VALUES ('glim', '$2a$10$wN3WpZgJ4g7N8dC5lRzPfeYk4GqU1xL8e9m3K7b0yU6r5T1w9P8a2', 'Gill', 'Lim', 'gill.lim@paypink.ph', '+63 920 333 4567', 'ACTIVE');
GO

-- Accounts with initial balances
INSERT INTO dbo.ACCOUNT (customer_id, account_number, account_type, currency, current_balance, status)
VALUES (1, 'ACC-PH-1001-8842', 'SAVINGS_ACCOUNT',   'PHP', 125450.0000, 'ACTIVE');
INSERT INTO dbo.ACCOUNT (customer_id, account_number, account_type, currency, current_balance, status)
VALUES (1, 'ACC-PH-1001-9921', 'CHECKING_ACCOUNT',  'PHP',  50000.0000, 'ACTIVE');
INSERT INTO dbo.ACCOUNT (customer_id, account_number, account_type, currency, current_balance, status)
VALUES (1, 'ACC-PH-1001-7714', 'STRESS_TEST_ACCOUNT','PHP',     60.0000, 'ACTIVE');
INSERT INTO dbo.ACCOUNT (customer_id, account_number, account_type, currency, current_balance, status)
VALUES (2, 'ACC-PH-2002-3311', 'SAVINGS_ACCOUNT',   'PHP',  84320.5000, 'ACTIVE');
INSERT INTO dbo.ACCOUNT (customer_id, account_number, account_type, currency, current_balance, status)
VALUES (3, 'ACC-PH-3003-4422', 'TIME_DEPOSIT',      'PHP', 350000.0000, 'ACTIVE');
GO
