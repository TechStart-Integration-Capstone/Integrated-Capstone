# AGENTS.md

Read `CONTEXT.md` before writing code. After any change, add a line to `CHANGELOG.md`.

## Never break these
- No risk score → reject (503). Money never moves without a score.
- Cached balances are display-only. Never use them to approve a transfer.
- Ledger change + OUTBOX row are saved in one database transaction.
- Every transfer needs an `Idempotency-Key`.
- Only the API Gateway exposes a port (8080).
- No secrets in code.

## SQL Server rules (learned in Phase 1)
- Table name is `LEDGER_TRANSACTION` — `TRANSACTION` is a reserved word in T-SQL.
- Pessimistic locking: `WITH (UPDLOCK, ROWLOCK)` in the FROM clause, not `FOR UPDATE` at the end.
- Pagination: `ORDER BY x OFFSET 0 ROWS FETCH NEXT n ROWS ONLY` — not `FETCH FIRST n ROWS ONLY`.
- String concat: `+` operator — not `||`.
- Substring: `SUBSTRING(col, start, len)` — not `SUBSTR()`.
- No `FROM DUAL` — remove it entirely; scalar subqueries work without it.
- Subquery with ORDER BY needs `TOP n`: `SELECT TOP 1 ... FROM ... ORDER BY ...` — not `FETCH FIRST 1 ROW ONLY`.
- JDBC driver: `mssql-jdbc:12.8.1.jre11` — there is no `jre17` classifier on Maven Central.
- JPA dialect: `org.hibernate.dialect.SQLServerDialect`.
- JDBC URL format: `jdbc:sqlserver://host:1433;databaseName=master;encrypt=true;trustServerCertificate=true`
- SQL Server Docker does NOT auto-run scripts from `/docker-entrypoint-initdb.d/` — run schema manually via sqlcmd after container is healthy.
