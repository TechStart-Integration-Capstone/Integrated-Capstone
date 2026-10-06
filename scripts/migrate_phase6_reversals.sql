-- ============================================================================
-- PayPink 2.0 — Azure SQL Phase 6 Reversals & Bounded Retries Migration Script
-- Supports 30s Client-Side Cancellation Window & Bounded Bank-Side Auto-Reversals
-- ============================================================================

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.REMITTANCE') AND name = 'cancel_until')
BEGIN
    ALTER TABLE dbo.REMITTANCE ADD cancel_until DATETIME2 NULL;
END
GO

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.REMITTANCE') AND name = 'retry_count')
BEGIN
    ALTER TABLE dbo.REMITTANCE ADD retry_count INT NOT NULL CONSTRAINT DF_REMITTANCE_RETRY_COUNT DEFAULT 0;
END
GO

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.REMITTANCE') AND name = 'max_retries')
BEGIN
    ALTER TABLE dbo.REMITTANCE ADD max_retries INT NOT NULL CONSTRAINT DF_REMITTANCE_MAX_RETRIES DEFAULT 3;
END
GO

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.REMITTANCE') AND name = 'next_retry_at')
BEGIN
    ALTER TABLE dbo.REMITTANCE ADD next_retry_at DATETIME2 NULL;
END
GO

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_remittance_status_retry')
BEGIN
    CREATE INDEX idx_remittance_status_retry ON dbo.REMITTANCE(status, next_retry_at, cancel_until);
END
GO
