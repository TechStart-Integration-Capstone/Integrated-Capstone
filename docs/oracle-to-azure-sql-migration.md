# Oracle to Azure SQL Database migration

The application database client and schema now target Azure SQL Database. PostgreSQL remains the separate audit/reconciliation store. No Azure resources are provisioned here, and no live database copy has been run because source and target access were not provided.

## Connection configuration

Set `AZURE_SQL_JDBC_URL` in the hosting environment; do not commit a real connection string. For an Azure-hosted managed identity, use a URL with the following form:

```text
jdbc:sqlserver://<server>.database.windows.net:1433;databaseName=<database>;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30;authentication=ActiveDirectoryMSI
```

For a user-assigned managed identity, set `AZURE_MANAGED_IDENTITY_CLIENT_ID` and include the JDBC driver's `msiClientId=<client-id>` property in the URL. Assign the identity a database user and only the database permissions required by the services. A database password is not required when using managed identity.

## Target schema and mapping

Run [schema-azure-sql.sql](../microservices/transaction-service/src/main/resources/schema-azuresql.sql) against the target before migrating rows. The script is additive: it creates missing tables and indexes, does not drop objects, and does not seed demo records.

| Oracle source | Azure SQL target | Conversion |
|---|---|---|
| `CUSTOMER`, `ACCOUNT`, `AUDIT_LOG`, `OUTBOX_EVENT` | Same table names | Oracle `NUMBER` identifiers to `BIGINT IDENTITY`; `NUMBER(18,4)` to `DECIMAL(18,4)`; `VARCHAR2` to `NVARCHAR`; `CLOB` to `NVARCHAR(MAX)` |
| `TRANSACTION` | `LEDGER_TRANSACTION` | Rename the table because `TRANSACTION` is a SQL Server reserved word |
| `AUDIT_LOG.timestamp` | `AUDIT_LOG.audit_timestamp` | Avoid SQL Server's `timestamp`/`rowversion` type name |
| `OUTBOX_EVENT` operational columns | `retry_count`, `next_attempt_at`, `last_error` included | Supports the legacy ledger-core retry mapping |
| `TRANSACTION.operation` | `LEDGER_TRANSACTION.operation` | Nullable to allow rows written by services that do not populate the legacy operation field |

The PostgreSQL audit schema and its stored records are not migrated or modified as part of this database switch. Its existing `RECONCILIATION_LOG.oracle_status` column is retained for compatibility with PostgreSQL data already stored there; the Java model now treats that value as the ledger status.

## Existing data transfer and cutover

1. Provision the Azure SQL target separately and validate connectivity, firewall rules, TLS, and the managed identity's database permissions.
2. Take and retain a consistent Oracle backup. Inventory the source tables, row counts, constraints, triggers, sequences/identity behavior, and dependent consumers before migration.
3. Use an approved Oracle-to-SQL Server/Azure SQL migration method (for example, SQL Server Migration Assistant for Oracle where supported) to convert the Oracle schema and copy data. Review generated conversion output rather than running destructive source-side scripts.
4. Map source `TRANSACTION` rows to `LEDGER_TRANSACTION`; map `AUDIT_LOG.timestamp` to `audit_timestamp`. Preserve primary keys and load referenced parent rows before dependent rows (customers, accounts, transactions, outbox and audit rows).
5. For explicit identity values, use a migration tool that preserves identity values, then reseed each target identity to the maximum imported key before application writes resume. Validate unique keys, foreign keys, decimal precision, timestamp ranges, and large outbox payloads.
6. Compare source and target row counts and financial control totals (including balances and transaction amounts) while writes are stopped or captured consistently. Test login, account reads, transfers, outbox publishing, and reconciliation against the target.
7. Switch the application configuration to the Azure SQL URL, monitor errors and reconciliation, and keep the Oracle backup available for the agreed rollback window.

The live transfer requires authorized Oracle read access, Azure SQL schema/data write access, network connectivity from the migration host to both systems, and an agreed cutover window. Supply secrets only through your organization's approved secret store or environment configuration; do not paste passwords into chat or commit them to the repository.
