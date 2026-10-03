# Changelog
Newest first. One line per change: date, what changed, who.

- 2026-10-03 — fix(phase1): outbox-publisher OutboxEventRepository FETCH FIRST -> OFFSET 0 ROWS FETCH NEXT (was crashing every 5s, breaking Kafka pipeline). auth-service BankingFavoritesSchema user_tables -> INFORMATION_SCHEMA.TABLES + T-SQL DDL. Committed 0481518 — [dom/kiro]
- 2026-10-03 — Phase 1 complete: Oracle XE → Azure SQL (SQL Server 2022 local Docker). schema-azuresql.sql written (T-SQL DDL). LEDGER_TRANSACTION rename (reserved word fix). mssql-jdbc:12.8.1.jre11 in all 5 pom.xml. All Oracle SQL syntax rewritten in auth-service (FOR UPDATE, FETCH FIRST, FROM DUAL, SUBSTR, ||, JSON_VALUE subquery). @Table annotations updated in transaction-service and reconciliation-service. All 9 actuator/health UP. Precision test passed. Login smoke test passed. Committed cc992f5 — [dom]
- 2026-10-03 — Phase 0 complete: git tag capstone1-freeze (commit 1e51aea), branch feature/capstone2-paypink-2.0-dom created and pushed. docs/PHASE0_BASELINE.md written with full Phase 1 change list. Committed 69f147f — [dom]
- 2026-10-02 — Added AGENTS.md, CONTEXT.md, CHANGELOG.md — [name]
