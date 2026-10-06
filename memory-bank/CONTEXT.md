# PayPink 2.0 — Project Context

_Owner: **dom**_
_Last updated: 2026-10-05 (Phase 6 complete — Immutable Risk Decision Log)_

---

## What it is

Mobile P2P remittance app (domestic, PHP) with real-time fraud screening and T24 core banking integration.
Built on top of the Capstone 1 ledger engine.

---

## Phase Status

| Phase | Description | Status |
|---|---|---|
| 0 | Capstone 1 Base Setup & Verification | Done |
| 1 | Oracle XE → Azure SQL Migration | Done |
| 2 | OpenTelemetry & Observability Mesh | Done |
| 3 | Risk Engine & Fraud Screening (rule-based + Isolation Forest ML) | Done |
| 4 | T24 Core Adapter & Simulator | Done |
| 5 | Remittance Orchestrator & Saga Engine Hardening | Done |
| 5b | Loans — apply / accept / disburse / repay / EOD | Implemented (unit-tested; not yet Docker end-to-end) |
| 6 | Immutable Audit & Risk Decision Log (RISK_DECISION table in PostgreSQL) | Done |
| 7 | Mobile Frontend (PWA) | Pending |
| 8 | Chaos + Load Testing | Pending |

> Phase 5b (Loans) was built alongside Phase 5 hardening and got labeled "Phase 6" in older entries. That was wrong. Phase 6 is the RISK_DECISION audit table in PostgreSQL. Loans = Phase 5b.

---

## How a Transfer Works (Remittance Saga)

1. App sends request to API Gateway — JWT check, rate limit, X-Correlation-ID injected, untrusted X-Auth-* headers stripped
2. Remittance Orchestrator checks for duplicates — Redis atomic SETNX idempotency (409 if in-progress)
3. Risk Engine scores FIRST — two-layer score (rules + Isolation Forest). Score > 0.85 = REJECT before any funds are touched
4. Hold funds SECOND — atomic held_balance UPDATE in Azure SQL + REMITTANCE row created in PENDING_CORE
5. Ownership validated — caller customer ID must match source account customer_id (403 on mismatch)
6. T24 Core Adapter posts THIRD — synchronous OFS call. Responses: POSTED / REJECTED / PROCESSING / timeout
7. Ledger commit — debit source, release hold, credit target, write LEDGER_TRANSACTION + OUTBOX_EVENT in one DB transaction
8. Saga Worker runs background forward recovery for T24_POSTED and PROCESSING timeouts

---

## Stack

- Backend: Spring Boot 3.2.3, Spring Cloud Gateway 2023.0.0, Python 3.11 FastAPI (Risk Engine)
- Primary DB: Azure SQL (SQL Server 2022) — local Docker or cloud-hosted
- Audit DB: PostgreSQL 15
- Cache: Redis 7 — idempotency keys, rate-limit buckets, display-only balance cache
- Messaging: Apache Kafka — topics: ledger.transaction.events, loan.*
- Observability: OTel Collector → Prometheus + Loki + Tempo → Grafana + Jaeger
- Frontend: Vanilla JS SPA served by Nginx (port 3001)

> The running auth-service (as of 2026-10-05) uses a hosted Azure SQL database named `paypink`. Checked-in Compose defaults still point to local SQL Server. Preserve runtime connection settings when restarting.

---

## Microservices

| Service | Port | DB |
|---|---|---|
| api-gateway | 8080 (only exposed host port) | Redis |
| auth-service | 8081 (internal) | Azure SQL |
| account-service | 8082 (internal) | Azure SQL + Redis |
| transaction-service | 8083 (internal) | Azure SQL + Redis + Kafka |
| notification-service | 8084 (internal) | PostgreSQL + Kafka |
| audit-service | 8085 (internal) | PostgreSQL + Kafka |
| reconciliation-service | 8086 (internal) | Azure SQL + PostgreSQL |
| outbox-publisher | 8087 (internal) | Azure SQL + Kafka |
| analytics-service | 8088 (internal) | Kafka (in-memory) |
| risk-engine | 8000 (internal) | None — stateless Python |
| t24-adapter | 8090 (internal) | None — simulator |
| loan-service | 8091 (internal) | Azure SQL (money via transaction-service only) |

---

## Risk Engine (Phase 3 — upgraded 2026-10-05 to v3.0.0)

Architecture: two independent layers, combined with `max(rule_score, ml_score * 0.90)`. Reject threshold: > 0.85.

### Layer 1 — Rule-based scorer (scorer.py)

All rules now fire. Enrichment fields are queried by RiskEngineClient.java before every /score call.

| Rule | Added score | Data source |
|---|---|---|
| self_transfer (same account ID) | +0.90 | targetAccountId in request |
| amount > 100,000 PHP | +0.50 | request |
| amount > 50,000 PHP | +0.30 | request |
| amount > 20,000 PHP | +0.15 | request |
| new account under 24h | +0.25 | accountAgeHours — ACCOUNT.created_date |
| high velocity > 5 txns/hr | +0.30 | recentTxCount — REMITTANCE last 1h |
| medium velocity > 2 txns/hr | +0.15 | recentTxCount — REMITTANCE last 1h |
| amount >= 10x customer 30d avg | +0.40 | amountVsAvgRatio — REMITTANCE last 30d |
| amount >= 5x customer 30d avg | +0.25 | amountVsAvgRatio — REMITTANCE last 30d |
| amount >= 3x customer 30d avg | +0.10 | amountVsAvgRatio — REMITTANCE last 30d |
| non-PHP currency | +0.20 | request |

### Layer 2 — Isolation Forest ML scorer (ml_scorer.py)

Unsupervised anomaly detection — no labeled fraud data required.
- 10,000 synthetic training samples: 90% normal, 10% anomalous (4 fraud archetypes)
- 6 features: amount_php, hour_of_day, account_age_hours, recent_tx_count, amount_vs_avg_ratio, is_new_recipient
- Model serialized to /tmp/paypink_if_model.joblib — retrained at startup if file is missing
- ML_WEIGHT = 0.90 so an anomaly alone cannot reject a transfer without at least one rule also firing

### RiskEngineClient.java — three DB queries before every /score call

- queryAccountAgeHours: SELECT created_date FROM dbo.ACCOUNT WHERE account_id = ?
- queryRecentTxCount: SELECT COUNT(*) FROM dbo.REMITTANCE WHERE source_account_id = ? AND created_at >= DATEADD(HOUR,-1,GETUTCDATE()) AND status NOT IN ('FAILED','CANCELLED')
- queryAmountVsAvgRatio: SELECT AVG(CAST(amount AS FLOAT)) FROM dbo.REMITTANCE WHERE source_account_id = ? AND created_at >= DATEADD(DAY,-30,GETUTCDATE()) AND status IN ('POSTED','Reserved','Authorized','Processing')

### Response contract

Java consumer reads RiskResult{score, decision, reasons} — unchanged. New HTTP-only fields ruleScore and mlScore are for observability only (Grafana/logs); the Java client ignores them.

### Frontend visibility

bank.js renders riskScore + riskDecision on the transfer receipt: "Risk evaluation: Score 0.40 (APPROVED)". No frontend changes were needed.

---

## Azure SQL Schema (current)

Tables: CUSTOMER, ACCOUNT, AUDIT_LOG, BANKING_FAVORITE, LEDGER_TRANSACTION, OUTBOX_EVENT, REMITTANCE, LOAN_APPLICATION, LOAN, LOAN_SCHEDULE, LOAN_REPAYMENT

Migration run order on an existing database:
1. schema-azuresql.sql
2. scripts/migrate_phase5_hardening.sql
3. scripts/migrate_phase6_loans.sql
4. scripts/seed_demo_azure_sql.sql

PostgreSQL migration run order (ledger_audit_db):
1. microservices/audit-service/src/main/resources/schema-postgres.sql (full schema on new DB)
2. scripts/migrate_phase6_risk_decision.sql (adds RISK_DECISION on existing DB — idempotent)

Key SQL Server constraints to remember:
- TRANSACTION is a reserved word — table is LEDGER_TRANSACTION everywhere
- DECIMAL(18,4) for all balances
- WITH (UPDLOCK, ROWLOCK) for pessimistic locking (dynamic H2 fallback for tests)
- OFFSET 0 ROWS FETCH NEXT n ROWS ONLY for pagination
- SUBSTRING(), + for concat, TOP 1 in subqueries, no FROM DUAL
- mssql-jdbc:12.8.1.jre11 — no jre17 classifier on Maven Central
- SQL Server Docker does NOT auto-run /docker-entrypoint-initdb.d — run schema via sqlcmd manually

---

## Phase 5b — Loans

Endpoints via gateway:
- POST /api/v1/loans/applications
- POST /api/v1/loans/applications/{ref}/accept
- GET /api/v1/loans
- GET /api/v1/loans/{id}/schedule
- POST /api/v1/loans/{id}/repayments
- POST /api/v1/loans/eod/run?businessDate= (admin only, ROLE_ADMIN)

Money moves only through transaction-service POST /internal/remittance/transfer (header X-Internal-Service: loan-service, not gateway-routed). Disbursements skip the risk engine; repayments run through it normally.

Bank loan pool: PH1000000LOAN (INTERNAL account type, owner paypink_bank, 50,000,000 PHP). auth-service account renumbering skips INTERNAL accounts.

Demo credentials (password: password123):

| Username | credit_score | monthly_income | Band | Max amount / rate / term |
|---|---|---|---|---|
| lviernes | 520 | 20,000 | LOW | 30,000 / 28% / 12 mo |
| arosales | 670 | 45,000 | NORMAL | 250,000 / 18% / 36 mo |
| glim | 800 | 150,000 | HIGH | 1,000,000 / 10.5% / 60 mo |

> glim only has a TIME_DEPOSIT account in the seed — loans still disburse into it.

---

## Admin Transaction Monitor

- Endpoint: GET /api/v1/auth/admin/transactions/today (ROLE_ADMIN required)
- Data source: dbo.LEDGER_TRANSACTION joined with dbo.ACCOUNT and dbo.OUTBOX_EVENT
- Filtering: today only in Asia/Manila (PHT, UTC+8), newest first, STRESS_TEST_ACCOUNT rows excluded
- Operation column reads $.operation from OUTBOX_EVENT JSON (LEDGER_TRANSACTION has no operation column)
- Refresh: every 5 seconds while signed in; 15s timeout; no demo fallback on error
- Validation: mvn -f microservices/auth-service/pom.xml package; node --test frontend/tests/transaction-monitor.test.cjs

---

## Git

- Freeze tag: capstone1-freeze → commit 1e51aea
- Working branch (2026-10-05): aly-feature
- Latest commit at last update: b6ee3e4 — Merge branch 'main' of https://github.com/TechStart-Integration-Capstone/Integrated-Capstone

---

## Phase 6 — Risk Decision Log (Done)

Every risk engine scoring call (APPROVE, REJECT, UNAVAILABLE) is now persisted as an append-only row in PostgreSQL.

### Data flow
```
transaction-service RemittanceOrchestratorService
  → evaluateRisk() returns RiskResult{score, decision, reasons, ruleScore, mlScore, latencyMs}
  → RiskDecisionPublisher.publish(referenceNo, risk)   ← fire-and-forget, never blocks saga
  → Kafka topic: risk.decisions
  → audit-service RiskDecisionConsumer
  → RISK_DECISION table in PostgreSQL (ledger_audit_db)
```

### REST endpoints (via gateway)
- `GET /api/v1/audit/risk-decisions` — paginated list, filter by `decision` or `from`/`to`
- `GET /api/v1/audit/risk-decisions/{referenceNo}` — single decision by reference
- `GET /api/v1/audit/risk-decisions/stats` — last24h + last7d approval/rejection counts

### Key design decisions
- REJECT and UNAVAILABLE outcomes are captured — not just successful transfers
- `reasons` stored as JSONB TEXT with GIN index — supports queries like "all transfers rejected due to amount_above_100k"
- `ruleScore` and `mlScore` recorded separately from combined `score` — shows which layer triggered a rejection
- Idempotent consumer — duplicate reference_no events are silently skipped

### Rebuild after Phase 6
```powershell
mvn -f microservices/transaction-service/pom.xml clean package -DskipTests
mvn -f microservices/audit-service/pom.xml clean package -DskipTests
mvn -f microservices/api-gateway/pom.xml clean package -DskipTests
cd docker
docker compose up -d --build transaction-service audit-service api-gateway
# Then run migration on PostgreSQL:
docker exec -i postgres-immutable-audit psql -U audit_user -d ledger_audit_db -f /dev/stdin < scripts/migrate_phase6_risk_decision.sql
```

---

## Current Focus

All planned phases complete through Phase 6. Remaining work:
- **Phase 7** — Mobile Frontend (PWA)
- **Phase 8** — Chaos + Load Testing
