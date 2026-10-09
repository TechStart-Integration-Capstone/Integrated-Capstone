# Savings backend integration

This change adds backend APIs and an **unapplied** SQL Server migration. No accounts,
customers, goals or circles are seeded, and existing balances are not changed by the
migration. Customers choose their own goals; a newly created goal starts at PHP 0.

The actual bank Savings page now uses authenticated backend APIs through
`frontend/bank/savings-live.js`. Personal goals, PinkCircles, invitations, contributions,
releases, schedules, Smart Split, privacy, target approvals and activity are connected.
The separate `savings-preview.html` remains an offline demonstration with sample data.
The bank page shows an unavailable state if the APIs are disabled or cannot be reached;
it never substitutes sample customer data.

## Enable later, on the machine hosting the banking stack

1. Apply `scripts/migrate_savings.sql` to the same SQL Server/Azure SQL database used
   by account-service and t24-adapter, after schema split and phase-6 outbox migration.
   Use the project's normal authenticated SQL client. This script is additive and rerunnable.
2. Provision Kafka topic `savings.events` using the environment's normal topic policy
   if automatic topic creation is disabled.
3. Set `SAVINGS_ENABLED=true` for Compose, or `APP_SAVINGS_ENABLED=true` directly on
   account-service. It defaults to false. This enables both customer endpoints and
   the recovery/schedule/completion worker.
4. Compile the Java JARs first (`scripts/build-all.ps1` from the repository root),
   then build/start account-service, t24-adapter, outbox-publisher, notification-service
   and frontend with the rest of the banking stack. The frontend must be rebuilt too:
   Nginx serves the JavaScript copied into its image.
5. Test with two test customers and small amounts using the bank's Savings navigation.

### Short checklist for your other Windows machine

No more frontend-to-API wiring is required. Copy/sync these code changes, including
new files, to the other machine, then:

1. Open `scripts/migrate_savings.sql` in your normal SQL client (for example SSMS),
   select the existing banking database, and execute the script once. It creates
   seven savings tables. It does not delete accounts or change account balances.
   If its prerequisite check fails, apply the existing schema-split/outbox migrations
   first; do not remove the checks.
2. From the repository root, in PowerShell, enable Savings and build/start the stack:

   ```powershell
   .\scripts\build-all.ps1
   $env:SAVINGS_ENABLED = 'true'
   docker compose -f docker/docker-compose.yml up -d --build
   ```

   Keep the project's existing database and other environment configuration. The
   service Dockerfiles copy prebuilt `target/*.jar` files; Docker `--build` does not
   compile Java source. Do not proceed to Docker startup if the JAR build fails. The
   PowerShell setting applies to this terminal; for later starts, put
   `SAVINGS_ENABLED=true` in the Compose environment file you normally use. In an
   environment with Kafka automatic topic creation disabled, provision `savings.events`
   before using Savings.
3. Open the bank, sign in, select **Savings**, and test the checklist below. Use the
   actual bank page rather than `savings-preview.html`. A hard refresh may be needed
   after rebuilding an older frontend image.

The migration has **not** been applied by this coding session. Docker and the full
app have **not** been started here. Running the migration is the one-time database
setup; the enable setting must remain present on subsequent starts.

### Manual acceptance checklist

- With an active PHP savings account, create a personal goal at zero; reload and
  verify it persists. Create another with an initial amount and a savings plan.
- Add a small amount, verify the total, and release part of it. Check activity.
  The account's book balance stays unchanged; available funds reflect reservations.
- Try insufficient funds and a split exceeding its budget. Neither should be shown
  as a successful contribution. Pending requests must resolve before another change.
- Create a PinkCircle, invite the second customer's PayPink username, then sign in
  as that customer and accept using their own savings account. Unknown usernames
  must be rejected. Inviting alone never reserves funds.
- Contribute as both members. The rose card's group switch shows only the signed-in
  customer's contributions; the circle card shows the collective total. Verify
  individual amounts follow member privacy settings.
- Propose a member target as the circle admin and approve it as that member.
  Verify non-admins cannot invite or propose targets.
- Set a schedule due today (15th/month-end for PAYDAY), allow a worker cycle, and
  verify one contribution or a declined/pending result. Check savings notifications.
- Sign out and in again; goals and circles should remain, while the previous
  customer's page/dialog data should be cleared.

Optional properties:
- `app.savings.core-url`: defaults to `http://t24-adapter:8090/internal/savings`.
- `app.savings.poll-ms`: defaults to 60000.
- T24 `app.risk-engine.url`: defaults to `http://risk-engine:8000/score`.

No extra service port is published. `/internal/savings/**` is available only on the
private service network and has no API Gateway route. Deployment must retain that
network boundary. Public customer APIs use the gateway's existing accounts route and
require its validated customer identity header; clients send a normal bearer token,
not their own `X-Auth-Customer-Id`.

## Tables and ownership

| Table | Purpose |
|---|---|
| `app.SAVINGS_GOAL` | Owner, linked savings account, target, category, date, optional circle |
| `app.SAVINGS_SCHEDULE` | Optional contribution amount, frequency, next due date and enablement |
| `app.PINK_CIRCLE` | Shared target, administrator and once-only completion notification flag |
| `app.PINK_CIRCLE_MEMBER` | Invitation/acceptance, goal link, consent and agreed/proposed target |
| `app.SAVINGS_ACTIVITY` | Durable pending request, retry key, final result and UTC timestamps |
| `t24.SAVINGS_RESERVATION` | Non-expiring, individually owned reserved amount per goal |
| `t24.SAVINGS_OPERATION` | Append-only core reservation journal and original replay response |

The 24-hour remittance holds in `t24.LOCKED_AMOUNT` are not reused. Savings reservations
contribute to `ACCOUNT.held_balance`, so the existing transfer authorization sees less
available money. Creating/releasing a reservation does not change `current_balance`.
Other holds may coexist: `available = current - held`, where held includes savings.
The core uses the same pessimistic account lock as transfer posting. A split across
goals on one account is atomic; splitting across different accounts is rejected.

Every new allocation/release is risk-checked before balances change. A risk outage
leaves the request pending for a safe retry. Rejected scores leave funds unchanged.
Core reservation changes, journal and outbox record commit or roll back together.
This journal records reservation changes; it is not a fake debit/credit transfer.

## Customer endpoints

Base: `/api/v1/accounts/savings`

| Method/path | Behavior |
|---|---|
| `GET /` | Own goals, confirmed core amounts, personal/group totals and goal counts |
| `POST /goals` | Create a zero-balance personal goal |
| `PUT /goals/{goalId}` | Edit own personal goal; target cannot fall below saved amount |
| `PUT /goals/{goalId}/schedule` | Set/disable optional auto-contribution |
| `POST /operations` | Allocate/release, including multi-goal Smart Split |
| `GET /operations/{operationId}` | Own request status |
| `GET /activity` | Latest 100 own operations, timestamps, commands and results |
| `GET /circles` | Own circles/invitations and consent-filtered member progress |
| `POST /circles` | Create circle plus creator's zero-balance personal allocation goal |
| `POST /circles/{circleId}/invitations` | Admin invites an active PayPink username |
| `POST /circles/{circleId}/accept` | Invitee accepts and links their own savings account |
| `PUT /circles/{circleId}/visibility` | Member changes individual amount visibility |
| `POST /circles/{circleId}/members/{customerId}/target` | Admin proposes an individual target |
| `POST /circles/{circleId}/target/accept` | Member approves their pending target proposal |

A user needs their own active PHP `SAVINGS_ACCOUNT` or `SAVINGS` account to create a
personal goal, create a circle, or accept membership. Invitations can only target
existing active PayPink users. Inviting a user does not reserve any money or enroll
them automatically. Non-admins cannot invite members or propose target changes.
Accepted members can reserve/release only their own money. Individual contribution
amounts are private by default; circle totals remain visible to active members.
A group total is collective progress, not a jointly owned bank account.

Responses from goal/circle queries retain database field names such as `goal_id` and
`target_amount`; computed values use `savedAmount`, `personalTotal`, `groupTotal`,
`goalCount` and `completedGoals`. Missing consent means `savedAmount` is omitted for
that member. Other members' account IDs and goal IDs are never returned. Overview also
returns the signed-in `customerId` for admin controls and each goal's `streak`: the
number of consecutive confirmed scheduled attempts since the last rejected attempt.
Manual operations and pending attempts do not count. This is an attempt streak,
not proof of uninterrupted calendar saving when schedules are paused or the stack
is offline. Badges are derived from current saved amounts and these streaks; they
are not a separate permanently awarded badge ledger.

### Create a personal goal

`POST /goals`:
```json
{
  "accountId": 11,
  "name": "Christmas Fund",
  "category": "HOLIDAY",
  "target": 10000.00,
  "targetDate": "2027-12-15"
}
```
Categories: `EMERGENCY`, `HOLIDAY`, `TRAVEL`, `LIFESTYLE`, `OTHER`.
The response supplies `goalId`. Initial funding is a separate operation, so creating
a goal can succeed even when initial funding is declined. Schedule saving is also
separate; the frontend must report each outcome instead of claiming all three succeeded.
Metadata creation is not idempotent: after an uncertain create response, refresh the
goal/circle list before offering another create attempt.

### Allocate, release or Smart Split

`POST /operations`, header `Idempotency-Key: <client-generated UUID>`:
```json
{
  "accountId": 11,
  "type": "ALLOCATE",
  "lines": [
    {"goalId": "00000000-0000-0000-0000-000000000001", "amount": 1500.00},
    {"goalId": "00000000-0000-0000-0000-000000000002", "amount": 1000.00}
  ]
}
```
Use returned goal IDs, not the illustrative UUIDs above. `RELEASE` uses the same shape.
Amounts must be positive whole centavos. Each line must fit its remaining target or
reserved amount; the entire split must fit the core available balance. Client UI
should also enforce any user-entered Smart Split budget before sending its lines.

- `200 CONFIRMED`: core committed the operation.
- `202 PENDING`: result uncertain/unavailable; poll or retry the identical request/key.
- `422 REJECTED`: definitive business/risk rejection, no funds reserved by that attempt.
- `409`: another intent is pending, or key reused with different input.

Do not generate a fresh key after a timeout. The durable intent is retried by the
worker using the exact core command. While an intent is pending, new monetary
operations and goal/schedule/target changes for that customer are blocked. A later
confirmed operation can appear after the first request timed out.

The bank UI retains an uncertain request's exact body/key in username-scoped
`sessionStorage`, checks owner-scoped activity (which includes `idempotency_key`),
and polls while the Savings page is visible. It never stores a token in that retry
record. Signing out clears rendered/in-memory data but retains the retry record for
that username in the same browser tab. Closing the tab clears browser session storage;
persisted backend intents still recover through the worker and appear in activity.
Metadata creation is not automatically retried after an uncertain response: the UI
refreshes the list and asks the customer to check it before creating another item.
Initial funding and schedule setup report partial success separately.

### Pending requests and the recovery worker

A pending reservation is awaiting a confirmed core result, not manual admin approval.
The recovery worker retries the original persisted command; never delete the intent
or manually mark it confirmed to unlock the UI.

The 2026-10-09 worker fix addresses a runtime `NullPointerException` on
`this.service.jdbc`. Direct field access on the transactional service's Spring proxy
read uninitialized proxy fields. The worker now receives `JdbcTemplate` and
`SavingsCoreClient` through its own constructor. Recovery, due schedules and circle
completion are covered by Spring/H2 tests using the transactional service proxy.
Sync the fixed source and rebuild account-service's JAR before rebuilding/recreating
its container. No new database migration is required for this fix. Existing pending
intents will be retried on the worker's next cycle; their eventual outcome still
depends on core/database/risk availability and validation.

### Circle creation and acceptance

`POST /circles`:
```json
{
  "goal": {
    "accountId": 11, "name": "Boracay 2027", "category": "TRAVEL",
    "target": 40000.00, "targetDate": "2027-06-15"
  },
  "myTarget": 10000.00,
  "shareProgress": false
}
```
Invite: `{"username":"maria","target":15000.00}`.
Accept: `{"accountId":22,"shareProgress":true}` using the invited user's token.
Only the invitee can accept, and account 22 must belong to that user.
Targets do not have to be equal. Accepted targets and outstanding proposal capacity
cannot exceed the shared target. Group settings are otherwise immutable in this version.

### Optional schedule

`PUT /goals/{goalId}/schedule`:
```json
{"amount":500.00,"frequency":"PAYDAY","nextDue":"2027-01-15","enabled":true}
```
Frequencies: `WEEKLY`, `MONTHLY`, `PAYDAY` (15th and month-end). Scheduling uses
Asia/Manila business dates and stores activity timestamps in UTC. Missed periods do
not trigger multiple catch-up contributions: the worker attempts one installment and
advances to a future due date after a definitive result. Insufficient funds skip that
occurrence. Timeouts stay pending and are reconciled before advancing. Requests use
reserved `schedule:` retry keys, which public operation requests cannot supply.
Concurrent schedule changes are rechecked under the customer lock before execution.

Completion detection polls confirmed core balances. Each circle's first observed
completion emits a notification per accepted member through the outbox; subsequent
releases reduce displayed progress without repeating the first-completion notification.
Actual trip/gift payments remain separately authorized transfers.

## Validation and remaining rollout checks

Local verification uses bounded-memory Maven and H2, not Docker, Kafka, Redis or a live
SQL Server. Core tests cover concurrency, idempotency, ownership, funds checks, risk
failure, partial release and transactional outbox rollback. Account tests cover
eligibility, zero-balance goals, invitations/acceptance, consent, target approval,
operation recovery, schedule edits and completion notifications. Outbox/notification
unit tests verify the dedicated savings topic and messages.

The SQL Server migration and live service-to-service/network behavior still require
an environment smoke test. Frontend API wiring is complete. Offline browser checks
exercise the actual bank shell with mocked HTTP, covering authentication, exact retry
keys, pending/rejected operations, partial creation, privacy, member/admin controls,
schedules, unavailable/empty states and logout. The separate preview is checked for
zero API calls, mobile layout and its demo interactions. Run them without the app:

```powershell
node tests/savings-preview.cjs
```

These checks require installed Chrome/Edge (or `CHROME_PATH`) and do not replace the
manual test against SQL Server, the gateway, risk engine and Kafka on the other machine.

Disabling the feature stops its public API and workers; it does not release existing
reservations. Do not drop reservation tables or subtract held balances manually to
roll back a deployment. Reconcile pending operations and release funds through the core.
