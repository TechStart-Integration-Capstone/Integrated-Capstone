# PayPink 2.0 — Project Context

_Project Team: Team 4 (Collaborative Capstone; no single owner)_  
_Active Working Branch: refactor/domain-t24-core_  
_Last Updated: 2026-10-08 (Phase 4 Completed & Pushed to Remote by [dom])_  

---

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
| **Phase 5** | Account Service Consolidation | Move `/me`, recipient lookup, recipients directory, and banking favorites/beneficiaries into `account-service`. Route live balance inquiries to T24 Core. | Up Next |
| **Phase 6** | Auth Slimming & Loan Service Alignment | Slim `auth-service` down to login/register/JWT only. Move loan disbursements/repayments to post directly into T24 Core. | Pending |
| **Phase 7** | EOD Service Alignment | Align Interest EOD and Loan EOD to post settlements via T24 Core posting API. | Pending |
| **Phase 8** | Events, Audit & Reconciliation Re-point | Relate outbox events with T24 core journal feed; update reconciliation and notification consumers. | Pending |
| **Phase 9** | Frontend Polish, Synonym Cleanup & Final Verification | Final end-to-end verification across Web SPA and frozen Mobile contracts; retire `dbo` synonyms. | Pending |

---

## Architecture Overview

```
                      +-----------------------------------------+
                      |    API Gateway (:8080) — Public Port    |
                      |  JWT Validation, Rate Limiting, RBAC    |
                      +--------------------+--------------------+
                                           |
         +------------------+--------------+-----+------------------+
         |                  |                    |                  |
         v                  v                    v                  v
   auth-service      account-service     transaction-service    loan-service
      (:8081)            (:8082)               (:8083)             (:8091)
    Login/Tokens     Profiles/Balances      Saga / Ledger /      Loan Engine
         |                  |               CQRS / Interest          |
         |                  |                    |                   |
         |                  +---------->         |                   |
         |                             v         v                   |
         |                         t24-adapter (:8090) <-------------+
         |                   Stateful T24 Core Banking Engine
         |                   Holds, Postings, Double-Entry Ledger
         |                             |
         v                             v
   +-----------+                 +-----------+
   | app.* DB  |                 | t24.* DB  |
   | (Azure)   |                 | (Azure)   |
   +-----------+                 +-----------+
```

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

- **Azure SQL Server 2022:** Local container `azure-sql-master` or hosted Azure SQL `paypink`. 13 tables partitioned across `t24` and `app` schemas with `dbo.*` synonyms.
- **PostgreSQL 15:** Local container `postgres-immutable-audit` hosting `ledger_audit_db` for immutable risk audit records and Interest EOD snapshots.
- **Redis 7:** Container `redis-idempotency-matrix` for idempotency locks and rate limits.
- **Kafka:** Container `kafka` for asynchronous transaction events.
- **Azure Host VM:** `vm-paypink` (`20.69.157.88`) in `RG-PAYPINK-WESTUS2`. Auto-shutdown at 11:00 UTC (7:00 PM PHT).
