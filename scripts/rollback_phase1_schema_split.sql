-- =========================================================================
-- PayPink 2.0: Rollback Phase 1 — Revert Schema Split back to dbo
-- Target: SQL Server 2022 / Azure SQL
-- =========================================================================

SET NOCOUNT ON;
PRINT 'Beginning Rollback of Phase 1 Schema Split...';

-- 1. Drop all dbo.* SYNONYMS
DECLARE @synonyms TABLE (syn NVARCHAR(100));
INSERT INTO @synonyms VALUES
    ('ACCOUNT'), ('LEDGER_TRANSACTION'), ('LOAN'), ('LOAN_SCHEDULE'), ('LOAN_REPAYMENT'), ('EOD_JOB_RUN'),
    ('CUSTOMER'), ('BANKING_FAVORITE'), ('AUDIT_LOG'), ('OUTBOX_EVENT'), ('REMITTANCE'), ('LOAN_APPLICATION');

DECLARE @s NVARCHAR(100);
DECLARE syn_drop_cur CURSOR FOR SELECT syn FROM @synonyms;
OPEN syn_drop_cur;
FETCH NEXT FROM syn_drop_cur INTO @s;
WHILE @@FETCH_STATUS = 0
BEGIN
    IF EXISTS (SELECT 1 FROM sys.synonyms WHERE name = @s AND schema_id = SCHEMA_ID('dbo'))
    BEGIN
        DECLARE @drop_syn_sql NVARCHAR(200) = N'DROP SYNONYM dbo.' + QUOTENAME(@s) + N';';
        EXEC sp_executesql @drop_syn_sql;
        PRINT 'Dropped synonym dbo.' + @s;
    END
    FETCH NEXT FROM syn_drop_cur INTO @s;
END
CLOSE syn_drop_cur;
DEALLOCATE syn_drop_cur;

-- 2. Transfer tables back from t24 to dbo
DECLARE @t24_tables TABLE (tbl NVARCHAR(100));
INSERT INTO @t24_tables VALUES ('ACCOUNT'), ('LEDGER_TRANSACTION'), ('LOAN'), ('LOAN_SCHEDULE'), ('LOAN_REPAYMENT'), ('EOD_JOB_RUN');

DECLARE t24_back_cur CURSOR FOR SELECT tbl FROM @t24_tables;
OPEN t24_back_cur;
FETCH NEXT FROM t24_back_cur INTO @s;
WHILE @@FETCH_STATUS = 0
BEGIN
    IF EXISTS (SELECT 1 FROM sys.tables WHERE name = @s AND schema_id = SCHEMA_ID('t24'))
    BEGIN
        DECLARE @revert_t24 NVARCHAR(200) = N'ALTER SCHEMA dbo TRANSFER t24.' + QUOTENAME(@s) + N';';
        EXEC sp_executesql @revert_t24;
        PRINT 'Reverted t24.' + @s + ' -> dbo.' + @s;
    END
    FETCH NEXT FROM t24_back_cur INTO @s;
END
CLOSE t24_back_cur;
DEALLOCATE t24_back_cur;

-- 3. Transfer tables back from app to dbo
DECLARE @app_tables TABLE (tbl NVARCHAR(100));
INSERT INTO @app_tables VALUES ('CUSTOMER'), ('BANKING_FAVORITE'), ('AUDIT_LOG'), ('OUTBOX_EVENT'), ('REMITTANCE'), ('LOAN_APPLICATION');

DECLARE app_back_cur CURSOR FOR SELECT tbl FROM @app_tables;
OPEN app_back_cur;
FETCH NEXT FROM app_back_cur INTO @s;
WHILE @@FETCH_STATUS = 0
BEGIN
    IF EXISTS (SELECT 1 FROM sys.tables WHERE name = @s AND schema_id = SCHEMA_ID('app'))
    BEGIN
        DECLARE @revert_app NVARCHAR(200) = N'ALTER SCHEMA dbo TRANSFER app.' + QUOTENAME(@s) + N';';
        EXEC sp_executesql @revert_app;
        PRINT 'Reverted app.' + @s + ' -> dbo.' + @s;
    END
    FETCH NEXT FROM app_back_cur INTO @s;
END
CLOSE app_back_cur;
DEALLOCATE app_back_cur;

-- 4. Drop schemas if empty
IF EXISTS (SELECT 1 FROM sys.schemas WHERE name = 't24') AND NOT EXISTS (SELECT 1 FROM sys.tables WHERE schema_id = SCHEMA_ID('t24'))
BEGIN
    EXEC('DROP SCHEMA t24');
    PRINT 'Dropped schema t24';
END

IF EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'app') AND NOT EXISTS (SELECT 1 FROM sys.tables WHERE schema_id = SCHEMA_ID('app'))
BEGIN
    EXEC('DROP SCHEMA app');
    PRINT 'Dropped schema app';
END

PRINT 'Rollback completed.';
