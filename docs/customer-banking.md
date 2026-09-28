# PayPink customer banking

The customer website is at **http://localhost:3001/bank/**. The existing simulation remains at **http://localhost:3001/**; its HTML, CSS, and JavaScript are unchanged.

## Features

- Log in with an existing customer, or register a new customer with two PHP accounts: Savings at PHP 0 and Everyday at PHP 50.
- Registration stores a BCrypt password and creates both accounts plus a PHP 50 welcome-credit transaction, audit entry, and outbox event in one database transaction. Existing customers are not retroactively credited.
- See balances from the same Oracle ledger used by the simulation. The page refreshes every 30 seconds while visible, and has a manual Refresh button.
- Mask/reveal account numbers and balances, copy an account number, and inspect account details.
- Transfer between your own accounts or to another PayPink account number. Select any active PHP source account; Everyday is the default (the legacy demo Everyday account is also recognized). Review the recipient, amount, and zero fee before confirming.
- Enter a full recipient account number to automatically look up its holder's name. The lookup requires a logged-in customer and returns no recipient balance or contact information. Late responses for previously typed numbers are ignored.
- Use the star to save/remove a favorite, or choose from Favorites and Recent recipients. Favorites persist in Oracle per customer; recent recipients include the last ten distinct external accounts involved in successful sent or received transfers.
- Completion receipts and transaction-history details show the recipient name and masked account ending. Save receipt downloads a PNG; Done returns from the completion receipt to a fresh transfer form. In history, Done closes the detail dialog. Receipts remain visible until dismissed, so there is time to save them.
- Search the latest 200 transactions, filter by account/status, and inspect references. History includes welcome gifts, sent transfers, and received transfers.
- Log out to clear this tab’s session and displayed customer information. The session survives a page reload in the same tab and expires after 24 hours; visibility preferences reset on logout.

Existing seeded customers: `lviernes`, `arosales`, and `glim`, with password `password123`. New customers choose their own password. The startup demo-password repair is restricted to those three original demo usernames.

## Build and run

From the repository root, with Java 17+ and Maven on PATH:

```powershell
mvn -f microservices/auth-service/pom.xml package
mvn -f microservices/api-gateway/pom.xml package
docker compose -f docker/docker-compose.yml up -d --build auth-service api-gateway frontend
```

These commands reuse the infrastructure and OpenTelemetry agent JARs from the existing project setup. No frontend package installation is required.

On startup, the authentication service creates `BANKING_FAVORITE` if it does not exist. This additive Oracle migration preserves existing customers, accounts, transactions, and favorites and is safe to run again on restart.

## API

The additional API lives under `/api/v1/auth/banking` and is routed to the existing authentication service:

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/register` | Create customer, Savings, Everyday, and welcome credit, returning a session |
| POST | `/login` | Log in, returning a session |
| GET | `/me` | Current customer and their accounts |
| GET | `/transactions` | Current customer’s latest 200 ledger transactions |
| POST | `/transfers` | Transfer from an owned account to a PayPink account number |
| GET | `/recipients/lookup?accountNumber=...` | Look up an exact active PHP account's holder and favorite status |
| GET | `/recipients` | Current customer's favorites and recent recipients |
| POST | `/favorites` | Save a favorite using `{ "accountNumber": "..." }` |
| DELETE | `/favorites/{accountNumber}` | Remove only the current customer's favorite |

Both reads independently validate the signed bearer token in the authentication service and derive customer identity from it, including when accessed directly. They never accept a customer ID from the browser. Existing simulation endpoints retain their original behavior; the project remains a local banking simulation, not a production banking security boundary.

The transaction operation comes from the existing outbox payload. Transfers record two ledger rows: `TRANSFER_OUT` on the sender and `TRANSFER_IN` on the receiver, each with its own audit entry and outbox event. History shows actual mutations on each customer's accounts; it does not infer a credit from the old simulation's target-account field. The monthly summary is based on completed transactions within the latest 200 records, including movement between the customer's own accounts.

The transfer endpoint validates the signed bearer token independently and checks source ownership, account status, PHP currency, positive amounts with at most two decimal places, and sufficient funds. It locks both accounts in ascending ID order and commits both balances, ledger entries, audits, and outbox events together. The existing outbox publisher delivers committed events to Kafka.

Transfer requests contain `sourceAccountId`, `destinationAccountNumber`, `amount`, and `idempotencyKey`. The reference is derived from the authenticated customer and request key and protected by the database's unique reference constraint. The same key and details return the original receipt; different details are rejected. The UI retains the pending request across page reloads after an uncertain network/server failure so a retry does not send the money twice.

## Verification

```powershell
mvn -f microservices/auth-service/pom.xml test
node --check frontend/bank/bank.js
```

Tests cover password hashing, duplicate registrations, two-account creation and the welcome ledger credit, customer-scoped reads, invalid/expired JWTs, input validation, and password preservation. Transactional integration tests cover debit/credit consistency, audit/outbox entries, insufficient funds, ownership, currency/status checks, concurrent duplicate requests, competing debits, opposite-direction transfers, and full rollback when the receiver's ledger write fails.

The browser smoke test at `tests/customer-banking.cjs` uses Playwright with installed Chrome. It creates one uniquely named test customer and credits PHP 25 then debits PHP 10 on that customer's account only. It leaves that fixture in the simulation database for inspection.

```powershell
npm.cmd install --prefix "$env:TEMP/paypink-browser-check" playwright --no-audit --no-fund
$env:PLAYWRIGHT_MODULE="$env:TEMP/paypink-browser-check/node_modules/playwright"
node tests/customer-banking.cjs
node tests/customer-transfers.cjs
```

It verifies registration, validation, login, masking, live balances, transaction filters, customer isolation, logout, reload persistence, error recovery, session expiry, and desktop/mobile layouts. Screenshots are written to `$env:TEMP/paypink-browser-check/artifacts`.

The transfer browser test creates two isolated customers and transfers only between their accounts. It checks the welcome gifts, default source selection, changing source accounts, internal/external transfers, recipient history, cancellation, ownership checks, concurrent duplicate and overdraft prevention, recovery after a lost response and reload, name lookup, favorites across reloads, removing favorites, recent recipients, stale lookup responses, masked receipts, PNG downloads, and Done buttons. The test customers remain in the local simulation database for inspection.
