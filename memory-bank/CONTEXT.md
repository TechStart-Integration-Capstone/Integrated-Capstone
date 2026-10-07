# PayPink 2.0 — Project Context

_Owner: **dom**_
_Latest source change: 2026-10-07 - single-admin simulation workflow implemented; interest admin UI deployed locally; 96 tests passed - dom / aly_
_Last updated: 2026-10-07 (Single-admin interest workflow and Admin UI deployed to local Docker)_

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
| 4 | T24 Core Adapter & Simulator (Deterministic OFS & Account State) | Done |
| 5 | Remittance Orchestrator & Saga Engine Hardening | Done |
| 5b | Loans — apply / accept / disburse / repay / EOD | Implemented (unit-tested; not yet Docker end-to-end) |
| 6 | Immutable Audit & Risk Decision Log (RISK_DECISION table in PostgreSQL) | Done |
| Interest EOD | Daily interest accrual and monthly savings posting | Enabled in local Docker; single-admin simulation and Admin UI deployed |
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
4. scripts/migrate_interest_recovery_postgres.sql (existing installations: immutable resolution metadata)
5. scripts/migrate_interest_approval_postgres.sql (after recovery migration: immutable proposals, independent approvals and rejection of new waivers)

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

All planned phases complete through Phase 6 with CI/CD passing on Java 17 Temurin runners. Remaining work:
- **Interest EOD monitoring & operations** — local Docker is running with single-admin simulation workflow (`InterestEodService.java`), PostgreSQL migration applied (`scripts/migrate_interest_single_admin_postgres.sql`), and Interest Admin UI deployed in `frontend-spa`. Monitor scheduled snapshots and end-of-month posting. Preserve runtime Azure SQL and EOD settings when recreating containers; unchanged Compose defaults disable EOD. Azure VM deployment was left unchanged per request.
- **Phase 7** — Mobile Frontend (PWA)
- **Phase 8** — Chaos + Load Testing

---

## Daily Interest Accrual and Monthly Posting (2026-10-06) - aly

- **Worker location:** `microservices/transaction-service/src/main/java/com/bank/transaction/orchestrator/interest/`. Uses the existing orchestrator service, without adding a separate backend or exposed port.
- **Daily calculation:** active `SAVINGS` and `SAVINGS_ACCOUNT` use whole-balance tiers of 1% below 1,000, 2.5% below 10,000, and 4% from 10,000. Active `LOAN` accounts use the annual fraction in `ACCOUNT.interest_rate`. Divide by 365 and round half-up to six decimals, including leap years. Daily runs read `ACCOUNT.current_balance` without crediting it.
- **Immutable PostgreSQL records:** `interest_accrual` is unique per account/business date. `interest_accrual_batch` commits with all daily rows and seals the date, including empty batches. Rules ignore UPDATE/DELETE; triggers reject TRUNCATE and inserts into sealed dates. Neither table has a posting flag.
- **Monthly posting:** after the final day's accrual, sum the period and round once to two decimals. Savings credits, `LEDGER_TRANSACTION`, `OUTBOX_EVENT` and `EOD_JOB_RUN` commit in one Azure SQL transaction. Outbox/Kafka delivers credits to the existing PostgreSQL `LEDGER_MUTATION_AUDIT` GL; there is no separate Azure GL table. Loans accrue only; existing loan repayment/overdue processing stays separate. Zero interest completes the EOD job without a financial transaction or GL entry.
- **Retry and concurrency protection:** shared SQL Server application lock plus account locks; the application lock requires an explicit transaction before acquisition. The existing unique LEDGER_TRANSACTION reference `INT-<period-end>-<account-id>` prevents duplicate credits; replay verifies its amount against immutable accrual totals. Audit consumers deduplicate by transaction/account and retry database failures rather than acknowledge them. Retries reuse committed PostgreSQL snapshots after an Azure SQL failure. Missing dates block posting; historical snapshots are never fabricated from current balances. Hourly recovery retries unfinished closed months.
- **Admin endpoints:** `POST /api/v1/interest/eod/accrue?businessDate=YYYY-MM-DD` and `POST /api/v1/interest/eod/post?businessDate=YYYY-MM-DD`. Recovery: `GET /api/v1/interest/eod/missing?periodEnd=YYYY-MM-DD`; `POST /api/v1/interest/eod/resolve?businessDate=YYYY-MM-DD` prepares a proposal only; `GET /api/v1/interest/eod/backfills/{id}` reviews it; `POST /api/v1/interest/eod/backfills/{id}/approve` requires a different admin. Gateway and controller require `ROLE_ADMIN`; posting requires a calendar month-end.
- **Activation:** disabled by default. Apply `scripts/migrate_interest_azuresql.sql` and `scripts/migrate_interest_postgres.sql`; existing installations require recovery then approval PostgreSQL migrations. Set `INTEREST_EOD_ENABLED=true`, a stable `INTEREST_START_DATE`, and `INTEREST_POSTGRES_URL`, `INTEREST_POSTGRES_USERNAME`, `INTEREST_POSTGRES_PASSWORD`. A midmonth start creates an explicit partial first period. Source default cutoff remains 23:59:59 Asia/Manila (`59 59 23 * * *`); recovery runs hourly at minute 15. Preserve runtime overrides; do not change the banking cutoff as a scheduling workaround.
- **Validation:** transaction-service 66/66 and api-gateway 16/16 passed, including 10 native PostgreSQL 15/SQL Server 2022 tests for immutability, precision, duplicate/concurrent posting and rollback/recovery. `scripts/test_interest.ps1` creates and removes disposable databases; test containers were removed. Subsequent PostgreSQL GL integration validation: transaction-service 68/68 (including 12 native database tests) and audit-service 10/10 passed. Both migrations applied; transaction/audit containers rebuilt and healthy. The retirement migration refuses to drop GL_ENTRY if historical rows exist.
- **Documentation:** [interest EOD setup and operation](../docs/interest-eod.md); README and ERD updated.

## Required workflow and verified local deployment (2026-10-06)

The missed-day fix below is implemented and tested; the previously verified runtime settings in this section have not been changed by this fix.

- Read this file and memory-bank/AGENTS.md before writing code. After every change, update this file and add a newest-first CHANGELOG.md entry. Root AGENTS.md makes this rule visible to future workspace sessions.
- Local containers use hosted Azure SQL paypink-sql.database.windows.net, database paypink; PostgreSQL ledger_audit_db remains in Docker. Azure VM deployment/status was not reverified in this session.
- Interest EOD enabled in transaction-service runtime: start 2026-10-06, Asia/Manila, daily 23:59:59, recovery hourly at :15. User explicitly selected the first accrual date.
- Interest audit role interest_eod_writer has SELECT/INSERT and sequence USAGE; no UPDATE/DELETE, schema CREATE, or superuser privileges. Credentials exist only in container settings.
- EOD settings were passed in memory without editing Compose/.env; preserve them during future rebuilds or recreate will restore disabled defaults.
- PostgreSQL GL delivery is asynchronous through remittance.events/ledger.transaction.events and audit-service. Duplicate ledger legs are ignored; database errors propagate to Kafka retries.
- No live accrual or monthly posting was manually triggered for verification. Mobile remained excluded from rebuilding.

## Interest missed-day recovery and independent approval (2026-10-06) - aly

- Worker now retains the intended scheduled date before execution; queue delays across midnight cannot select the next day. Live captures crossing midnight fail safely and require historical recovery.
- Admin missing-day listing includes only elapsed dates. `/resolve` accepts only BACKFILL and stores an immutable PENDING_APPROVAL proposal with historical manifest, reason, source reference, original preparer and hash. Preparation does not complete a day. A second admin reviews `/backfills/{id}` and approves with a reason and confirmation at `/backfills/{id}/approve`. Self-approval (including case variants) is rejected by both service and PostgreSQL.
- Approval, historical accruals and batch completion commit together in PostgreSQL. Original maker/checker identities, timestamps, reasons and source remain immutable. Account existence/opening dates and current account types are checked. Matching retries reuse the original proposal/snapshot; posted periods and sealed dates cannot be replaced. Savings rates are derived from supplied historical balances; loan rates come from supplied contract history. Both admins verify completeness against historical evidence because current account data cannot prove past activity.
- WAIVER requests and new waiver records are rejected. Missing data never becomes zero interest. Legacy waived/unapproved sealed records remain archived but do not count as complete; correcting them needs a separate adjustment process. Missing periods remain unresolved and block their monthly posting. Hourly recovery reports them while processing complete later periods. Compensation for delayed capitalization and recalculation of already sealed later interest are not implemented by this API.
- Midmonth customer openings remain valid: September 20-30 earns 11 days, with no pre-opening account accrual required. Bank-wide daily completion checks stay in place.
- Validation: 94/94 transaction-service tests passed, including 23 native PostgreSQL 15/SQL Server 2022 tests. Coverage includes self-approval/waiver rejection, immutable proposals/approvals, concurrent approval, atomic rollback, Azure failure after PostgreSQL commit, pinned dates/midnight guards and September 20-30 accrual. Disposable test containers were removed. See `docs/interest-eod.md` for requests and migrations. Local runtime and Azure VM have not been updated with this fix.
