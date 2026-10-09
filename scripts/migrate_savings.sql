-- SQL Server / Azure SQL. Additive, rerunnable, unapplied. Run after phases 1-6.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID('app.CUSTOMER','U') IS NULL OR OBJECT_ID('t24.ACCOUNT','U') IS NULL
 THROW 51000, 'Apply schema split before savings.', 1;
IF COL_LENGTH('app.OUTBOX_EVENT','aggregate_id') IS NULL
 THROW 51000, 'Apply loan/outbox migrations before savings.', 1;
IF EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID('app.OUTBOX_EVENT') AND name='transaction_id' AND is_nullable=0)
 THROW 51000, 'Outbox must support non-transfer events.', 1;
IF OBJECT_ID('app.PINK_CIRCLE','U') IS NULL
CREATE TABLE app.PINK_CIRCLE (
 circle_id VARCHAR(36) PRIMARY KEY,
 admin_customer_id BIGINT NOT NULL REFERENCES app.CUSTOMER(customer_id),
 name NVARCHAR(80) NOT NULL,
 completion_notified BIT NOT NULL DEFAULT 0,
 target_amount DECIMAL(18,2) NOT NULL CHECK(target_amount>0),
 target_date DATE NOT NULL,
 created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
);
IF COL_LENGTH('app.PINK_CIRCLE','completion_notified') IS NULL
 ALTER TABLE app.PINK_CIRCLE ADD completion_notified BIT NOT NULL DEFAULT 0;
IF OBJECT_ID('app.SAVINGS_GOAL','U') IS NULL
CREATE TABLE app.SAVINGS_GOAL (
 goal_id VARCHAR(36) PRIMARY KEY,
 customer_id BIGINT NOT NULL REFERENCES app.CUSTOMER(customer_id),
 account_id BIGINT NOT NULL REFERENCES t24.ACCOUNT(account_id),
 circle_id VARCHAR(36) NULL REFERENCES app.PINK_CIRCLE(circle_id),
 name NVARCHAR(80) NOT NULL, category VARCHAR(30) NOT NULL,
 target_amount DECIMAL(18,2) NOT NULL CHECK(target_amount>0), target_date DATE NOT NULL,
 created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
 CONSTRAINT uq_savings_owner UNIQUE(goal_id,customer_id)
);
IF OBJECT_ID('app.PINK_CIRCLE_MEMBER','U') IS NULL
CREATE TABLE app.PINK_CIRCLE_MEMBER (
 circle_id VARCHAR(36) NOT NULL REFERENCES app.PINK_CIRCLE(circle_id),
 customer_id BIGINT NOT NULL REFERENCES app.CUSTOMER(customer_id),
 goal_id VARCHAR(36) NULL,
 status VARCHAR(12) NOT NULL CHECK(status IN ('INVITED','ACTIVE')),
 target_amount DECIMAL(18,2) NOT NULL CHECK(target_amount>0),
 proposed_target DECIMAL(18,2) NULL CHECK(proposed_target>0),
 share_progress BIT NOT NULL DEFAULT 0,
 PRIMARY KEY(circle_id,customer_id),
 FOREIGN KEY(goal_id,customer_id) REFERENCES app.SAVINGS_GOAL(goal_id,customer_id),
 CHECK((status='INVITED' AND goal_id IS NULL) OR (status='ACTIVE' AND goal_id IS NOT NULL))
);
IF OBJECT_ID('app.SAVINGS_SCHEDULE','U') IS NULL
CREATE TABLE app.SAVINGS_SCHEDULE (
 goal_id VARCHAR(36) PRIMARY KEY REFERENCES app.SAVINGS_GOAL(goal_id),
 amount DECIMAL(18,2) NOT NULL CHECK(amount>0),
 frequency VARCHAR(12) NOT NULL CHECK(frequency IN ('WEEKLY','MONTHLY','PAYDAY')),
 next_due DATE NOT NULL, enabled BIT NOT NULL DEFAULT 1
);
IF OBJECT_ID('app.SAVINGS_ACTIVITY','U') IS NULL
CREATE TABLE app.SAVINGS_ACTIVITY (
 operation_id VARCHAR(36) PRIMARY KEY,
 customer_id BIGINT NOT NULL REFERENCES app.CUSTOMER(customer_id),
 account_id BIGINT NOT NULL REFERENCES t24.ACCOUNT(account_id),
 idempotency_key VARCHAR(100) COLLATE Latin1_General_100_BIN2 NOT NULL,
 request_json NVARCHAR(MAX) NOT NULL,
 status VARCHAR(12) NOT NULL CHECK(status IN ('PENDING','CONFIRMED','REJECTED')),
 result_json NVARCHAR(MAX) NULL,
 created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(), completed_at DATETIME2 NULL,
 CONSTRAINT uq_savings_retry UNIQUE(customer_id,idempotency_key)
);
IF OBJECT_ID('t24.SAVINGS_RESERVATION','U') IS NULL
CREATE TABLE t24.SAVINGS_RESERVATION (
 goal_id VARCHAR(36) PRIMARY KEY,
 account_id BIGINT NOT NULL REFERENCES t24.ACCOUNT(account_id),
 reserved_amount DECIMAL(18,2) NOT NULL CHECK(reserved_amount>=0)
);
-- Append-only reservation journal, not a transfer posting.
IF OBJECT_ID('t24.SAVINGS_OPERATION','U') IS NULL
CREATE TABLE t24.SAVINGS_OPERATION (
 operation_id VARCHAR(36) PRIMARY KEY,
 account_id BIGINT NOT NULL REFERENCES t24.ACCOUNT(account_id),
 request_json NVARCHAR(MAX) NOT NULL, result_json NVARCHAR(MAX) NOT NULL,
 risk_score DECIMAL(6,5) NOT NULL CHECK(risk_score BETWEEN 0 AND 0.85),
 created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('app.SAVINGS_GOAL') AND name='ix_savings_customer')
 CREATE INDEX ix_savings_customer ON app.SAVINGS_GOAL(customer_id,circle_id);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('app.SAVINGS_ACTIVITY') AND name='ix_savings_pending')
 CREATE INDEX ix_savings_pending ON app.SAVINGS_ACTIVITY(status,created_at);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('app.SAVINGS_SCHEDULE') AND name='ix_savings_due')
 CREATE INDEX ix_savings_due ON app.SAVINGS_SCHEDULE(enabled,next_due);
COMMIT;