# PayPink 2.0 — Project Context

_Project Team: **Team 4** (Collaborative Capstone; no single owner)_  
_Active Working Branch: `refactor/domain-t24-core`_  
_Last Updated: 2026-10-07 (Phase 4 Completed & Pushed to Remote by [dom])_  

---

## 🚀 Active Initiative: T24 Core Banking & DDD Domain Refactoring

We are refactoring PayPink from a shared-database monolithic ledger into a **Domain-Driven Design (DDD) banking architecture** where Temenos T24 (`t24-adapter`) is the stateful **System of Record (SoR)** and authoritative double-entry book of record.

### Current Refactor Roadmap & Progress

| Phase | Title | Description | Status |
|:---:|---|---|:---:|
| **Phase 0** | **Perimeter Lockdown & Bypass Elimination** | Enforced `ROLE_ADMIN` on admin routes in API Gateway; added defense-in-depth in `account-service`; deprecated unscored transfers (`410 GONE`) in `auth-service`. | ✅ **DONE** (Commit `43f3025`) |
| **Phase 1** | **Azure SQL Schema Split** | Separated shared DB into `t24` (core) and `app` (application) schemas with backward-compatible `dbo.*` synonyms. Qualified all JPA `@Table(schema = "...")`. | ✅ **DONE** (Commit `a8d6213`) |
| **Phase 2** | **Stateful T24 Core Banking Engine** | Added `t24.LOCKED_AMOUNT` and `t24.POSTING_JOURNAL` tables. Built `T24HoldService` (lock/release funds) and `T24PostingService` (double-entry posting with immutable journal). | ✅ **DONE** (Commit `3cd7129`) |
| **Phase 3** | **Remittance Saga Hold Integration & Cutover** | Built `T24HoldClient` with circuit breaker in `transaction-service`. Refactored `RemittanceLedgerService` to delegate hold placement and releases to T24 Core. | ✅ **DONE** (Commit `3b3f9e2`) |
| **Phase 4** | **Transaction History & CQRS Read-Model** | Ported customer transaction feeds, statement PDF generation (`TransactionStatementReportService`), and Operations Desk admin monitor into `transaction-service`. Added gateway routing. | ✅ **DONE** (Commit `801b044`) |
| **Phase 5** | **Account Service Consolidation** | Move `/me`, recipient lookup, recipients directory, and banking favorites/beneficiaries into `account-service`. Route live balance inquiries to T24 Core. | ⏳ **UP NEXT** |
| **Phase 6** | **Auth Slimming & Loan Service Alignment** | Slim `auth-service` down to login/register/JWT only. Move loan disbursements/repayments to post directly into T24 Core. | 📋 **Pending** |
| **Phase 7** | **EOD Service Alignment** | Align Interest EOD and Loan EOD to post settlements via T24 Core posting API. | 📋 **Pending** |

---

## 🏛️ Architecture Overview

```
                      ┌─────────────────────────────────────────┐
                      │    API Gateway (:8080) — Public Port    │
                      │  JWT Validation, Rate Limiting, RBAC    │
                      └────────────────────┬────────────────────┘
                                           │
         ┌──────────────────┬──────────────┴─────┬──────────────────┐
         ▼                  ▼                    ▼                  ▼
   auth-service      account-service     transaction-service    loan-service
      (:8081)            (:8082)               (:8083)             (:8091)
    Login/Tokens     Profiles/Balances      Saga / Ledger /      Loan Engine
         │                  │               CQRS / Interest          │
         │                  │                    │                   │
         │                  └──────────┐         │                   │
         │                             ▼         ▼                   │
         │                         t24-adapter (:8090) ◄─────────────┘
         │                   Stateful T24 Core Banking Engine
         │                   Holds, Postings, Double-Entry Ledger
         │                             │
         ▼                             ▼
   ┌───────────┐                 ┌───────────┐
   │ app.* DB  │                 │ t24.* DB  │
   │ (Azure)   │                 │ (Azure)   │
   └───────────┘                 └───────────┘
```

---

## 📦 Active Microservices Directory

| Service | Host Port | Responsibility & Primary Domain | Database Schema |
|---|:---:|---|---|
| **`api-gateway`** | **8080** | **Sole external entry point.** JWT auth filter, role enforcement, rate limiting, and reverse proxy. | Redis (token bucket) |
| **`auth-service`** | 8081 | Authentication, user registration, JWT generation, password hashing. | `app.CUSTOMER` |
| **`account-service`** | 8082 | Customer accounts, balance inquiry, account lifecycle status. | `t24.ACCOUNT`, `app.CUSTOMER` |
| **`transaction-service`** | 8083 | Remittance Orchestrator (4-step saga), CQRS Activity & PDF statements, Admin Monitor, and Interest EOD. | `app.REMITTANCE`, `t24.LEDGER_TRANSACTION`, `app.OUTBOX_EVENT` |
| **`t24-adapter`** | 8090 | **Core Banking Engine (T24).** Authoritative account balances, locked amounts (holds), double-entry posting journal. | `t24.ACCOUNT`, `t24.LOCKED_AMOUNT`, `t24.POSTING_JOURNAL` |
| **`risk-engine`** | 8000 | Python 3.11 FastAPI. Two-layer fraud scoring: Rules engine + Isolation Forest ML. | Stateless |
| **`loan-service`** | 8091 | Loan product applications, credit evaluation, and repayments. | `t24.LOAN`, `t24.LOAN_SCHEDULE`, `t24.LOAN_REPAYMENT` |
| **`audit-service`** | 8085 | Kafka consumer logging immutable risk decision audit records. | PostgreSQL (`RISK_DECISION`) |
| **`notification-service`** | 8084 | Kafka consumer for SMS/Email/Push transaction notification dispatch. | PostgreSQL |
| **`reconciliation-service`**| 8086 | Discrepancy detector between application outbox and ledger transactions. | Azure SQL + PostgreSQL |
| **`outbox-publisher`** | 8087 | Poller worker that pushes `OUTBOX_EVENT` rows onto Kafka topics. | Azure SQL + Kafka |
| **`analytics-service`** | 8088 | Real-time transaction volume and velocity metrics streamer. | Kafka (in-memory) |

---

## 🔒 Non-Negotiable Core Rules & Boundaries

1. **Mobile Scope (`mobile/`):** The Flutter/PWA mobile app directory is **100% frozen and untouched**. All mobile API contracts must remain strictly backward-compatible.
2. **Money Never Moves Without a Risk Score:** Risk score > 0.85 = REJECT before any hold or balance is touched.
3. **Core Owns Money & Balances:** `transaction-service` no longer modifies balances directly; holds and postings are executed by `t24-adapter`.
4. **Display Balances are Display-Only:** Redis-cached balances must never be used to authorize a transfer.
5. **Team Attribution:** Project ownership is **Team 4**. Commits and changelogs attributed to `[dom]`.
6. **Zero Regression:** Every change must compile cleanly and pass unit tests before committing.

---

## 🛠️ Infrastructure & Deployment Notes

- **Azure SQL Server 2022:** Local container `azure-sql-master` or hosted Azure SQL `paypink`. 13 tables partitioned across `t24` and `app` schemas with `dbo.*` synonyms.
- **PostgreSQL 15:** Local container `postgres-immutable-audit` hosting `ledger_audit_db` for immutable risk audit records and Interest EOD snapshots.
- **Redis 7:** Container `redis-idempotency-matrix` for idempotency locks and rate limits.
- **Kafka:** Container `kafka` for asynchronous transaction events.
- **Azure Host VM:** `vm-paypink` (`20.69.157.88`) in `RG-PAYPINK-WESTUS2`. Auto-shutdown at 11:00 UTC (7:00 PM PHT).
