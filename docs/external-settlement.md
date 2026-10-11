# External settlement (customer fix 3)

InstaPay and PESONet retain the existing customer endpoints, recipient directory and receipt fields.
Recipients remain simulated partners. This change does not connect to a real external banking network.

Auth-service records a durable PENDING instruction under the source account lock, commits it, then
calls POST /internal/remittance/external on transaction-service. No network call holds that database
transaction open. InstaPay attempts immediately; PESONet waits 90 seconds. Pending instructions are
retried by the existing worker, including after lost responses or service restarts.

The private endpoint accepts only a persisted reference and its customer ID from auth-service.
Amounts, rail, source and recipient are read from that instruction. It rejects ownership mismatches
and unknown types. It is not gateway-routed and uses the same private-network trust boundary as the
loan internal endpoint. Public callers cannot select a clearing account or bypass risk screening.

The remittance saga uses the external reference as its stable idempotency key/reference, evaluates
risk and customer limits, obtains a T24 hold and posts a debit/credit journal to PH1000000EXT. The
clearing account starts at zero; it is separate from the bank's loan pool. Core owns balances and
reservation checks. The application completes the original queued ledger row and writes its outbox
event together, preserving receipt identity and avoiding duplicate customer activity.

Unknown transport outcomes leave the instruction pending. Terminal rejection marks it failed.
Missing/unavailable risk scores prevent posting; a recorded failed risk attempt becomes a failed
receipt when the worker observes that result. Core processing remains pending until saga recovery
completes. Legacy pending rows with an already-posted
success event are completed without a second debit.

## Rollout

1. Apply scripts/migrate_external_clearing.sql after the schema split and phase6_loans migration.
   It creates only the clearing account; reruns never reset its balance.
2. Rebuild transaction-service and auth-service. Stop the old auth-service external worker during
   cutover, then bring up transaction-service before the new auth-service.
3. Auth-service uses app.orchestrator.url (default http://transaction-service:8083). Override via
   APP_ORCHESTRATOR_URL when running services outside Compose.
4. Verify on a test database: approved InstaPay posts once, risk rejection/unavailability does not
   debit, PESONet waits then respects holds, and replay after timeout completes the same receipt.
   Verify t24.POSTING_JOURNAL, held balances, one ledger row and its outbox event for that reference.

On 2026-10-10, the migration was applied to the user-selected paypink.database.windows.net / paypink
database. Verified account_id 13, PH1000000EXT, INTERNAL/PHP/ACTIVE, owned by paypink_bank,
with current and held balances both zero. Service rebuilds/restarts and real multi-service
acceptance checks remain required before release. Other database environments were not migrated.

Local verification: 20 auth-service banking integration tests and 54 transaction-service tests
passed, covering queued dispatch, ownership, risk rejection/unavailability, reserved funds,
idempotent replay, core rejection and recovery after a local commit failure.
