# PayPink 2.0 — Project Context

_Last updated: 2026-10-04 (Phase 5 Hardening & Must-Dos Complete)_

## What it is
Mobile P2P remittance app (domestic, PHP) with real-time fraud screening and T24 core banking integration.
Built on top of the Capstone 1 ledger engine.

## Status / Phases
- [x] Phase 0: Capstone 1 Base Setup & Verification
- [x] Phase 1: Oracle to Azure SQL DB Migration
- [x] Phase 2: OpenTelemetry & Observability Mesh
- [x] Phase 3: Risk Engine & Fraud Screening Service
- [x] Phase 4: T24 Core Adapter & Simulator
- [x] Phase 5: Remittance Orchestrator & Saga Engine Hardening (All Must-Dos & Security Fixes Complete)

## How a transfer works
1. App → API Gateway (JWT check, rate limit, X-Correlation-ID, unconditional header stripping of incoming untrusted X-Auth-* headers)
2. Remittance Orchestrator checks for duplicates (Redis atomic SETNX idempotency check, 409 Conflict if in-progress)
3. Risk Engine scores it FIRST (Python FastAPI, ≤ 200 ms SLA). Score > 0.85 → rejected before holding funds
4. Hold funds SECOND via atomic `held_balance` UPDATE in Azure SQL & REMITTANCE row in PENDING_CORE state
5. Remittance Ledger Service validates account ownership (caller customer ID match against source account)
6. T24 Core Adapter posts to T24 OFS THIRD (synchronous). Response codes: 200/503/202/422
7. Remittance Ledger Service performs short DB transaction: Debit balance, release held balance, credit target, write LEDGER_TRANSACTION + OUTBOX row
8. Remittance Saga Worker periodically executes forward recovery for T24_POSTED sagas and resolves PROCESSING sagas

## Stack
- **Backend:** Spring Boot 3.2.3, Spring Cloud Gateway 2023.0.0, Python FastAPI (Risk Engine)
- **Database (OLTP):** Azure SQL = SQL Server 2022 local Docker (`mcr.microsoft.com/mssql/server:2022-latest`)
- **Database (Audit):** PostgreSQL 15
- **Cache:** Redis 7 — idempotency keys + rate limit buckets + balance cache (display only)
- **Messaging:** Apache Kafka, topic: `ledger.transaction.events`
- **Observability:** OTel Collector → Prometheus + Loki + Tempo → Grafana + Jaeger
- **Frontend:** Vanilla JS SPA served by Nginx (port 3001)

## Microservices
| Service | Internal Port | DB |
|---|---|---|
| api-gateway | 8080 (only exposed host port) | Redis |
| auth-service | 8081 (internal only) | Azure SQL |
| account-service | 8082 (internal only) | Azure SQL + Redis |
| transaction-service | 8083 (internal only) | Azure SQL + Redis + Kafka |
| notification-service | 8084 (internal only) | PostgreSQL + Kafka |
| audit-service | 8085 (internal only) | PostgreSQL + Kafka |
| reconciliation-service | 8086 (internal only) | Azure SQL + PostgreSQL |
| outbox-publisher | 8087 (internal only) | Azure SQL + Kafka |
| analytics-service | 8088 (internal only) | Kafka (in-memory) |
| risk-engine | 8000 (internal only) | None — stateless |
| t24-adapter | 8090 (internal only) | None — simulator |

## Risk Engine (Phase 3)
- Route: `POST /api/v1/risk/score` via gateway (StripPrefix=3 → risk-engine:8000/score)
- Health: `GET /api/v1/risk/health` (public, no JWT)
- Threshold: score > 0.85 → REJECT
- Rules: self-transfer +0.90, >100k +0.50, >50k +0.30, >20k +0.15, new account +0.25, high velocity +0.30, non-PHP +0.20
- OTel: manual tracing with W3C traceparent propagation (no auto-instrumentation — pkg_resources missing in python:3.12-slim)

## Azure SQL Schema (as of Phase 5)
Tables: `CUSTOMER`, `ACCOUNT`, `AUDIT_LOG`, `BANKING_FAVORITE`, `LEDGER_TRANSACTION`, `OUTBOX_EVENT`, `REMITTANCE`

> TRANSACTION is a reserved word in T-SQL — table is named LEDGER_TRANSACTION everywhere.

Key SQL Server rules:
- `DECIMAL(18,4)` for balances
- `WITH (UPDLOCK, ROWLOCK)` for pessimistic locking (with dynamic H2 dialect fallback)
- `OFFSET 0 ROWS FETCH NEXT n ROWS ONLY` for pagination
- `SUBSTRING()`, `+` concat, `TOP 1` in subqueries, no `FROM DUAL`
- mssql-jdbc:12.8.1.jre11 (no jre17 on Maven Central)
- SQL Server Docker does NOT auto-run /docker-entrypoint-initdb.d — run schema via sqlcmd manually

## Git
- **Freeze tag:** `capstone1-freeze` → commit `1e51aea`
- **Working branch:** `feature/capstone2-paypink-2.0-dom`
- **Latest commit:** `7688fda` — docs(memory-bank): update CHANGELOG.md for Phase 5 completion and type safety fixes

## Completed Phases
- **Phase 0** (Completed) — Git freeze tag, baseline doc, all containers green
- **Phase 1** (Completed) — Oracle XE → Azure SQL. All 5 services migrated, all 9 UP, precision + login tests passed
- **Phase 2** (Completed) — X-Correlation-ID filter, Resilience4j circuit breaker on account-service, FallbackController, port isolation (only 8080+3001 exposed)
- **Phase 3** (Completed) — Risk Engine Python FastAPI. scorer.py rules, /score + /health, manual OTel, routed via gateway
- **Phase 4** (Completed) — T24 Core Adapter + Simulator (Spring Boot microservice in `microservices/t24-adapter/`). OfsFormatterService, T24SimulatorController sidecar (90% /1 success, 8% /-1 reject, 2% timeout), T24IdempotencyStore, routed via gateway
- **Phase 5** (Completed) — Remittance Orchestrator 4-step saga engine (Hold → Risk → T24 → Commit/Release), live REMITTANCE table, Resilience4j circuit breakers, Kafka topic `remittance.events`, Postman Folder 10, post-T24 commit safety, customer-scoped idempotency, resolved account comparisons

## Current focus
**Phase 6** — Immutable Audit & Risk Decision Log (`RISK_DECISION` table in PostgreSQL + `audit-service`)

## Remaining Phases
| Phase | Description | Risk |
|---|---|---|
| 6 | RISK_DECISION table in PostgreSQL | LOW |
| 7 | Mobile Frontend (PWA) | LOW |
| 8 | Chaos + Load Testing | MEDIUM |
