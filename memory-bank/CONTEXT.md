# PayPink 2.0 — Project Context

_Last updated: 2026-10-02_

## What it is
Mobile P2P remittance app (domestic, PHP) with real-time fraud screening.
Built on our Capstone 1 ledger.

## How a transfer works
1. App → API Gateway (JWT check, rate limit)
2. Orchestrator checks for duplicates (Redis)
3. Risk Engine scores it (Python, ≤ 200 ms). Score > 0.85 → rejected
4. Account Service validates the account
5. Ledger + OUTBOX saved in one transaction (Azure SQL)
6. Outbox → Kafka → audit, notifications, reconciliation, analytics

## Stack
Spring Boot · Spring Cloud Gateway · Python FastAPI · Azure SQL (local: SQL Server 2022) · PostgreSQL · Redis · Kafka · Docker Compose · Grafana

## Decisions
- Risk check runs before the DB transaction (no locks held during the call)
- Audit goes to PostgreSQL through outbox + Kafka, not a dual commit
- SQL Server locking: `WITH (UPDLOCK, ROWLOCK)` (no `SELECT … FOR UPDATE`)
- Still to confirm with trainer: T24 before or after commit; domestic only?

## Current focus
Day 1: API contract + Jira board
