# PayPink 2.0 — Project Context

_Project Team: Team 4 (Collaborative Capstone; no single owner)_  
_Active Working Branch: main_  
_Last Updated: 2026-10-09 (Cleaned up Remittance confirmation modal & receipt UI: removed Risk Screening, Screenshot Ready pill, Settlement row, and 15-Minute Grace banner in Flutter app)_
_Last Updated: 2026-10-09 (Auto-shutdown disabled on Azure VM vm-paypink, 26/26 containers restored and verified online, and mobile app enabled for cloud connection via API_BASE_URL by [levi])_  

---

## What it is

The PayPink system is being refactored from a shared-database monolithic ledger into a Domain-Driven Design (DDD) banking architecture. In this design, Temenos T24 (simulated by `t24-adapter`) acts as the stateful System of Record (SoR) and authoritative double-entry book of record. It powers a mobile P2P remittance application (domestic, PHP) with real-time fraud screening and T24 core banking integration.

### Refactoring Roadmap (Phases 0 through 10)

| Phase | Title | Scope and Deliverables | Status |
|:---:|---|---|:---:|
| **Phase 0** | Perimeter Lockdown & Bypass Elimination | Enforced ROLE_ADMIN on admin/stress routes in API Gateway; added defense-in-depth in account-service; deprecated direct transfer bypass in auth-service (410 GONE). | Done (Commit `43f3025`) |
| **Phase 1** | Azure SQL Schema Split | Separated database into `t24` (core) and `app` (application) schemas with backward-compatible `dbo.*` synonyms. Qualified JPA `@Table(schema = "...")` across all microservices. | Done (Commit `a8d6213`) |
| **Phase 2** | Stateful T24 Core Banking Engine | Added `t24.LOCKED_AMOUNT` and `t24.POSTING_JOURNAL` tables. Implemented `T24HoldService` (atomic lock/release) and `T24PostingService` (double-entry posting journal). | Done (Commit `3cd7129`) |
| **Phase 3** | Remittance Saga Hold Integration & Cutover | Integrated `T24HoldClient` with circuit breaker into `RemittanceLedgerService`. Replaced local SQL balance lock updates with T24 Core hold API calls. | Done (Commit `3b3f9e2`) |
| **Phase 4** | Transaction History & CQRS Read-Model | Built CQRS read-store in `transaction-service`: `TransactionActivityService`, PDF statement generation (`TransactionStatementReportService`), and Operations Desk admin monitor. Added gateway routes. | Done (Commit `801b044`) |
| **Phase 5** | Account Service Consolidation | Move `/me`, recipient lookup, recipients directory, and banking favorites/beneficiaries into `account-service`. Route live balance inquiries to T24 Core. | Done (Commit `9982ba3`) |
| **Phase 6** | Auth Slimming & Loan Service Alignment | Slim `auth-service` perimeter via gateway route cutover for recipients, favorites, and admin monitor; loan disbursements & repayments routed via T24 Core posting saga. | Done (Commit `9d29e3e`) |
| **Phase 7** | EOD Service Alignment | Align Interest EOD and Loan EOD to use qualified `t24.*` and `app.*` schemas with T24 Core EOD job logs (`t24.EOD_JOB_RUN`) and posting events. | Done (Commit `2f9e31e`) |
| **Phase 8** | Events, Audit & Reconciliation Re-point | Verify and relate outbox events with T24 Core double-entry posting journals across `audit-service`, `reconciliation-service`, `notification-service`, and `analytics-service`. | Done (Commit `9590d5f`) |
| **Phase 9** | Frontend Polish, Synonym Cleanup & Final Verification | Final end-to-end verification across Web SPA and frozen Mobile contracts; created synonym retirement script `scripts/retire_phase9_synonyms.sql`; verified 100% test pass rate across all microservices. | Done |
| **Phase 10** | Database RBAC, RFC-7807 & Security Hardening | Database-driven roles in `app.CUSTOMER`, BCrypt admin user seeding (`migrate_phase10_rbac_roles.sql` in Docker Compose), gateway CORS trusted origin allow-list, account list pagination, and platform-wide RFC-7807 Problem Details (`application/problem+json`) standardisation. | Done |

---

## Phase Status

| Phase | Description | Status |
|---|---|---|
| 0 | Capstone 1 Base Setup & Verification | Done |
| 1 | Oracle XE → Azure SQL Migration | Done |
| 2 | OpenTelemetry & Observability Mesh | Done |
| 3 | Risk Engine & Fraud Screening (rule-based + Isolation Forest ML) | Done |
| 4 | T24 Core Adapter & Simulator (Deterministic OFS & Account State) | Done |
| 5 | Remittance Orchestrator & Saga Engine Hardening | Done |
| 5b | Loans — apply / accept / disburse / repay / EOD | Implemented (unit-tested; not yet Docker end-to-end) |
| 6 | Immutable Audit & Risk Decision Log (RISK_DECISION table in PostgreSQL) | Done |
| Interest EOD | Daily interest accrual and monthly savings posting | Enabled in local Docker; PostgreSQL GL integration deployed; 78 change-specific tests passed |
| 7 | Mobile Frontend (PWA) | Pending |
| 8 | Chaos + Load Testing | Pending |

- **Perimeter (Edge):** `api-gateway` (:8080) acts as the sole public ingress, validating JWTs, enforcing RBAC (Customer vs. Admin), and applying rate limits.
- **Core Domain (SoR):** `t24-adapter` (:8090) serves as the authoritative Core Banking System of Record (simulating Temenos T24), managing live balances, holds (`t24.LOCKED_AMOUNT`), and double-entry general ledger journals (`t24.POSTING_JOURNAL`).
- **Remittance Orchestrator & Transactions:** Hosted inside `transaction-service` (:8083), executing the 4-step distributed transfer saga (Idempotency -> Fraud Scoring -> T24 Core Hold -> Ledger Settlement & Outbox) along with CQRS transaction history, PDF statement generation, and Operations Desk admin monitoring.
- **Fraud & Risk Scoring:** `risk-engine` (:8000) is a standalone Python 3.11 FastAPI microservice providing two-layer real-time fraud scoring (Rules Engine + Isolation Forest ML) invoked directly by the Remittance Orchestrator before any funds are locked or moved.
- **Customer & Loan Domains:** `account-service` (:8082) manages customer profiles, live T24 balance inquiry, and beneficiary management; `loan-service` (:8091) handles loan origination, credit evaluation, and repayments routed via T24 Core. All credit bands (LOW/NORMAL/HIGH) lend at a flat 7% a year (`loan.bands.*.annual-rate` in `application.yml`); bands still set max amount and term. In the Web SPA, accepting a loan offer requires a key-facts + terms & conditions review modal and an explicit agreement checkbox before disbursement.
- **Async Events & Audit:** Kafka topics distribute immutable audit records to `audit-service` (:8085) and PostgreSQL, transaction alerts to `notification-service` (:8084), stream metrics to `analytics-service` (:8088), and verify consistency via `reconciliation-service` (:8086).

> Note: Phase 5b (Loans) was built alongside Phase 5 hardening and got labeled "Phase 6" in older entries. Phase 6 in the immutable audit log is RISK_DECISION in PostgreSQL. Loans = Phase 5b.

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
- Frontend: Vanilla JS SPA served by Nginx (ports 80 and 3001), Flutter PWA (port 3002)

> The running auth-service (as of 2026-10-05) uses a hosted Azure SQL database named `paypink`. Checked-in Compose defaults still point to local SQL Server. Preserve runtime connection settings when restarting.

---

## Microservices

| Service | Port | DB |
|---|---|---|
| api-gateway | 8080 (only exposed host port) | Redis |
| auth-service | 8081 (internal) | Azure SQL |
| account-service | 8082 (internal) | Azure SQL + Redis |
| transaction-service | 8083 (internal) | Azure SQL + Redis + Kafka; PostgreSQL for interest EOD when enabled |
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

## T24 Core Adapter & OFS Simulator (Phase 4 — updated 2026-10-06)

Deterministic Temenos T24 OFS Core Banking Integration and Simulation:
- **No Probabilities:** Probabilistic simulation (90% success, 8% rejection, 2% timeout) has been removed. Outcomes are 100% deterministic based on message syntax and account lifecycle state.
- **OFS Message Validation:** Validates `FUNDS.TRANSFER,%s/I/PROCESS,,DEBIT.ACCT.NO::%s,CREDIT.ACCT.NO::%s,AMOUNT::%.2f,CURRENCY::%s`. Rejects with `/-1` (HTTP 422) if syntax is malformed, fields are missing, debit equals credit, or amount <= 0.
- **Account State Verification:** Verifies lifecycle state of debit and credit accounts. If an account is `FROZEN` or `CLOSED`, returns deterministic rejection `/-1` (HTTP 422) triggering immediate hold release in transaction-service with 0 retries. State managed via in-memory registry, `POST /ofs/account-status`, or convention matching (`ACC-FROZEN-*`, `ACC-CLOSED-*`).
- **Timeout Preservation:** Explicit timeout simulation retained for `SIM-TIMEOUT` references (2500ms delay to exceed 2s SLA). Circuit breaker and `RemittanceSagaWorker` bounded retries (3 attempts with exponential backoff: 15s, 45s, 135s) and auto-reversal remain fully operational.

---

## Azure SQL Schema (current)

Tables: CUSTOMER, ACCOUNT, AUDIT_LOG, BANKING_FAVORITE, LEDGER_TRANSACTION, OUTBOX_EVENT, REMITTANCE, LOAN_APPLICATION, LOAN, LOAN_SCHEDULE, LOAN_REPAYMENT. Interest migration adds EOD_JOB_RUN and ACCOUNT.interest_rate. Applied to hosted Azure SQL `paypink`; the empty legacy GL_ENTRY table was retired. Financial GL entries use existing PostgreSQL LEDGER_MUTATION_AUDIT.

Migration run order on an existing database:
1. schema-azuresql.sql
2. scripts/migrate_phase5_hardening.sql
3. scripts/migrate_phase6_loans.sql
4. scripts/seed_demo_azure_sql.sql

PostgreSQL migration run order (ledger_audit_db):
1. microservices/audit-service/src/main/resources/schema-postgres.sql (full schema on new DB)
2. scripts/migrate_phase6_risk_decision.sql (adds RISK_DECISION on existing DB — idempotent)
3. scripts/migrate_interest_postgres.sql (immutable daily interest snapshots and completion records)

Interest EOD also requires scripts/migrate_interest_azuresql.sql on an existing ledger.
The worker is in transaction-service's orchestrator.interest package. Enable with
INTEREST_EOD_ENABLED=true, an explicit INTEREST_START_DATE, and PostgreSQL credentials.
See docs/interest-eod.md. It credits SAVINGS/SAVINGS_ACCOUNT monthly through
LEDGER_TRANSACTION and OUTBOX_EVENT; LOAN accounts accrue only. Missing historical
snapshots block posting instead of being recalculated from live balances.

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
- Working branch (2026-10-06): levi-feature
- Latest CI update: CI/CD Dev stage hardened for Java 17 Temurin; all 11 microservices pass parallel unit tests; Trivy scan configured with official action.

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

## Cloud Deployment & Azure Host Status (`vm-paypink`)

- **VM Name:** `vm-paypink` (Ubuntu 24.04 LTS)
- **Public IP:** `20.69.157.88`
- **FQDN:** `paypink-levi-westus2.westus2.cloudapp.azure.com`
- **Resource Group:** `RG-PAYPINK-WESTUS2`
- **Active Endpoints:**
  - Web Banking Frontend SPA: `http://20.69.157.88:80`
  - Mobile PWA App: `http://20.69.157.88:3002`
  - API Gateway: `http://20.69.157.88:8080`
- **Container Health:** 26/26 Docker containers running and healthy.
- **GitHub Self-Hosted Runner:** v2.337.0 active as systemd service (`actions.runner.TechStart-Integration-Capstone-Integrated-Capstone.vm-paypink.service`) with labels `self-hosted,azure-vm` for automated Stage 3 CD deployments. Pipeline uses Node 24 actions (`checkout@v5`, `setup-java@v5`, `setup-python@v6`, `upload-artifact@v5`), hosted jobs pinned to `ubuntu-24.04`, and `setup-java@v5` (Temurin 17) and host fallback to guarantee Java 17 toolchain for Maven artifact packaging before Docker image builds.
- **Budget Control:** Zero-cost / low-cost tier. Scheduled auto-shutdown active at 11:00 UTC (7:00 PM PHT). Deallocate when idle.
- **Compose project:** Prod runs as `-p paypink` (volumes `paypink_*`). On 2026-10-06 the manual `docker` project stack was removed and its data copied to `paypink_*` with `scripts/04-migrate-compose-project.sh`; old `docker_*` volumes kept as backup until the pipeline-deployed stack is verified. Pipeline guard blocks deploys while foreign-project containers exist.
- **Reconciliation Hotfix Migration:** Run `scripts/migrate_reconciliation_fix.sql` against `postgres-immutable-audit` container on the VM to sync `account_id` and `azure_sql_status` column renames.

---

## Current Focus

All planned phases complete through Phase 10 with CI/CD passing on Java 17 Temurin runners. Remaining work:
- **Interest EOD monitoring** — local Docker is enabled. Verify scheduled snapshot and month-end posting. Preserve runtime Azure SQL and EOD settings when recreating containers.
- **Phase 7** — Mobile Frontend (Flutter App & PWA)
- **Phase 8** — Chaos + Load Testing

---

## Daily Interest Accrual and Monthly Posting (2026-10-06) - aly

- **Worker location:** `microservices/transaction-service/src/main/java/com/bank/transaction/orchestrator/interest/`. Uses the existing orchestrator service, without adding a separate backend or exposed port.
- **Daily calculation:** active `SAVINGS` and `SAVINGS_ACCOUNT` use whole-balance tiers of 1% below 1,000, 2.5% below 10,000, and 4% from 10,000. Active `LOAN` accounts use the annual fraction in `ACCOUNT.interest_rate`. Divide by 365 and round half-up to six decimals, including leap years. Daily runs read `ACCOUNT.current_balance` without crediting it.
- **Immutable PostgreSQL records:** `interest_accrual` is unique per account/business date. `interest_accrual_batch` commits with all daily rows and seals the date, including empty batches. Rules ignore UPDATE/DELETE; triggers reject TRUNCATE and inserts into sealed dates. Neither table has a posting flag.
- **Monthly posting:** after the final day's accrual, sum the period and round once to two decimals. Savings credits, `LEDGER_TRANSACTION`, `OUTBOX_EVENT` and `EOD_JOB_RUN` commit in one Azure SQL transaction. Outbox/Kafka delivers credits to the existing PostgreSQL `LEDGER_MUTATION_AUDIT` GL; there is no separate Azure GL table. Loans accrue only; existing loan repayment/overdue processing stays separate. Zero interest completes the EOD job without a financial transaction or GL entry.
- **Retry and concurrency protection:** shared SQL Server application lock plus account locks; the application lock requires an explicit transaction before acquisition. The existing unique LEDGER_TRANSACTION reference `INT-<period-end>-<account-id>` prevents duplicate credits; replay verifies its amount against immutable accrual totals. Audit consumers deduplicate by transaction/account and retry database failures rather than acknowledge them. Retries reuse committed PostgreSQL snapshots after an Azure SQL failure. Missing dates block posting; historical snapshots are never fabricated from current balances. Hourly recovery retries unfinished closed months.
- **Admin endpoints:** `POST /api/v1/interest/eod/accrue?businessDate=YYYY-MM-DD` and `POST /api/v1/interest/eod/post?businessDate=YYYY-MM-DD`. Gateway and controller require `ROLE_ADMIN`; posting requires a calendar month-end.
- **Activation:** disabled by default. Apply `scripts/migrate_interest_azuresql.sql` and `scripts/migrate_interest_postgres.sql`; set `INTEREST_EOD_ENABLED=true`, a stable `INTEREST_START_DATE`, and `INTEREST_POSTGRES_URL`, `INTEREST_POSTGRES_USERNAME`, `INTEREST_POSTGRES_PASSWORD`. A midmonth start creates an explicit partial first period. Default cutoff: 23:59:59 Asia/Manila; recovery runs hourly at minute 15. Cron and timezone are configurable.
- **Validation:** transaction-service 66/66 and api-gateway 16/16 passed, including 10 native PostgreSQL 15/SQL Server 2022 tests for immutability, precision, duplicate/concurrent posting and rollback/recovery. `scripts/test_interest.ps1` creates and removes disposable databases; test containers were removed. Subsequent PostgreSQL GL integration validation: transaction-service 68/68 (including 12 native database tests) and audit-service 10/10 passed. Both migrations applied; transaction/audit containers rebuilt and healthy. The retirement migration refuses to drop GL_ENTRY if historical rows exist.
## Required workflow and verified local deployment (2026-10-06)

- Read this file and memory-bank/AGENTS.md before writing code. After every change, update this file and add a newest-first CHANGELOG.md entry. Root AGENTS.md makes this rule visible to future workspace sessions.
- Local containers use hosted Azure SQL paypink-sql.database.windows.net, database paypink; PostgreSQL ledger_audit_db remains in Docker. Azure VM deployment/status was not reverified in this session.
- Interest EOD enabled in transaction-service runtime: start 2026-10-06, Asia/Manila, daily 23:59:59, recovery hourly at :15. User explicitly selected the first accrual date.
- Interest audit role interest_eod_writer has SELECT/INSERT and sequence USAGE; no UPDATE/DELETE, schema CREATE, or superuser privileges. Credentials exist only in container settings.
- EOD settings were passed in memory without editing Compose/.env; preserve them during future rebuilds or recreate will restore disabled defaults.
- PostgreSQL GL delivery is asynchronous through remittance.events/ledger.transaction.events and audit-service. Duplicate ledger legs are ignored; database errors propagate to Kafka retries.
- Mobile app (`mobile/lib/main.dart`, `screens/dashboard_screen.dart`, `screens/remittance_screen.dart`, `services/remittance_service.dart`, `services/account_service.dart`): added global `DevHttpOverrides`, native `TextStyle` font fallbacks, Clean Architecture with BLoC state machines, and dynamically bound live Azure SQL accounts and transaction history across Overview, Accounts, Transfer (Remittance), and Activity (Transactions) screens.

## Active Initiative: T24 Core Banking & DDD Domain Refactoring

The PayPink system is being refactored from a shared-database monolithic ledger into a Domain-Driven Design (DDD) banking architecture. In this design, Temenos T24 (simulated by `t24-adapter`) acts as the stateful System of Record (SoR) and authoritative double-entry book of record.

### Refactoring Roadmap (Phases 0 through 9)

| Phase | Title | Scope and Deliverables | Status |
|:---:|---|---|:---:|
| **Phase 0** | Perimeter Lockdown & Bypass Elimination | Enforced ROLE_ADMIN on admin/stress routes in API Gateway; added defense-in-depth in account-service; deprecated direct transfer bypass in auth-service (410 GONE). | Done (Commit `43f3025`) |
| **Phase 1** | Azure SQL Schema Split | Separated database into `t24` (core) and `app` (application) schemas with backward-compatible `dbo.*` synonyms. Qualified JPA `@Table(schema = "...")` across all microservices. | Done (Commit `a8d6213`) |
| **Phase 2** | Stateful T24 Core Banking Engine | Added `t24.LOCKED_AMOUNT` and `t24.POSTING_JOURNAL` tables. Implemented `T24HoldService` (atomic lock/release) and `T24PostingService` (double-entry posting journal). | Done (Commit `3cd7129`) |
| **Phase 3** | Remittance Saga Hold Integration & Cutover | Integrated `T24HoldClient` with circuit breaker into `RemittanceLedgerService`. Replaced local SQL balance lock updates with T24 Core hold API calls. | Done (Commit `3b3f9e2`) |
| **Phase 4** | Transaction History & CQRS Read-Model | Built CQRS read-store in `transaction-service`: `TransactionActivityService`, PDF statement generation (`TransactionStatementReportService`), and Operations Desk admin monitor. Added gateway routes. | Done (Commit `801b044`) |
| **Phase 5** | Account Service Consolidation | Move `/me`, recipient lookup, recipients directory, and banking favorites/beneficiaries into `account-service`. Route live balance inquiries to T24 Core. | Done (Commit `9982ba3`) |
| **Phase 6** | Auth Slimming & Loan Service Alignment | Slim `auth-service` perimeter via gateway route cutover for recipients, favorites, and admin monitor; loan disbursements & repayments routed via T24 Core posting saga. | Done (Commit `9d29e3e`) |
| **Phase 7** | EOD Service Alignment | Align Interest EOD and Loan EOD to use qualified `t24.*` and `app.*` schemas with T24 Core EOD job logs (`t24.EOD_JOB_RUN`) and posting events. | Done (Commit `2f9e31e`) |
| **Phase 8** | Events, Audit & Reconciliation Re-point | Verify and relate outbox events with T24 Core double-entry posting journals across `audit-service`, `reconciliation-service`, `notification-service`, and `analytics-service`. | Done (Commit `9590d5f`) |
| **Phase 9** | Frontend Polish, Synonym Cleanup & Final Verification | Final end-to-end verification across Web SPA and frozen Mobile contracts; created synonym retirement script `scripts/retire_phase9_synonyms.sql`; verified 100% test pass rate across all microservices. | Done |

---

## Architecture Overview

PayPink 2.0 operates as an event-driven, domain-partitioned microservices banking platform behind a centralized Spring Cloud API Gateway:

- **Perimeter (Edge):** `api-gateway` (:8080) acts as the sole public ingress, validating JWTs, enforcing RBAC (Customer vs. Admin), and applying rate limits.
- **Core Domain (SoR):** `t24-adapter` (:8090) serves as the authoritative Core Banking System of Record (simulating Temenos T24), managing live balances, holds (`t24.LOCKED_AMOUNT`), and double-entry general ledger journals (`t24.POSTING_JOURNAL`).
- **Remittance Orchestrator & Transactions:** Hosted inside `transaction-service` (:8083), executing the 4-step distributed transfer saga (Idempotency -> Fraud Scoring -> T24 Core Hold -> Ledger Settlement & Outbox) along with CQRS transaction history, PDF statement generation, and Operations Desk admin monitoring.
- **Fraud & Risk Scoring:** `risk-engine` (:8000) is a standalone Python 3.11 FastAPI microservice providing two-layer real-time fraud scoring (Rules Engine + Isolation Forest ML) invoked directly by the Remittance Orchestrator before any funds are locked or moved.
- **Customer & Loan Domains:** `account-service` (:8082) manages customer profiles, live T24 balance inquiry, and beneficiary management; `loan-service` (:8091) handles loan origination, credit evaluation, and repayments routed via T24 Core.
- **Async Events & Audit:** Kafka topics distribute immutable audit records to `audit-service` (:8085) and PostgreSQL, transaction alerts to `notification-service` (:8084), stream metrics to `analytics-service` (:8088), and verify consistency via `reconciliation-service` (:8086).

---

## Active Microservices Directory

| Service | Host Port | Responsibility & Primary Domain | Database Schema |
|---|:---:|---|---|
| `api-gateway` | 8080 | Sole external entry point. JWT validation, role checking, rate limiting, and reverse proxy. | Redis (token bucket) |
| `auth-service` | 8081 | Authentication, user registration, JWT generation, password hashing. | `app.CUSTOMER` |
| `account-service` | 8082 | Customer accounts, balance inquiry, account lifecycle status. | `t24.ACCOUNT`, `app.CUSTOMER` |
| `transaction-service` | 8083 | Remittance Orchestrator (4-step saga), CQRS Activity & PDF statements, Admin Monitor, and Interest EOD. | `app.REMITTANCE`, `t24.LEDGER_TRANSACTION`, `app.OUTBOX_EVENT` |
| `t24-adapter` | 8090 | Core Banking Engine (T24). Authoritative account balances, locked amounts (holds), double-entry posting journal. | `t24.ACCOUNT`, `t24.LOCKED_AMOUNT`, `t24.POSTING_JOURNAL` |
| `risk-engine` | 8000 | Python 3.11 FastAPI. Two-layer fraud scoring: Rules engine + Isolation Forest ML. | Stateless |
| `loan-service` | 8091 | Loan product applications, credit evaluation, and repayments. | `t24.LOAN`, `t24.LOAN_SCHEDULE`, `t24.LOAN_REPAYMENT` |
| `audit-service` | 8085 | Kafka consumer logging immutable risk decision audit records. | PostgreSQL (`RISK_DECISION`) |
| `notification-service` | 8084 | Kafka consumer for SMS/Email/Push transaction notification dispatch. | PostgreSQL |
| `reconciliation-service` | 8086 | Discrepancy detector between application outbox and ledger transactions. | Azure SQL + PostgreSQL |
| `outbox-publisher` | 8087 | Poller worker that pushes `OUTBOX_EVENT` rows onto Kafka topics. | Azure SQL + Kafka |
| `analytics-service` | 8088 | Real-time transaction volume and velocity metrics streamer. | Kafka (in-memory) |

---

## Core Rules and Architectural Boundaries

1. **Mobile Scope (`mobile/`):** The mobile application directory is 100% frozen and untouched. All 6 mobile API contracts must remain strictly backward-compatible.
2. **Money Never Moves Without a Risk Score:** Risk score > 0.85 results in rejection before any hold or balance is touched.
3. **Core Owns Money and Balances:** Microservices do not mutate balances directly in SQL; hold placement and postings are executed by `t24-adapter`.
4. **Display Balances are Display-Only:** Redis-cached balances are never used for transfer authorization.
5. **Team Attribution:** Project ownership is Team 4. Commits and changelog entries are attributed to `[dom]`.
6. **Testing Discipline:** Every change must compile cleanly and pass unit tests before committing.

---

## Infrastructure and Deployment Notes

- **Azure SQL Server 2022:** Local container `azure-sql-master` or hosted Azure SQL `paypink`. 13 tables partitioned across `t24` and `app` schemas with `dbo.*` synonyms. Fresh container setup automated via `mssql-server-setup-scripts.d` (01_schema -> 02_loans -> 03_seed -> 04_schema_split -> 05_interest).
- **PostgreSQL 15:** Local container `postgres-immutable-audit` hosting `ledger_audit_db` for immutable risk audit records and Interest EOD snapshots.
- **Redis 7:** Container `redis-idempotency-matrix` for idempotency locks and rate limits.
- **Kafka:** Container `kafka` for asynchronous transaction events.
- **Azure Host VM:** `vm-paypink` (`20.69.157.88`) in `RG-PAYPINK-WESTUS2`. Auto-shutdown at 11:00 UTC (7:00 PM PHT).
