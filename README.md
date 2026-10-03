# PayPink: Core Retail Ledger and Balance Mutation Engine

**FSE Capstone Project** — High-throughput relational balance mutation engine built on a microservices architecture with dual-store persistence, distributed idempotency validation, transactional outbox Kafka streaming, and a PostgreSQL immutable audit trail.

---

## Table of Contents

**Customer website:** [PayPink personal banking](http://localhost:3001/bank/) — login, registration with Savings and Everyday accounts plus a PHP 50 welcome gift, balances, fund transfers, masked account numbers, and transaction history. The [simulation UI](http://localhost:3001/) stays at its original URL. See [customer banking setup and features](docs/customer-banking.md).

1. [System Architecture](#system-architecture)
2. [Microservices Overview](#microservices-overview)
3. [Port Reference](#port-reference)
4. [Database Schema](#database-schema)
5. [Prerequisites](#prerequisites)
6. [Quick Start — Docker Compose](#quick-start--docker-compose-recommended)
7. [Rebuilding a Specific Service](#rebuilding-a-specific-service)
8. [Stopping and Cleaning Up](#stopping-and-cleaning-up)
9. [API Reference](#api-reference)
10. [Observability Stack](#observability-stack)
11. [Load Testing with JMeter](#load-testing-with-jmeter)
12. [Postman Collection](#postman-collection)
13. [Repository Structure](#repository-structure)
14. [Troubleshooting](#troubleshooting)

---

## System Architecture

```
Layer 1 — Client
  PayPink SPA (Port 3001)  ·  JMeter stress test

Layer 2 — Edge
  API Gateway  :8080
  · JWT validation filter (shared secret with auth-service)
  · Redis token-bucket rate limiter (100 req/s, burst 200)
  · Routes: /api/v1/auth, /accounts, /ledger, /stress,
            /reconciliation, /telemetry, /analytics

Layer 3 — Business / Microservices
  auth-service         :8081   account-service        :8082
  transaction-service  :8083   notification-service   :8084
  audit-service        :8085   reconciliation-service :8086
  outbox-publisher     :8087   analytics-service      :8088

  Azure SQL Database        PostgreSQL 15 :5432        Redis :6380
  CUSTOMER                  LEDGER_MUTATION_AUDIT      idempotency cache
  ACCOUNT                   RECONCILIATION_LOG         rate-limit store
  LEDGER_TRANSACTION        NOTIFICATION
  OUTBOX_EVENT
  AUDIT_LOG

Layer 4 — Event Streaming
  Zookeeper :2181  ·  Kafka :9092
  Topic: ledger.transaction.events
  Producer:  outbox-publisher
  Consumers: audit-service, notification-service,
             reconciliation-service, analytics-service

Layer 5 — Observability
  OTel Collector :4317/:4318  ·  Prometheus :9090
  Loki :3100  ·  Tempo :3200  ·  Grafana :3000
```

---

## Microservices Overview

| Service | Port | Responsibility |
|---|---|---|
| **api-gateway** | 8080 | Spring Cloud Gateway — JWT auth filter, Redis rate limiting, CORS, routing to all downstream services |
| **auth-service** | 8081 | Login endpoint, JWT token generation (24-hour expiry), Azure SQL user store |
| **account-service** | 8082 | Read account balances, list accounts by customer, balance reset (dev only) |
| **transaction-service** | 8083 | Core balance mutation engine — pessimistic locking, Redis idempotency, Kafka outbox write, telemetry stats, stress-test endpoint |
| **notification-service** | 8084 | Kafka consumer — persists customer alerts to PostgreSQL NOTIFICATION table |
| **audit-service** | 8085 | Kafka consumer — writes append-only double-entry records to LEDGER_MUTATION_AUDIT |
| **reconciliation-service** | 8086 | Scheduled every 15 min — cross-database drift detection between Azure SQL and PostgreSQL; manual trigger available |
| **outbox-publisher** | 8087 | Transactional outbox poller — polls OUTBOX_EVENT every 5 s, publishes to Kafka, marks rows PROCESSED / FAILED |
| **analytics-service** | 8088 | Kafka consumer — in-memory real-time aggregates; summary, per-account, and recent-events REST endpoints |

Infrastructure containers (not custom builds — pulled from Docker Hub):

| Container | Port | Purpose |
|---|---|---|
| postgresql | 5432 | Immutable audit database |
| redis | 6380 (host) | Idempotency cache and rate-limit backing store |
| zookeeper | 2181 | Kafka coordinator |
| kafka | 9092 | Event broker |
| otel-collector | 4317, 4318 | OpenTelemetry aggregation |
| loki | 3100 | Log aggregation |
| tempo | 3200 | Distributed tracing backend |
| prometheus | 9090 | Metrics scraping |
| grafana | 3000 | Dashboards |

---

## Port Reference

| URL | What you get |
|---|---|
| http://localhost:3001 | PayPink SPA frontend |
| http://localhost:8080 | API Gateway (all calls should go through here) |
| http://localhost:8081 | auth-service direct |
| http://localhost:8082 | account-service direct |
| http://localhost:8083 | transaction-service direct |
| http://localhost:8084 | notification-service direct |
| http://localhost:8085 | audit-service direct |
| http://localhost:8086 | reconciliation-service direct |
| http://localhost:8087 | outbox-publisher direct |
| http://localhost:8088 | analytics-service direct |
| http://localhost:3000 | Grafana (admin / admin) |
| http://localhost:9090 | Prometheus |
| http://localhost:3100 | Loki |
| http://localhost:3200 | Tempo |

---

## Database Schema

### Azure SQL Database — Master OLTP

| Table | Description |
|---|---|
| `CUSTOMER` | User accounts and hashed credentials |
| `ACCOUNT` | Master balance state — `DECIMAL(18,4)`, pessimistic write locks on mutations |
| `LEDGER_TRANSACTION` | Master financial transaction records (`TRANSACTION` was renamed because it is reserved in SQL Server) |
| `OUTBOX_EVENT` | Transactional outbox rows — `PENDING` / `PROCESSED` / `FAILED` / `DEAD_LETTER` |
| `AUDIT_LOG` | Synchronous security and operational audit for immediate local ACID non-repudiation |

The additive target schema is [schema-azure-sql.sql](backend/ledger-core/src/main/resources/schema-azure-sql.sql). Existing Oracle data is not copied by Docker Compose; see [Oracle to Azure SQL data migration](docs/oracle-to-azure-sql-migration.md) for the table/column mapping, cutover checks, and access prerequisites.

### PostgreSQL 15 — Immutable Audit Store

| Table | Description |
|---|---|
| `LEDGER_MUTATION_AUDIT` | Append-only double-entry records (`before_balance`, `after_balance`, `DEBIT`/`CREDIT`) |
| `RECONCILIATION_LOG` | Cross-database drift detection results — `MATCHED` / `DRIFT_DETECTED` |
| `NOTIFICATION` | Customer alert delivery history — `SENT` / `FAILED` / `RETRY` |

### Redis

| Key pattern | TTL | Purpose |
|---|---|---|
| `idempotency:{key}` | 24 h | Prevents duplicate transaction processing |
| Rate limit buckets | Rolling window | 100 req/s steady-state, burst 200, keyed per JWT user |

---

## Prerequisites

Make sure these are installed before you start:

- **Docker Desktop** with Compose V2 — https://docs.docker.com/desktop/
- **Java 17+** — required to build JARs with Maven
- **Maven 3.9+** — required to build JARs before Docker copies them into images
- **At least 8 GB RAM** recommended for the local containers; Azure SQL Database is external to Compose

---

## Quick Start — Docker Compose (Recommended)

The supporting stack runs in Docker. Every microservice must be compiled into a JAR first — Docker just copies the built artifact into the image. There is no pre-built registry; you build locally.

The application services connect to the Azure SQL database configured by `AZURE_SQL_JDBC_URL`; this variable must contain an encrypted SQL Server JDBC URL with an authentication mode supported by the runtime identity. In Azure, use `authentication=ActiveDirectoryMSI`; set `AZURE_MANAGED_IDENTITY_CLIENT_ID` only for a user-assigned identity. Do not commit connection details or credentials. Local Compose can connect only when its runtime has an Azure SQL-compatible identity/authentication context.

Use [docker/.env.example](docker/.env.example) as the template for the real local `docker/.env` file. The real `.env` file is ignored by Git.

For a VS Code connection that uses **SQL Login**, copy its server and database into the JDBC URL and use `authentication=SqlPassword`. Set `AZURE_SQL_USERNAME` and `AZURE_SQL_PASSWORD` separately in `docker/.env`; put the password in single quotes to preserve special characters. The five Azure SQL services read these values, including reconciliation's Azure SQL datasource. VS Code's connected session does not supply a password to Docker. Rebuild the service JARs and run `docker compose up -d` after changing configuration so the containers receive it.

### Step 1 — Build all microservice JARs

Run from the workspace root (`FSE-Capstone/` folder). Each service has its own `pom.xml`:

```powershell
cd microservices\api-gateway
mvn clean package -DskipTests
cd ..\..
cd microservices\auth-service
mvn clean package -DskipTests
cd ..\..
cd microservices\account-service
mvn clean package -DskipTests
cd ..\..
cd microservices\transaction-service
mvn clean package -DskipTests
cd ..\..
cd microservices\notification-service
mvn clean package -DskipTests
cd ..\..
cd microservices\audit-service
mvn clean package -DskipTests
cd ..\..
cd microservices\reconciliation-service
mvn clean package -DskipTests
cd ..\..
cd microservices\outbox-publisher
mvn clean package -DskipTests
cd ..\..
cd microservices\analytics-service
mvn clean package -DskipTests
cd ..\..
```

### Step 2 — Start the full stack

```powershell
cd docker
Copy-Item .env.example .env
# Edit docker\.env and replace <server>, <database>, and identity settings.
..\scripts\verify_azure_sql_connection.ps1
docker compose up -d
```

Replace the server and database placeholders before starting. `ActiveDirectoryMSI` requires an Azure-hosted identity endpoint; ordinary local Docker does not provide a managed identity. Use a supported local authentication mode for local development without committing credentials. The application and supporting containers start in dependency order. PostgreSQL and Kafka have local health checks; Azure SQL is external and must be reachable by the configured identity.

### Step 3 — Verify everything is running

```powershell
docker compose ps
```

All local containers should show `running` status. In Docker Desktop, all should have a green dot. Kafka may take extra time to become healthy.

Check logs for any specific container:

```powershell
docker logs <container-name> --tail 50
```

### Step 4 — Open the app

Go to **http://localhost:3001** in your browser.

Optional demo data: run [scripts/seed_demo_azure_sql.sql](scripts/seed_demo_azure_sql.sql) against the application's Azure database in VS Code after creating the schema. It adds the three original demo customers and five funded PHP accounts. Rerunning it preserves existing customers, passwords and balances. It uses synthetic contact details and the current account-number format. `DataInitializer` repairs existing demo passwords; it does not create demo customers.

Demo credentials after seeding:

| Username | Password |
|---|---|
| `lviernes` | `password123` |
| `arosales`| `password123` |
| `glim` | `password123` |

To get a JWT token via the API:

```bash
POST http://localhost:8080/api/v1/auth/login
Content-Type: application/json

{
  "username": "alice",
  "password": "password123"
}
```

Or grab a demo token without credentials:

```
GET http://localhost:8080/api/v1/auth/demo-token
```

---

## Rebuilding a Specific Service

When you change code in one microservice and need to redeploy only that container:

```powershell
# 1. Rebuild the JAR
cd microservices\transaction-service
mvn clean package -DskipTests
cd ..\..
# 2. Rebuild the Docker image and restart only that container
cd docker
docker compose up -d --build transaction-service
```

Replace `transaction-service` with any service name from `docker-compose.yml`.

---

## Stopping and Cleaning Up

Stop all containers but keep local data volumes (PostgreSQL and Kafka data persist on next start):

```powershell
cd docker
docker compose down
```

Stop and delete local data volumes (PostgreSQL and Kafka data are wiped; Azure SQL is not affected):

```powershell
cd docker
docker compose down -v
```

Stop, delete data, and remove built images (forces a full image rebuild next time):

```powershell
cd docker
docker compose down -v --rmi local
```

---

## API Reference

All endpoints are proxied through the API Gateway at **http://localhost:8080**.

Include the JWT token in every authenticated request:

```
Authorization: Bearer <your-token-here>
```

### Auth Service — `/api/v1/auth`

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | /api/v1/auth/login | No | Login with username/password, returns JWT |
| GET | /api/v1/auth/demo-token | No | Returns a demo JWT without credentials |
| GET | /api/v1/auth/admin/transactions/today | Admin JWT | Reads today's Azure ledger transactions in Philippine time, newest timestamp and ID first |

The admin transaction monitor refreshes this endpoint every five seconds. It uses stored ledger rows without browser-cached simulation data or sample fallbacks. Missing operations are shown as "Not recorded"; idempotency tokens are not fabricated. Empty results and connection failures have separate display states.

### Account Service — `/api/v1/accounts`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | /api/v1/accounts | Yes | List all accounts |
| GET | /api/v1/accounts/{accountId} | Yes | Get account by ID |
| GET | /api/v1/accounts/customer/{customerId} | Yes | Get all accounts for a customer |
| POST | /api/v1/accounts/{accountId}/reset-balance | Yes | Reset balance to seed value (dev only) |

### Transaction Service — `/api/v1/ledger`

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | /api/v1/ledger/mutate | Yes | Execute a DEBIT or CREDIT balance mutation |
| POST | /api/v1/stress/double-spend-test | Yes | Concurrent stress test to verify pessimistic locking |
| GET | /api/v1/telemetry/stats | Yes | Transaction throughput and latency statistics |

Mutation request body:

```json
{
  "accountId": 1001,
  "amount": 500.00,
  "type": "DEBIT",
  "currency": "PHP",
  "description": "Payment",
  "idempotencyKey": "a8098c1a-f86e-11da-bd1a-00112444be1e"
}
```

The `idempotencyKey` ensures the same mutation is never applied twice within 24 hours, even on retries.

### Reconciliation Service — `/api/v1/reconciliation`

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | /api/v1/reconciliation/run | Yes | Manually trigger a cross-DB reconciliation run |
| GET | /api/v1/reconciliation/logs | Yes | Get reconciliation history logs |

Reconciliation also runs automatically on a cron schedule every 15 minutes (`0 */15 * * * *`).

### Analytics Service — `/api/v1/analytics`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | /api/v1/analytics/summary | Yes | Overall ledger stats — total transactions, volume, etc. |
| GET | /api/v1/analytics/accounts | Yes | Per-account transaction breakdown |
| GET | /api/v1/analytics/recent | Yes | Last N processed transaction events |

### Outbox Publisher — `/api/v1/outbox`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | /api/v1/outbox/status | No | Outbox queue status — PENDING / PROCESSED / FAILED counts |

### Actuator Health (all services)

```
GET http://localhost:{port}/actuator/health
GET http://localhost:{port}/actuator/prometheus
```

---

## Observability Stack

### Grafana — http://localhost:3000

Login: **admin / admin**

Three pre-configured data sources: Prometheus (metrics), Loki (logs), Tempo (distributed traces).

### Prometheus — http://localhost:9090

Scrapes `/actuator/prometheus` from all microservices every 15 seconds.
Config: `docker/prometheus.yml`

### OpenTelemetry Collector — ports 4317 (gRPC) / 4318 (HTTP)

Every microservice runs with the OpenTelemetry Java agent (`opentelemetry-javaagent.jar`) attached at container startup. Traces and metrics are forwarded to the collector, which fans them out to Tempo and Prometheus.
Config: `docker/otel-config.yaml`

### Loki — http://localhost:3100

Log aggregation backend.
Config: `docker/loki-config.yaml`

### Tempo — http://localhost:3200

Distributed tracing backend.
Config: `docker/tempo.yaml`

---

## Load Testing with JMeter

A JMeter test plan is included to simulate high-throughput concurrent balance mutations.

Test plan: `docker/jmeter/balance_mutation_stress.jmx`

What it verifies:
- Pessimistic locking prevents overdrafts under concurrent DEBIT load
- Idempotency keys deduplicate retried requests
- System holds under 800+ TPS sustained load with 0% overdraft rate

To run: open the `.jmx` file in Apache JMeter, make sure the stack is up, and point it at `http://localhost:8080`.

---

## Postman Collection

Ready-to-use Postman files are in the `postman/` directory:

- `postman/PayPink_Retail_Ledger_Postman_Collection.json` — all API endpoints organised by service
- `postman/PayPink_Local_Environment.json` — local environment variables (base URL, token variable)

To use: import both files into Postman, select the **PayPink Local** environment, then run the **Login** request first — it auto-populates the `{{token}}` variable for all subsequent requests.

---

## Repository Structure

```
FSE-Capstone/
├── backend/                          # Legacy monolith (kept for reference, disabled in compose)
│
├── microservices/                    # All active microservices
│   ├── api-gateway/                  # Spring Cloud Gateway — port 8080
│   ├── auth-service/                 # JWT auth — port 8081
│   ├── account-service/              # Account CRUD — port 8082
│   ├── transaction-service/          # Core mutation engine — port 8083
│   ├── notification-service/         # Kafka consumer, alerts — port 8084
│   ├── audit-service/                # Kafka consumer, audit trail — port 8085
│   ├── reconciliation-service/       # Cross-DB reconciliation — port 8086
│   ├── outbox-publisher/             # Outbox poller to Kafka — port 8087
│   ├── analytics-service/            # In-memory analytics — port 8088
│   └── opentelemetry-javaagent.jar   # Shared OTel agent (copied into each image)
│
├── frontend/                         # PayPink SPA (Vanilla HTML/CSS/JS, served by Nginx)
│   ├── index.html
│   ├── src/css/
│   ├── src/js/
│   ├── nginx.conf
│   └── Dockerfile
│
├── docker/                           # Docker Compose and observability configs
│   ├── docker-compose.yml
│   ├── prometheus.yml
│   ├── otel-config.yaml
│   ├── loki-config.yaml
│   ├── tempo.yaml
│   ├── fix_passwords.sql             # Utility SQL for user password-hash updates
│   └── jmeter/
│       └── balance_mutation_stress.jmx
│
├── postman/
│   ├── PayPink_Retail_Ledger_Postman_Collection.json
│   └── PayPink_Local_Environment.json
│
├── docs/
│   ├── ARCHITECTURE.md
│   ├── JIRA_BACKLOG.md
│   ├── SONARQUBE_QUALITY_REPORT.md
│   ├── TESTING.md
│   └── CAPSTONE_DEFENSE_REVIEWER.md
│
└── README.md
```

---

## Troubleshooting

### analytics-service or outbox-publisher exits with "no main manifest attribute"

This means the Dockerfile tried to `COPY` a JAR with a version suffix (e.g. `analytics-service-1.0.0-SNAPSHOT.jar`) but the `pom.xml` `<finalName>` produces a jar without the suffix (`analytics-service.jar`). The fix has been applied. If it reappears after a Maven change, rebuild both:

```powershell
cd microservices\analytics-service
mvn clean package -DskipTests
cd ..\..cd microservices\outbox-publisher
mvn clean package -DskipTests
cd ..\..cd docker
docker compose up -d --build analytics-service outbox-publisher
```

### Azure SQL connection fails

Confirm `AZURE_SQL_JDBC_URL` points to the target database, enables encryption, and specifies an authentication mode supported by the runtime. For Azure-hosted managed identity, ensure that the identity is assigned to the host and granted a database user with the required permissions. For a user-assigned identity, set `AZURE_MANAGED_IDENTITY_CLIENT_ID` and the JDBC `msiClientId` property.

```powershell
..\scripts\verify_azure_sql_connection.ps1
docker compose logs auth-service account-service transaction-service outbox-publisher reconciliation-service
```

### Port already in use

```powershell
netstat -ano | findstr :8080
```

Stop the conflicting process or change the host port in `docker-compose.yml`.

### Container has stopped (no green dot in Docker Desktop)

```powershell
docker logs <container-name> --tail 50
```

Common causes:
- JAR was not built before `docker compose up` — missing `target/*.jar` file
- Azure SQL is unreachable, the JDBC authentication mode is unsupported in the local container, or the managed identity lacks database permissions
- Out of memory — increase Docker Desktop memory limit in Settings → Resources

### Full reset — wipe everything and start clean

```powershell
cd docker
docker compose down -v --rmi local
```

Then rebuild all JARs from Step 1 of Quick Start and run `docker compose up -d` again.
