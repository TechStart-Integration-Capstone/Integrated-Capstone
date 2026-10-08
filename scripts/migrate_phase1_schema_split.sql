-- =========================================================================
-- PayPink 2.0: Phase 1 — Azure SQL Schema Split (T24 Core vs Application)
-- Target: SQL Server 2022 / Azure SQL
-- Idempotent: Can be run safely multiple times
-- =========================================================================

SET NOCOUNT ON;
PRINT 'Beginning Phase 1 Schema Split...';

-- 1. Create schemas if they do not exist
IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 't24')
BEGIN
    EXEC('CREATE SCHEMA t24');
    PRINT 'Created schema: t24';
END
ELSE
    PRINT 'Schema t24 already exists.';

IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'app')
BEGIN
    EXEC('CREATE SCHEMA app');
    PRINT 'Created schema: app';
END
ELSE
    PRINT 'Schema app already exists.';

-- 2. Drop cross-boundary Foreign Keys dynamically
-- (SQL Server does not allow foreign keys to reference synonyms, and cross-boundary FKs violate domain isolation)
DECLARE @drop_fks NVARCHAR(MAX) = N'';

SELECT @drop_fks += N'ALTER TABLE ' + QUOTENAME(OBJECT_SCHEMA_NAME(parent_object_id)) + N'.' + QUOTENAME(OBJECT_NAME(parent_object_id)) +
                    N' DROP CONSTRAINT ' + QUOTENAME(name) + N';' + CHAR(13)
FROM sys.foreign_keys
WHERE (
    (OBJECT_NAME(parent_object_id) IN ('OUTBOX_EVENT', 'REMITTANCE', 'BANKING_FAVORITE', 'LOAN_APPLICATION')
     AND OBJECT_NAME(referenced_object_id) IN ('ACCOUNT', 'LEDGER_TRANSACTION'))
    OR
    (OBJECT_NAME(parent_object_id) IN ('ACCOUNT', 'LOAN')
     AND OBJECT_NAME(referenced_object_id) IN ('CUSTOMER', 'LOAN_APPLICATION'))
    OR
    (OBJECT_NAME(parent_object_id) IN ('LOAN', 'LOAN_REPAYMENT')
     AND OBJECT_NAME(referenced_object_id) IN ('LEDGER_TRANSACTION'))
);

IF LEN(@drop_fks) > 0
BEGIN
    PRINT 'Dropping cross-boundary foreign keys:';
    PRINT @drop_fks;
    EXEC sp_executesql @drop_fks;
END
ELSE
    PRINT 'No cross-boundary foreign keys found to drop.';

-- 3. Transfer tables to t24 schema
DECLARE @t24_tables TABLE (tbl NVARCHAR(100));
INSERT INTO @t24_tables VALUES ('ACCOUNT'), ('LEDGER_TRANSACTION'), ('LOAN'), ('LOAN_SCHEDULE'), ('LOAN_REPAYMENT'), ('EOD_JOB_RUN');

DECLARE @cur_tbl NVARCHAR(100);
DECLARE t24_cur CURSOR FOR SELECT tbl FROM @t24_tables;
OPEN t24_cur;
FETCH NEXT FROM t24_cur INTO @cur_tbl;
WHILE @@FETCH_STATUS = 0
BEGIN
    IF EXISTS (SELECT 1 FROM sys.tables WHERE name = @cur_tbl AND schema_id = SCHEMA_ID('dbo'))
    BEGIN
        DECLARE @sql_t24 NVARCHAR(200) = N'ALTER SCHEMA t24 TRANSFER dbo.' + QUOTENAME(@cur_tbl) + N';';
        EXEC sp_executesql @sql_t24;
        PRINT 'Transferred dbo.' + @cur_tbl + ' -> t24.' + @cur_tbl;
    END
    FETCH NEXT FROM t24_cur INTO @cur_tbl;
END
CLOSE t24_cur;
DEALLOCATE t24_cur;

-- 4. Transfer tables to app schema
DECLARE @app_tables TABLE (tbl NVARCHAR(100));
INSERT INTO @app_tables VALUES ('CUSTOMER'), ('BANKING_FAVORITE'), ('AUDIT_LOG'), ('OUTBOX_EVENT'), ('REMITTANCE'), ('LOAN_APPLICATION');

DECLARE app_cur CURSOR FOR SELECT tbl FROM @app_tables;
OPEN app_cur;
FETCH NEXT FROM app_cur INTO @cur_tbl;
WHILE @@FETCH_STATUS = 0
BEGIN
    IF EXISTS (SELECT 1 FROM sys.tables WHERE name = @cur_tbl AND schema_id = SCHEMA_ID('dbo'))
    BEGIN
        DECLARE @sql_app NVARCHAR(200) = N'ALTER SCHEMA app TRANSFER dbo.' + QUOTENAME(@cur_tbl) + N';';
        EXEC sp_executesql @sql_app;
        PRINT 'Transferred dbo.' + @cur_tbl + ' -> app.' + @cur_tbl;
    END
    FETCH NEXT FROM app_cur INTO @cur_tbl;
END
CLOSE app_cur;
DEALLOCATE app_cur;

-- 5. Create or Replace dbo.* SYNONYMS pointing to t24 and app schemas
-- This guarantees backward compatibility for all existing queries.
DECLARE @synonyms TABLE (syn NVARCHAR(100), target NVARCHAR(200));
INSERT INTO @synonyms VALUES
    ('ACCOUNT', 't24.ACCOUNT'),
    ('LEDGER_TRANSACTION', 't24.LEDGER_TRANSACTION'),
    ('LOAN', 't24.LOAN'),
    ('LOAN_SCHEDULE', 't24.LOAN_SCHEDULE'),
    ('LOAN_REPAYMENT', 't24.LOAN_REPAYMENT'),
    ('EOD_JOB_RUN', 't24.EOD_JOB_RUN'),
    ('CUSTOMER', 'app.CUSTOMER'),
    ('BANKING_FAVORITE', 'app.BANKING_FAVORITE'),
    ('AUDIT_LOG', 'app.AUDIT_LOG'),
    ('OUTBOX_EVENT', 'app.OUTBOX_EVENT'),
    ('REMITTANCE', 'app.REMITTANCE'),
    ('LOAN_APPLICATION', 'app.LOAN_APPLICATION');

DECLARE @syn_name NVARCHAR(100), @syn_target NVARCHAR(200);
DECLARE syn_cur CURSOR FOR SELECT syn, target FROM @synonyms;
OPEN syn_cur;
FETCH NEXT FROM syn_cur INTO @syn_name, @syn_target;
WHILE @@FETCH_STATUS = 0
BEGIN
    IF EXISTS (SELECT 1 FROM sys.synonyms WHERE name = @syn_name AND schema_id = SCHEMA_ID('dbo'))
    BEGIN
        DECLARE @drop_syn NVARCHAR(200) = N'DROP SYNONYM dbo.' + QUOTENAME(@syn_name) + N';';
        EXEC sp_executesql @drop_syn;
    END

    -- Only create synonym if table is not still in dbo
    IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = @syn_name AND schema_id = SCHEMA_ID('dbo'))
    BEGIN
        DECLARE @create_syn NVARCHAR(300) = N'CREATE SYNONYM dbo.' + QUOTENAME(@syn_name) + N' FOR ' + @syn_target + N';';
        EXEC sp_executesql @create_syn;
        PRINT 'Created synonym dbo.' + @syn_name + ' -> ' + @syn_target;
    END
    FETCH NEXT FROM syn_cur INTO @syn_name, @syn_target;
END
CLOSE syn_cur;
DEALLOCATE syn_cur;

PRINT 'Phase 1 Schema Split completed successfully!';
