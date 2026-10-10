-- External transfers settle into a dedicated, zero-funded simulated clearing account.
-- Run after the schema split and phase6_loans migration. Safe to rerun; never reset its balance.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF NOT EXISTS (SELECT 1 FROM app.CUSTOMER WHERE username=N'paypink_bank')
    THROW 50001, 'Apply phase6_loans before external clearing migration.', 1;
IF NOT EXISTS (SELECT 1 FROM t24.ACCOUNT WITH (UPDLOCK,HOLDLOCK) WHERE account_number=N'PH1000000EXT')
BEGIN
    INSERT INTO t24.ACCOUNT (customer_id,account_number,account_type,currency,current_balance,held_balance,status)
    SELECT customer_id,N'PH1000000EXT',N'INTERNAL',N'PHP',0,0,N'ACTIVE'
    FROM app.CUSTOMER WHERE username=N'paypink_bank';
END;
IF NOT EXISTS (SELECT 1 FROM t24.ACCOUNT a JOIN app.CUSTOMER c ON c.customer_id=a.customer_id
    WHERE a.account_number=N'PH1000000EXT' AND a.account_type=N'INTERNAL' AND a.currency=N'PHP'
      AND a.status=N'ACTIVE' AND c.username=N'paypink_bank')
    THROW 50002, 'External clearing account exists with incompatible settings. Review before proceeding.', 1;
COMMIT;
GO
