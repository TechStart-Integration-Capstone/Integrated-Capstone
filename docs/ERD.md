# PayPink 2.0 — Entity Relationship Diagram

Generated from the current code:

- **Azure SQL (OLTP):** `backend/ledger-core/src/main/resources/schema-azuresql.sql` (+ `scripts/migrate_phase5_hardening.sql`, `scripts/migrate_phase6_loans.sql`, `BankingFavoritesSchema.java`)
- **PostgreSQL (audit):** `backend/event-consumers/src/main/resources/schema-postgres.sql`, `backend/notification-service/src/main/resources/schema-postgres.sql`

Solid lines are enforced foreign keys. Dotted lines are logical references with no FK constraint. These include every Azure SQL → PostgreSQL link, because the two databases are separate and kept in sync through Kafka events.

```mermaid
erDiagram
    %% ===================== Azure SQL (OLTP) =====================
    CUSTOMER {
        bigint customer_id PK
        nvarchar username UK
        nvarchar password_hash
        nvarchar first_name
        nvarchar last_name
        nvarchar email UK
        nvarchar contact_no
        nvarchar status "default ACTIVE"
        datetime2 created_date
        int credit_score "default 650 - CHECK 300..850 (Phase 6)"
        decimal monthly_income "18,4 - default 30000 (Phase 6)"
        decimal daily_transfer_limit "18,4 - default 50000.0000 (Phase 7 Monitoring)"
        decimal per_tx_limit "18,4 - default 25000.0000 (Phase 7 Monitoring)"
    }

    ACCOUNT {
        bigint account_id PK
        bigint customer_id FK
        nvarchar account_number UK
        nvarchar account_type "default SAVINGS"
        nvarchar currency "default PHP"
        decimal current_balance "18,4 - CHECK >= 0"
        decimal held_balance "18,4 - CHECK 0 <= held <= current"
        nvarchar status "default ACTIVE"
        datetime2 created_date
    }

    AUDIT_LOG {
        bigint audit_id PK
        bigint customer_id FK
        nvarchar action
        nvarchar entity
        nvarchar details
        datetime2 timestamp
    }

    BANKING_FAVORITE {
        bigint favorite_id PK
        bigint customer_id FK "UK(customer_id, account_id)"
        bigint account_id FK
        datetime2 created_date
    }

    LEDGER_TRANSACTION {
        bigint transaction_id PK
        bigint from_account_id FK
        bigint to_account_id FK "nullable"
        decimal amount "18,4 - CHECK > 0"
        nvarchar source_currency
        nvarchar target_currency
        nvarchar transaction_type
        nvarchar reference_no UK
        nvarchar status
        nvarchar failure_reason "nullable"
        datetime2 transaction_date
    }

    OUTBOX_EVENT {
        bigint event_id PK
        bigint transaction_id FK "nullable - NULL for loan.* events"
        nvarchar aggregate_id "nullable - e.g. LN-... (Kafka key when no transaction)"
        nvarchar event_type
        nvarchar payload "NVARCHAR(MAX)"
        nvarchar status "default PENDING"
        datetime2 created_date
        datetime2 processed_date "nullable"
    }

    REMITTANCE {
        bigint remittance_id PK
        nvarchar reference_no UK
        bigint caller_customer_id "UK(caller_customer_id, idempotency_key)"
        nvarchar idempotency_key "nullable"
        bigint source_account_id FK
        bigint target_account_id FK
        decimal amount "18,4"
        nvarchar currency "default PHP"
        nvarchar status "Initiated, Authorized, Reserved, Processing, Posted, Failed, Cancelled"
        nvarchar internal_status "nullable - 11-step pipeline code (Phase 7 Monitoring)"
        nvarchar current_service "nullable - executing microservice name (Phase 7 Monitoring)"
        decimal risk_score "5,4 - nullable"
        nvarchar risk_decision "APPROVE, REJECT"
        nvarchar ft_reference "T24 FT ref - nullable"
        nvarchar reason "nullable"
        datetime2 created_at
        datetime2 updated_at
        nvarchar transaction_type "TRANSFER, LOAN_DISBURSEMENT, LOAN_REPAYMENT"
    }

    LOAN_APPLICATION {
        bigint application_id PK
        nvarchar reference_no UK "LAP-yyyyMMdd-nnnnnn"
        nvarchar idempotency_key UK
        bigint customer_id FK
        bigint account_id FK "disbursement + repayment account"
        decimal requested_amount "18,4"
        int requested_term
        int credit_score "copied at decision time"
        nvarchar decision "APPROVED, COUNTER_OFFER, DECLINED"
        decimal offered_amount "nullable"
        int offered_term "nullable"
        decimal annual_rate "6,3 - nullable"
        decimal monthly_installment "nullable"
        nvarchar decline_reason "nullable"
        nvarchar status "DECIDED, ACCEPTED, EXPIRED"
        datetime2 expires_at "created + 7 days"
        datetime2 created_date
    }

    LOAN {
        bigint loan_id PK
        nvarchar reference_no UK "LN-yyyyMMdd-nnnnnn"
        bigint application_id FK "UK"
        bigint customer_id FK
        bigint account_id FK
        decimal principal "18,4"
        decimal annual_rate "6,3"
        int term_months
        decimal monthly_installment
        decimal outstanding_principal "CHECK >= 0"
        decimal penalty_due "default 0"
        nvarchar status "ACTIVE, OVERDUE, CLOSED"
        bigint disbursement_txn_id FK "nullable"
        nvarchar ft_reference "T24 FT ref - nullable"
        date disbursed_date
        date maturity_date
    }

    LOAN_SCHEDULE {
        bigint schedule_id PK
        bigint loan_id FK "UK(loan_id, installment_no)"
        int installment_no
        date due_date
        decimal principal_due
        decimal interest_due
        decimal amount_paid "default 0"
        bit penalty_charged "one-time EOD penalty"
        nvarchar status "PENDING, PAID, OVERDUE"
    }

    LOAN_REPAYMENT {
        bigint repayment_id PK
        bigint loan_id FK
        nvarchar reference_no UK "LRP-yyyyMMdd-nnnnnn"
        nvarchar idempotency_key UK
        decimal amount "CHECK > 0"
        bigint transaction_id FK "nullable"
        datetime2 created_date
    }

    %% ===================== PostgreSQL (audit) =====================
    LEDGER_MUTATION_AUDIT {
        bigserial audit_id PK
        bigint transaction_id "UK(transaction_id, account_id)"
        bigint account_id
        varchar entry_type "DEBIT, CREDIT"
        numeric amount "18,4 - CHECK > 0"
        varchar currency
        numeric before_balance
        numeric after_balance
        timestamptz created_date
    }

    RECONCILIATION_LOG {
        bigserial recon_id PK
        bigint transaction_id "UK(transaction_id, account_id)"
        bigint account_id
        varchar oracle_status
        varchar postgres_status
        varchar recon_status "MATCHED, DRIFT_DETECTED"
        varchar mismatch_fields "nullable"
        int check_count
        timestamptz last_checked_at
        timestamptz recon_date
    }

    NOTIFICATION {
        bigserial notification_id PK
        bigint customer_id
        bigint account_id "UK(reference_no, account_id)"
        varchar reference_no
        text message
        varchar status "PENDING, SENT, FAILED, RETRY"
        timestamptz created_date
        timestamptz updated_date "nullable"
    }

    %% ---------- Enforced FKs (Azure SQL) ----------
    CUSTOMER ||--o{ ACCOUNT : owns
    CUSTOMER ||--o{ AUDIT_LOG : "is audited in"
    CUSTOMER ||--o{ BANKING_FAVORITE : saves
    ACCOUNT ||--o{ BANKING_FAVORITE : "is saved as"
    ACCOUNT ||--o{ LEDGER_TRANSACTION : "debited by (from)"
    ACCOUNT |o--o{ LEDGER_TRANSACTION : "credited by (to)"
    LEDGER_TRANSACTION ||--o{ OUTBOX_EVENT : emits
    ACCOUNT ||--o{ REMITTANCE : "source of"
    ACCOUNT ||--o{ REMITTANCE : "target of"
    CUSTOMER ||--o{ LOAN_APPLICATION : applies
    ACCOUNT ||--o{ LOAN_APPLICATION : "pays into"
    LOAN_APPLICATION ||--o| LOAN : "accepted as"
    CUSTOMER ||--o{ LOAN : borrows
    ACCOUNT ||--o{ LOAN : "disbursed to / repaid from"
    LOAN ||--|{ LOAN_SCHEDULE : "repaid in"
    LOAN ||--o{ LOAN_REPAYMENT : receives
    LEDGER_TRANSACTION |o--o| LOAN : "disbursement_txn_id"
    LEDGER_TRANSACTION |o--o| LOAN_REPAYMENT : "transaction_id"

    %% ---------- Logical references (no FK) ----------
    CUSTOMER ||..o{ REMITTANCE : "initiates (caller_customer_id)"
    LEDGER_TRANSACTION ||..o{ LEDGER_MUTATION_AUDIT : "one row per leg"
    ACCOUNT ||..o{ LEDGER_MUTATION_AUDIT : "balance change"
    LEDGER_TRANSACTION ||..o{ RECONCILIATION_LOG : "reconciled per leg"
    ACCOUNT ||..o{ RECONCILIATION_LOG : "checked for"
    LEDGER_TRANSACTION ||..o{ NOTIFICATION : "reference_no"
    CUSTOMER ||..o{ NOTIFICATION : receives
    ACCOUNT ||..o{ NOTIFICATION : "about"
```

## Notes and drift found in the code

- **`REMITTANCE.caller_customer_id` has no FK** to `CUSTOMER`. Ownership is checked in application code instead.
- **`LEDGER_MUTATION_AUDIT` column mismatch:** the SQL schema defines `entry_type` (DEBIT/CREDIT). The `microservices/audit-service` and `microservices/reconciliation-service` entities map a column named `operation`, which the DDL doesn't define. `ddl-auto` is `none`, so Hibernate won't fix this.
- ~~`NOTIFICATION` entity is partial~~ — fixed in Phase 6: the entity now maps `account_id`, `reference_no` and `updated_date`.
- **Phase 6 Loans:** the bank's loan pool is the `INTERNAL` account `PH1000000LOAN` owned by the system customer `paypink_bank`. Loan money only moves through transaction-service (REMITTANCE → LEDGER_TRANSACTION); loan-service never updates `ACCOUNT`.
- `REMITTANCE` exists in the Azure SQL schema only. `schema-oracle.sql` is the legacy Capstone 1 schema.
- Collapsed redundant indexes are not shown. For example, `idx_account_num` duplicates the `account_number` unique constraint.
