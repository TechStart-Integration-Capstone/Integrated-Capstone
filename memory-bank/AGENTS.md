# AGENTS.md

Read `CONTEXT.md` before writing code. After any change, add a line to `CHANGELOG.md`.
Project owner: **dom**

## Never break these
- No risk score → reject (503). Money never moves without a score.
- Cached balances are display-only. Never use them to approve a transfer.
- Ledger change + OUTBOX row are saved in one database transaction.
- Every transfer needs an `Idempotency-Key`.
- Only the API Gateway exposes a port (8080).
- No secrets in code.

## Risk Engine rules (as of 2026-10-05 upgrade — two-layer)
- Architecture: `max(rule_score, ml_score * 0.90)` — both layers run independently; combined score > 0.85 = REJECT.
- `RiskEngineClient.java` queries DB for `accountAgeHours`, `recentTxCount`, `amountVsAvgRatio` before every call — do not remove these enrichment queries or the velocity/new-account/ratio rules become dead code again.
- `ml_scorer.py` trains on synthetic data at startup if no model file exists — `scikit-learn`, `numpy`, `joblib` must remain in `requirements.txt`.
- `ml_scorer.py` must be in the `COPY` list in the risk-engine `Dockerfile` or the container crashes on import.
- `RiskResult{score, decision, reasons}` is the Java-side contract — do not rename these fields; `ruleScore`/`mlScore` are extra HTTP-only fields for observability.

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
