# PayPink 2.0 — Project Context

_Project Team: Team 4 (Collaborative Capstone; no single owner)_  
_Active Working Branch: main_  
_Last Updated: 2026-10-08 (Savings bank UI connected to backend; migration unapplied, [dom])_

---

### Admin UI simplification (2026-10-08)

- Admin presentation now uses concise navigation and single page headings, neutral slate surfaces, restrained rose accents and compact cards. Removed promotional banners and repeated technical copy; system configuration is expandable under System performance. Operational controls, validation guidance and simulation identification remain. Report cards stack on mobile.
- Local frontend change only; backend, API contracts and deployment unchanged. JavaScript syntax and 11 transaction-monitor tests pass; browser visual validation not performed.

## Active Initiative: T24 Core Banking & DDD Domain Refactoring

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
