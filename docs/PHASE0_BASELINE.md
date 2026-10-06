# Phase 0 — Freeze & Setup Baseline
## PayPink 2.0 — Capstone 2 Migration

**Date:** 2026-10-03
**Branch:** `feature/capstone2-paypink-2.0-dom`
**Freeze Tag:** `capstone1-freeze` → commit `1e51aea`
**Author:** dom

---

## 1. Git Snapshot

| Item | Value |
|---|---|
| Freeze tag | `capstone1-freeze` |
| Tagged commit | `1e51aea` — *Merge pull request #1 from TechStart-Integration-Capstone/gillianne/feature* |
| Working branch | `feature/capstone2-paypink-2.0-dom` |
| Base branch | `main` |

**To restore Capstone 1 at any time:**
```powershell
git checkout capstone1-freeze
```

### Pre-existing unstaged changes (Capstone 1 leftovers — NOT our changes)
These existed before Phase 0 and are not related to Capstone 2 work:
- `deleted:  docker/grafana/provisioning/datasources/datasources.yml`
- `modified: microservices/transaction-service/src/main/java/com/bank/transaction/controller/StressTestController.java`

---

## 2. Capstone 1 Health Baseline

**Captured:** 2026-10-03 — all 22 containers confirmed green before any migration work.

### Container Status
All 22 Docker containers running (`docker compose ps`):

| Container | Image | Port | Status |
|---|---|---|---|
| oracle-xe-master | gvenzl/oracle-xe:21-slim | 1522→1521 | Up (healthy) |
| postgres-immutable-audit | postgres:15-alpine | 5434→5432 | Up (healthy) |
| redis-idempotency-matrix | redis:7-alpine | 6380→6379 | Up (healthy) |
| kafka-event-bus | confluentinc/cp-kafka:7.5.0 | 9092 | Up (healthy) |
| zookeeper | confluentinc/cp-zookeeper:7.5.0 | 2181 | Up |
| api-gateway | docker-api-gateway | 8080 | Up |
| auth-service | docker-auth-service | 8081 | Up |
| account-service | docker-account-service | 8082 | Up |
| transaction-service | docker-transaction-service | 8083 | Up |
| notification-service | docker-notification-service | 8084 | Up |
| audit-service | docker-audit-service | 8085 | Up |
| reconciliation-service | docker-reconciliation-service | 8086 | Up |
| outbox-publisher | docker-outbox-publisher | 8087 | Up |
| analytics-service | docker-analytics-service | 8088 | Up |
| frontend-spa | nginx | 3001→80 | Up |
| grafana-dashboards | grafana/grafana:10.1.0 | 3000 | Up |
| prometheus-telemetry | prom/prometheus:v2.47.0 | 9090 | Up |
| loki-logs | grafana/loki:2.9.0 | 3100 | Up |
| tempo-traces | grafana/tempo:2.2.0 | 3200 | Up |
| otel-collector | otel/opentelemetry-collector-contrib:0.87.0 | 4317,4318 | Up |
| jaeger-tracing | jaegertracing/all-in-one:1.50 | 16686 | Up |
| redis-exporter | oliver006/redis_exporter:latest | 9121 | Up |

### Microservice Health (`/actuator/health`)
All 9 Java services returned `STATUS: UP`:

| Service | Port | Status |
|---|---|---|
| api-gateway | 8080 | UP |
| auth-service | 8081 | UP |
| account-service | 8082 | UP |
| transaction-service | 8083 | UP |
| notification-service | 8084 | UP |
| audit-service | 8085 | UP |
| reconciliation-service | 8086 | UP |
| outbox-publisher | 8087 | UP |
| analytics-service | 8088 | UP |

---

## 3. Current Dependency Versions (All Services)

| Dependency | Version | Used By |
|---|---|---|
| Spring Boot Parent | **3.2.3** | All 9 services |
| Spring Cloud | **2023.0.0** | api-gateway only |
| Java | **17** | All 9 services |
| `ojdbc8` (Oracle JDBC) | **19.3.0.0** | account, auth, transaction, outbox-publisher, reconciliation |
| `jjwt-api/impl/jackson` | **0.11.5** | account, auth, transaction, api-gateway |
| `spring-kafka` | managed by Boot 3.2.3 | transaction, outbox-publisher, reconciliation, analytics, audit, notification |
| `postgresql` (driver) | **42.7.2** | reconciliation-service |
| `micrometer-registry-prometheus` | managed by Boot | All 9 services |
| `lettuce` (Redis client) | **6.3.2.RELEASE** (pinned) | transaction-service |
| `pdfbox` | **3.0.8** | auth-service |
| `h2` | managed by Boot | auth-service (test scope only) |

---

## 4. Phase 1 Change List — Oracle → Azure SQL

> Every file that must change during Phase 1. Nothing else should be touched.

### 4a. `pom.xml` — Remove ojdbc8, Add mssql-jdbc (5 files)

| Service | File | Current | Change To |
|---|---|---|---|
| account-service | `pom.xml` line 23 | `ojdbc8:19.3.0.0` | `mssql-jdbc:12.4.2.jre17` |
| auth-service | `pom.xml` line 51 | `ojdbc8` (version from BOM) | `mssql-jdbc:12.4.2.jre17` |
| transaction-service | `pom.xml` line 30 | `ojdbc8:19.3.0.0` | `mssql-jdbc:12.4.2.jre17` |
| outbox-publisher | `pom.xml` line 22 | `ojdbc8:19.3.0.0` | `mssql-jdbc:12.4.2.jre17` |
| reconciliation-service | `pom.xml` line 22 | `ojdbc8:19.3.0.0` | `mssql-jdbc:12.4.2.jre17` |

### 4b. `application.yml` — Driver + Dialect + URL (5 files)

| Service | Lines | Change |
|---|---|---|
| account-service | 8–9, 16 | `oracle:thin` → `sqlserver`, `OracleDriver` → `SQLServerDriver`, `OracleDialect` → `SQLServerDialect` |
| auth-service | 8–9, 16 | Same 3 changes |
| transaction-service | 8–9, 17 | Same 3 changes |
| outbox-publisher | 11–12, 20 | Same 3 changes |
| reconciliation-service | 14–15 (oracle block) | Same 3 changes — postgres block stays as-is |

### 4c. Java Source — `FOR UPDATE` lock syntax (5 Java files)

| File | Line(s) | Current Syntax | T-SQL Replacement |
|---|---|---|---|
| `auth-service/.../BankingAccountNumberMigration.java` | 16 | `... FROM ACCOUNT ... FOR UPDATE` | `... FROM ACCOUNT WITH (UPDLOCK, ROWLOCK)` |
| `auth-service/.../BankingRecipientService.java` | 59 | `... FROM CUSTOMER WHERE customer_id = ? FOR UPDATE` | `... FROM CUSTOMER WITH (UPDLOCK, ROWLOCK) WHERE customer_id = ?` |
| `auth-service/.../BankingTransferService.java` | 76 | `FROM ACCOUNT WHERE account_id = ? FOR UPDATE` | `FROM ACCOUNT WITH (UPDLOCK, ROWLOCK) WHERE account_id = ?` |
| `auth-service/.../ExternalTransferService.java` | 54, 99 | `FROM ACCOUNT WHERE account_id=? FOR UPDATE` | `FROM ACCOUNT WITH (UPDLOCK, ROWLOCK) WHERE account_id=?` |
| `transaction-service/.../AccountRepository.java` | 14–16 | `@Lock(PESSIMISTIC_WRITE)` + JPQL | JPA `@Lock(PESSIMISTIC_WRITE)` works with SQL Server — **no change needed here**, Hibernate handles the hint |

> **Note on AccountRepository.java:** `@Lock(LockModeType.PESSIMISTIC_WRITE)` is a JPA abstraction — Hibernate translates it to `WITH (UPDLOCK, ROWLOCK)` for SQL Server automatically when the dialect is set correctly. Only the raw JDBC `FOR UPDATE` strings in auth-service need manual rewriting.

### 4d. Java Source — Hardcoded dialect string (1 file)

| File | Line | Change |
|---|---|---|
| `reconciliation-service/.../OracleDataSourceConfig.java` | 55 | `"org.hibernate.dialect.OracleDialect"` → `"org.hibernate.dialect.SQLServerDialect"` |

### 4e. Docker Compose — Environment variables (5 service blocks)

| Service | Variable | Change |
|---|---|---|
| auth-service | `SPRING_DATASOURCE_URL` | `jdbc:oracle:thin:@oracle-xe:1521/XEPDB1` → `jdbc:sqlserver://azure-sql:1433;databaseName=paypink` |
| account-service | `SPRING_DATASOURCE_URL` | Same swap |
| transaction-service | `SPRING_DATASOURCE_URL` | Same swap |
| outbox-publisher | `SPRING_DATASOURCE_URL` | Same swap |
| reconciliation-service | `ORACLE_DATASOURCE_URL` | Same swap |
| docker-compose.yml | `oracle-xe` service block | Replace with `azure-sql` container (mcr.microsoft.com/mssql/server:2022-latest) |

### 4f. DDL Schema — Oracle → T-SQL rewrite (1 file)

**Source:** `backend/ledger-core/src/main/resources/schema-oracle.sql`
**Target (new file):** `microservices/transaction-service/src/main/resources/schema-azuresql.sql`

Key Oracle → T-SQL translations needed:

| Oracle | T-SQL Equivalent |
|---|---|
| `NUMBER(19) GENERATED ALWAYS AS IDENTITY` | `BIGINT IDENTITY(1,1)` |
| `NUMBER(18,4)` | `DECIMAL(18,4)` |
| `VARCHAR2(n)` | `NVARCHAR(n)` |
| `TIMESTAMP DEFAULT CURRENT_TIMESTAMP` | `DATETIME2 DEFAULT GETUTCDATE()` |
| `CLOB` | `NVARCHAR(MAX)` |
| `BEGIN EXECUTE IMMEDIATE 'DROP TABLE...' EXCEPTION WHEN OTHERS THEN NULL; END; /` | `IF OBJECT_ID('dbo.TABLE') IS NOT NULL DROP TABLE dbo.TABLE;` |
| `CASCADE CONSTRAINTS` | (not needed — drop in reverse dependency order) |
| `CONSTRAINT chk_account_balance_positive CHECK (current_balance >= 0.0000)` | Identical — CHECK constraints work the same |

---

## 5. Schema Locations (Confirmed)

| Schema | Actual Location |
|---|---|
| Oracle DDL | `backend/ledger-core/src/main/resources/schema-oracle.sql` |
| PostgreSQL DDL (audit) | `backend/event-consumers/src/main/resources/schema-postgres.sql` |
| PostgreSQL DDL (notifications) | `backend/notification-service/src/main/resources/schema-postgres.sql` |

> **Important:** `backend/src/main/resources/schema-oracle.sql` is a **directory**, not a file — it was created erroneously. The real schema files are in the sub-modules above.

---

## 6. Services That Do NOT Touch Oracle (No Changes in Phase 1)

These services are completely unaffected by the Oracle → Azure SQL migration:

| Service | Database | Action |
|---|---|---|
| api-gateway | Redis only | No changes |
| analytics-service | Kafka only (in-memory) | No changes |
| audit-service | PostgreSQL + Kafka | No changes |
| notification-service | PostgreSQL + Kafka | No changes |
| Observability stack | N/A | No changes |

---

## 7. Execution Order for Phase 1 (Service-by-Service)

Do NOT migrate all 5 services at once. Order of least to most risk:

1. **account-service** — simplest schema (CUSTOMER + ACCOUNT reads), no lock writes
2. **auth-service** — CUSTOMER write + banking SQL with FOR UPDATE rewrites
3. **outbox-publisher** — OUTBOX_EVENT only, no balance logic
4. **transaction-service** — balance mutations + pessimistic lock (highest write risk)
5. **reconciliation-service** — dual datasource config, most complex, migrate last

**Verify each service fully before moving to the next.**

---

## 8. Precision Validation Test (Must Pass Before Phase 1 is Done)

Before declaring Phase 1 complete, run this specific test:

> Deposit exactly **PHP 1,234,567,890.1234** into a test account.
> Read it back via `account-service`.
> Confirm the stored value matches exactly — no rounding drift.

This validates `NUMBER(18,4)` → `DECIMAL(18,4)` precision equivalence between Oracle and Azure SQL, ensuring the reconciliation-service won't produce false drift alerts.

---

*Document generated by Kiro — Phase 0 complete.*
