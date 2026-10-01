# JIRA Sprint Backlog & Execution Blueprint (Days 31–37)

## Project: Capstone FSE - Core Retail Ledger & Balance Mutation Engine
**Team**: Full-Stack Engineering (FSE)  
**Methodology**: Agile Scrum & Microservices Architecture

---

### Epic FSE-300: Core Retail Ledger Engine & Transactional Outbox Hardening

| Item # | Issue Key | Type | Summary & Acceptance Criteria | Story Points | Assignee | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **#1** | **FSE-301** | Story | **Poller-Only Outbox Publisher & Retry Backoff**: Remove `triggerImmediatePublish()`, implement `@Scheduled(fixedDelay=1000)` with `SELECT ... FOR UPDATE SKIP LOCKED`, exponential backoff, and `DEAD` status after 5 attempts. | 5 SP | Backend Team | **DONE** |
| **#2** | **FSE-302** | Story | **Audit Uniqueness & Idempotent Consumer**: Add `entry_type` and `CONSTRAINT uq_audit_tx_account UNIQUE (transaction_id, account_id)` to `LEDGER_MUTATION_AUDIT`; skip duplicate deliveries gracefully. | 5 SP | Backend Team | **DONE** |
| **#3** | **FSE-303** | Story | **Redis Idempotency Lifecycle (`reserve` / `afterCommit` / `release`)**: Atomic `SET NX EX 30` reservation up-front, `afterCommit()` 24h completion, and `afterCompletion()` rollback release. | 5 SP | Security Lead | **DONE** |
| **#4** | **FSE-304** | Story | **Persist Failed Transactions**: Implement `FailedTransactionRecorder` in isolated transaction (`REQUIRES_NEW`) after main transaction rollback to prevent connection pool exhaustion. | 3 SP | Core Engine Lead | **DONE** |
| **#5** | **FSE-305** | Story | **Ordered Dual-Account Locking & Transfer Credit**: Lock accounts in ascending numerical ID order (`min(src, tgt)` then `max(src, tgt)`) to prevent transfer deadlocks; credit target account atomically on internal transfers. | 5 SP | Core Engine Lead | **DONE** |
| **#6** | **FSE-306** | Story | **Lagged Window Reconciliation**: `@Scheduled(cron = "0 */15 * * * *")` sweep comparing rolling time-window transactions (`now-20m` to `now-2m`), batch query `findByTransactionIdIn`, leg-by-leg verification, and upserting `RECONCILIATION_LOG`. | 5 SP | Backend Team | **DONE** |
| **#7** | **FSE-307** | Story | **Dual-Datasource Configuration**: Define `OracleDataSourceConfig` (`@Primary`, `oracleTransactionManager`) and `PostgresDataSourceConfig` (`postgresTransactionManager`). | 8 SP | Database Team | **DONE** |
| **#8** | **FSE-308** | Story | **Redis Idempotency Integration**: `StringRedisTemplate` with sub-5ms lookup latency and Micrometer timer `ledger.idempotency.check`. | 5 SP | Backend Team | **DONE** |
| **#9** | **FSE-309** | Story | **Kafka Producer & Listener Consumer Groups**: Stream outbox events to `transaction-events` with 6 partitions (key = `accountId`) and 3 dedicated consumer groups + DLT error handler. | 8 SP | Event Stream Lead | **DONE** |
| **#10** | **FSE-310** | Story | **Idempotency HandlerInterceptor**: Pre-handle interceptor enforcing `Idempotency-Key` header on `POST /api/v1/ledger/mutate` with `409 Conflict` on in-progress and `200 OK` on replay. | 5 SP | Backend Team | **DONE** |
| **#11-12** | **FSE-311** | Story | **Security Hardening, RBAC & Redis Token Matrix**: Remove `permitAll()`, enforce JWT role checks (`ROLE_CUSTOMER`, `ROLE_TELLER`, `ROLE_ADMIN`), and verify active tokens in Redis matrix (`token:{jti}`). | 8 SP | Security Lead | **DONE** |
| **#13** | **FSE-312** | Story | **3-Deployable Service Architecture**: Structured separation for API Gateway, Core Ledger Engine, and Event Consumers. | 8 SP | DevOps Team | **DONE** |
| **#14** | **FSE-313** | Story | **Environment Profiles**: Configure `local` (H2/embedded), `docker` (Oracle XEPDB1 + Postgres + Redis + Kafka with `ddl-auto: validate`), and `test` profiles. | 3 SP | DevOps Team | **DONE** |
| **#15** | **FSE-314** | Story | **Docker Infrastructure & Multi-Stage Dockerfile**: Package Java 21 JRE containers with container healthchecks and correct Oracle `XEPDB1` connections. | 5 SP | DevOps Team | **DONE** |
| **#16** | **FSE-315** | Story | **OTel Collector, Tempo & Grafana Provisioning**: Export distributed traces to Tempo and metrics to Prometheus; provision live Grafana dashboards with P95 latency and TPS gauges. | 5 SP | Observability Lead | **DONE** |
| **#17** | **FSE-316** | Story | **Automated Concurrency & Resilience Tests**: Concurrency tests verifying double-spend prevention, deadlock-free transfers, and idempotency replays. | 8 SP | QA Lead | **DONE** |
| **#18** | **FSE-317** | Story | **JMeter 800 TPS Load Plan & Account Seeding**: JMeter plan with Constant Throughput Timer (48,000 req/min = 800 TPS) and 1,000 seeded account generation script. | 5 SP | QA Lead | **DONE** |
| **#19-20** | **FSE-318** | Story | **Architectural Documentation & Diagram Sync**: Update `ARCHITECTURE.md` with "Design Decision: Transactional Outbox over Dual-Write" rationale and updated Mermaid diagrams. | 3 SP | Lead Architect | **DONE** |

**Total Estimated Velocity**: 101 Story Points Completed
