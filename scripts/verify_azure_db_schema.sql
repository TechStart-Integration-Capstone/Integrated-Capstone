-- ============================================================================
-- PayPink 2.0 — Azure SQL Schema & Seed Verification Audit Script
-- Target: paypink.database.windows.net / database: paypink
-- Purpose: Audits that all required tables, columns, synonyms, and system
--          accounts from Phases 1 through 10 are present and up to date.
-- ============================================================================

SET NOCOUNT ON;
PRINT '====================================================================';
PRINT '  PAYPINK 2.0 AZURE SQL SCHEMA & SEED AUDIT REPORT';
PRINT '  Target: paypink.database.windows.net / DB: paypink';
PRINT '====================================================================';
PRINT '';

DECLARE @Failures INT = 0;

-- ----------------------------------------------------------------------------
-- 1. SCHEMAS CHECK
-- ----------------------------------------------------------------------------
PRINT '--- 1. SCHEMA SEPARATION CHECK ---';
IF SCHEMA_ID('app') IS NOT NULL PRINT '[PASS] Schema "app" exists.'
ELSE BEGIN PRINT '[FAIL] Schema "app" is MISSING! Run scripts/migrate_phase1_schema_split.sql'; SET @Failures = @Failures + 1; END

IF SCHEMA_ID('t24') IS NOT NULL PRINT '[PASS] Schema "t24" exists.'
ELSE BEGIN PRINT '[FAIL] Schema "t24" is MISSING! Run scripts/migrate_phase1_schema_split.sql'; SET @Failures = @Failures + 1; END

PRINT '';

-- ----------------------------------------------------------------------------
-- 2. CORE & DOMAIN TABLES CHECK
-- ----------------------------------------------------------------------------
PRINT '--- 2. CORE TABLES EXISTENCE ---';
DECLARE @Tables TABLE (SchemaName NVARCHAR(50), TableName NVARCHAR(100), Phase NVARCHAR(100));
INSERT INTO @Tables VALUES 
  ('app', 'CUSTOMER', 'Phase 1 - App Customer'),
  ('app', 'REMITTANCE', 'Phase 1 - Remittance Saga'),
  ('app', 'OUTBOX_EVENT', 'Phase 1 - Outbox Pattern'),
  ('app', 'BANKING_FAVORITE', 'Phase 5 - Beneficiaries'),
  ('app', 'LOAN_APPLICATION', 'Phase 6 - Loans Origination'),
  ('t24', 'ACCOUNT', 'Phase 1 - T24 Core Accounts'),
  ('t24', 'LEDGER_TRANSACTION', 'Phase 1 - Double-Entry Ledger'),
  ('t24', 'LOCKED_AMOUNT', 'Phase 2 - T24 Core Holds'),
  ('t24', 'POSTING_JOURNAL', 'Phase 2 - Core Posting Journal'),
  ('t24', 'LOAN', 'Phase 6 - Loan Core Entity'),
  ('t24', 'LOAN_SCHEDULE', 'Phase 6 - Amortization Schedule'),
  ('t24', 'LOAN_REPAYMENT', 'Phase 6 - Repayment Records'),
  ('t24', 'EOD_JOB_RUN', 'Phase 7 - Interest EOD Job');

DECLARE @s NVARCHAR(50), @t NVARCHAR(100), @p NVARCHAR(100);
DECLARE table_cursor CURSOR FOR SELECT SchemaName, TableName, Phase FROM @Tables;
OPEN table_cursor;
FETCH NEXT FROM table_cursor INTO @s, @t, @p;
WHILE @@FETCH_STATUS = 0
BEGIN
    IF OBJECT_ID(@s + '.' + @t, 'U') IS NOT NULL
        PRINT '[PASS] Table ' + @s + '.' + @t + ' (' + @p + ')';
    ELSE
    BEGIN
        PRINT '[FAIL] Table ' + @s + '.' + @t + ' is MISSING! (' + @p + ')';
        SET @Failures = @Failures + 1;
    END
    FETCH NEXT FROM table_cursor INTO @s, @t, @p;
END
CLOSE table_cursor;
DEALLOCATE table_cursor;

PRINT '';

-- ----------------------------------------------------------------------------
-- 3. CRITICAL COLUMNS & MIGRATION FLAGS CHECK
-- ----------------------------------------------------------------------------
PRINT '--- 3. CRITICAL COLUMNS CHECK ---';

-- 3.1 app.CUSTOMER columns
IF COL_LENGTH('app.CUSTOMER', 'roles') IS NOT NULL PRINT '[PASS] app.CUSTOMER.roles exists (Phase 10 RBAC).'
ELSE IF COL_LENGTH('dbo.CUSTOMER', 'roles') IS NOT NULL PRINT '[PASS] dbo.CUSTOMER.roles exists (Phase 10 RBAC).'
ELSE BEGIN PRINT '[FAIL] CUSTOMER.roles is MISSING! Run scripts/migrate_phase10_rbac_roles.sql'; SET @Failures = @Failures + 1; END

IF COL_LENGTH('app.CUSTOMER', 'mpin_hash') IS NOT NULL PRINT '[PASS] app.CUSTOMER.mpin_hash exists (MPIN Security).'
ELSE IF COL_LENGTH('dbo.CUSTOMER', 'mpin_hash') IS NOT NULL PRINT '[PASS] dbo.CUSTOMER.mpin_hash exists (MPIN Security).'
ELSE BEGIN PRINT '[FAIL] CUSTOMER.mpin_hash is MISSING! Run scripts/migrate_mpin.sql'; SET @Failures = @Failures + 1; END

IF COL_LENGTH('app.CUSTOMER', 'credit_score') IS NOT NULL PRINT '[PASS] app.CUSTOMER.credit_score exists (Loans Engine).'
ELSE IF COL_LENGTH('dbo.CUSTOMER', 'credit_score') IS NOT NULL PRINT '[PASS] dbo.CUSTOMER.credit_score exists (Loans Engine).'
ELSE BEGIN PRINT '[FAIL] CUSTOMER.credit_score is MISSING! Run scripts/migrate_phase6_loans.sql'; SET @Failures = @Failures + 1; END

-- 3.2 t24.ACCOUNT columns
IF COL_LENGTH('t24.ACCOUNT', 'held_balance') IS NOT NULL PRINT '[PASS] t24.ACCOUNT.held_balance exists (Phase 5 Holds).'
ELSE IF COL_LENGTH('dbo.ACCOUNT', 'held_balance') IS NOT NULL PRINT '[PASS] dbo.ACCOUNT.held_balance exists (Phase 5 Holds).'
ELSE BEGIN PRINT '[FAIL] ACCOUNT.held_balance is MISSING! Run scripts/migrate_phase5_hardening.sql'; SET @Failures = @Failures + 1; END

IF COL_LENGTH('t24.ACCOUNT', 'interest_rate') IS NOT NULL PRINT '[PASS] t24.ACCOUNT.interest_rate exists (Interest EOD).'
ELSE IF COL_LENGTH('dbo.ACCOUNT', 'interest_rate') IS NOT NULL PRINT '[PASS] dbo.ACCOUNT.interest_rate exists (Interest EOD).'
ELSE BEGIN PRINT '[FAIL] ACCOUNT.interest_rate is MISSING! Run scripts/migrate_interest_azuresql.sql'; SET @Failures = @Failures + 1; END

-- 3.3 app.REMITTANCE columns
IF COL_LENGTH('app.REMITTANCE', 'cancel_until') IS NOT NULL PRINT '[PASS] app.REMITTANCE.cancel_until exists (Phase 5 Saga).'
ELSE IF COL_LENGTH('dbo.REMITTANCE', 'cancel_until') IS NOT NULL PRINT '[PASS] dbo.REMITTANCE.cancel_until exists (Phase 5 Saga).'
ELSE BEGIN PRINT '[FAIL] REMITTANCE.cancel_until is MISSING! Run scripts/migrate_phase5_hardening.sql'; SET @Failures = @Failures + 1; END

IF COL_LENGTH('app.REMITTANCE', 'transaction_type') IS NOT NULL PRINT '[PASS] app.REMITTANCE.transaction_type exists (Phase 6 Loans).'
ELSE IF COL_LENGTH('dbo.REMITTANCE', 'transaction_type') IS NOT NULL PRINT '[PASS] dbo.REMITTANCE.transaction_type exists (Phase 6 Loans).'
ELSE BEGIN PRINT '[FAIL] REMITTANCE.transaction_type is MISSING! Run scripts/migrate_phase6_loans.sql'; SET @Failures = @Failures + 1; END

-- 3.4 t24.LOAN_REPAYMENT columns
IF COL_LENGTH('t24.LOAN_REPAYMENT', 'status') IS NOT NULL PRINT '[PASS] t24.LOAN_REPAYMENT.status exists (Repayment Recovery).'
ELSE IF COL_LENGTH('dbo.LOAN_REPAYMENT', 'status') IS NOT NULL PRINT '[PASS] dbo.LOAN_REPAYMENT.status exists (Repayment Recovery).'
ELSE BEGIN PRINT '[FAIL] LOAN_REPAYMENT.status is MISSING! Run scripts/migrate_phase6_loans.sql'; SET @Failures = @Failures + 1; END

-- 3.5 t24.LOAN columns
IF COL_LENGTH('t24.LOAN', 'last_autodebit_status') IS NOT NULL PRINT '[PASS] t24.LOAN.last_autodebit_status exists (Auto-Debit Tracking).'
ELSE IF COL_LENGTH('dbo.LOAN', 'last_autodebit_status') IS NOT NULL PRINT '[PASS] dbo.LOAN.last_autodebit_status exists (Auto-Debit Tracking).'
ELSE BEGIN PRINT '[FAIL] LOAN.last_autodebit_status is MISSING! Run scripts/migrate_phase6_loans.sql'; SET @Failures = @Failures + 1; END

PRINT '';

-- ----------------------------------------------------------------------------
-- 4. SYNONYMS COMPATIBILITY CHECK
-- ----------------------------------------------------------------------------
PRINT '--- 4. SYNONYM ROUTING CHECK ---';
DECLARE @Synonyms TABLE (SynName NVARCHAR(100), TargetName NVARCHAR(100));
INSERT INTO @Synonyms VALUES
  ('dbo.CUSTOMER', 'app.CUSTOMER'),
  ('dbo.REMITTANCE', 'app.REMITTANCE'),
  ('dbo.OUTBOX_EVENT', 'app.OUTBOX_EVENT'),
  ('dbo.ACCOUNT', 't24.ACCOUNT'),
  ('dbo.LEDGER_TRANSACTION', 't24.LEDGER_TRANSACTION'),
  ('dbo.LOCKED_AMOUNT', 't24.LOCKED_AMOUNT'),
  ('dbo.POSTING_JOURNAL', 't24.POSTING_JOURNAL'),
  ('dbo.LOAN', 't24.LOAN'),
  ('dbo.LOAN_SCHEDULE', 't24.LOAN_SCHEDULE'),
  ('dbo.LOAN_REPAYMENT', 't24.LOAN_REPAYMENT'),
  ('dbo.LOAN_APPLICATION', 'app.LOAN_APPLICATION');

DECLARE @syn NVARCHAR(100), @tgt NVARCHAR(100);
DECLARE syn_cursor CURSOR FOR SELECT SynName, TargetName FROM @Synonyms;
OPEN syn_cursor;
FETCH NEXT FROM syn_cursor INTO @syn, @tgt;
WHILE @@FETCH_STATUS = 0
BEGIN
    IF OBJECT_ID(@syn, 'SN') IS NOT NULL
        PRINT '[PASS] Synonym ' + @syn + ' -> ' + @tgt;
    ELSE IF OBJECT_ID(@syn, 'U') IS NOT NULL
        PRINT '[WARN] ' + @syn + ' is a direct user table (pre-split baseline).';
    ELSE
    BEGIN
        PRINT '[FAIL] Synonym ' + @syn + ' is MISSING! Run scripts/migrate_phase1_schema_split.sql';
        SET @Failures = @Failures + 1;
    END
    FETCH NEXT FROM syn_cursor INTO @syn, @tgt;
END
CLOSE syn_cursor;
DEALLOCATE syn_cursor;

PRINT '';

-- ----------------------------------------------------------------------------
-- 5. SYSTEM ACCOUNTS & LIQUIDITY POOLS CHECK
-- ----------------------------------------------------------------------------
PRINT '--- 5. SYSTEM LIQUIDITY & CLEARING POOLS ---';

-- 5.1 Bank Owner Account
IF EXISTS (SELECT 1 FROM sys.tables WHERE object_id = OBJECT_ID('app.CUSTOMER', 'U'))
BEGIN
    IF EXISTS (SELECT 1 FROM app.CUSTOMER WHERE username = 'paypink_bank')
        PRINT '[PASS] System Customer "paypink_bank" exists in app.CUSTOMER.'
    ELSE BEGIN PRINT '[FAIL] System Customer "paypink_bank" is MISSING! Run scripts/migrate_phase6_loans.sql'; SET @Failures = @Failures + 1; END
END
ELSE IF EXISTS (SELECT 1 FROM dbo.CUSTOMER WHERE username = 'paypink_bank')
    PRINT '[PASS] System Customer "paypink_bank" exists in dbo.CUSTOMER.'
ELSE BEGIN PRINT '[FAIL] System Customer "paypink_bank" is MISSING!'; SET @Failures = @Failures + 1; END

-- 5.2 Bank Loan Pool: PH1000000LOAN (₱50,000,000)
IF EXISTS (SELECT 1 FROM sys.tables WHERE object_id = OBJECT_ID('t24.ACCOUNT', 'U'))
BEGIN
    IF EXISTS (SELECT 1 FROM t24.ACCOUNT WHERE account_number = 'PH1000000LOAN')
        PRINT '[PASS] Loan Liquidity Pool "PH1000000LOAN" exists in t24.ACCOUNT.'
    ELSE BEGIN PRINT '[FAIL] Loan Liquidity Pool "PH1000000LOAN" is MISSING! Run scripts/migrate_phase6_loans.sql'; SET @Failures = @Failures + 1; END

    IF EXISTS (SELECT 1 FROM t24.ACCOUNT WHERE account_number = 'PH1000000EXT')
        PRINT '[PASS] External Clearing Account "PH1000000EXT" exists in t24.ACCOUNT.'
    ELSE BEGIN PRINT '[FAIL] External Clearing Account "PH1000000EXT" is MISSING! Run scripts/migrate_external_clearing.sql'; SET @Failures = @Failures + 1; END
END
ELSE
BEGIN
    IF EXISTS (SELECT 1 FROM dbo.ACCOUNT WHERE account_number = 'PH1000000LOAN')
        PRINT '[PASS] Loan Liquidity Pool "PH1000000LOAN" exists in dbo.ACCOUNT.'
    ELSE BEGIN PRINT '[FAIL] Loan Liquidity Pool "PH1000000LOAN" is MISSING! Run scripts/migrate_phase6_loans.sql'; SET @Failures = @Failures + 1; END

    IF EXISTS (SELECT 1 FROM dbo.ACCOUNT WHERE account_number = 'PH1000000EXT')
        PRINT '[PASS] External Clearing Account "PH1000000EXT" exists in dbo.ACCOUNT.'
    ELSE BEGIN PRINT '[FAIL] External Clearing Account "PH1000000EXT" is MISSING! Run scripts/migrate_external_clearing.sql'; SET @Failures = @Failures + 1; END
END

PRINT '';

-- ----------------------------------------------------------------------------
-- 6. DEMO USERS SEED CHECK
-- ----------------------------------------------------------------------------
PRINT '--- 6. DEMO SEED USERS CHECK ---';
DECLARE @DemoUsers TABLE (Username NVARCHAR(50), RoleExpected NVARCHAR(100));
INSERT INTO @DemoUsers VALUES
  ('lviernes', 'ROLE_CUSTOMER,ROLE_RETAIL_USER'),
  ('arosales', 'ROLE_CUSTOMER,ROLE_RETAIL_USER'),
  ('glim', 'ROLE_CUSTOMER,ROLE_RETAIL_USER'),
  ('admin', 'ROLE_ADMIN,ROLE_OPERATIONS');

DECLARE @u NVARCHAR(50), @re NVARCHAR(100);
DECLARE user_cursor CURSOR FOR SELECT Username, RoleExpected FROM @DemoUsers;
OPEN user_cursor;
FETCH NEXT FROM user_cursor INTO @u, @re;
WHILE @@FETCH_STATUS = 0
BEGIN
    IF EXISTS (SELECT 1 FROM sys.tables WHERE object_id = OBJECT_ID('app.CUSTOMER', 'U'))
    BEGIN
        IF EXISTS (SELECT 1 FROM app.CUSTOMER WHERE username = @u)
            PRINT '[PASS] Seed user "' + @u + '" is present in app.CUSTOMER.';
        ELSE
        BEGIN
            PRINT '[FAIL] Seed user "' + @u + '" is MISSING! Run scripts/seed_demo_azure_sql.sql';
            SET @Failures = @Failures + 1;
        END
    END
    ELSE
    BEGIN
        IF EXISTS (SELECT 1 FROM dbo.CUSTOMER WHERE username = @u)
            PRINT '[PASS] Seed user "' + @u + '" is present in dbo.CUSTOMER.';
        ELSE
        BEGIN
            PRINT '[FAIL] Seed user "' + @u + '" is MISSING! Run scripts/seed_demo_azure_sql.sql';
            SET @Failures = @Failures + 1;
        END
    END
    FETCH NEXT FROM user_cursor INTO @u, @re;
END
CLOSE user_cursor;
DEALLOCATE user_cursor;

PRINT '';
PRINT '====================================================================';
IF @Failures = 0
    PRINT '  AUDIT RESULT: 100% COMPLETE & UP TO DATE! (0 Failures)';
ELSE
    PRINT '  AUDIT RESULT: ' + CAST(@Failures AS NVARCHAR(10)) + ' ISSUES FOUND! Review the missing items above.';
PRINT '====================================================================';
GO
