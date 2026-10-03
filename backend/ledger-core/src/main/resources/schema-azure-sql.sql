-- Azure SQL schema for the shared ledger tables.
-- This script is additive and intentionally does not drop or seed data.

IF OBJECT_ID(N'dbo.CUSTOMER', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.CUSTOMER (
        customer_id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_customer PRIMARY KEY,
        username NVARCHAR(50) NOT NULL CONSTRAINT uq_customer_username UNIQUE,
        password_hash NVARCHAR(255) NOT NULL,
        first_name NVARCHAR(100) NOT NULL,
        last_name NVARCHAR(100) NOT NULL,
        email NVARCHAR(150) NOT NULL CONSTRAINT uq_customer_email UNIQUE,
        contact_no NVARCHAR(30) NOT NULL,
        status NVARCHAR(20) NOT NULL CONSTRAINT df_customer_status DEFAULT N'ACTIVE',
        created_date DATETIME2(7) NOT NULL CONSTRAINT df_customer_created_date DEFAULT SYSUTCDATETIME()
    );
END;
GO

IF OBJECT_ID(N'dbo.ACCOUNT', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.ACCOUNT (
        account_id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_account PRIMARY KEY,
        customer_id BIGINT NOT NULL,
        account_number NVARCHAR(30) NOT NULL CONSTRAINT uq_account_number UNIQUE,
        account_type NVARCHAR(30) NOT NULL CONSTRAINT df_account_type DEFAULT N'SAVINGS',
        currency NVARCHAR(10) NOT NULL CONSTRAINT df_account_currency DEFAULT N'PHP',
        current_balance DECIMAL(18,4) NOT NULL CONSTRAINT df_account_balance DEFAULT 0.0000,
        status NVARCHAR(20) NOT NULL CONSTRAINT df_account_status DEFAULT N'ACTIVE',
        created_date DATETIME2(7) NOT NULL CONSTRAINT df_account_created_date DEFAULT SYSUTCDATETIME(),
        CONSTRAINT fk_account_customer FOREIGN KEY (customer_id) REFERENCES dbo.CUSTOMER(customer_id),
        CONSTRAINT chk_account_balance_positive CHECK (current_balance >= 0.0000)
    );
END;
GO

IF OBJECT_ID(N'dbo.AUDIT_LOG', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.AUDIT_LOG (
        audit_id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_audit_log PRIMARY KEY,
        customer_id BIGINT NOT NULL,
        action NVARCHAR(100) NOT NULL,
        entity NVARCHAR(50) NOT NULL,
        details NVARCHAR(4000) NOT NULL,
        audit_timestamp DATETIME2(7) NOT NULL CONSTRAINT df_audit_timestamp DEFAULT SYSUTCDATETIME(),
        CONSTRAINT fk_audit_customer FOREIGN KEY (customer_id) REFERENCES dbo.CUSTOMER(customer_id)
    );
END;
GO

IF OBJECT_ID(N'dbo.LEDGER_TRANSACTION', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.LEDGER_TRANSACTION (
        transaction_id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_ledger_transaction PRIMARY KEY,
        from_account_id BIGINT NOT NULL,
        to_account_id BIGINT NULL,
        amount DECIMAL(18,4) NOT NULL,
        source_currency NVARCHAR(10) NOT NULL CONSTRAINT df_tx_source_currency DEFAULT N'PHP',
        target_currency NVARCHAR(10) NOT NULL CONSTRAINT df_tx_target_currency DEFAULT N'PHP',
        transaction_type NVARCHAR(30) NOT NULL,
        reference_no NVARCHAR(64) NOT NULL CONSTRAINT uq_tx_reference_no UNIQUE,
        status NVARCHAR(20) NOT NULL,
        failure_reason NVARCHAR(255) NULL,
        transaction_date DATETIME2(7) NOT NULL CONSTRAINT df_tx_date DEFAULT SYSUTCDATETIME(),
        operation NVARCHAR(10) NULL,
        CONSTRAINT fk_tx_from_account FOREIGN KEY (from_account_id) REFERENCES dbo.ACCOUNT(account_id),
        CONSTRAINT fk_tx_to_account FOREIGN KEY (to_account_id) REFERENCES dbo.ACCOUNT(account_id),
        CONSTRAINT chk_tx_amount_positive CHECK (amount > 0.0000)
    );
END;
GO

IF OBJECT_ID(N'dbo.OUTBOX_EVENT', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.OUTBOX_EVENT (
        event_id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_outbox_event PRIMARY KEY,
        transaction_id BIGINT NOT NULL,
        event_type NVARCHAR(50) NOT NULL,
        payload NVARCHAR(MAX) NOT NULL,
        status NVARCHAR(20) NOT NULL CONSTRAINT df_outbox_status DEFAULT N'PENDING',
        retry_count INT NOT NULL CONSTRAINT df_outbox_retry_count DEFAULT 0,
        next_attempt_at DATETIME2(7) NOT NULL CONSTRAINT df_outbox_next_attempt DEFAULT SYSUTCDATETIME(),
        last_error NVARCHAR(500) NULL,
        created_date DATETIME2(7) NOT NULL CONSTRAINT df_outbox_created_date DEFAULT SYSUTCDATETIME(),
        processed_date DATETIME2(7) NULL,
        CONSTRAINT fk_outbox_transaction FOREIGN KEY (transaction_id)
            REFERENCES dbo.LEDGER_TRANSACTION(transaction_id)
    );
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.ACCOUNT') AND name = N'idx_account_cust_id')
    CREATE INDEX idx_account_cust_id ON dbo.ACCOUNT(customer_id);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.LEDGER_TRANSACTION') AND name = N'idx_tx_from_acc')
    CREATE INDEX idx_tx_from_acc ON dbo.LEDGER_TRANSACTION(from_account_id);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.OUTBOX_EVENT') AND name = N'idx_outbox_status')
    CREATE INDEX idx_outbox_status ON dbo.OUTBOX_EVENT(status, created_date);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.AUDIT_LOG') AND name = N'idx_audit_cust_id')
    CREATE INDEX idx_audit_cust_id ON dbo.AUDIT_LOG(customer_id, audit_timestamp);
GO
