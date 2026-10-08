-- =========================================================================
-- PayPink 2.0: Phase 10 — Database RBAC & Roles Migration
-- Target: SQL Server 2022 / Azure SQL
-- Idempotent: Can be run safely multiple times
-- =========================================================================

SET NOCOUNT ON;
PRINT 'Beginning Phase 10 RBAC Roles Migration...';

-- 1. Ensure roles column exists in app.CUSTOMER (or dbo.CUSTOMER)
IF EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id WHERE s.name = 'app' AND t.name = 'CUSTOMER')
BEGIN
    IF NOT EXISTS (SELECT 1 FROM sys.columns c JOIN sys.tables t ON c.object_id = t.object_id JOIN sys.schemas s ON t.schema_id = s.schema_id WHERE s.name = 'app' AND t.name = 'CUSTOMER' AND c.name = 'roles')
    BEGIN
        ALTER TABLE app.CUSTOMER ADD roles NVARCHAR(255) NOT NULL CONSTRAINT DF_APP_CUSTOMER_ROLES DEFAULT 'ROLE_CUSTOMER,ROLE_RETAIL_USER';
        PRINT 'Added column roles to app.CUSTOMER';
    END
    ELSE
    BEGIN
        PRINT 'Column roles already exists in app.CUSTOMER';
    END

    -- Ensure non-empty roles for all existing customers
    UPDATE app.CUSTOMER
    SET roles = 'ROLE_CUSTOMER,ROLE_RETAIL_USER'
    WHERE roles IS NULL OR LTRIM(RTRIM(roles)) = '';

    -- Seed or update admin user
    IF NOT EXISTS (SELECT 1 FROM app.CUSTOMER WHERE username = 'admin')
    BEGIN
        INSERT INTO app.CUSTOMER (username, password_hash, first_name, last_name, email, contact_no, status, roles)
        VALUES (
            N'admin',
            N'$2a$10$5X2WM6Ws7z.jjQCWeeLTheqLAFNyVboU0nSrYvQ9MTBeBfmkARsqq',
            N'PayPink',
            N'Administrator',
            N'admin@paypink.internal',
            N'+630000000000',
            N'ACTIVE',
            N'ROLE_ADMIN,ROLE_CORE_ENGINEER'
        );
        PRINT 'Seeded admin user into app.CUSTOMER';
    END
    ELSE
    BEGIN
        UPDATE app.CUSTOMER
        SET roles = N'ROLE_ADMIN,ROLE_CORE_ENGINEER',
            password_hash = N'$2a$10$5X2WM6Ws7z.jjQCWeeLTheqLAFNyVboU0nSrYvQ9MTBeBfmkARsqq',
            status = N'ACTIVE'
        WHERE username = N'admin';
        PRINT 'Updated admin user in app.CUSTOMER with ROLE_ADMIN,ROLE_CORE_ENGINEER';
    END
END
ELSE IF EXISTS (SELECT 1 FROM sys.tables t JOIN sys.schemas s ON t.schema_id = s.schema_id WHERE s.name = 'dbo' AND t.name = 'CUSTOMER')
BEGIN
    IF NOT EXISTS (SELECT 1 FROM sys.columns c JOIN sys.tables t ON c.object_id = t.object_id JOIN sys.schemas s ON t.schema_id = s.schema_id WHERE s.name = 'dbo' AND t.name = 'CUSTOMER' AND c.name = 'roles')
    BEGIN
        ALTER TABLE dbo.CUSTOMER ADD roles NVARCHAR(255) NOT NULL CONSTRAINT DF_DBO_CUSTOMER_ROLES DEFAULT 'ROLE_CUSTOMER,ROLE_RETAIL_USER';
        PRINT 'Added column roles to dbo.CUSTOMER';
    END
    ELSE
    BEGIN
        PRINT 'Column roles already exists in dbo.CUSTOMER';
    END

    UPDATE dbo.CUSTOMER
    SET roles = 'ROLE_CUSTOMER,ROLE_RETAIL_USER'
    WHERE roles IS NULL OR LTRIM(RTRIM(roles)) = '';

    IF NOT EXISTS (SELECT 1 FROM dbo.CUSTOMER WHERE username = 'admin')
    BEGIN
        INSERT INTO dbo.CUSTOMER (username, password_hash, first_name, last_name, email, contact_no, status, roles)
        VALUES (
            N'admin',
            N'$2a$10$5X2WM6Ws7z.jjQCWeeLTheqLAFNyVboU0nSrYvQ9MTBeBfmkARsqq',
            N'PayPink',
            N'Administrator',
            N'admin@paypink.internal',
            N'+630000000000',
            N'ACTIVE',
            N'ROLE_ADMIN,ROLE_CORE_ENGINEER'
        );
        PRINT 'Seeded admin user into dbo.CUSTOMER';
    END
    ELSE
    BEGIN
        UPDATE dbo.CUSTOMER
        SET roles = N'ROLE_ADMIN,ROLE_CORE_ENGINEER',
            password_hash = N'$2a$10$5X2WM6Ws7z.jjQCWeeLTheqLAFNyVboU0nSrYvQ9MTBeBfmkARsqq',
            status = N'ACTIVE'
        WHERE username = N'admin';
        PRINT 'Updated admin user in dbo.CUSTOMER with ROLE_ADMIN,ROLE_CORE_ENGINEER';
    END
END
ELSE
BEGIN
    PRINT 'CUSTOMER table not found in app or dbo schema.';
END

PRINT 'Phase 10 RBAC Roles Migration complete.';
