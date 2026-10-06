# Daily interest accrual and monthly posting

The EOD worker lives in the existing transaction-service orchestrator package,
`com.bank.transaction.orchestrator.interest`. Azure SQL remains the balance master;
PostgreSQL holds immutable daily interest snapshots. The existing ledger column is
`ACCOUNT.current_balance` (the specification calls it `balance`).

## Calculation and posting

- Active `SAVINGS` and the existing `SAVINGS_ACCOUNT` accounts earn 1% below 1,000,
  2.5% from 1,000 up to but excluding 10,000, and 4% from 10,000 upwards. The selected
  rate applies to the whole balance. Held funds remain part of the ledger balance.
- Active `LOAN` accounts use `ACCOUNT.interest_rate`. Rates are annual fractions:
  `0.1800` means 18%, not `18.0000`. Existing `LOAN`/`LOAN_SCHEDULE` loan products
  are separate entities; this feature does not create loan accounts or replace
  their repayment and overdue processing. The bank's `INTERNAL` loan pool is excluded.
- Daily calculation uses decimal arithmetic, actual calendar days / **365**, and
  half-up rounding to six decimals, including leap years.
- On the last calendar day, daily accrual completes first. PostgreSQL sums the
  period's stored amounts and rounds once to two decimals. Savings balances then
  increase by that amount. Loans accrue only; no loan capitalization policy was specified.
- Posting writes `ACCOUNT`, `GL_ENTRY`, `LEDGER_TRANSACTION`, `OUTBOX_EVENT` and
  `EOD_JOB_RUN` in one Azure SQL transaction. Existing consumers receive the normal
  credit event with before/after balances. A zero total gets a GL period record,
  without a zero-value financial transaction or event.

## Setup

1. Apply [migrate_interest_azuresql.sql](../scripts/migrate_interest_azuresql.sql)
   to the ledger database and [migrate_interest_postgres.sql](../scripts/migrate_interest_postgres.sql)
   to the audit database. Both are additive and rerunnable. Fresh database bootstraps
   already include these definitions. Do not use the destructive full bootstrap on an existing database.
2. Set the transaction-service environment:

   ```text
   INTEREST_EOD_ENABLED=true
   INTEREST_START_DATE=2026-10-06
   INTEREST_ZONE=Asia/Manila
   INTEREST_EOD_CRON=59 59 23 * * *
   INTEREST_RECOVERY_CRON=0 15 * * * *
   INTEREST_POSTGRES_URL=jdbc:postgresql://postgresql:5432/ledger_audit_db
   INTEREST_POSTGRES_USERNAME=<audit writer username>
   INTEREST_POSTGRES_PASSWORD=<audit writer password>
   ```

   Choose the actual first accrual business date; the example is not hardcoded.
   A midmonth start explicitly makes the first posting a partial month. All later
   periods start on the first. Keep this date stable after activation. Credentials
   are required when enabled and are not stored in source. The Azure connection
   continues to use the existing `SPRING_DATASOURCE_*` settings.
3. Build transaction-service and api-gateway, then deploy both through the existing
   deployment process, preserving the runtime Azure SQL connection settings.
   EOD is disabled by default until migrations and start-date configuration are ready.

The default business cutoff is 23:59:59 in Asia/Manila (also UTC+8). Set the cron and
zone to the bank's agreed cutoff. The worker freezes balances while reading the
daily snapshot; transactions committed after that snapshot belong to the next
business cutoff. A manual accrual seals the day's balances immediately, so invoke
it only at the intended cutoff. There is no historical balance store in this repo:
if a day was never snapshotted, recovery refuses to substitute today's balance.
Missing historical snapshots require an approved source of historical EOD balances.

## Retries and audit boundaries

All worker instances acquire the same transaction-owned Azure SQL application lock.
Account locks prevent concurrent balance changes during capture and posting.
PostgreSQL inserts the entire daily batch and an immutable `interest_accrual_batch`
completion record in one transaction. That record also represents days with no
eligible accounts. It contains no posting status. If Azure SQL fails after that
commit, a retry reuses the completed snapshot and repairs the job log.

The PostgreSQL rules ignore UPDATE and DELETE; triggers reject TRUNCATE and inserts
into completed dates. The application issues only SELECT and INSERT there. Use a
non-owner runtime role with SELECT/INSERT and identity-sequence USAGE, without
schema-altering rights; database owners/superusers can change any database protection.

Month-end refuses to post unless every date from the configured period start through
month-end has a completed snapshot. Duplicate credits are prevented by an Azure SQL
unique key on `(account_id, posting_type, period_end)` and checking posted GL amounts
for that period. A posting failure rolls back the whole month, including its job log;
the worker logs the failure and leaves PostgreSQL untouched. An hourly recovery job
retries unfinished closed months, including a crash between the final daily accrual
and monthly posting. Successful job records suppress unnecessary scheduled retries;
manual retries still verify the posted GL amounts.

The GL follows the supplied single customer-credit entry specification. It does not
invent an expense-side chart-of-accounts entry or change the existing loan subledger.

## Admin operations

Use the gateway with a JWT containing `ROLE_ADMIN`:

```http
POST /api/v1/interest/eod/accrue?businessDate=2026-10-31
Authorization: Bearer <admin JWT>

POST /api/v1/interest/eod/post?businessDate=2026-10-31
Authorization: Bearer <admin JWT>
```

Responses contain `businessDate`, `accounts` (new snapshots or GL postings), and
`replayed`. Accrual retries return zero new accounts. Posting excludes loans.
Future dates and non-month-end posting dates return 400; missing snapshots or
incomplete periods return 409; non-admin requests return 403. The gateway strips
spoofed identity headers and the controller also checks the forwarded role.
No additional service port is exposed.

## Verification

Run `./scripts/test_interest.ps1` from PowerShell with Docker running. It creates
isolated PostgreSQL 15 and SQL Server 2022 containers, runs the transaction-service
suite against disposable `interest_test` databases, and removes only those containers.
It uses locally cached Maven dependencies (`-o`); populate the Maven cache first on
a new development machine. Pass `-Maven` and `-JavaHome` if needed.

Native tests cover migrations, immutable snapshots, duplicate accruals, full-month
rounding, leap days, zero balances, missing dates, concurrent posting, recovery
after PostgreSQL commits, and atomic rollback when outbox insertion fails. Run the
api-gateway Maven tests as well for route authorization. Ordinary Maven tests skip
the native database suite unless the disposable test environment is explicitly set.
