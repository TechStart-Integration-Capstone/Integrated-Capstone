-- =========================================================================
-- PayPink 2.0: MPIN Persistence Migration
-- Target: SQL Server 2022 / Azure SQL
-- Idempotent: Adds mpin_hash column to app.CUSTOMER (or dbo.CUSTOMER)
-- =========================================================================

SET NOCOUNT ON;
PRINT 'Beginning MPIN Persistence Migration...';

-- 1. Ensure mpin_hash column exists in app.CUSTOMER (if schema app exists)
IF EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id WHERE s.name = 'app' AND t.name = 'CUSTOMER')
BEGIN
    IF NOT EXISTS (SELECT 1 FROM sys.columns c JOIN sys.tables t ON c.object_id = t.object_id JOIN sys.schemas s ON t.schema_id = s.schema_id WHERE s.name = 'app' AND t.name = 'CUSTOMER' AND c.name = 'mpin_hash')
    BEGIN
        ALTER TABLE app.CUSTOMER ADD mpin_hash NVARCHAR(255) NULL;
        PRINT 'Added column mpin_hash to app.CUSTOMER';
    END
    ELSE
    BEGIN
        PRINT 'Column mpin_hash already exists in app.CUSTOMER';
    END
END

-- 2. Ensure mpin_hash column exists in dbo.CUSTOMER
IF EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id WHERE s.name = 'dbo' AND t.name = 'CUSTOMER')
BEGIN
    IF NOT EXISTS (SELECT 1 FROM sys.columns c JOIN sys.tables t ON c.object_id = t.object_id JOIN sys.schemas s ON t.schema_id = s.schema_id WHERE s.name = 'dbo' AND t.name = 'CUSTOMER' AND c.name = 'mpin_hash')
    BEGIN
        ALTER TABLE dbo.CUSTOMER ADD mpin_hash NVARCHAR(255) NULL;
        PRINT 'Added column mpin_hash to dbo.CUSTOMER';
    END
    ELSE
    BEGIN
        PRINT 'Column mpin_hash already exists in dbo.CUSTOMER';
    END
END

PRINT 'MPIN Persistence Migration complete.';
