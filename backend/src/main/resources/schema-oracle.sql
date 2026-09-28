-- ============================================================================
-- CAPSTONE FSE: Core Retail Ledger & Balance Mutation Engine
-- Oracle XE 21c Database Schema (Master Source of Truth & Security Audit)
-- ============================================================================

-- Drop tables if existing (in reverse dependency order)
BEGIN
   EXECUTE IMMEDIATE 'DROP TABLE OUTBOX_EVENT CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'DROP TABLE TRANSACTION CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'DROP TABLE AUDIT_LOG CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'DROP TABLE ACCOUNT CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/
BEGIN
   EXECUTE IMMEDIATE 'DROP TABLE CUSTOMER CASCADE CONSTRAINTS';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- 1. CUSTOMER TABLE
CREATE TABLE CUSTOMER (
    customer_id      NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username         VARCHAR2(50) NOT NULL UNIQUE,
    password_hash    VARCHAR2(255) NOT NULL,
    first_name       VARCHAR2(100) NOT NULL,
    last_name        VARCHAR2(100) NOT NULL,
    email            VARCHAR2(150) NOT NULL UNIQUE,
    contact_no       VARCHAR2(30) NOT NULL,
    status           VARCHAR2(20) DEFAULT 'ACTIVE' NOT NULL,
    created_date     TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 2. ACCOUNT TABLE (customer_balance_master)
CREATE TABLE ACCOUNT (
    account_id       NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id      NUMBER(19) NOT NULL,
    account_number   VARCHAR2(30) NOT NULL UNIQUE,
    account_type     VARCHAR2(30) DEFAULT 'SAVINGS' NOT NULL,
    currency         VARCHAR2(10) DEFAULT 'PHP' NOT NULL,
    current_balance  NUMBER(18,4) DEFAULT 0.0000 NOT NULL,
    status           VARCHAR2(20) DEFAULT 'ACTIVE' NOT NULL,
    created_date     TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_account_customer FOREIGN KEY (customer_id) REFERENCES CUSTOMER(customer_id),
    CONSTRAINT chk_account_balance_positive CHECK (current_balance >= 0.0000)
);

-- 3. AUDIT_LOG TABLE (Synchronous Security & Operational Audit)
CREATE TABLE AUDIT_LOG (
    audit_id         NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id      NUMBER(19) NOT NULL,
    action           VARCHAR2(100) NOT NULL,
    entity           VARCHAR2(50) NOT NULL,
    details          VARCHAR2(4000) NOT NULL,
    timestamp        TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_audit_customer FOREIGN KEY (customer_id) REFERENCES CUSTOMER(customer_id)
);

-- 4. TRANSACTION TABLE
CREATE TABLE TRANSACTION (
    transaction_id   NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    from_account_id  NUMBER(19) NOT NULL,
    to_account_id    NUMBER(19),
    amount           NUMBER(18,4) NOT NULL,
    source_currency  VARCHAR2(10) DEFAULT 'PHP' NOT NULL,
    target_currency  VARCHAR2(10) DEFAULT 'PHP' NOT NULL,
    transaction_type VARCHAR2(30) NOT NULL,
    reference_no     VARCHAR2(64) NOT NULL UNIQUE,
    status           VARCHAR2(20) NOT NULL,
    failure_reason   VARCHAR2(255),
    transaction_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_tx_from_account FOREIGN KEY (from_account_id) REFERENCES ACCOUNT(account_id),
    CONSTRAINT fk_tx_to_account FOREIGN KEY (to_account_id) REFERENCES ACCOUNT(account_id),
    CONSTRAINT chk_tx_amount_positive CHECK (amount > 0.0000)
);

-- 5. OUTBOX_EVENT TABLE (Transactional Outbox Pattern for Kafka Streaming)
CREATE TABLE OUTBOX_EVENT (
    event_id         NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transaction_id   NUMBER(19) NOT NULL,
    event_type       VARCHAR2(50) NOT NULL,
    payload          CLOB NOT NULL,
    status           VARCHAR2(20) DEFAULT 'PENDING' NOT NULL,
    created_date     TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    processed_date   TIMESTAMP,
    CONSTRAINT fk_outbox_transaction FOREIGN KEY (transaction_id) REFERENCES TRANSACTION(transaction_id)
);

-- Indexes for high-throughput concurrency
CREATE INDEX idx_account_cust_id ON ACCOUNT(customer_id);
CREATE INDEX idx_account_num ON ACCOUNT(account_number);
CREATE INDEX idx_tx_from_acc ON TRANSACTION(from_account_id);
CREATE INDEX idx_tx_ref_no ON TRANSACTION(reference_no);
CREATE INDEX idx_outbox_status ON OUTBOX_EVENT(status, created_date);
CREATE INDEX idx_audit_cust_id ON AUDIT_LOG(customer_id, timestamp);

-- Seed Initial Retail Banking Customer & Accounts (Philippine Context)
INSERT INTO CUSTOMER (username, password_hash, first_name, last_name, email, contact_no, status)
VALUES ('lviernes', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Levi', 'Viernes', 'levi.viernes@paypink.ph', '+63 917 888 1234', 'ACTIVE');

INSERT INTO CUSTOMER (username, password_hash, first_name, last_name, email, contact_no, status)
VALUES ('arosales', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Aly', 'Rosales', 'aly.rosales@paypink.ph', '+63 918 555 6789', 'ACTIVE');

INSERT INTO CUSTOMER (username, password_hash, first_name, last_name, email, contact_no, status)
VALUES ('glim', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Gill', 'Lim', 'gill.lim@paypink.ph', '+63 920 333 4567', 'ACTIVE');

-- Accounts with initial balances (DECIMAL 18,4)
INSERT INTO ACCOUNT (customer_id, account_number, account_type, currency, current_balance, status)
VALUES (1, 'ACC-PH-1001-8842', 'SAVINGS_ACCOUNT', 'PHP', 125450.0000, 'ACTIVE');

INSERT INTO ACCOUNT (customer_id, account_number, account_type, currency, current_balance, status)
VALUES (1, 'ACC-PH-1001-9921', 'CHECKING_ACCOUNT', 'PHP', 50000.0000, 'ACTIVE');

INSERT INTO ACCOUNT (customer_id, account_number, account_type, currency, current_balance, status)
VALUES (1, 'ACC-PH-1001-7714', 'STRESS_TEST_ACCOUNT', 'PHP', 60.0000, 'ACTIVE');

INSERT INTO ACCOUNT (customer_id, account_number, account_type, currency, current_balance, status)
VALUES (2, 'ACC-PH-2002-3311', 'SAVINGS_ACCOUNT', 'PHP', 84320.5000, 'ACTIVE');

INSERT INTO ACCOUNT (customer_id, account_number, account_type, currency, current_balance, status)
VALUES (3, 'ACC-PH-3003-4422', 'TIME_DEPOSIT', 'PHP', 350000.0000, 'ACTIVE');
