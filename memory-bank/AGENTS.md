# AGENTS.md

Read `CONTEXT.md` before writing code. After any change, add an entry to `CHANGELOG.md` and update `CONTEXT.md`.
Project owner: **dom**

## Memory Bank Protocol
- **MANDATORY:** Always update the memory bank (`memory-bank/CHANGELOG.md` and `memory-bank/CONTEXT.md`) after completing any change — whether code, database schema, CI/CD pipeline, test refactoring, or infrastructure deployment.
- Keep `CHANGELOG.md` structured (newest on top, referencing specific microservice paths and committers).
- Keep `CONTEXT.md` aligned with current architectural state, port mappings, active Git branches, and cloud status.

## Never break these
- No risk score → reject (503). Money never moves without a score.
- Cached balances are display-only. Never use them to approve a transfer.
- Ledger change + OUTBOX row are saved in one database transaction.
- Every transfer needs an `Idempotency-Key`.
- Only the API Gateway exposes a port (8080) for external API traffic.
- No secrets in code or repository commits.

## Java 17 Temurin & Testing Rules
- **No Reflection on Final Fields:** Never use reflection (`Field.setAccessible(true)`) to mutate `private final` fields (such as `ObjectMapper`, repository mocks, or Kafka templates). Java 17 Temurin runners throw `IllegalAccessException`. Always use constructor injection or package-private test setters.
- **Kafka Topic Parity:** Matchers in test stubs must match actual production topic names (e.g. `remittance.events` instead of obsolete `ledger.transaction.events`).
- **Entity Model Parity:** Ensure JPA entity models across services maintain all required getters and constructors to avoid compilation breakage when upstream branches merge.

## Core Context & Localization (Philippines)
- **Currency & Formatting:** Always use Philippine Peso (PHP / ₱) with standard comma separation (e.g., ₱1,500.00). Dates formatted as `DD/MM/YYYY` or `MMMM DD, YYYY`. Timestamps in Asia/Manila (UTC+8).
- **Entities & Rails:** Real Filipino names (Juan Dela Cruz, Maria Santos), local mobile numbers (`+63 9XX XXX XXXX` or `09XX-XXX-XXXX`), and Philippine digital rails (InstaPay, PESONet, QR Ph, Maya, GCash).
- **Design & UI Theme (Pink Palette):** Professional FinTech theme — Primary Rose (`#E11D48`), Deep Blush (`#DB2777`), Soft Pastel Pink (`#FDF2F8`, `#FCE7F3`), Crisp Slate (`#0F172A`, `#334155`).

## Cloud & Cost Controls (Azure)
- **Host:** Azure VM `vm-paypink` (`20.69.157.88`) in `RG-PAYPINK-WESTUS2` (FQDN: `paypink-levi-westus2.westus2.cloudapp.azure.com`).
- **Budget:** Zero-cost / low-cost ($10 credit cap). Auto-shutdown configured for 11:00 UTC (7:00 PM PHT). Deallocate when idle.

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
