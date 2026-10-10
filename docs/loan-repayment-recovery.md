# Loan repayment recovery (customer fix 4)

Loan-service now commits a PENDING LOAN_REPAYMENT before requesting a transfer. The loan ID,
amount and idempotency key remain fixed. Remote dispatch runs outside the loan database transaction.
If the response is delayed or lost, a worker retries every 30 seconds using the same
LOAN-REPAY-{key}; transaction-service replays the existing transfer instead of creating a second debit.

Only confirmed POSTED responses with a transaction ID reduce the loan balance. The loan row lock
serializes completion, and the schedule, repayment status and loan outbox events commit together.
If that transaction fails, the saved instruction remains pending for recovery. Rejection leaves the
loan unchanged and records a terminal result. Unknown outcomes never expire into an assumed failure.
Additional payments for the same loan are blocked until its pending repayment is resolved.
Delayed automatic repayments also update the recorded auto-debit outcome.

## Rollout

Rebuild the loan-service JAR and Docker image, stop the old loan-service container, then start the
new version. Its existing startup migration applies the guarded LOAN_REPAYMENT.status addition in
db/phase6_loans.sql. The identical manual migration is scripts/migrate_phase6_loans.sql.
If startup migrations are disabled, apply the guarded migration before starting the new service.
Existing repayment rows default to POSTED, preserving their completed status. Do not run old and new
loan-service instances together during this cutover: older code cannot interpret pending rows.

Verify a delayed repayment against the running services: one debit, one repayment, one schedule
allocation and the expected repayment/closure events after recovery. Test rejection and replay too.
All 49 loan-service tests passed. The database regression suite covers transaction rollback and concurrent completion with H2;
On 2026-10-10, the guarded status-column migration was applied to paypink.database.windows.net /
paypink. Verified NVARCHAR(24), NOT NULL, default POSTED; all five existing repayments were
backfilled to POSTED and every original field was preserved. The startup migration will safely
skip the existing column. The user will handle JAR/image rebuilds and container recreation.
Live multi-service acceptance remains pending; no containers were changed.

This prevents lost repayments for newly saved instructions. Historical debits created by older
code without a repayment record need separate reconciliation; the fix does not guess their loan.
