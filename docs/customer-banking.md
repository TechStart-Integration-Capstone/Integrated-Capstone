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

PESONet queues a PENDING instruction for approximately 90 seconds without deducting funds. The server then locks the account, checks funds, and atomically posts the debit, audit/outbox and SUCCESS status. Insufficient funds or an unavailable account at processing time produces FAILED without a debit. Repeated processing cannot double-debit. InstaPay remains immediate. Existing pending payments with a previously posted outbox debit complete without another deduction. External recipient records remain hardcoded.

## In-app transfer notifications

The bank header includes a notification bell, unread count, individual transfer details, and Mark all as read. Alerts distinguish money sent, money received, and external pending/failed transfers; a pending-to-completed status change becomes a new unread update. Full account numbers are masked in messages. The inbox includes transfers within the API's latest 200 activity records.

The implementation follows NotificationKafkaConsumer/NotificationDispatcher debit and credit semantics. It reads the existing authenticated /api/v1/auth/banking/transactions API (every eight seconds while visible), rather than the notification service's simulated email/SMS/push delivery logs. No new notification API or external messaging provider is required. The existing Kafka notification service continues unchanged. Read state persists per username in this browser's local storage, not across devices. Previously recorded transfers appear in the inbox on login; only newly observed updates trigger a toast.

New transfer updates also appear as clickable six-second popups, with up to three visible at once. Hover or keyboard focus pauses dismissal. Clicking opens transfer details and marks the notification read; dismissing or timing out leaves it unread in the inbox. Popups are cleared on logout and customer changes.

## Transaction report PDF

Use Generate transaction report on Overview, My accounts, or Transactions. Select one owned account and inclusive From/To dates, then Download PDF. Dates use Asia/Manila calendar days (converted to UTC for database filtering). The default is the current month. Maximum period: 366 days, ending today or earlier.

Authenticated endpoint: GET /api/v1/auth/banking/reports/transactions.pdf?accountId=...&from=YYYY-MM-DD&to=YYYY-MM-DD. The service verifies account ownership and queries the selected range independently of the activity feed's 200-row limit. Up to 10,000 rows are supported; larger requests fail with a request to narrow the range rather than silently truncating. PDF responses are attachment downloads with Cache-Control: no-store.

PDFs contain PayPink branding, customer name, masked account, currency, period, generation time, transaction date/reference/status and money-in/out columns. Completed totals exclude pending/failed amounts. Dates represent submission dates; statuses are current at generation time. This is a transaction report, without inferred opening/closing balances. Pages repeat the period, totals, table headings, and page numbers. Empty date ranges produce a valid PDF with a no-transactions message.

Generated server-side with Apache PDFBox 3.0.8. Layout uses standard Helvetica PDF fonts; characters not supported by that font render as a question mark. Amounts are displayed to two decimal places. Banking-flow reference: https://www.bpi.com.ph/online/internet-banking-service-agreement.

Tests: TransactionReportServiceTest covers pagination beyond 200 records, masked numbers, summary exclusion of pending entries, empty output, ownership and date validation. `node tests/transaction-reports.cjs` checks the live authenticated PDF download and mobile date-picker layout with an isolated test customer.

## Consistent banking identifiers

Accounts use 12 numeric digits: `[Branch: 3][Type: 1][Random customer number: 7][Luhn check digit: 1]`. The local branch is `001`; type codes are `1` Savings, `2` Everyday, `3` legacy Checking, `4` Time Deposit, and `9` Stress Test. For example, `001112345671` is displayed as `001 1 1234567 1`. Account numbers are strings so leading zeros are retained. The seven-digit customer number comes from SecureRandom, is shared by the customer's accounts, and is unrelated to their database ID. Allocation excludes current and historical numbers; the database unique constraint and retries also handle simultaneous registration collisions.

The startup migration replaces earlier sequential and legacy account numbers, preserving valid structured numbers on subsequent restarts. It records old values in ACCOUNT_RENUMBERED audit entries, allowing both numeric and older alphanumeric numbers to resolve to the same account. Balances, account IDs, ownership, favorites, transaction references, and transaction relationships remain unchanged. No schema columns or tables are added. External bank fixture numbers are unchanged. This format supports one account per type code per customer; migration rejects duplicate type codes or unsupported types before changing any numbers.

Customer-visible transaction references are PP-YYYYMMDD-NNNNNNNNNNNN, using the submission date in Philippine time and the transaction ID padded to 12 digits. History, receipts, notifications, and PDF reports use this same reference. Internal reference_no values and historic audit/outbox payloads are deliberately preserved for idempotency and traceability; the public reference's numeric suffix identifies the transaction_id. No schema column changes are required: account_number remains VARCHAR2(30), reference_no remains VARCHAR2(64).

Verification includes checksum corruption, collision retries, leading zeros, migration restart stability, preserved balances/favorites, and transfer retries with both earlier account-number formats. `tests/account-number-migration.cjs prepare` creates isolated fixtures before deployment; `verify` checks the same accounts after migration, including legacy-number browser review and PDF generation. The customer-transfer browser suite also checks type prefixes, shared customer numbers and Luhn checksums. Tests leave their fixtures in the local database.

Verified locally on 2026-09-29: all 32 backend tests and the migration, transfer, and PDF browser checks passed. All 63 stored accounts passed branch/type, checksum, uniqueness and shared-customer-number checks. A service restart preserved their numbers, balances, ownership, statuses and migration audit count. Extracted PDF text for both migrated and newly registered accounts matched the receipt reference and masked account ending.
