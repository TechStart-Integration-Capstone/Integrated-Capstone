-- ============================================================================
-- PayPink 2.0 — Azure SQL Phase 5 Hardening Migration Script
-- ============================================================================

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.ACCOUNT') AND name = 'held_balance')
BEGIN
    ALTER TABLE dbo.ACCOUNT ADD held_balance DECIMAL(18,4) NOT NULL DEFAULT 0.0000;
    ALTER TABLE dbo.ACCOUNT ADD CONSTRAINT chk_account_held_positive CHECK (held_balance >= 0.0000);
END
GO

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.REMITTANCE') AND name = 'idempotency_key')
BEGIN
    ALTER TABLE dbo.REMITTANCE ADD idempotency_key NVARCHAR(80) NULL;
    CREATE UNIQUE INDEX uq_remittance_idemp_key ON dbo.REMITTANCE(idempotency_key) WHERE idempotency_key IS NOT NULL;
END
GO
