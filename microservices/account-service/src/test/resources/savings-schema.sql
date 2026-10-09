CREATE SCHEMA app;
CREATE SCHEMA t24;
CREATE TABLE app.CUSTOMER(customer_id BIGINT PRIMARY KEY,username VARCHAR(100),first_name VARCHAR(50),last_name VARCHAR(50),status VARCHAR(20));
CREATE TABLE t24.ACCOUNT(account_id BIGINT PRIMARY KEY,customer_id BIGINT,account_type VARCHAR(30),status VARCHAR(20),currency VARCHAR(3));
CREATE TABLE app.PINK_CIRCLE (
 circle_id VARCHAR(36) PRIMARY KEY,
 admin_customer_id BIGINT NOT NULL REFERENCES app.CUSTOMER(customer_id),
 name NVARCHAR(80) NOT NULL,
 completion_notified BIT NOT NULL DEFAULT 0,
 target_amount DECIMAL(18,2) NOT NULL CHECK(target_amount>0),
 target_date DATE NOT NULL,
 created_at DATETIME2 NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE app.SAVINGS_GOAL (
 goal_id VARCHAR(36) PRIMARY KEY,
 customer_id BIGINT NOT NULL REFERENCES app.CUSTOMER(customer_id),
 account_id BIGINT NOT NULL REFERENCES t24.ACCOUNT(account_id),
 circle_id VARCHAR(36) NULL REFERENCES app.PINK_CIRCLE(circle_id),
 name NVARCHAR(80) NOT NULL, category VARCHAR(30) NOT NULL,
 target_amount DECIMAL(18,2) NOT NULL CHECK(target_amount>0), target_date DATE NOT NULL,
 created_at DATETIME2 NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uq_savings_owner UNIQUE(goal_id,customer_id)
);
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
CREATE TABLE app.SAVINGS_SCHEDULE (
 goal_id VARCHAR(36) PRIMARY KEY REFERENCES app.SAVINGS_GOAL(goal_id),
 amount DECIMAL(18,2) NOT NULL CHECK(amount>0),
 frequency VARCHAR(12) NOT NULL CHECK(frequency IN ('WEEKLY','MONTHLY','PAYDAY')),
 next_due DATE NOT NULL, enabled BIT NOT NULL DEFAULT 1
);
CREATE TABLE app.SAVINGS_ACTIVITY (
 operation_id VARCHAR(36) PRIMARY KEY,
 customer_id BIGINT NOT NULL REFERENCES app.CUSTOMER(customer_id),
 account_id BIGINT NOT NULL REFERENCES t24.ACCOUNT(account_id),
 idempotency_key VARCHAR(100) NOT NULL,
 request_json NVARCHAR(MAX) NOT NULL,
 status VARCHAR(12) NOT NULL CHECK(status IN ('PENDING','CONFIRMED','REJECTED')),
 result_json NVARCHAR(MAX) NULL,
 created_at DATETIME2 NOT NULL DEFAULT CURRENT_TIMESTAMP, completed_at DATETIME2 NULL,
 CONSTRAINT uq_savings_retry UNIQUE(customer_id,idempotency_key)
);
CREATE TABLE t24.SAVINGS_RESERVATION (
 goal_id VARCHAR(36) PRIMARY KEY,
 account_id BIGINT NOT NULL REFERENCES t24.ACCOUNT(account_id),
 reserved_amount DECIMAL(18,2) NOT NULL CHECK(reserved_amount>=0)
);
CREATE TABLE t24.SAVINGS_OPERATION (
 operation_id VARCHAR(36) PRIMARY KEY,
 account_id BIGINT NOT NULL REFERENCES t24.ACCOUNT(account_id),
 request_json NVARCHAR(MAX) NOT NULL, result_json NVARCHAR(MAX) NOT NULL,
 risk_score DECIMAL(6,5) NOT NULL CHECK(risk_score BETWEEN 0 AND 0.85),
 created_at DATETIME2 NOT NULL DEFAULT CURRENT_TIMESTAMP
);