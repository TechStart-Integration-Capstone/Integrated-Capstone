-- ============================================================================
-- PayPink 2.0 — Real-Time Transaction Monitoring Dashboard Migration Script
-- Safe to re-run: every change is guarded by an existence check.
-- ============================================================================

SET QUOTED_IDENTIFIER ON;
GO

-- 1. Ensure transaction_type exists on REMITTANCE
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.REMITTANCE') AND name = 'transaction_type')
BEGIN
    ALTER TABLE dbo.REMITTANCE ADD transaction_type NVARCHAR(30) NOT NULL
        CONSTRAINT DF_REMITTANCE_TYPE DEFAULT 'TRANSFER';
END
GO

-- 2. Add internal_status to REMITTANCE (tracks 11-step granular transaction lifecycle)
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.REMITTANCE') AND name = 'internal_status')
BEGIN
    ALTER TABLE dbo.REMITTANCE ADD internal_status NVARCHAR(40) NULL;
END
GO

-- 3. Add current_service to REMITTANCE (tracks microservice executing the step)
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.REMITTANCE') AND name = 'current_service')
BEGIN
    ALTER TABLE dbo.REMITTANCE ADD current_service NVARCHAR(40) NULL;
END
GO

-- 4. Add transfer limits to CUSTOMER for Step 5: Limit Check
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.CUSTOMER') AND name = 'daily_transfer_limit')
BEGIN
    ALTER TABLE dbo.CUSTOMER ADD daily_transfer_limit DECIMAL(18,4) NOT NULL
        CONSTRAINT DF_CUSTOMER_DAILY_LIMIT DEFAULT 50000.0000;
END
GO

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.CUSTOMER') AND name = 'per_tx_limit')
BEGIN
    ALTER TABLE dbo.CUSTOMER ADD per_tx_limit DECIMAL(18,4) NOT NULL
        CONSTRAINT DF_CUSTOMER_PER_TX_LIMIT DEFAULT 25000.0000;
END
GO
