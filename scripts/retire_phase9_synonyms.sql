-- =========================================================================
-- PayPink 2.0: Phase 9 — Synonym Retirement & Clean-Up Script
-- Target: SQL Server 2022 / Azure SQL
-- Note: Execute only when all microservices and external clients have been
--       fully cut over to explicit t24.* and app.* schemas.
-- =========================================================================

SET NOCOUNT ON;
PRINT 'Beginning Phase 9 Synonym Retirement Verification & Cleanup...';

DECLARE @synonyms TABLE (syn NVARCHAR(100));
INSERT INTO @synonyms VALUES
    ('ACCOUNT'),
    ('LEDGER_TRANSACTION'),
    ('LOAN'),
    ('LOAN_SCHEDULE'),
    ('LOAN_REPAYMENT'),
    ('EOD_JOB_RUN'),
    ('LOCKED_AMOUNT'),
    ('POSTING_JOURNAL'),
    ('CUSTOMER'),
    ('BANKING_FAVORITE'),
    ('AUDIT_LOG'),
    ('OUTBOX_EVENT'),
    ('REMITTANCE'),
    ('LOAN_APPLICATION');

DECLARE @syn_name NVARCHAR(100);
DECLARE syn_retire_cur CURSOR FOR SELECT syn FROM @synonyms;
OPEN syn_retire_cur;
FETCH NEXT FROM syn_retire_cur INTO @syn_name;
WHILE @@FETCH_STATUS = 0
BEGIN
    IF EXISTS (SELECT 1 FROM sys.synonyms WHERE name = @syn_name AND schema_id = SCHEMA_ID('dbo'))
    BEGIN
        DECLARE @drop_syn NVARCHAR(200) = N'DROP SYNONYM dbo.' + QUOTENAME(@syn_name) + N';';
        PRINT 'Dropping synonym dbo.' + @syn_name;
        EXEC sp_executesql @drop_syn;
    END
    ELSE
    BEGIN
        PRINT 'Synonym dbo.' + @syn_name + ' does not exist or already retired.';
    END
    FETCH NEXT FROM syn_retire_cur INTO @syn_name;
END
CLOSE syn_retire_cur;
DEALLOCATE syn_retire_cur;

PRINT 'Phase 9 Synonym Retirement script completed.';
