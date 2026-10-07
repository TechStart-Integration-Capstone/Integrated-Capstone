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
- Posting writes `ACCOUNT`, `LEDGER_TRANSACTION`, `OUTBOX_EVENT` and
  `EOD_JOB_RUN` in one Azure SQL transaction. Existing consumers receive the normal
  credit event with before/after balances into the existing PostgreSQL `LEDGER_MUTATION_AUDIT` GL. A zero total completes the EOD job,
  without a zero-value financial transaction or event.

## Setup

1. Apply [migrate_interest_azuresql.sql](../scripts/migrate_interest_azuresql.sql)
   to the ledger database and [migrate_interest_postgres.sql](../scripts/migrate_interest_postgres.sql)
   to the audit database. Both are additive and rerunnable. Fresh database bootstraps
   already include these definitions. Do not use the destructive full bootstrap on an existing database.
   Existing interest installations must also apply
   [migrate_interest_recovery_postgres.sql](../scripts/migrate_interest_recovery_postgres.sql)
   followed by [migrate_interest_approval_postgres.sql](../scripts/migrate_interest_approval_postgres.sql)
   before deploying the recovery API. These add immutable resolution metadata and
   separate preparation/approval records; existing amounts and dates are preserved.
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

The existing default business cutoff remains 23:59:59 in Asia/Manila (UTC+8). Set the cron and
zone to the bank's agreed cutoff. The worker freezes balances while reading the
daily snapshot; transactions committed after that snapshot belong to the next
business cutoff. A manual accrual seals the day's balances immediately, so invoke
it only at the intended cutoff. There is no historical balance store in this repo:
if a day was never snapshotted, recovery refuses to substitute today's balance.
Missing historical snapshots require an approved source of historical EOD balances.
The scheduler captures the intended business date when scheduling each execution,
so a delayed September 30 callback cannot select October 1 instead. If execution or
balance capture crosses midnight before a snapshot exists, it requires historical
recovery; it never mislabels the new day's live balances. This preserves the configured
cutoff instead of silently moving it five minutes earlier. Scheduling near midnight
can still require recovery when capture is late. Exact historical balances require a
historical source; the scheduler alone cannot reconstruct them.

A customer opening savings on September 20 earns interest for September 20–30
(11 daily snapshots). Earlier bank-wide batches can be complete without containing
that account. `INTEREST_START_DATE` is the feature's activation date, not each
customer's opening date. Missing system batches and a customer joining midmonth
are different situations; starting midmonth requires no waiver.

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
unique `LEDGER_TRANSACTION.reference_no` (`INT-<period-end>-<account-id>`) and checking the existing transaction amount
for that period. A posting failure rolls back the whole month, including its job log;
the worker logs the failure and leaves PostgreSQL untouched. An hourly recovery job
retries unfinished closed months, including a crash between the final daily accrual
and monthly posting. Successful job records suppress unnecessary scheduled retries;
manual retries still verify the posted transaction amounts.
Periods with missing dates are reported as awaiting admin recovery. They no longer
throw the same incomplete-period exception on every hourly pass or prevent a later,
complete period from being processed. After resolution, the next hourly pass posts
the closed month, or an admin can invoke `/post` immediately.

There is no separate Azure SQL GL table. GL credits arrive asynchronously through the existing outbox/Kafka/audit consumer; database failures are retried and duplicate deliveries are skipped per transaction/account. The retirement migration drops the old `GL_ENTRY` only when empty and refuses to discard historical entries. This does not
invent an expense-side chart-of-accounts entry or change the existing loan subledger.

## Admin operations

Use the gateway with a JWT containing `ROLE_ADMIN`:

```http
POST /api/v1/interest/eod/accrue?businessDate=2026-10-31
Authorization: Bearer <admin JWT>

POST /api/v1/interest/eod/post?businessDate=2026-10-31
Authorization: Bearer <admin JWT>
```

Responses contain `businessDate`, `accounts` (new snapshots or interest credits), and
`replayed`. Accrual retries return zero new accounts. Posting excludes loans.
Future dates and non-month-end posting dates return 400; missing snapshots or
incomplete periods return 409; non-admin requests return 403. The gateway strips
spoofed identity headers and the controller also checks the forwarded role.
No additional service port is exposed.

### Recover a missing day

List missing, elapsed days in a month (today and future days are excluded):

```http
GET /api/v1/interest/eod/missing?periodEnd=2026-10-31
Authorization: Bearer <admin JWT>
```

An outage never authorizes forfeiting a customer's earned interest. Missing data stays
unresolved until historical evidence is verified. Recovery requires two different admin
identities: one prepares a proposal and the other reviews and approves it.

Prepare a complete historical manifest for one past business date:

```http
POST /api/v1/interest/eod/resolve?businessDate=2026-10-25
Authorization: Bearer <admin JWT>
Content-Type: application/json

{
  "mode": "BACKFILL",
  "reason": "Nightly outage; verified the complete eligible-account export",
  "sourceReference": "approved-eod-export-2026-10-25",
  "confirmed": true,
  "accounts": [
    {"accountId": 123, "accountType": "SAVINGS_ACCOUNT", "eodBalance": 10000.0000},
    {"accountId": 456, "accountType": "LOAN", "eodBalance": 5000.0000, "annualRate": 0.1800}
  ]
}
```

Replace the example IDs and values with verified historical records for **every
account eligible on that date**, including accounts since closed. The admin confirms
manifest completeness: the current ACCOUNT table cannot prove historical activity or
balances. Account IDs must exist and must have been created by that business date;
current types must agree (the two savings names are equivalent). Savings rates are
calculated from the supplied balance; loans require the historical contract rate.
Interest amounts are calculated by the server, never supplied by the caller.
An empty manifest is valid only when the verified source confirms there were no
eligible accounts. An unknown balance or missing evidence must never be represented
as zero. Both admins must verify the source and completeness; the service cannot
automatically establish those facts from current account data.

The response includes `proposalId`, the stored manifest, `preparedBy`, `requestHash`
and `status: "PENDING_APPROVAL"`. The missing day and month remain incomplete.
The second admin retrieves the exact immutable proposal for review:

```http
GET /api/v1/interest/eod/backfills/<proposalId>
Authorization: Bearer <second admin JWT>
```

After checking the historical source, balances, contract rates and account coverage:

```http
POST /api/v1/interest/eod/backfills/<proposalId>/approve
Authorization: Bearer <second admin JWT>
Content-Type: application/json

{"reason": "Independently checked the export and complete account coverage", "confirmed": true}
```

The original preparer cannot approve, including through a case variant of their
username. The API and PostgreSQL both enforce this separation. Approval, calculated
accruals and the completion record commit together in PostgreSQL. Only then can the
month post. No account balance changes until monthly posting. The review endpoint
then reports `APPROVED` and `approvedBy`. Approval identity, reason and timestamp are
stored in `interest_backfill_approval`; the original manifest, identity, hash and
preparation timestamp are stored in `interest_backfill_proposal`.

Resolution type, original admin username, reason, source reference and request hash
also remain immutable fields in `interest_accrual_batch`, linked to the proposal.
An identical preparation retry returns the original proposal and preparer; approval
retries reuse the committed snapshot. A corrected manifest can be submitted as a new
proposal while the day is unresolved; leave incorrect proposals unapproved. Once a
day is sealed, a different manifest or replacement returns 409. New backfills are
refused for already posted periods. No existing PostgreSQL rows are updated or deleted.

`WAIVER` requests are rejected, and PostgreSQL rejects new waiver completion records.
If an older version created waivers or backfills without independent approval, their
audit records are retained but no longer count as complete days. This API cannot
overwrite those sealed records. They require a separately reviewed adjustment process;
that process, including compensation for delayed capitalization, is not implemented
here. Recovery does not recalculate already sealed later balances or interest amounts.

## Verification

Run `./scripts/test_interest.ps1` from PowerShell with Docker running. It creates
isolated PostgreSQL 15 and SQL Server 2022 containers, runs the transaction-service
suite against disposable `interest_test` databases, and removes only those containers.
It uses locally cached Maven dependencies (`-o`); populate the Maven cache first on
a new development machine. Pass `-Maven` and `-JavaHome` if needed.

Native tests cover migrations, immutable snapshots, duplicate accruals, full-month
rounding, leap days, zero balances, missing dates, concurrent posting, recovery
after PostgreSQL commits, and atomic rollback when outbox insertion fails. Recovery
regressions also cover scheduled date retention, midnight capture, historical
backfill preparation/approval, waiver and self-approval rejection, atomic approval
rollback, concurrent approvals, replay conflicts, account opening dates and September
20–30 accrual. Run the
api-gateway Maven tests as well for route authorization. Ordinary Maven tests skip
the native database suite unless the disposable test environment is explicitly set.
