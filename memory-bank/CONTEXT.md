# PayPink 2.0 — Project Context

_Last updated: 2026-10-03_

## What it is
Mobile P2P remittance app (domestic, PHP) with real-time fraud screening.
Built on top of the Capstone 1 ledger engine.

## How a transfer works
1. App → API Gateway (JWT check, rate limit, X-Correlation-ID)
2. Remittance Orchestrator checks for duplicates (Redis idempotency)
3. Risk Engine scores it (Python FastAPI, ≤ 200 ms SLA). Score > 0.85 → rejected
4. T24 Core Adapter posts to T24 OFS first (synchronous). Response codes: 200/503/202/422
5. Account Service validates account + ownership (Resilience4j circuit breaker → Redis cache fallback)
6. Ledger balance mutation + OUTBOX row saved in one Azure SQL transaction (UPDLOCK, ROWLOCK)
7. Outbox Publisher polls PENDING rows → Kafka → Audit, Notification, Reconciliation, Analytics

## Stack
- **Backend:** Spring Boot 3.2.3, Spring Cloud Gateway 2023.0.0, Python FastAPI (Risk Engine)
- **Database (OLTP):** Azure SQL = SQL Server 2022 local Docker (`mcr.microsoft.com/mssql/server:2022-latest`, port 1433)
- **Database (Audit):** PostgreSQL 15 (port 5434)
- **Cache:** Redis 7 (port 6380) — idempotency keys + rate limit buckets + balance cache (display only)
- **Messaging:** Apache Kafka (port 9092), topic: `ledger.transaction.events`
- **Observability:** OTel Collector → Prometheus + Loki + Tempo → Grafana + Jaeger
- **Frontend:** Vanilla JS SPA served by Nginx (port 3001)

## Microservices (ports)
| Service | Port | DB |
|---|---|---|
| api-gateway | 8080 | Redis |
| auth-service | 8081 | Azure SQL |
| account-service | 8082 | Azure SQL |
| transaction-service | 8083 | Azure SQL + Redis + Kafka |
| notification-service | 8084 | PostgreSQL + Kafka |
| audit-service | 8085 | PostgreSQL + Kafka |
| reconciliation-service | 8086 | Azure SQL + PostgreSQL |
| outbox-publisher | 8087 | Azure SQL + Kafka |
| analytics-service | 8088 | Kafka (in-memory) |

## Azure SQL Schema (as of Phase 1)
Tables: `CUSTOMER`, `ACCOUNT`, `AUDIT_LOG`, `BANKING_FAVORITE`, `LEDGER_TRANSACTION`, `OUTBOX_EVENT`

> ⚠️ `TRANSACTION` is a reserved word in T-SQL — table is named `LEDGER_TRANSACTION` everywhere.

Key decisions baked in:
- `DECIMAL(18,4)` for balances (matches Oracle `NUMBER(18,4)` exactly — precision validated)
- `WITH (UPDLOCK, ROWLOCK)` for pessimistic locking (replaces Oracle `SELECT … FOR UPDATE`)
- `OFFSET 0 ROWS FETCH NEXT n ROWS ONLY` for pagination (replaces Oracle `FETCH FIRST n ROWS ONLY`)
- `SUBSTRING()` not `SUBSTR()`, `+` not `||` for string concat, `TOP 1` not `FETCH FIRST 1 ROW ONLY` in subqueries
- No `FROM DUAL` — removed entirely

## Git
- **Freeze tag:** `capstone1-freeze` → commit `1e51aea` (Capstone 1 fully working baseline)
- **Working branch:** `feature/capstone2-paypink-2.0-dom`
- **Phase 0 commit:** `69f147f` — baseline doc
- **Phase 1 commit:** `cc992f5` — Oracle XE → Azure SQL complete

## Completed Phases
- **Phase 0** ✅ — Git freeze tag, baseline document (`docs/PHASE0_BASELINE.md`), all 22 containers confirmed green
- **Phase 1** ✅ — Oracle XE → Azure SQL migration. All 5 services migrated, all 9 health endpoints UP, precision test passed, login smoke test passed
- **Phase 2** ✅ — Network hardening + Resilience4j. X-Correlation-ID filter (api-gateway), circuit breaker on account-service DB reads with Redis fallback (display-only), CircuitBreaker filter on transaction-service route, global 30s httpclient timeout

## Current focus
**Phase 3** — Risk Engine (Python FastAPI)
- New Python FastAPI microservice in `microservices/risk-engine/`
- Rule-based scorer returning 0.00–1.00 score
- Score > 0.85 → transfer rejected
- OTel trace context propagation (W3C traceparent header)
- Health endpoint at /health for Docker health check
- Add to docker-compose.yml on ledger-net

## Remaining Phases
| Phase | Description | Risk |
|---|---|---|
| 2 | Network + Resilience4j | 🟡 MEDIUM |
| 3 | Risk Engine (Python FastAPI) | 🟢 LOW |
| 4 | T24 Core Adapter + Simulator | 🟡 MEDIUM |
| 5 | Remittance Orchestrator (Hold→Risk→T24→Commit/Release) | 🔴 HIGH |
| 6 | RISK_DECISION table in PostgreSQL | 🟢 LOW |
| 7 | Mobile Frontend (PWA) | 🟢 LOW |
| 8 | Chaos + Load Testing | 🟡 MEDIUM |
