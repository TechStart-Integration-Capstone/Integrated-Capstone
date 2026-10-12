# Manual reconciliation recovery

The admin **Run reconciliation** button checks the latest 50 Azure SQL ledger
transactions. It first attempts to recover missing PostgreSQL audit records from
their original `app.OUTBOX_EVENT` payloads, then reads PostgreSQL again to compute
the result. This supports local stacks sharing Azure SQL but using separate
PostgreSQL databases and Kafka brokers.

Recovery requires a successful ledger transaction and a complete original
`TRANSACTION_SUCCESS` or `REMITTANCE_COMPLETED` event. The transaction ID, reference,
source account, currency, amount, operation and before/after balance arithmetic must
agree. Multiple retained events must describe the same audit leg. An incomplete,
malformed or conflicting payload leaves the transaction drifted. No current balance
is substituted for missing historical data.

The operation only appends missing `LEDGER_MUTATION_AUDIT` records. It does not change
balances, replay transfers, reset outbox delivery state, republish notifications,
or overwrite existing audit discrepancies. The existing unique transaction/account
key prevents duplicate audit inserts. Original outbox event IDs are recorded in the
reconciliation service logs on recovery; the inserted row records its recovery time.
The scheduled/Kafka checks continue reporting discrepancies without initiating recovery.

The API/button reports checked, recovered, matched and remaining drift counts.
Missing source evidence and existing amount mismatches require separate investigation;
pressing the button does not guarantee all 50 records match. If a request fails after
some inserts, those committed records remain and a retry skips them. The frontend
does not automatically repeat the recovery request or claim success on failure.

Deploy from the updated project on the computer running the admin page:

```powershell
mvn -f microservices/reconciliation-service/pom.xml -DskipTests package
docker compose -f docker/docker-compose.yml up -d --build --force-recreate --no-deps reconciliation-service frontend
```

Use Java 17. No database migration is required. Refresh the browser after deployment
and run reconciliation again. Local validation: 36 backend unit tests with mocked
repositories/JDBC and three frontend request/result checks passed; the service JAR
was packaged. Native PostgreSQL/Azure SQL and live browser acceptance remain pending.
