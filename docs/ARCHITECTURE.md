# System Architecture & Technical Specifications

## Capstone FSE: Core Retail Ledger & Balance Mutation Engine

### 1. Executive Summary & Core Topology
The **PayPink Core Retail Ledger & Balance Mutation Engine** is a high-throughput, banking-grade financial ledger platform designed for high-concurrency balance mutations ($\ge 800\text{ TPS}$ with $\le 50\text{ms}$ P95 latency), distributed in-memory validation, and asynchronous audit trail propagation.

---

### 2. Design Decision: Transactional Outbox over Dual-Write

#### The Dual-Write Fallacy
In distributed architectures, attempting to write synchronously to two heterogeneous datastores (e.g., Oracle XE master state and PostgreSQL immutable audit) within the same application request introduces severe consistency hazards:
1. **Lack of Distributed Atomicity**: There is no atomic two-phase commit (2PC) across non-XA heterogeneous datasources without prohibitive latency penalties.
2. **Partial Failures**: If Oracle commits successfully but PostgreSQL fails (or network partitions occur), the primary ledger balance updates while the audit trail is permanently lost. Conversely, if PostgreSQL commits first and Oracle rolls back due to a constraint violation, phantom audit records exist.

#### The Transactional Outbox Solution
PayPink eliminates dual-write hazards via the **Transactional Outbox Pattern**:
* **Single Local Oracle ACID Boundary**: The mutation engine modifies the source/target account balances, records the financial `TRANSACTION`, creates a synchronous security `AUDIT_LOG`, and inserts an `OUTBOX_EVENT` (`status = 'PENDING'`) **within a single Oracle commit**.
* **At-Least-Once Delivery**: The `OutboxPublisherService` claims pending outbox rows using `SELECT ... FOR UPDATE SKIP LOCKED` and streams them to Apache Kafka (`transaction-events`).
* **Idempotent Consumers**: Downstream PostgreSQL consumers enforce idempotency via unique constraints (`uq_audit_tx_account` on `(transaction_id, account_id)` and `reference_no` on notifications), safely discarding duplicates on replay.
* **Lagged Reconciliation Sweep**: An asynchronous `@Scheduled` 15-minute reconciliation engine compares rolling time-window transactions (`now - 20m` to `now - 2m`) between Oracle and PostgreSQL to detect and alert on any cross-store data drift.

---

### 3. System Architecture Diagram

```mermaid
flowchart TD
    subgraph ClientLayer["1. Client & Load Generation Layer"]
        SPA["PayPink Banking SPA (HTML5 / Vanilla CSS / JS)"]
        JMeter["Apache JMeter (800+ TPS Load Generator)"]
    end

    subgraph EdgeLayer["2. Gateway & Edge Layer"]
        Gateway["Spring Cloud API Gateway (Port 8080)"]
    end

    subgraph CoreLayer["3. Core Ledger Service (ledger-core)"]
        AuthService["Auth & Session Service"]
        IdempInter["Idempotency HandlerInterceptor"]
        MutationEngine["Ledger Mutation Engine (@Lock PESSIMISTIC_WRITE)"]
        OutboxPoller["Outbox Publisher (SKIP LOCKED Poller)"]
    end

    subgraph EventLayer["4. Event Streaming & In-Memory Layer"]
        Redis[("Redis 7 (Idempotency & Token Matrix ≤5ms)")]
        Kafka{{"Apache Kafka (transaction-events, 6 partitions + DLT)"}}
    end

    subgraph ConsumerLayer["5. Event Consumers Service (event-consumers)"]
        AuditConsumer["Audit Consumer (@KafkaListener: ledger-audit-group)"]
        NotifConsumer["Notification Consumer (@KafkaListener: ledger-notification-group)"]
        ReconService["Reconciliation Engine (15-min Lagged Window Sweep)"]
    end

    subgraph StorageLayer["6. Dual Datastore Storage Layer"]
        OracleXE[("Oracle XE 21c (Master OLTP & Security AUDIT_LOG)")]
        Postgres[("PostgreSQL 15+ (LEDGER_MUTATION_AUDIT & RECONCILIATION_LOG)")]
    end

    subgraph ObservabilityLayer["7. Observability & Telemetry"]
        OTel["OTel Collector (Ports 4317/4318)"]
        Tempo["Grafana Tempo (Distributed Tracing)"]
        Prometheus["Prometheus (Port 9090)"]
        Grafana["Grafana Dashboards (Port 3000)"]
    end

    ClientLayer --> Gateway
    Gateway --> CoreLayer
    CoreLayer --> Redis
    CoreLayer --> OracleXE
    OutboxPoller --> Kafka
    Kafka --> ConsumerLayer
    ConsumerLayer --> Postgres
    ConsumerLayer -.->|Read-only Recon Query| OracleXE
    CoreLayer & ConsumerLayer & Gateway --> OTel
    OTel --> Tempo
    OTel --> Prometheus
    Prometheus --> Grafana
```

---

### 4. Database Topology (Dual-Store Strategy)

| Database Store | Tables Hosted | Primary Purpose |
| :--- | :--- | :--- |
| **Oracle XE 21c** | `CUSTOMER`, `ACCOUNT`, `TRANSACTION`, `OUTBOX_EVENT`, `AUDIT_LOG` | **Master OLTP & Security Engine**: Dedicated to low-latency balance mutations with `@Lock(LockModeType.PESSIMISTIC_WRITE)`, ordered dual-account locking (`min(src,tgt)` then `max(src,tgt)`), and synchronous security audit records. |
| **PostgreSQL 15+** | `LEDGER_MUTATION_AUDIT`, `RECONCILIATION_LOG`, `NOTIFICATION` | **Immutable Audit & Downstream Store**: Append-only double-entry financial audit entries with `uq_audit_tx_account`, cross-database drift logs, and customer notification dispatch history. |
| **Redis** | `idempotency:{key}`, `token:{jti}` | In-memory distributed token validation and request deduplication matrix achieving **$\le 5\text{ms}$** operational turnaround. |

---

### 5. 11-Step Event Lifecycle

1. **Client Submits Mutation**: Client submits transfer payload with JWT Bearer and `Idempotency-Key` header.
2. **Idempotency Interceptor**: Intercepts request in `<5ms`. If cached, returns completed JSON (`Idempotent-Replay: true`); if in-flight, returns `409 Conflict`; if new, reserves key (`IN_PROGRESS`).
3. **JSR-380 Validation**: Validates `@Digits(integer=14, fraction=4)` and `@Positive`. Violations return RFC-7807 Problem Details.
4. **Ordered Pessimistic Locking**: Locks accounts in ascending numerical ID order (`Math.min(src, tgt)` then `Math.max(src, tgt)`) via `SELECT ... FOR UPDATE` preventing concurrency race conditions and deadlocks.
5. **Oracle Local Commit**: Atomically updates `ACCOUNT` balance, inserts `TRANSACTION`, writes `AUDIT_LOG`, and writes `OUTBOX_EVENT` with multi-leg `entries[]`.
6. **Transaction Synchronization**: On successful commit, `afterCommit()` marks idempotency key `COMPLETED` in Redis with 24-hour TTL; on rollback, `afterCompletion()` deletes key so client can retry.
7. **Outbox Publisher Sweep**: Background poller claims pending outbox events using `SELECT ... FOR UPDATE SKIP LOCKED`.
8. **Kafka Event Streaming**: Publishes message to `transaction-events` (partition key = `accountId`). Retries with exponential backoff on failure; routes poison pills to `transaction-events.DLT` after 5 attempts.
9. **Audit Consumer**: `ledger-audit-group` writes append-only legs to PostgreSQL `LEDGER_MUTATION_AUDIT`, idempotently skipping duplicate legs.
10. **Notification Consumer**: `ledger-notification-group` dispatches customer alert first, then persists delivery status (`SENT`/`FAILED`/`RETRY`) in PostgreSQL `NOTIFICATION`.
11. **Reconciliation Sweep**: `@Scheduled(cron = "0 */15 * * * *")` sweep checks lagged window (`now-20m` to `now-2m`), comparing Oracle vs PostgreSQL legs and logging any drift into `RECONCILIATION_LOG`.

---

### 6. Non-Functional Performance SLAs (NFRs)
- **Throughput**: $\ge 800\text{ TPS}$ peak concurrent velocity.
- **Latency SLA**: P95 $\le 50\text{ms}$ under peak concurrent execution.
- **Redis Turnaround**: $\le 5\text{ms}$ per token/idempotency check.
- **Connection Pool Isolation**: Oracle HikariCP (`maximum-pool-size=30`, `minimum-idle=5`), Postgres HikariCP (`maximum-pool-size=10`, `minimum-idle=2`).
