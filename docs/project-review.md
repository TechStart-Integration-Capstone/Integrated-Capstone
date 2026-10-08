# PayPink 2.0 — Project Review & Analysis

_Reviewed: 2026-10-06 | Reviewer: Kiro_

---

## 1. What This Project Is

PayPink 2.0 is a production-grade domestic P2P remittance platform targeting the Philippine retail banking market. It is built on a microservices architecture and covers the full stack: backend services, a Vanilla JS web SPA, a Flutter mobile PWA, observability, CI/CD, and cloud deployment on Azure.

It is Capstone 2, built on top of a Capstone 1 ledger engine, and has evolved through six deliberate phases — DB migration, observability, risk engine, T24 core integration, remittance saga, loans, and an immutable audit log.

---

## 2. Architecture Overview

### 2.1 Microservices (13 total)

| Service | Port | Role |
|---|---|---|
| api-gateway | 8080 (only external port) | JWT auth, rate limiting, circuit breaking, routing |
| auth-service | 8081 | User registration, login, JWT issuance, admin transaction monitor |
| account-service | 8082 | Account CRUD, balance display (Redis cache), favourites |
| transaction-service | 8083 | Remittance saga orchestrator, ledger writes, interest EOD |
| notification-service | 8084 | Kafka consumer, async notification delivery |
| audit-service | 8085 | Immutable PostgreSQL audit trail, risk-decision log |
| reconciliation-service | 8086 | Cross-DB integrity checks (Azure SQL vs PostgreSQL) |
| outbox-publisher | 8087 | Polls OUTBOX_EVENT table, publishes to Kafka |
| analytics-service | 8088 | In-memory Kafka consumer for live analytics |
| risk-engine | 8000 | Python FastAPI — two-layer fraud scoring |
| t24-adapter | 8090 | T24 core banking simulator / adapter |
| loan-service | 8091 | Loan apply, accept, disburse, repay, EOD |
| (legacy) backend/* | — | Older Capstone 1 services (superseded) |

### 2.2 Data Stores

- **Azure SQL (SQL Server 2022)** — primary transactional DB: customers, accounts, ledger, remittances, loans, outbox
- **PostgreSQL 15** — immutable audit: LEDGER_MUTATION_AUDIT, RECONCILIATION_LOG, RISK_DECISION, interest_accrual
- **Redis 7** — idempotency keys, rate-limit token buckets, display-only balance cache
- **Apache Kafka** — async event streaming: `remittance.events`, `ledger.transaction.events`, `risk.decisions`, `loan.*`

### 2.3 Observability Stack

OpenTelemetry Collector → Prometheus + Loki + Tempo → Grafana + Jaeger. All Java services use the OTel Java agent. The Python risk engine manually instruments W3C traceparent propagation so traces are correlated end-to-end.

---

## 3. Transfer Flow (Remittance Saga)

The saga is the architectural centrepiece. It runs synchronously inside transaction-service:

```
1. API Gateway  — JWT verify, X-Auth-* headers stripped and re-injected, X-Correlation-ID assigned
2. Idempotency  — Redis SETNX on Idempotency-Key → 409 if duplicate in-flight
3. Risk Engine  — rule score + Isolation Forest → max(rule, ml×0.90); >0.85 = REJECT (no money moves)
4. Hold funds   — ACCOUNT.held_balance UPDATE (WITH UPDLOCK, ROWLOCK) + REMITTANCE row PENDING_CORE
5. Ownership    — caller customer_id must match source account → 403 on mismatch
6. T24 Core     — synchronous OFS call; POSTED / REJECTED / PROCESSING / timeout
7. Ledger       — debit + credit + LEDGER_TRANSACTION + OUTBOX_EVENT in one DB transaction
8. Saga Worker  — background recovery for PROCESSING timeouts and T24_POSTED retries
```

Key invariant: **ledger write and outbox row are always in the same DB transaction** — the transactional outbox pattern ensures at-least-once Kafka delivery without dual-write inconsistency.

---

## 4. Risk Engine

### Design
Two independent layers, combined: `max(rule_score, ml_score × 0.90)`. Reject threshold: `> 0.85`.

**Layer 1 — Rule scorer (scorer.py):** eleven deterministic rules covering amount thresholds (₱20k/₱50k/₱100k), new-account detection (<24h), velocity (>2 or >5 txns/hr), amount-vs-30d-average ratio, self-transfer, and non-PHP currency.

**Layer 2 — Isolation Forest (ml_scorer.py):** unsupervised anomaly detection on 6 features (amount, hour of day, account age, velocity, ratio, is_new_recipient). Trains on 10,000 synthetic samples at startup if no model file exists. Serialized to `/tmp/paypink_if_model.joblib`.

**Enrichment:** `RiskEngineClient.java` runs three DB queries before every `/score` call (account age, recent tx count, 30d average) — these must never be removed or the velocity/ratio rules become dead letter.

### Strengths
- ML score is down-weighted so an anomaly alone cannot reject; both layers must agree to cross the threshold — avoids false positives from the Isolation Forest alone.
- All scoring calls (APPROVE, REJECT, UNAVAILABLE) are persisted to the `RISK_DECISION` PostgreSQL table with separate `rule_score` and `ml_score` columns for per-layer observability.
- OTel spans propagated from the Java caller through to the Python service.

---

## 5. Database Design

### Azure SQL Schema (11 tables)
Well-structured. Notable design choices:
- `LEDGER_TRANSACTION` avoids the reserved word `TRANSACTION`.
- `OUTBOX_EVENT.aggregate_id` makes the table reusable for non-ledger events (e.g. loan events where `transaction_id` is NULL).
- `REMITTANCE` includes `retry_count`, `max_retries`, `next_retry_at` and `cancel_until` — saga recovery state built directly into the table.
- `LOAN_APPLICATION` copies `credit_score` at decision time — correct snapshotting, avoids drift if the customer's score is updated later.
- `ACCOUNT` has a `CHECK (held_balance <= current_balance)` constraint — good database-level safety net.
- Filtered unique index on `REMITTANCE(caller_customer_id, idempotency_key) WHERE idempotency_key IS NOT NULL` — prevents duplicate transfers per customer while allowing null idempotency keys for internal transfers.

### PostgreSQL Schema
- `LEDGER_MUTATION_AUDIT` is truly immutable — PostgreSQL rules block UPDATE/DELETE; triggers block TRUNCATE.
- `interest_accrual` and `interest_accrual_batch` enforce immutability at the DB layer with `RAISE EXCEPTION` triggers that also validate row-count consistency between daily rows and batch seals. This is unusually robust for a Capstone project.
- `RISK_DECISION.reasons` is JSONB with a GIN index — supports efficient queries like "all rejections due to amount_above_100k".

---

## 6. Security Model

- **Single ingress:** only port 8080 (api-gateway) is externally accessible. All other service ports are Docker-internal only.
- **JWT validation at the gateway:** `JwtAuthFilter` strips and re-injects `X-Auth-*` headers on every request, preventing header spoofing from reaching downstream services.
- **ROLE_ADMIN enforcement at gateway:** `/api/v1/loans/eod` and `/api/v1/interest/eod` require `ROLE_ADMIN` in the JWT; the check happens before the request reaches the service.
- **Internal service header:** loan-service uses `X-Internal-Service: loan-service` to signal to transaction-service that disbursements bypass the risk engine — this header is stripped at the gateway so it cannot be spoofed externally.
- **Idempotency-Key:** enforced at both the Redis layer (atomic SETNX) and the DB layer (filtered unique index on REMITTANCE).
- **Rate limiting:** token bucket keyed per user (100 req/s steady, 200 burst) on all write paths (ledger, remittance, stress-test, loans).
- **Circuit breaker:** Resilience4j on the transaction-service route — opens after 50% failure rate over 10 calls, half-open after 20s.

---

## 7. CI/CD Pipeline

Three-stage GitHub Actions pipeline:

**Stage 1 — Dev:** 11 Java services built and unit-tested in parallel (matrix strategy, `fail-fast: false`). Python risk engine tested with pytest. Trivy filesystem scan (CRITICAL severity, continue-on-error — non-blocking).

**Stage 2 — Test:** Compiles all JARs, spins up a throwaway Docker Compose stack (Azure SQL, Redis, Kafka, core services), polls the gateway health endpoint, runs Newman (Postman) API contract tests, tears down everything.

**Stage 3 — Prod:** Runs only on pushes/manual triggers to `main`. Runs on a self-hosted runner on `vm-paypink` (Azure VM). Requires a GitHub environment approval gate. Compiles, `docker compose up --build`, health-checks gateway and SPA, records the last good SHA, prunes images.

---

## 8. Frontend

**Web SPA** (`frontend/bank/`): Vanilla JS, served by Nginx on port 3001. Files: `bank.js` (core banking), `loans.js`, `reports.js`, `notifications.js`, `external.js`. Pink palette theme (`#E11D48` primary rose).

**Admin UI** (`frontend/admin/`): Separate panel for transaction monitoring, analytics.

**Mobile PWA** (`mobile/`): Flutter web app. Screens: login/register, dashboard, accounts, remittance, transactions, circuit-breaker status. Served by a second Nginx container on port 3002.

---

## 9. Interest EOD

The daily interest accrual and monthly posting worker lives inside transaction-service (not a separate microservice). This is a reasonable trade-off for a cost-constrained deployment. The design is careful:
- Tiers: 1% below ₱1k, 2.5% below ₱10k, 4% from ₱10k for SAVINGS accounts.
- Accrual records are immutable (PostgreSQL rules + triggers); missing historical snapshots block posting rather than being fabricated from live balances.
- Monthly posting is one atomic Azure SQL transaction: LEDGER_TRANSACTION + OUTBOX_EVENT + EOD_JOB_RUN.
- Duplicate prevention: unique LEDGER_TRANSACTION reference `INT-<period-end>-<account-id>`, plus PostgreSQL advisory locking during batch sealing.

---

## 10. Strengths

1. **Transactional outbox pattern is implemented correctly** — ledger write and outbox event are always one DB transaction. No dual-write risk.
2. **Risk-first money movement** — the gateway never moves money without a score. 503 on unavailable risk engine.
3. **Immutable audit trail** — PostgreSQL rules and triggers make the audit tables genuinely append-only at the DB layer, not just by application convention.
4. **Two-layer fraud scoring** — deterministic rules + unsupervised ML, combined score, with correct down-weighting of the ML score to avoid excessive false positives.
5. **Header sanitisation at the gateway** — X-Auth-* headers stripped unconditionally before re-injection; downstream services cannot be spoofed.
6. **Pessimistic locking on balance updates** — `WITH (UPDLOCK, ROWLOCK)` prevents balance races under concurrent transfers.
7. **DB-level balance integrity constraints** — `CHECK (held_balance <= current_balance)` and `CHECK (current_balance >= 0)` catch bugs the application layer misses.
8. **Well-structured CI/CD** — parallel matrix builds, throwaway integration stack, environment-gated production deploy, last-good-SHA rollback record.
9. **OTel trace correlation across language boundary** — W3C traceparent propagated from Java services into the Python risk engine so end-to-end traces are unified in Jaeger/Tempo.
10. **Interest EOD immutability design** — advisor locks + row-count validation on batch sealing is production-quality, not typical of a capstone project.

---

## 11. Areas to Address

### 11.1 Hardcoded default secrets in application.yml
`SPRING_DATASOURCE_PASSWORD` has a hardcoded fallback (`PayPink2.0_StrongPass!`) and `JWT_SECRET` has a hardcoded fallback hex value. These are fine for local dev but if a container is ever started without the env vars set (e.g. in a misconfigured deployment), those defaults are used silently. The CI pipeline also echoes `JWT_SECRET` into a temp `.env` file in plaintext. Consider failing fast on missing secrets in production profiles.

### 11.2 No service-to-service authentication
Internal services call each other over plain HTTP with no mTLS or service token. The `X-Internal-Service` header used by loan-service is stripped at the gateway but any container inside the Docker network could forge it. This is acceptable for a cost-constrained local deployment but worth documenting as a known gap.

### 11.3 CORS is fully open
`allowedOriginPatterns: "*"` with `allowCredentials: true` is a security risk — browsers block this combination in practice (the spec requires an explicit origin when credentials are involved), but it should be locked down to the actual frontend origins in production.

### 11.4 Trivy scan is non-blocking
`exit-code: '0'` and `continue-on-error: true` on the Trivy step means a CRITICAL vulnerability will never fail the pipeline. This is noted as a temporary choice but should have a planned date to be tightened.

### 11.5 Risk engine is stateless but model lives in /tmp
`ml_scorer.py` serializes the Isolation Forest to `/tmp/paypink_if_model.joblib`. On container restart the model is retrained from scratch, which is fine, but it adds ~5-10 seconds of cold-start latency. Mounting a persistent volume or baking the trained model into the image would eliminate this.

### 11.6 T24 adapter is a simulator
The T24 core adapter (`t24-adapter`) is a simulator — not connected to a real T24 instance. This is expected for a capstone, but the architecture correctly isolates it behind the adapter pattern so swapping in a real OFS endpoint only requires changing that service.

### 11.7 Mobile app is Phase 7 (incomplete)
The Flutter mobile app exists in `mobile/` with screens scaffolded (login, dashboard, accounts, remittance, transactions, circuit-breaker), but Phase 7 is listed as Pending. The app compiles to web and is served on port 3002, but feature completeness relative to the web SPA is not yet documented.

### 11.8 Phase 8 (Chaos + Load Testing) pending
JMeter scripts exist in `docker/jmeter/` and the `performance/` directory exists, but chaos and load testing haven't been run end-to-end. Before a production launch, the saga's behaviour under concurrent remittances and the circuit breaker's recovery time under load should be verified.

### 11.9 Interest EOD settings not persisted in Compose
The EOD activation settings (`INTEREST_EOD_ENABLED=true`, `INTEREST_START_DATE`, PostgreSQL credentials) are passed at runtime, not in the committed Compose file. If the container is recreated with default Compose, EOD is silently disabled. This is documented in CONTEXT.md but is an operational risk — a recreate by a new team member would lose the configuration.

### 11.10 Loan disbursements bypass the risk engine
Loan disbursements send `X-Internal-Service: loan-service` to transaction-service, which skips the risk engine. This is intentional (the loan decision process is the risk gate) but it means a compromised loan-service or a network-internal actor could disburse without scoring. Worth adding a note in the service's security threat model.

---

## 12. Phase Completion Summary

| Phase | Status | Notes |
|---|---|---|
| 0 — Capstone 1 base | Done | |
| 1 — Oracle → Azure SQL | Done | |
| 2 — OTel observability | Done | |
| 3 — Risk engine (rule + ML) | Done | v3.0.0, two-layer |
| 4 — T24 adapter/simulator | Done | |
| 5 — Remittance saga | Done | |
| 5b — Loans | Done (unit tested) | Not Docker end-to-end verified |
| 6 — Immutable audit + risk log | Done | |
| Interest EOD | Done locally | Not deployed to Azure VM this session |
| 7 — Mobile PWA | In progress | Scaffolded, not feature-complete |
| 8 — Chaos + load testing | Pending | |

---

## 13. Overall Assessment

This is a well-engineered project for a FinTech capstone. The architecture follows production patterns — transactional outbox, saga orchestration, gateway-level JWT validation, pessimistic locking, immutable audit trail, and a hybrid rule+ML risk engine — that you would find in a real payment system. The deliberate separation of concerns (risk first, then funds, then core, then ledger) reflects an understanding of financial system safety invariants rather than just software patterns.

The main gap before a real production release would be secret management hardening, service-to-service authentication (mTLS), CORS lockdown, and a passing Trivy gate. Everything else is either complete or has a clear, documented path to completion.
