-- =========================================================================
-- PayPink 2.0: Rollback Phase 2 — Drop t24.LOCKED_AMOUNT & t24.POSTING_JOURNAL
-- Target: SQL Server 2022 / Azure SQL
-- =========================================================================

SET NOCOUNT ON;
PRINT 'Beginning Rollback of Phase 2 T24 Core Schema...';

-- 1. Drop Synonyms
IF EXISTS (SELECT 1 FROM sys.synonyms WHERE name = 'POSTING_JOURNAL' AND schema_id = SCHEMA_ID('dbo'))
BEGIN
    DROP SYNONYM dbo.POSTING_JOURNAL;
    PRINT 'Dropped synonym dbo.POSTING_JOURNAL';
END

IF EXISTS (SELECT 1 FROM sys.synonyms WHERE name = 'LOCKED_AMOUNT' AND schema_id = SCHEMA_ID('dbo'))
BEGIN
    DROP SYNONYM dbo.LOCKED_AMOUNT;
    PRINT 'Dropped synonym dbo.LOCKED_AMOUNT';
END

-- 2. Drop Tables
IF EXISTS (SELECT 1 FROM sys.tables WHERE name = 'POSTING_JOURNAL' AND schema_id = SCHEMA_ID('t24'))
BEGIN
    DROP TABLE t24.POSTING_JOURNAL;
    PRINT 'Dropped table t24.POSTING_JOURNAL';
END

IF EXISTS (SELECT 1 FROM sys.tables WHERE name = 'LOCKED_AMOUNT' AND schema_id = SCHEMA_ID('t24'))
BEGIN
    DROP TABLE t24.LOCKED_AMOUNT;
    PRINT 'Dropped table t24.LOCKED_AMOUNT';
END

PRINT 'Rollback of Phase 2 T24 Core Schema completed successfully!';
