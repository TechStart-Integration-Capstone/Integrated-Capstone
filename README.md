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

  Oracle XE 21c :1521       PostgreSQL 15 :5432        Redis :6380
  CUSTOMER                  LEDGER_MUTATION_AUDIT      idempotency cache
  ACCOUNT                   RECONCILIATION_LOG         rate-limit store
  TRANSACTION               NOTIFICATION
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
| **auth-service** | 8081 | Login endpoint, JWT token generation (24-hour expiry), Oracle XE user store |
| **account-service** | 8082 | Read account balances, list accounts by customer, balance reset (dev only) |
| **transaction-service** | 8083 | Core balance mutation engine — pessimistic locking, Redis idempotency, Kafka outbox write, telemetry stats, stress-test endpoint |
| **notification-service** | 8084 | Kafka consumer — persists customer alerts to PostgreSQL NOTIFICATION table |
| **audit-service** | 8085 | Kafka consumer — writes append-only double-entry records to LEDGER_MUTATION_AUDIT |
| **reconciliation-service** | 8086 | Scheduled every 15 min — cross-database drift detection Oracle vs PostgreSQL; manual trigger available |
| **outbox-publisher** | 8087 | Transactional outbox poller — polls OUTBOX_EVENT every 5 s, publishes to Kafka, marks rows PROCESSED / FAILED |
| **analytics-service** | 8088 | Kafka consumer — in-memory real-time aggregates; summary, per-account, and recent-events REST endpoints |

Infrastructure containers (not custom builds — pulled from Docker Hub):

| Container | Port | Purpose |
|---|---|---|
| oracle-xe | 1521 | Master OLTP database |
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

### Oracle XE 21c — Master OLTP

| Table | Description |
|---|---|
| `CUSTOMER` | User accounts and hashed credentials |
| `ACCOUNT` | Master balance state — `NUMBER(18,4)`, `PESSIMISTIC_WRITE` lock on all mutations |
| `TRANSACTION` | Master financial transaction records |
| `OUTBOX_EVENT` | Transactional outbox rows — `PENDING` / `PROCESSED` / `FAILED` / `DEAD_LETTER` |
| `AUDIT_LOG` | Synchronous security and operational audit for immediate local ACID non-repudiation |

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
- **At least 16 GB RAM** recommended — Oracle XE alone uses ~2 GB, the full stack peaks around 5–6 GB

---

## Quick Start — Docker Compose (Recommended)

The full stack runs entirely in Docker. Every microservice must be compiled into a JAR first — Docker just copies the built artifact into the image. There is no pre-built registry; you build locally.

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
cd ..\..```

### Step 2 — Start the full stack

```powershell
cd docker
docker compose up -d
```

All 19 containers start in the correct dependency order. Oracle XE takes the longest (~60–90 seconds) to pass its health check. Other services that depend on it wait automatically via `depends_on` + `condition: service_healthy`.

### Step 3 — Verify everything is running

```powershell
docker compose ps
```

All containers should show `running` status. In Docker Desktop, all should have a green dot. The two most likely to need extra time are `oracle-xe` and `kafka`.

Check logs for any specific container:

```powershell
docker logs <container-name> --tail 50
```

### Step 4 — Open the app

Go to **http://localhost:3001** in your browser.

Default demo credentials (auto-seeded on first startup by `DataInitializer`):

| Username | Password |
|---|---|
| `alice` | `password123` |
| `bob` | `password123` |

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

Stop all containers but keep data volumes (Oracle, Postgres, Kafka data persists on next start):

```powershell
cd docker
docker compose down
```

Stop and delete all data volumes (full reset — Oracle and Postgres data is wiped):

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
│   ├── fix_passwords.sql             # Utility SQL for Oracle password fixes
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
│   └── SONARQUBE_QUALITY_REPORT.md
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

### Oracle XE takes too long and dependent services crash on startup

Oracle has a 60-second `start_period` in its health check. On slower machines it can take longer. After Oracle is healthy, restart any services that failed:

```powershell
cd docker
docker compose restart auth-service account-service transaction-service outbox-publisher
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
- Oracle XE health check failed — the service started before Oracle was ready (restart it)
- Out of memory — increase Docker Desktop memory limit in Settings → Resources

### Full reset — wipe everything and start clean

```powershell
cd docker
docker compose down -v --rmi local
```

Then rebuild all JARs from Step 1 of Quick Start and run `docker compose up -d` again.
