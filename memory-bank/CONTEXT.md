# PayPink 2.0 — Project Context

_Project Team: Team 4 (Collaborative Capstone; no single owner)_  
_Active Working Branch: main_  
_Changelog attribution for this user's changes: [aly], per explicit instruction on 2026-10-11._
_Last Updated: 2026-10-10 (Mobile MPIN Database Persistence Audit & CI/CD Pipeline Wiring: resolved silent HTTP 400 rejection via dual-key 'mpin'/'pin' support in BankingController and SecureTokenStorage; wired scripts/migrate_mpin.sql into GitHub Actions pipeline.yml Stages 2 and 3, docker-compose.yml, and schema-azuresql.sql; live Azure SQL persistence verified for lviernes and arosales; 54/54 auth-service tests and 38/38 mobile tests pass clean by [dom & Antigravity])_
_Last Updated: 2026-10-09 (Reverted web app UI changes to original team baseline, focusing strictly on mobile app dark mode refinements and Azure SQL MPIN database persistence; 38/38 mobile tests pass clean by [dom & Antigravity])_
_Last Updated: 2026-10-09 (Personal Loan Details modal & Accounts screen theme alignment: purged indigo/blue relics in favor of signature PayPink wine/rose gradient hero cards, adaptive dark mode contrast for due dates, status badges, and Schedule action buttons; 38/38 mobile tests pass by [dom & Antigravity])_
_Last Updated: 2026-10-09 (Azure SQL MPIN DB persistence & verification across logins/devices via auth-service, Web dark mode toggle aligned with mobile wine theme tokens, and refined fintech gradient balance card; all 54/54 backend and 37/37 mobile tests pass clean by [dom & Antigravity])_
_Last Updated: 2026-10-09 (Fixed Loans hub AppBar alignment on wide screens: wrapped Scaffold in Center and ConstrainedBox(maxWidth: 440) inside full-bleed background Container so back arrow and title align with mobile cards; 37/37 tests pass by [dom & Antigravity])_
_Last Updated: 2026-10-09 (Mobile Loans hub: one Loans button opens a screen with pay, details and apply; 30/30 tests pass by [gillianneysha])_  
_Last Updated: 2026-10-09 (Loan payments left shown on web and mobile from the repayment schedule; web Pay dialog adds Next payment / Full balance / Another amount; 28/28 mobile tests pass by [gillianneysha])_  
_Last Updated: 2026-10-09 (Restored mobile-app service in docker/docker-compose.yml, preventing Azure VM CD deployment from tearing down the container with --remove-orphans and restoring live availability on http://paypink.westus2.cloudapp.azure.com:3002)_
_Last Updated: 2026-10-09 (Fixed Mobile Transfer Favorites persistence and receipt favoriting: canonical /auth/banking/recipients backend alignment, corrected Quick Favorites row conditional branch structure, zero-lag SecureTokenStorage per-user cache hydration, and interactive favorite action inside transfer receipt modal; 33/33 tests passing clean by [dom & Antigravity])_
_Last Updated: 2026-10-09 (Fixed ProfileSheet night mode toggle modal synchronization via dynamic _isDark state and Material ListTile wrappers, and fixed In-App Notification drawer overflow via SingleChildScrollView with dark mode styling; 31/31 mobile tests passing clean by [dom & Antigravity])_
_Last Updated: 2026-10-09 (Resolved double transfer activity and UTC timestamp mismatch in mobile app: defaulted preserveLocalTransactions to false, normalized Spring Boot/Azure SQL UTC timestamps with Z and .toLocal() for Asia/Manila parity, aligned own-account transfer titles to friendly format, added secondary deduplication pass in TransactionsScreen, and fixed BuildContext lints; 29/29 tests pass clean by [dom & Antigravity])_
_Last Updated: 2026-10-09 (Fixed double transfer notifications and duplicate activity in transaction history: eliminated phantom credit legs on external/P2P transfers, preserved authoritative server refId, and enforced multi-layer deduplication across _loadLiveDatabaseData and TransactionsScreen; 20/20 mobile tests pass clean by [dom])_
_Last Updated: 2026-10-09 (Mobile/web feature parity: missed loan auto-debit alert, real InstaPay/PESONet transfers, 30-second cancel window removed from web and mobile, server-derived notifications, server transaction report, local card freeze and client reversal removed; 27/27 tests pass by [gillianneysha])_  
_Last Updated: 2026-10-09 (Patched db/phase6_loans.sql and scripts/migrate_phase6_loans.sql with dual dbo/app/t24 schema guards, preventing SQL Server 4909 'Cannot alter dbo.CUSTOMER because it is not a table' synonym error and allowing loan-service to start and pass Swagger probes in CI/CD)_
_Last Updated: 2026-10-09 (Restored docker-compose.yml DataSource URL fallbacks and CI workflow env secrets, resolving Spring Boot HikariCP failed jdbc url crash and enabling automated CI/CD pipeline health checks and Newman tests to succeed)_
_Last Updated: 2026-10-09 (Mobile UI aligned with the customer web app: DM Sans/Manrope fonts bundled, flat web-style tokens and cards, login/register rebuilt to mirror the frontend/bank auth panel, web-style bottom tab bar; 17/17 tests pass by [gillianneysha])_  
_Last Updated: 2026-10-09 (Resolved Web Crypto Null check operator crash in SecureTokenStorage on plain HTTP via browser localStorage fallback, enabling seamless session persistence and login on http://paypink.westus2.cloudapp.azure.com:3002)_
_Last Updated: 2026-10-09 (Aligned Mobile Web to dynamic reverse proxy origin ${Uri.base.origin}/api/v1 via container Nginx on port 3002, matching Web Banking architecture and eliminating cross-port CORS blocks)_
_Last Updated: 2026-10-09 (Mobile real-time loan origination & instant state refresh implemented: merged fetchLoans() into fetchProfile() UserProfile.accounts, and wired onRefreshData to trigger _loadLiveDatabaseData(bypassCache: true) immediately upon loan acceptance; 17/17 tests pass clean by [dom])_  
_Last Updated: 2026-10-09 (Corrected Azure FQDN to paypink.westus2.cloudapp.azure.com:8080/api/v1, enabled GoogleFonts runtime fetching, and tuned nginx caching to avoid stale mobile app browser caching)_
_Last Updated: 2026-10-09 (Self-contained offline fonts bundled in mobile/assets/fonts/ and registered in pubspec.yaml; resolved blank white screen crash on http://localhost:3002 Docker release; 17/17 tests pass by [dom])_  
_Last Updated: 2026-10-09 (Created reusable LoadingOverlayWrapper with AbsorbPointer and glassmorphism backdrop; shielded RemittanceScreen and LoanPaymentSheet against touch events during in-flight network requests)_
_Last Updated: 2026-10-09 (Mobile Features 3, 4, 5 implemented: TelemetryService structured logging & Dio integration, SessionManager backgrounding auto-lock & SessionLockWrapper, expanded BLoC and Telemetry test suite with 30/30 tests passing)_

_Last Updated: 2026-10-09 (Cleaned up AccountService.fetchProfile() syntax, retrieved customerId, and eliminated hardcoded dummy account seeding for 100% Cloud-Native live Azure SQL data fetching)_

_Last Updated: 2026-10-09 (Removed Ledger Balance from Account Details modal, customized 3rd card with distinct Electric Fuchsia & Magenta Pink palette)_
_Last Updated: 2026-10-09 (Mobile card styling & half-gradient black, bottom logo removal, favorites UI redesign matching PayPink theme with inline error messaging)_
_Last Updated: 2026-10-09 (Mobile brand refactoring & backend validation: MOB-102 receipt PayPinkLogo, MOB-103 card color gradients & SVG watermark, MOB-104 dynamic dates, MOB-105 server-side favorites & 12-digit lookup verification)_
_Last Updated: 2026-10-09 (AccountService conflict resolved: merged loan-service fetchLoans aggregation, ProfileUnavailableException, Member 5 cascading profile fallbacks and ApiClient error extractor by [dom])_  
_Last Updated: 2026-10-09 (Member 5 Implementation: Mobile backlog MOB-501 through MOB-506 delivered, Gateway CORS and routing aligned, CQRS activity feed integrated, RFC-7807 error handling unified by [dom])_  
_Last Updated: 2026-10-09 (Fixed isSmall constructor parameter and Widget mapping in bottom_sheets.dart; verified clean flutter analyze build)_  
_Last Updated: 2026-10-09 (Permanent Web & Mobile Cloud Synchronization established; mobile defaults to Azure Cloud API Gateway and aligns directly with /auth/banking/me endpoint, rendering 3 live accounts totaling ₱235,238.85 identically across Web Banking and Mobile App)_
_Last Updated: 2026-10-09 (Hosted Azure Cloud SQL paypink-sql.database.windows.net schema patched with roles, Saga columns, and synonyms; Web Banking login and end-to-end remittance transfers fully verified operational by [levi])_  
_Last Updated: 2026-10-09 (Auto-shutdown disabled on Azure VM vm-paypink, 26/26 containers restored and verified online, and mobile app enabled for cloud connection via API_BASE_URL by [levi])_  
_Last Updated: 2026-10-09 (AccountService conflict resolved after pulling from main: seamlessly merged loan-service fetchLoans aggregation, ProfileUnavailableException, and RFC-7807 problem details with Member 5 cascading profile fallbacks and ApiClient error extractor; 17/17 mobile tests pass by [dom])_  
_Last Updated: 2026-10-09 (Member 5 Implementation: Mobile backlog MOB-501 through MOB-506 delivered, Gateway CORS and routing aligned, CQRS activity feed integrated, RFC-7807 error handling unified, all 6 widget tests and 24 gateway tests passing by [dom])_  
_Last Updated: 2026-10-09 (Mobile sprint MOB-304/301/302/303/305: payload normalization, offline mock purge, client risk removal, RFC-7807 error surfacing, direct settlement with skipClientWindow, reversal UI purge by [dom])_  
_Last Updated: 2026-10-09 (Clean Architecture, BLoC State Machines, Mobile App cloud connection by [levi, cisko]; Newman auth credentials aligned with seeded Azure SQL users, dual token persistence, full CI stack coverage, 20 Newman API contract test resolutions, and 100% test assertion parity on Interest EOD resolve/post endpoints by [dom])_  
_Last Updated: 2026-10-09 (Hosted Azure Cloud SQL paypink-sql.database.windows.net schema patched with roles, Saga columns, and synonyms; Web Banking login and end-to-end remittance transfers fully verified operational by [levi])_  
_Last Updated: 2026-10-09 (Auto-shutdown disabled on Azure VM vm-paypink, 26/26 containers restored and verified online, and mobile app enabled for cloud connection via API_BASE_URL by [levi])_

---

## What it is

### Customer bug fixes in progress (2026-10-09)

- Fix 1: transaction-service no longer releases a hold a second time when committing a core-posted remittance. Remaining savings/transfer reservations are owned by T24. All 29 targeted saga/recovery/hold tests passed. Local only, transaction-service rebuild required; no database changes or deployment.
- Fix 2 complete: PESONet batch settlement uses available balance and preserves the held-balance snapshot across payments. All 18 banking transfer integration tests passed, including holds placed after submission and multiple payments in one batch. Local only; auth-service rebuild required, no migration or deployment.
- Fix 3 code implemented locally: external instructions route through a private transaction-service settlement endpoint, risk screening and T24 posting; auth-service no longer directly debits external transfers. Pending instructions retain their original ledger row/reference through retry and recovery. Added scripts/migrate_external_clearing.sql (zero-funded PH1000000EXT), Compose/CI wiring and docs/external-settlement.md. All 20 auth-service banking integration tests and 54 transaction-service external/saga/recovery/hold/internal-transfer tests passed. On 2026-10-10, applied the migration to the user-selected paypink.database.windows.net / paypink database and verified account_id 13, PH1000000EXT, INTERNAL/PHP/ACTIVE, owned by paypink_bank, with zero current and held balances. Auth-service and transaction-service rebuild/restart and multi-service acceptance remain pending; no application deployment performed.
- Fix 4 code implemented locally: loan-service persists repayment intent before remote dispatch and recovers delayed postings using the same key; loan allocation, repayment completion and outbox commit together. Pending repayments block additional payments for the same loan; delayed auto-debits update their recorded outcome. All 49 loan-service tests passed, including database rollback and concurrent recovery. On 2026-10-10, applied the guarded LOAN_REPAYMENT.status migration to paypink.database.windows.net / paypink: verified NVARCHAR(24), NOT NULL, default POSTED, and all five existing repayments backfilled with their original fields unchanged. Both Phase 6 script copies retain the idempotent migration. User will rebuild/recreate loan-service; no application deployment performed. Live multi-service acceptance remains pending. Historical orphaned repayments without saved intent require separate reconciliation. See docs/loan-repayment-recovery.md.
- Fix 5 implemented locally: /bank keeps processing/unknown transfer results pending, persists their key/reference/recipient/date, and exposes Check transfer status using the existing GET remittance status endpoint. Status-check errors preserve the instruction; terminal failures render unsuccessful results. Success messages and ledger broadcasts require confirmed completion. JavaScript syntax check and all 24 mocked DOM/network tests in scripts/test_bank_transfer_status.mjs passed; live browser/service acceptance remains pending. Frontend only: no database change or Java rebuild. User handles frontend image rebuild/container recreation; no deployment performed.
- Fix 6 implemented locally: PayPink and external transfer submission retain the original request/key on HTTP 409; conflict responses do not imply reversal. Removed the unresolved external-request discard shortcut. Both JavaScript syntax checks and all 32 mocked DOM/network regressions in scripts/test_bank_transfer_status.mjs passed, covering retries and reloads for PayPink/InstaPay/PESONet. Frontend only, no database change or Java rebuild; user handles frontend image rebuild/container recreation. No deployment performed; live browser/service verification remains pending.
- Fix 7 implemented locally: clear all loan cache/form/payment-key state on logout/login and guard loan alerts and delayed list/progress/schedule/payment responses by session/owner. Old completions cannot clear a new load, reopen dialogs or alter keys; old loan 401s cannot log out the next session. JavaScript syntax checks, all 17 new scripts/test_bank_loan_sessions.mjs tests and all 32 transfer regressions passed (mocked DOM/network). Frontend only; no database change or Java rebuild. User handles frontend image rebuild/container recreation; live browser/service verification pending, no deployment performed. Credited to [aly].
- Fix 8 implemented locally: account-service registers /api/v1/accounts/savings/balance-summary independently of the optional Savings Hub flag via AccountBalanceController. Overview balances remain available with savings disabled/unset; ownership checks, live core reservation-aware amounts and 503 on core failure are preserved. All 40 account-service tests passed, including six new Spring MVC/H2 regressions with mocked core. No database migration or frontend change needed for this fix. User handles account-service JAR/image rebuild and container recreation; no deployment performed, live browser/service acceptance pending. Credited to [aly].
- Fix 9 implemented locally: account details and the PayPink transfer form now correctly label profile currentBalance as Total account balance and explain that reserved funds cannot be transferred. Overview retains its live available-balance summary. JavaScript syntax check and all 49 existing transfer/session tests passed (mocked DOM/network). Frontend only; no database migration or Java rebuild. User handles frontend image rebuild/container recreation; no deployment performed, live browser verification pending. Credited to [aly].
- Fix 10 implemented locally: auth-service history and PDF reports include sender-owned unposted PayPink TRANSFER requests from app.REMITTANCE as PENDING/FAILED/CANCELLED, with no incoming credit or completed-total contribution. Matching ledger source/reference suppresses duplicate requests after posting. Unposted entries have transactionId=null and use their remittance reference; /bank selects them with reference-based keys. Combined latest-200 history and report date/cap restrictions remain. All 73 auth-service tests and 50 frontend regressions passed, plus JavaScript syntax check. No database migration needed; deploy auth-service and frontend together. User handles JAR/image rebuilds and container recreation; no deployment performed, live SQL Server/browser verification pending. Credited to [aly].
- Current authorized scope is fix 10 only. Review findings 11–18 have not been started. User handles JAR/image rebuilds and container recreation; necessary database migrations are authorized.

### Savings contribution limits (2026-10-09)

- Overview card now labels Available balance and explains "After reservations for goals, PinkCircles, and pending transactions." Total balance remains beneath it. The owner-scoped `/api/v1/accounts/savings/balance-summary` aggregates live core snapshots across PHP accounts; failed reads show Unavailable. Verified: 14 savings service tests and browser checks passed. Local only; account-service and frontend rebuild required alongside the pending core reservation fixes.

- Stale-held-total correction (local): core funding derives effective holds from the greater of stored held balance and recorded savings plus active transfer reservations. Locked mutation paths protect that reservation minimum before debit/release checks. Breakdown has a 24px gap before the privacy card. Regression covers PHP 129,014.59 total minus PHP 21,100 reserved = PHP 107,914.59 available. All 42 core tests and savings browser checks passed. Existing savings and hold tables required; no new migration or deployment. Rebuild t24-adapter JAR/image and frontend to deploy. The VM's stored-held discrepancy has not been inspected directly.

- My accounts now includes a live per-account savings breakdown and View allocations disclosure showing only the owner's goal/circle reservations. Other holds are omitted from the panel per user preference; actual core available balance remains authoritative. New owner-checked `/api/v1/accounts/savings/accounts/{id}/breakdown` endpoint uses a locked core snapshot. Local only; backend/frontend rebuild required, no migration. Verified with 13 savings app tests, 10 core savings tests, posting/hold suites and browser checks. Transfer regression rejects spending reserved funds, then permits spending after release while preserving the remaining reservation.

- The web goals/PinkCircles section is now named Savings Hub in live navigation, breadcrumbs and headings, with matching offline preview. Account product names remain Savings account. Rename is local; syntax and savings browser checks passed; awaits frontend deployment.

- Add-money and own PinkCircle contribution dialogs fetch an owner-checked live core funding snapshot and display account balance minus all goal/circle allocations and other holds. Maximum equals min(available funds, remaining own target), rounded down to centavos. Core confirmation still rechecks funds under the account lock. Failed reads block contribution instead of using cached balances. Offline preview mirrors the UI with sample funds. Validation: account-service and t24-adapter test reports have zero failures/errors; savings browser checks and git diff --check passed. No migration/deployment; updating requires account-service and t24-adapter JAR/image builds plus frontend rebuild.

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
| 7 | Mobile Frontend (Flutter App & PWA) | Integrated with Microservices (MOB-501..506 complete; self-contained offline fonts bundled; Docker verified; Flutter analyze & tests pass) |
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
- Mobile UI / web alignment (2026-10-09, branch `feature/mobile-web-design-alignment`): `PayPinkTheme` mirrors `frontend/bank/bank.css` tokens (DM Sans body, Manrope display, paper `#FAF9F6`, wine `#651C3E`, radii 9/12/14/17, `eyebrow()` labels; monospace only for account numbers/IDs). Fonts bundled in `mobile/assets/fonts/`. Login/register mirrors the web auth panel and shows errors inline; bottom nav mirrors the web phone tab bar. Dark mode uses a wine-tinted palette.
- Mobile MPIN lifecycle (2026-10-09): MPIN is created once per user per device (`SecureTokenStorage.hasPinFor`, owner stored in `paypink_mpin_owner`). Logout and 401 call `clearSession()`, which keeps the MPIN; `clearVault()` (full wipe) is no longer used on logout. App start with an unexpired JWT shows the MPIN unlock screen. MPIN is local-only: with no refresh-token endpoint in auth-service (JWT lifetime 24h), an ended or expired session still needs a password sign-in. Transfers verify the MPIN once. Overview order mirrors the web: balance, quick actions, this month, accounts, recent activity, spending, promo, privacy note.
- Mobile/web feature parity (2026-10-09, branch `feature/mobile-web-feature-parity`): mobile mirrors web for the missed loan auto-debit banner (`BankAccount.hasMissedAutoDebit`), bank transfers (`POST /api/v1/auth/banking/external/transfers`, partner directory from `.../external/recipients`), notifications (derived from server transactions in `NotificationService`), and the transaction report (server PDF `GET /api/v1/auth/banking/reports/transactions.pdf`). There is no client cancel window on either app: transfers post immediately or return 202 PROCESSING, which the apps poll. Card freeze is server-controlled (admin-only `POST /api/v1/accounts/{id}/status`); customers cannot freeze from the app. Customer-initiated reversal does not exist; REVERSED/FAILED are server statuses only.
- Loan payments left and payoff (2026-10-09, branch `feature/loan-pending-payments`): web and mobile derive "payments left" (unpaid installments of total) and the payoff amount (penalty + unpaid installments) from `GET /api/v1/loans/{id}/schedule`. Payoff equals the maximum loan-service accepts and closes the loan. The web Pay dialog offers Next payment / Full balance / Another amount; mobile already offered Pay in Full.
- Mobile Loans hub (2026-10-09): the Overview has one **Loans** quick action (replacing Pay Loan / Apply Loan) that opens `LoansScreen`: loans with payments left, next payment, payoff, missed auto-debit alert, Pay and Details & schedule, plus a Borrow card with eligibility and Apply. It reloads via `AccountService.fetchLoansOrNull()` and never shows placeholder accounts. Note: `AccountService.fetchProfile()` still returns placeholder accounts when the server is unreachable (known issue).
- Mobile Brand & Validation Hardening (2026-10-09):
  - **MOB-102:** Integrated `PayPinkLogo.markOnly(size: 26)` into transfer receipt dialog.
  - **MOB-103:** Replaced payment network badges (Mastercard/Visa) with PayPink card emblem, configured 3 distinct card gradients (Checking: `#E11D48` to `#DB2777`, Savings: `#FB7185` to `#FDA4AF`, Loan: `#18181B` through `#4C0519` to `#881337`), and rendered custom white PayPink "P" SVG watermark at 60% opacity centered on cards.
  - **MOB-104:** Replaced static date strings with dynamic runtime dates across `main.dart` and `remittance_screen.dart`.
  - **MOB-105:** Integrated live backend endpoints for favorites (`GET`, `POST`, `DELETE` on `/api/v1/accounts/favorites`), client-side 12-digit numeric validation (`^\d{12}$`), and server-side lookup verification (`/api/v1/accounts/recipients/lookup`), blocking transfers and favorite additions until both validations pass.

=======
_Last Updated: 2026-10-09 (Savings recovery worker proxy fix validated; 28 account-service tests pass, [dom])_

---

### Admin UI simplification (2026-10-08)

- Admin presentation now uses concise navigation and single page headings, neutral slate surfaces, restrained rose accents and compact cards. Removed promotional banners and repeated technical copy; system configuration is expandable under System performance. Operational controls, validation guidance and simulation identification remain. Report cards stack on mobile.
- Local frontend change only; backend, API contracts and deployment unchanged. JavaScript syntax and 11 transaction-monitor tests pass; browser visual validation not performed.


## Active Initiative: T24 Core Banking & DDD Domain Refactoring

### Savings milestones (2026-10-09)

- Bank and offline preview share savings-milestones.js, showing PHP 10K, 100K, 500K, 1M, 2M and later million milestones. Progress targets the next unreached amount, and reached tiles reflect current personal savings. Larger totals show current/next million milestones without an unbounded list. Existing first-1K recognition and completed-goal count remain. Syntax, existing offline/mocked bank browser checks (including mobile overflow), and whitespace checks pass. Frontend-only change; rebuild the frontend image on the other machine to deploy. No migration or deployment performed.

### Savings rollout debugging (2026-10-09)

- The backend guide now explicitly requires Java compilation before Docker image rebuilding because service Dockerfiles copy prebuilt JARs. It documents the worker proxy fix and preserves existing pending intents; no new migration is needed.

- User reports applying schema-split, core and Savings migrations to Azure paypink and rebuilding/running on another machine. After correcting local/Azure configuration and rebuilding Java JARs, the live Savings UI works but a contribution remains pending. These deployment reports are user-provided, not independently verified from this machine.
- Supplied logs show SavingsWorker crashing on service.jdbc because SavingsService is a transactional Spring proxy. Worker now constructor-injects its own JdbcTemplate and SavingsCoreClient instead of accessing proxy fields. Service dependencies are private. All 28 account-service tests pass, including three new Spring/H2 tests for worker recovery, due schedules and circle completion through the transactional service proxy. Whitespace checks pass. Existing durable intents/retry keys are preserved. Fix still needs syncing, JAR rebuilding and container recreation on the user's other machine. No database changes or local stack startup performed.

### Savings backend implementation (2026-10-08)

- Final validation passes: 25 account-service tests (including streak/response regression), real bank shell browser checks with mocked authenticated HTTP, offline preview interactions/no-network checks, mobile layout and JavaScript syntax checks. Prior unchanged-module checks passed 38 core, 10 outbox and 18 notification tests (91 backend tests total across these runs). Live mobile screenshot reviewed. Whitespace checks pass.
- Logout clears customer page/dialog state; uncertain contributions retain an exact username-scoped retry record for safe recovery. Definitive creation validation errors retain the form, while uncertain creation refreshes the list before any manual new attempt. The guide gives manual SQL migration, Compose enable/build and two-customer acceptance steps. No full stack started, migration applied, real database changed or deployment performed.

- Bank Savings connects through authenticated savings-live.js, with goal creation, circle invitations/acceptance, own contributions/releases, schedules, Smart Split, privacy, target approval and activity. API overview supplies own customer ID and consecutive successful scheduled-attempt counts; badges reflect current progress rather than a permanent award ledger. Offline preview remains on savings.js. No fake balances or members are used in the bank entry point.

- Added unapplied scripts/migrate_savings.sql: five application tables for goals, schedules, circles, memberships and durable operation activity; two T24 tables for non-expiring reservations and their journal. Creating a goal starts at zero; actual reservations remain a separate operation.
- account-service exposes /api/v1/accounts/savings through the existing gateway route. Own active PHP SAVINGS/SAVINGS_ACCOUNT accounts are required for goals, circle creation and invitation acceptance. Invitations target active registered PayPink usernames; member consent controls individual amount visibility. Admin target proposals require the affected member's approval.
- t24-adapter internal savings APIs serialize on the same account lock as transfers, enforce core available funds and goal/release limits, run risk checks, and atomically persist held-balance changes, reservation journal and outbox. Smart Split supports multiple goals on one account. Request intents and exact core-command retries recover timeouts without duplicate reservations.
- Optional schedules use Asia/Manila business dates, skip repeated catch-up contributions, and recheck schedule settings under the customer lock. Circle completion emits once-only notifications to accepted members. outbox-publisher routes savings events to savings.events; notification-service uses reservation/completion copy. Core reservation journal supplies monetary audit history.
- Default OFF: Compose SAVINGS_ENABLED maps to account-service APP_SAVINGS_ENABLED. No migration applied, real database changed, Docker stack started or deployment performed. Public customer identity still relies on the existing gateway/private-network boundary. Rollout and API examples are in docs/savings-backend.md.
- Backend tests use H2/mocks and bounded-memory JVMs. SQL Server migration, real gateway/core/risk calls, schedule execution and Kafka delivery still require the documented manual acceptance test on the other machine. Frontend API wiring is complete.

### Savings UI prototype (2026-10-08)

- Standalone preview uses savings.js with four sample personal goals, three PinkCircles, three-step creation, Smart Split, add/release, editing and session activity. Preview allocations only adjust sample funds. Bank Savings uses savings-live.js with backend data; both entry points share savings.css.
- Compact overview cards use reduced padding, smaller total typography and badge medals. My Savings / PinkCircles navigation sits below the overview. Personal savings shows a dynamic goal count with in-progress/completed counts, matching the circle summary. Compact layout reviewed in a desktop screenshot; existing offline browser and mobile checks pass.
- Rose total card switches between personal savings (initial PHP 23,500) and own contributions across circles (initial PHP 10,700). Removed shared UI concept banner and spendable/reserved/account overview breakdown, plus standalone topbar Demo preview label.
- Restored collectible First PHP 1K, Emergency Era (PHP 100K emergency cushion), Consistency Queen (four consecutive scheduled contributions) and Million Club badges, earned/locked states and detail dialogs. Streaks and milestones remain separate. Existing interest tiers unchanged; proposed 5% is illustrative and inactive.
- Local frontend only; backend, schemas, mobile and deployment unchanged. Current offline browser checks pass for totals and their updates, badges, banner removal, creation validation, mobile layouts, keyboard dismissal, no preview API calls and real bank navigation/logout. Desktop screenshot reviewed; corrected new badge icon encoding.

The PayPink system is being refactored from a shared-database monolithic ledger into a Domain-Driven Design (DDD) banking architecture. In this design, Temenos T24 (simulated by `t24-adapter`) acts as the stateful System of Record (SoR) and authoritative double-entry book of record.

### Refactoring Roadmap (Phases 0 through 9)

|    Phase    | Title                                                 | Scope and Deliverables                                                                                                                                                                                   |         Status          |
| :---------: | ----------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :---------------------: |
| **Phase 0** | Perimeter Lockdown & Bypass Elimination               | Enforced ROLE_ADMIN on admin/stress routes in API Gateway; added defense-in-depth in account-service; deprecated direct transfer bypass in auth-service (410 GONE).                                      | Done (Commit `43f3025`) |
| **Phase 1** | Azure SQL Schema Split                                | Separated database into `t24` (core) and `app` (application) schemas with backward-compatible `dbo.*` synonyms. Qualified JPA `@Table(schema = "...")` across all microservices.                         | Done (Commit `a8d6213`) |
| **Phase 2** | Stateful T24 Core Banking Engine                      | Added `t24.LOCKED_AMOUNT` and `t24.POSTING_JOURNAL` tables. Implemented `T24HoldService` (atomic lock/release) and `T24PostingService` (double-entry posting journal).                                   | Done (Commit `3cd7129`) |
| **Phase 3** | Remittance Saga Hold Integration & Cutover            | Integrated `T24HoldClient` with circuit breaker into `RemittanceLedgerService`. Replaced local SQL balance lock updates with T24 Core hold API calls.                                                    | Done (Commit `3b3f9e2`) |
| **Phase 4** | Transaction History & CQRS Read-Model                 | Built CQRS read-store in `transaction-service`: `TransactionActivityService`, PDF statement generation (`TransactionStatementReportService`), and Operations Desk admin monitor. Added gateway routes.   | Done (Commit `801b044`) |
| **Phase 5** | Account Service Consolidation                         | Move `/me`, recipient lookup, recipients directory, and banking favorites/beneficiaries into `account-service`. Route live balance inquiries to T24 Core.                                                | Done (Commit `9982ba3`) |
| **Phase 6** | Auth Slimming & Loan Service Alignment                | Slim `auth-service` perimeter via gateway route cutover for recipients, favorites, and admin monitor; loan disbursements & repayments routed via T24 Core posting saga.                                  | Done (Commit `9d29e3e`) |
| **Phase 7** | EOD Service Alignment                                 | Align Interest EOD and Loan EOD to use qualified `t24.*` and `app.*` schemas with T24 Core EOD job logs (`t24.EOD_JOB_RUN`) and posting events.                                                          | Done (Commit `2f9e31e`) |
| **Phase 8** | Events, Audit & Reconciliation Re-point               | Verify and relate outbox events with T24 Core double-entry posting journals across `audit-service`, `reconciliation-service`, `notification-service`, and `analytics-service`.                           | Done (Commit `9590d5f`) |
| **Phase 9** | Frontend Polish, Synonym Cleanup & Final Verification | Final end-to-end verification across Web SPA and frozen Mobile contracts; created synonym retirement script `scripts/retire_phase9_synonyms.sql`; verified 100% test pass rate across all microservices. |          Done           |

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

| Service                  | Host Port | Responsibility & Primary Domain                                                                                  | Database Schema                                                |
| ------------------------ | :-------: | ---------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------- |
| `api-gateway`            |   8080    | Sole external entry point. JWT validation, role checking, rate limiting, and reverse proxy.                      | Redis (token bucket)                                           |
| `auth-service`           |   8081    | Authentication, user registration, JWT generation, password hashing.                                             | `app.CUSTOMER`                                                 |
| `account-service`        |   8082    | Customer accounts, balance inquiry, account lifecycle status.                                                    | `t24.ACCOUNT`, `app.CUSTOMER`                                  |
| `transaction-service`    |   8083    | Remittance Orchestrator (4-step saga), CQRS Activity & PDF statements, Admin Monitor, and Interest EOD.          | `app.REMITTANCE`, `t24.LEDGER_TRANSACTION`, `app.OUTBOX_EVENT` |
| `t24-adapter`            |   8090    | Core Banking Engine (T24). Authoritative account balances, locked amounts (holds), double-entry posting journal. | `t24.ACCOUNT`, `t24.LOCKED_AMOUNT`, `t24.POSTING_JOURNAL`      |
| `risk-engine`            |   8000    | Python 3.11 FastAPI. Two-layer fraud scoring: Rules engine + Isolation Forest ML.                                | Stateless                                                      |
| `loan-service`           |   8091    | Loan product applications, credit evaluation, and repayments.                                                    | `t24.LOAN`, `t24.LOAN_SCHEDULE`, `t24.LOAN_REPAYMENT`          |
| `audit-service`          |   8085    | Kafka consumer logging immutable risk decision audit records.                                                    | PostgreSQL (`RISK_DECISION`)                                   |
| `notification-service`   |   8084    | Kafka consumer for SMS/Email/Push transaction notification dispatch.                                             | PostgreSQL                                                     |
| `reconciliation-service` |   8086    | Discrepancy detector between application outbox and ledger transactions.                                         | Azure SQL + PostgreSQL                                         |
| `outbox-publisher`       |   8087    | Poller worker that pushes `OUTBOX_EVENT` rows onto Kafka topics.                                                 | Azure SQL + Kafka                                              |
| `analytics-service`      |   8088    | Real-time transaction volume and velocity metrics streamer.                                                      | Kafka (in-memory)                                              |

---

## Core Rules and Architectural Boundaries

1. **Mobile Scope (`mobile/`):** The mobile application directory is 100% frozen and untouched. All 6 mobile API contracts must remain strictly backward-compatible. Exception: the team has opened `mobile/` for the UI and web-alignment backlog in `docs/MOBILE_BACKLOG.md` (MOB-101…MOB-506, split across 5 members). Member 2's tasks (MOB-201…206) are done: the app loads its profile from `GET /api/v1/accounts/me`, loans from `GET /api/v1/loans`, repays via `POST /api/v1/loans/{loanId}/repayments`, and shows errors instead of placeholder balances. It no longer calls `/api/v1/loans/pay`.
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
