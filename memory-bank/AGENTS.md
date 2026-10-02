# AGENTS.md

Read `CONTEXT.md` before writing code. After any change, add a line to `CHANGELOG.md`.

## Never break these
- No risk score → reject (503). Money never moves without a score.
- Cached balances are display-only. Never use them to approve a transfer.
- Ledger change + OUTBOX row are saved in one database transaction.
- Every transfer needs an `Idempotency-Key`.
- Only the API Gateway exposes a port (8080).
- No secrets in code.
