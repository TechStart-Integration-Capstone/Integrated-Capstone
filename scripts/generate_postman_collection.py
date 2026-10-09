import json
import uuid

def create_collection():
    collection = {
        "info": {
            "_postman_id": str(uuid.uuid4()),
            "name": "PayPink 2.0 — Complete API Reference & Verification Suite",
            "description": "Comprehensive Postman Collection covering all 11 microservices and every API endpoint documented in docs/API_REFERENCE.md.\n\nIncludes dynamic token extraction, customer vs. admin authorization headers, idempotency keys, and automated assertion scripts.",
            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
        },
        "variable": [
            {"key": "base_url", "value": "http://localhost:8080", "type": "string"},
            {"key": "customer_username", "value": "jdelacruz", "type": "string"},
            {"key": "customer_password", "value": "password123", "type": "string"},
            {"key": "admin_username", "value": "admin", "type": "string"},
            {"key": "admin_password", "value": "Admin@PayPink2026!", "type": "string"},
            {"key": "customer_token", "value": "", "type": "string"},
            {"key": "admin_token", "value": "", "type": "string"},
            {"key": "customer_id", "value": "10", "type": "string"},
            {"key": "account_id", "value": "1", "type": "string"},
            {"key": "source_account_no", "value": "001181233469", "type": "string"},
            {"key": "target_account_no", "value": "001133218709", "type": "string"},
            {"key": "remittance_reference_no", "value": "", "type": "string"},
            {"key": "loan_reference_no", "value": "", "type": "string"},
            {"key": "loan_id", "value": "1", "type": "string"},
            {"key": "backfill_id", "value": "", "type": "string"}
        ],
        "item": []
    }

    # Helper function to create request items
    def make_req(name, method, url_path, headers=None, body=None, query_params=None, test_script=None, description=""):
        path_segments = [seg for seg in url_path.strip("/").split("/") if seg]
        req_headers = []
        if headers:
            for k, v in headers.items():
                req_headers.append({"key": k, "value": v, "type": "text"})

        url_obj = {
            "raw": "{{base_url}}/" + "/".join(path_segments) + (("?" + "&".join([f"{k}={v}" for k, v in query_params.items()])) if query_params else ""),
            "host": ["{{base_url}}"],
            "path": path_segments
        }
        if query_params:
            url_obj["query"] = [{"key": k, "value": str(v)} for k, v in query_params.items()]

        req_obj = {
            "name": name,
            "request": {
                "method": method,
                "header": req_headers,
                "url": url_obj,
                "description": description
            }
        }

        if body:
            req_obj["request"]["body"] = {
                "mode": "raw",
                "raw": json.dumps(body, indent=2) if isinstance(body, (dict, list)) else str(body),
                "options": {
                    "raw": {
                        "language": "json"
                    }
                }
            }

        events = []
        if test_script:
            events.append({
                "listen": "test",
                "script": {
                    "exec": test_script if isinstance(test_script, list) else [test_script],
                    "type": "text/javascript"
                }
            })
        if events:
            req_obj["event"] = events

        return req_obj

    # ─────────────────────────────────────────────────────────────────────────────
    # Folder 1: Authentication & Identity (auth-service :8081)
    # ─────────────────────────────────────────────────────────────────────────────
    auth_folder = {
        "name": "1. Authentication & Identity (auth-service :8081)",
        "item": [
            make_req(
                "POST Customer Login (/api/v1/auth/banking/login)",
                "POST",
                "api/v1/auth/banking/login",
                headers={"Content-Type": "application/json"},
                body={"username": "{{customer_username}}", "password": "{{customer_password}}"},
                test_script=[
                    "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                    "var jsonData = pm.response.json();",
                    "pm.test('Customer JWT token received', function () {",
                    "    pm.expect(jsonData.token).to.be.a('string');",
                    "    pm.collectionVariables.set('customer_token', jsonData.token);",
                    "    if (jsonData.customerId) pm.collectionVariables.set('customer_id', String(jsonData.customerId));",
                    "});"
                ],
                description="Authenticates a retail banking customer and captures JWT into {{customer_token}}."
            ),
            make_req(
                "POST Admin Login (/api/v1/auth/login)",
                "POST",
                "api/v1/auth/login",
                headers={"Content-Type": "application/json"},
                body={"username": "{{admin_username}}", "password": "{{admin_password}}"},
                test_script=[
                    "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                    "var jsonData = pm.response.json();",
                    "pm.test('Admin JWT token received with ROLE_ADMIN', function () {",
                    "    pm.expect(jsonData.token).to.be.a('string');",
                    "    pm.expect(jsonData.roles).to.include('ROLE_ADMIN');",
                    "    pm.collectionVariables.set('admin_token', jsonData.token);",
                    "});"
                ],
                description="Authenticates the administrator and stores admin JWT with ROLE_ADMIN into {{admin_token}}."
            ),
            make_req(
                "POST Customer Self-Registration (/api/v1/auth/banking/register)",
                "POST",
                "api/v1/auth/banking/register",
                headers={"Content-Type": "application/json"},
                body={
                    "username": "user_{{$randomInt}}",
                    "password": "Password123!",
                    "firstName": "Juan",
                    "lastName": "Dela Cruz",
                    "email": "user_{{$randomInt}}@paypink.ph",
                    "contactNo": "+639171234567"
                },
                test_script=[
                    "pm.test('Status code is 201 Created or 409 Conflict', function () {",
                    "    pm.expect(pm.response.code).to.be.oneOf([201, 409]);",
                    "});"
                ],
                description="Registers a new customer profile."
            ),
            make_req(
                "GET Legacy Profile (/api/v1/auth/banking/me)",
                "GET",
                "api/v1/auth/banking/me",
                headers={"Authorization": "Bearer {{customer_token}}"},
                test_script=["pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });"],
                description="Legacy profile endpoint in auth-service (superseded by /api/v1/accounts/me)."
            ),
            make_req(
                "GET Legacy Customer Transactions (/api/v1/auth/banking/transactions)",
                "GET",
                "api/v1/auth/banking/transactions",
                headers={"Authorization": "Bearer {{customer_token}}"},
                test_script=["pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });"],
                description="Legacy transactions feed in auth-service."
            ),
            make_req(
                "GET Legacy PDF Report (/api/v1/auth/banking/reports/transactions.pdf)",
                "GET",
                "api/v1/auth/banking/reports/transactions.pdf",
                headers={"Authorization": "Bearer {{customer_token}}"},
                query_params={"accountId": "{{account_id}}", "from": "2026-10-01", "to": "2026-10-08"},
                test_script=["pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });"],
                description="Legacy PDF statement in auth-service."
            ),
            make_req(
                "POST Legacy Transfer [Deprecated 410 Gone] (/api/v1/auth/banking/transfers)",
                "POST",
                "api/v1/auth/banking/transfers",
                headers={"Authorization": "Bearer {{customer_token}}", "Content-Type": "application/json"},
                body={"sourceAccountNumber": "001181233469", "targetAccountNumber": "001133218709", "amount": 100.00},
                test_script=[
                    "pm.test('Status code is 410 Gone (direct SQL bypass prevented)', function () {",
                    "    pm.response.to.have.status(410);",
                    "});"
                ],
                description="Confirms direct SQL update transfers are permanently deprecated with HTTP 410 Gone."
            ),
            make_req(
                "GET External Recipients (/api/v1/auth/banking/external/recipients)",
                "GET",
                "api/v1/auth/banking/external/recipients",
                headers={"Authorization": "Bearer {{customer_token}}"},
                test_script=["pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });"],
                description="Returns predefined external recipient banks."
            ),
            make_req(
                "GET External Transfers History (/api/v1/auth/banking/external/transfers)",
                "GET",
                "api/v1/auth/banking/external/transfers",
                headers={"Authorization": "Bearer {{customer_token}}"},
                test_script=["pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });"],
                description="Returns external transfer history for current user."
            ),
            make_req(
                "POST External Transfer (/api/v1/auth/banking/external/transfers)",
                "POST",
                "api/v1/auth/banking/external/transfers",
                headers={"Authorization": "Bearer {{customer_token}}", "Content-Type": "application/json"},
                body={
                    "sourceAccountNumber": "{{source_account_no}}",
                    "recipientBankCode": "BDO_UNIBANK",
                    "recipientAccountNumber": "10987654321",
                    "recipientName": "Maria Santos",
                    "amount": 250.00,
                    "channel": "INSTAPAY"
                },
                test_script=[
                    "pm.test('Status code is 200 or 400', function () {",
                    "    pm.expect(pm.response.code).to.be.oneOf([200, 400]);",
                    "});"
                ],
                description="Initiates an external interbank transfer (under active development)."
            )
        ]
    }
    collection["item"].append(auth_folder)

    # ─────────────────────────────────────────────────────────────────────────────
    # Folder 2: Account & Customer Domain (account-service :8082)
    # ─────────────────────────────────────────────────────────────────────────────
    account_folder = {
        "name": "2. Account & Customer Domain (account-service :8082)",
        "item": [
            make_req(
                "GET Signed-in Customer Profile (/api/v1/accounts/me)",
                "GET",
                "api/v1/accounts/me",
                headers={"Authorization": "Bearer {{customer_token}}"},
                test_script=[
                    "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                    "var jsonData = pm.response.json();",
                    "pm.test('Contains profile and accounts', function () {",
                    "    pm.expect(jsonData.username).to.be.a('string');",
                    "    pm.expect(jsonData.accounts).to.be.an('array');",
                    "    if (jsonData.accounts.length > 0) {",
                    "        pm.collectionVariables.set('account_id', String(jsonData.accounts[0].accountId));",
                    "        pm.collectionVariables.set('source_account_no', jsonData.accounts[0].accountNumber);",
                    "    }",
                    "});"
                ],
                description="Retrieves live user profile and balances for the caller identified by X-Auth-Customer-Id."
            ),
            make_req(
                "GET Accounts (Scoped to Customer) (/api/v1/accounts)",
                "GET",
                "api/v1/accounts",
                headers={"Authorization": "Bearer {{customer_token}}"},
                query_params={"page": "0", "size": "10"},
                test_script=[
                    "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                    "var jsonData = pm.response.json();",
                    "pm.test('Returns array of accounts for caller', function () { pm.expect(jsonData).to.be.an('array'); });"
                ],
                description="Returns caller's accounts when authenticated as customer, or all accounts when caller is admin (supports ?page=&size= pagination)."
            ),
            make_req(
                "GET Account by ID (/api/v1/accounts/{accountId})",
                "GET",
                "api/v1/accounts/{{account_id}}",
                headers={"Authorization": "Bearer {{customer_token}}"},
                test_script=[
                    "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                    "var jsonData = pm.response.json();",
                    "pm.test('Contains balance and status', function () {",
                    "    pm.expect(jsonData.currentBalance).to.not.be.undefined;",
                    "    pm.expect(jsonData.status).to.be.a('string');",
                    "});"
                ],
                description="Returns balance and details for the specified account ID (enforces ownership)."
            ),
            make_req(
                "GET Customer Profile by ID (/api/v1/accounts/customer/{customerId})",
                "GET",
                "api/v1/accounts/customer/{{customer_id}}",
                headers={"Authorization": "Bearer {{customer_token}}"},
                test_script=["pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });"],
                description="Returns customer details and linked accounts (enforces customer ownership)."
            ),
            make_req(
                "GET All Customers [Admin Only 👑] (/api/v1/accounts/customers)",
                "GET",
                "api/v1/accounts/customers",
                headers={"Authorization": "Bearer {{admin_token}}"},
                query_params={"page": "0", "size": "10"},
                test_script=[
                    "pm.test('Status code is 200 OK for Admin', function () { pm.response.to.have.status(200); });",
                    "var jsonData = pm.response.json();",
                    "pm.test('Returns all customers list', function () { pm.expect(jsonData).to.be.an('array'); });"
                ],
                description="Admin-only endpoint listing all registered customers in the system (supports ?page=&size= pagination)."
            ),
            make_req(
                "POST Update Account Status [Admin Only 👑] (/api/v1/accounts/{accountId}/status)",
                "POST",
                "api/v1/accounts/{{account_id}}/status",
                headers={"Authorization": "Bearer {{admin_token}}", "Content-Type": "application/json"},
                body={"status": "ACTIVE"},
                test_script=["pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });"],
                description="Sets account status (ACTIVE, FROZEN, CLOSED) — requires ROLE_ADMIN."
            ),
            make_req(
                "POST Reset Account Balance [Admin Only 👑] (/api/v1/accounts/{accountId}/reset-balance)",
                "POST",
                "api/v1/accounts/{{account_id}}/reset-balance",
                headers={"Authorization": "Bearer {{admin_token}}", "Content-Type": "application/json"},
                body={"targetBalance": 10000.00},
                test_script=["pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });"],
                description="Admin utility to reset test account balances for stress demonstrations."
            ),
            make_req(
                "GET Recipient Lookup (/api/v1/accounts/recipients/lookup)",
                "GET",
                "api/v1/accounts/recipients/lookup",
                headers={"Authorization": "Bearer {{customer_token}}"},
                query_params={"accountNumber": "{{target_account_no}}"},
                test_script=[
                    "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                    "var jsonData = pm.response.json();",
                    "pm.test('Recipient confirmed', function () { pm.expect(jsonData.fullName).to.be.a('string'); });"
                ],
                description="Looks up recipient account number before initiating a transfer."
            ),
            make_req(
                "GET Recipient Directory (/api/v1/accounts/recipients)",
                "GET",
                "api/v1/accounts/recipients",
                headers={"Authorization": "Bearer {{customer_token}}"},
                test_script=["pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });"],
                description="Returns favorite and recent recipients directory."
            ),
            make_req(
                "POST Save Favorite Recipient (/api/v1/accounts/favorites)",
                "POST",
                "api/v1/accounts/favorites",
                headers={"Authorization": "Bearer {{customer_token}}", "Content-Type": "application/json"},
                body={"accountNumber": "{{target_account_no}}"},
                test_script=["pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });"],
                description="Adds target account to customer's favorites list."
            ),
            make_req(
                "DELETE Remove Favorite Recipient (/api/v1/accounts/favorites/{accountNumber})",
                "DELETE",
                "api/v1/accounts/favorites/{{target_account_no}}",
                headers={"Authorization": "Bearer {{customer_token}}"},
                test_script=["pm.test('Status code is 204 No Content', function () { pm.response.to.have.status(204); });"],
                description="Removes target account from favorites."
            )
        ]
    }
    collection["item"].append(account_folder)

    # ─────────────────────────────────────────────────────────────────────────────
    # Folder 3: Remittance Saga Engine (transaction-service :8083)
    # ─────────────────────────────────────────────────────────────────────────────
    remittance_folder = {
        "name": "3. Remittance Saga Engine (transaction-service :8083)",
        "item": [
            make_req(
                "GET Remittance Health (Public) (/api/v1/remittance/health)",
                "GET",
                "api/v1/remittance/health",
                test_script=[
                    "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                    "pm.test('Liveness UP', function () { pm.expect(pm.response.json().status).to.eql('UP'); });"
                ],
                description="Public health check for the Remittance saga orchestrator."
            ),
            make_req(
                "POST Execute Distributed Remittance Saga (/api/v1/remittance/transfer)",
                "POST",
                "api/v1/remittance/transfer",
                headers={
                    "Authorization": "Bearer {{customer_token}}",
                    "Content-Type": "application/json",
                    "Idempotency-Key": "{{$guid}}"
                },
                body={
                    "sourceAccountId": "{{source_account_no}}",
                    "targetAccountId": "{{target_account_no}}",
                    "amount": 100.00,
                    "description": "Postman Remittance Verification"
                },
                test_script=[
                    "pm.test('Status code is 200 OK or 202 Accepted', function () {",
                    "    pm.expect(pm.response.code).to.be.oneOf([200, 202]);",
                    "});",
                    "var jsonData = pm.response.json();",
                    "if (jsonData.referenceNo) {",
                    "    pm.collectionVariables.set('remittance_reference_no', jsonData.referenceNo);",
                    "}"
                ],
                description="Executes the full 4-step distributed transfer saga (Idempotency -> Fraud scoring -> T24 hold -> Ledger posting)."
            ),
            make_req(
                "GET Remittance Status (/api/v1/remittance/{referenceNo}/status)",
                "GET",
                "api/v1/remittance/{{remittance_reference_no}}/status",
                headers={"Authorization": "Bearer {{customer_token}}"},
                test_script=["pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });"],
                description="Checks final settlement status for a remittance reference."
            ),
            make_req(
                "POST Cancel Remittance [Legacy 409] (/api/v1/remittance/{ref}/cancel)",
                "POST",
                "api/v1/remittance/{{remittance_reference_no}}/cancel",
                headers={"Authorization": "Bearer {{customer_token}}"},
                test_script=[
                    "pm.test('Returns 409 Conflict (window closed) or 400', function () {",
                    "    pm.expect(pm.response.code).to.be.oneOf([409, 400]);",
                    "});"
                ],
                description="Confirms cancellation is rejected once transfer is dispatched to T24 Core."
            ),
            make_req(
                "POST Send Now Remittance (/api/v1/remittance/{ref}/send-now)",
                "POST",
                "api/v1/remittance/{{remittance_reference_no}}/send-now",
                headers={"Authorization": "Bearer {{customer_token}}"},
                test_script=["pm.test('Status code is 200 OK or 409', function () { pm.expect(pm.response.code).to.be.oneOf([200, 409]); });"],
                description="Flushes transfer immediately to core banking."
            )
        ]
    }
    collection["item"].append(remittance_folder)

    # ─────────────────────────────────────────────────────────────────────────────
    # Folder 4: CQRS Transaction Activity & Statements (transaction-service :8083)
    # ─────────────────────────────────────────────────────────────────────────────
    cqrs_folder = {
        "name": "4. CQRS Transaction History & Statements (transaction-service :8083)",
        "item": [
            make_req(
                "GET Customer Activity Feed (/api/v1/transactions/activity)",
                "GET",
                "api/v1/transactions/activity",
                headers={"Authorization": "Bearer {{customer_token}}"},
                test_script=[
                    "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                    "var jsonData = pm.response.json();",
                    "pm.test('Activity list returned', function () { pm.expect(jsonData).to.be.an('array'); });"
                ],
                description="Retrieves customer transaction activity feed indexed by the CQRS read-store."
            ),
            make_req(
                "GET Download PDF Statement (/api/v1/transactions/reports/transactions.pdf)",
                "GET",
                "api/v1/transactions/reports/transactions.pdf",
                headers={"Authorization": "Bearer {{customer_token}}"},
                query_params={"accountId": "{{account_id}}", "from": "2026-10-01", "to": "2026-10-08"},
                test_script=[
                    "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                    "pm.test('Content-Type is application/pdf', function () {",
                    "    pm.expect(pm.response.headers.get('Content-Type')).to.include('application/pdf');",
                    "});"
                ],
                description="Generates and streams downloadable PDF bank statement."
            )
        ]
    }
    collection["item"].append(cqrs_folder)

    # ─────────────────────────────────────────────────────────────────────────────
    # Folder 5: Operations Desk & Admin Mutations (transaction-service :8083)
    # ─────────────────────────────────────────────────────────────────────────────
    admin_ops_folder = {
        "name": "5. Operations Desk & Admin Mutations (transaction-service :8083)",
        "item": [
            make_req(
                "GET Today Ledger Transactions [Admin Only 👑] (/api/v1/transactions/admin/today)",
                "GET",
                "api/v1/transactions/admin/today",
                headers={"Authorization": "Bearer {{admin_token}}"},
                test_script=[
                    "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                    "var jsonData = pm.response.json();",
                    "pm.test('Ledger movement rows returned', function () { pm.expect(jsonData).to.be.an('array'); });"
                ],
                description="Operations Desk live transaction monitor feed (Asia/Manila business date)."
            ),
            make_req(
                "POST Direct Ledger Mutate [Admin Only 👑] (/api/v1/ledger/mutate)",
                "POST",
                "api/v1/ledger/mutate",
                headers={
                    "Authorization": "Bearer {{admin_token}}",
                    "Content-Type": "application/json",
                    "Idempotency-Key": "{{$guid}}"
                },
                body={
                    "accountId": "{{account_id}}",
                    "amount": 50.00,
                    "type": "CREDIT"
                },
                test_script=[
                    "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                    "var jsonData = pm.response.json();",
                    "pm.test('Mutation successful', function () { pm.expect(jsonData.success).to.be.true; });"
                ],
                description="Direct ledger mutation with pessimistic row locking — restricted to ROLE_ADMIN."
            ),
            make_req(
                "POST Concurrency Double-Spend Test [Admin Only 👑] (/api/v1/stress/double-spend-test)",
                "POST",
                "api/v1/stress/double-spend-test",
                headers={"Authorization": "Bearer {{admin_token}}"},
                test_script=[
                    "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                    "var jsonData = pm.response.json();",
                    "pm.test('Only one debit wins', function () { pm.expect(jsonData.successfulDebits).to.eql(1); });"
                ],
                description="Spawns 10 concurrent debit threads against ₱60.00 account balance to prove double-spend prevention."
            ),
            make_req(
                "GET Telemetry Engine Stats [Admin Only 👑] (/api/v1/telemetry/stats)",
                "GET",
                "api/v1/telemetry/stats",
                headers={"Authorization": "Bearer {{admin_token}}"},
                test_script=["pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });"],
                description="Engine metrics, TPS rates, latency percentiles, and database pool statistics."
            )
        ]
    }
    collection["item"].append(admin_ops_folder)

    # ─────────────────────────────────────────────────────────────────────────────
    # Folder 6: Interest End-of-Day (EOD) (transaction-service :8083)
    # ─────────────────────────────────────────────────────────────────────────────
    interest_folder = {
        "name": "6. Interest End-of-Day (EOD) [Admin Only 👑] (transaction-service :8083)",
        "item": [
            make_req(
                "GET Interest Period Overview (/api/v1/interest/eod/overview)",
                "GET",
                "api/v1/interest/eod/overview",
                headers={"Authorization": "Bearer {{admin_token}}"},
                query_params={"periodEnd": "2026-10-31"},
                test_script=["pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });"],
                description="Status overview for the monthly interest accrual and posting cycle."
            ),
            make_req(
                "GET Missing Interest Accrual Days (/api/v1/interest/eod/missing)",
                "GET",
                "api/v1/interest/eod/missing",
                headers={"Authorization": "Bearer {{admin_token}}"},
                query_params={"periodEnd": "2026-10-31"},
                test_script=["pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });"],
                description="Returns list of dates missing EOD interest accruals."
            ),
            make_req(
                "POST Accrue Daily Interest (/api/v1/interest/eod/accrue)",
                "POST",
                "api/v1/interest/eod/accrue",
                headers={"Authorization": "Bearer {{admin_token}}"},
                query_params={"businessDate": "2026-10-07"},
                test_script=["pm.test('Status code is 200 OK or 409', function () { pm.expect(pm.response.code).to.be.oneOf([200, 409]); });"],
                description="Runs daily interest accrual calculation and stores snapshot in PostgreSQL."
            ),
            make_req(
                "POST Propose Missing Day Backfill (/api/v1/interest/eod/resolve)",
                "POST",
                "api/v1/interest/eod/resolve",
                headers={"Authorization": "Bearer {{admin_token}}", "Content-Type": "application/json"},
                query_params={"businessDate": "2026-10-06"},
                body={"reason": "Nightly maintenance simulated outage backfill", "useFallbackRates": True},
                test_script=[
                    "pm.test('Status code is 200 OK or 409 Conflict', function () {",
                    "    pm.expect(pm.response.code).to.be.oneOf([200, 409]);",
                    "});",
                    "var jsonData = pm.response.json();",
                    "if (jsonData.id) pm.collectionVariables.set('backfill_id', jsonData.id);"
                ],
                description="Creates an administrative proposal to backfill missed accruals."
            ),
            make_req(
                "GET Review Backfill Proposal (/api/v1/interest/eod/backfills/{id})",
                "GET",
                "api/v1/interest/eod/backfills/{{backfill_id}}",
                headers={"Authorization": "Bearer {{admin_token}}"},
                test_script=["pm.test('Status code is 200 OK or 404', function () { pm.expect(pm.response.code).to.be.oneOf([200, 404]); });"],
                description="Retrieves backfill proposal details for four-eyes approval."
            ),
            make_req(
                "POST Approve Backfill Proposal (/api/v1/interest/eod/backfills/{id}/approve)",
                "POST",
                "api/v1/interest/eod/backfills/{{backfill_id}}/approve",
                headers={"Authorization": "Bearer {{admin_token}}", "Content-Type": "application/json"},
                body={"approved": True, "notes": "Approved for month-end reconciliation"},
                test_script=["pm.test('Status code is 200 OK or 404', function () { pm.expect(pm.response.code).to.be.oneOf([200, 404]); });"],
                description="Executes approved backfill and records approver audit identity."
            ),
            make_req(
                "POST Post Month-End Interest (/api/v1/interest/eod/post)",
                "POST",
                "api/v1/interest/eod/post",
                headers={"Authorization": "Bearer {{admin_token}}"},
                query_params={"businessDate": "2026-10-31"},
                test_script=["pm.test('Status code is 200 OK or 409', function () { pm.expect(pm.response.code).to.be.oneOf([200, 409]); });"],
                description="Posts accrued monthly interest as credit journal movements into customer savings accounts."
            )
        ]
    }
    collection["item"].append(interest_folder)

    # ─────────────────────────────────────────────────────────────────────────────
    # Folder 7: T24 Core Banking System of Record (t24-adapter :8090)
    # ─────────────────────────────────────────────────────────────────────────────
    t24_folder = {
        "name": "7. T24 Core Banking System of Record (t24-adapter :8090)",
        "item": [
            make_req(
                "GET T24 Adapter Health (Public) (/api/v1/t24/health)",
                "GET",
                "api/v1/t24/health",
                test_script=[
                    "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                    "pm.test('T24 status UP', function () { pm.expect(pm.response.json().status).to.eql('UP'); });"
                ],
                description="Public liveness check for T24 Core Banking Adapter."
            ),
            make_req(
                "GET Authoritative Core Balance [Admin Only 👑] (/api/v1/t24/accounts/{acc}/balance)",
                "GET",
                "api/v1/t24/accounts/{{source_account_no}}/balance",
                headers={"Authorization": "Bearer {{admin_token}}"},
                test_script=[
                    "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                    "var jsonData = pm.response.json();",
                    "pm.test('Authoritative balances returned', function () {",
                    "    pm.expect(jsonData.currentBalance).to.not.be.undefined;",
                    "    pm.expect(jsonData.availableBalance).to.not.be.undefined;",
                    "    pm.expect(jsonData.heldBalance).to.not.be.undefined;",
                    "});"
                ],
                description="Direct SoR live balance inquiry from T24 Core engine."
            ),
            make_req(
                "POST Place Atomic Core Hold [Admin Only 👑] (/api/v1/t24/holds/lock)",
                "POST",
                "api/v1/t24/holds/lock",
                headers={"Authorization": "Bearer {{admin_token}}", "Content-Type": "application/json"},
                body={
                    "referenceNo": "HOLD-TEST-{{$timestamp}}",
                    "accountNumber": "{{source_account_no}}",
                    "amount": 200.00,
                    "currency": "PHP",
                    "holdDurationSeconds": 60
                },
                test_script=[
                    "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                    "var jsonData = pm.response.json();",
                    "pm.test('Hold placed in t24.LOCKED_AMOUNT', function () { pm.expect(jsonData.holdStatus).to.eql('ACTIVE'); });"
                ],
                description="Places an atomic hold on funds in t24.LOCKED_AMOUNT."
            ),
            make_req(
                "POST Release Core Hold [Admin Only 👑] (/api/v1/t24/holds/release)",
                "POST",
                "api/v1/t24/holds/release",
                headers={"Authorization": "Bearer {{admin_token}}", "Content-Type": "application/json"},
                body={"referenceNo": "HOLD-TEST-{{$timestamp}}"},
                test_script=["pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });"],
                description="Releases locked hold back to available balance."
            ),
            make_req(
                "POST Post Double-Entry Journal [Admin Only 👑] (/api/v1/t24/transfer)",
                "POST",
                "api/v1/t24/transfer",
                headers={"Authorization": "Bearer {{admin_token}}", "Content-Type": "application/json"},
                body={
                    "referenceNo": "TX-T24-{{$timestamp}}",
                    "debitAccountNo": "{{source_account_no}}",
                    "creditAccountNo": "{{target_account_no}}",
                    "amount": 150.00,
                    "currency": "PHP"
                },
                test_script=[
                    "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                    "var jsonData = pm.response.json();",
                    "pm.test('Status is POSTED', function () { pm.expect(jsonData.status).to.eql('POSTED'); });",
                    "pm.test('FT Reference received', function () { pm.expect(jsonData.ftReference).to.be.a('string'); });"
                ],
                description="Executes double-entry posting in t24.POSTING_JOURNAL."
            )
        ]
    }
    collection["item"].append(t24_folder)

    # ─────────────────────────────────────────────────────────────────────────────
    # Folder 8: Loan Products & Credit Lifecycle (loan-service :8091)
    # ─────────────────────────────────────────────────────────────────────────────
    loan_folder = {
        "name": "8. Loan Products & Credit Lifecycle (loan-service :8091)",
        "item": [
            make_req(
                "GET Loan Eligibility & Borrowing Capacity (/api/v1/loans/eligibility)",
                "GET",
                "api/v1/loans/eligibility",
                headers={"Authorization": "Bearer {{customer_token}}"},
                test_script=[
                    "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                    "var jsonData = pm.response.json();",
                    "pm.test('Credit limit returned', function () { pm.expect(jsonData.creditLimit).to.not.be.undefined; });"
                ],
                description="Checks customer credit limit, active loans, and remaining borrowing capacity."
            ),
            make_req(
                "POST Apply for Personal Loan (/api/v1/loans/applications)",
                "POST",
                "api/v1/loans/applications",
                headers={
                    "Authorization": "Bearer {{customer_token}}",
                    "Content-Type": "application/json",
                    "Idempotency-Key": "{{$guid}}"
                },
                body={
                    "accountId": "{{account_id}}",
                    "requestedAmount": 50000.00,
                    "requestedTerm": 12,
                    "monthlyIncome": 45000.00,
                    "purpose": "Home Improvement"
                },
                test_script=[
                    "pm.test('Status code is 201 Created or 200 OK', function () {",
                    "    pm.expect(pm.response.code).to.be.oneOf([200, 201]);",
                    "});",
                    "var jsonData = pm.response.json();",
                    "if (jsonData.referenceNo) {",
                    "    pm.collectionVariables.set('loan_reference_no', jsonData.referenceNo);",
                    "}"
                ],
                description="Applies for a personal loan and receives credit evaluation decision."
            ),
            make_req(
                "POST Accept Loan Offer & Disburse (/api/v1/loans/applications/{ref}/accept)",
                "POST",
                "api/v1/loans/applications/{{loan_reference_no}}/accept",
                headers={
                    "Authorization": "Bearer {{customer_token}}",
                    "Idempotency-Key": "{{$guid}}"
                },
                test_script=[
                    "pm.test('Status code is 201 Created or 409 Conflict', function () {",
                    "    pm.expect(pm.response.code).to.be.oneOf([201, 409]);",
                    "});",
                    "var jsonData = pm.response.json();",
                    "if (jsonData.loanId) {",
                    "    pm.collectionVariables.set('loan_id', String(jsonData.loanId));",
                    "}"
                ],
                description="Accepts approved loan offer and triggers disbursement transfer into customer account."
            ),
            make_req(
                "POST Admin Reset Application Status [Admin Only 👑] (/api/v1/loans/applications/{ref}/reset)",
                "POST",
                "api/v1/loans/applications/{{loan_reference_no}}/reset",
                headers={"Authorization": "Bearer {{admin_token}}"},
                test_script=[
                    "pm.test('Status code is 200 OK or 409 Conflict', function () {",
                    "    pm.expect(pm.response.code).to.be.oneOf([200, 409]);",
                    "});"
                ],
                description="Admin recovery route to reset failed application back to DECIDED for customer acceptance."
            ),
            make_req(
                "GET Customer Active Loans (/api/v1/loans)",
                "GET",
                "api/v1/loans",
                headers={"Authorization": "Bearer {{customer_token}}"},
                test_script=[
                    "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                    "var jsonData = pm.response.json();",
                    "pm.test('Loans array returned', function () { pm.expect(jsonData).to.be.an('array'); });"
                ],
                description="Returns all active and historical loans for authenticated customer."
            ),
            make_req(
                "GET Loan Amortization Schedule (/api/v1/loans/{loanId}/schedule)",
                "GET",
                "api/v1/loans/{{loan_id}}/schedule",
                headers={"Authorization": "Bearer {{customer_token}}"},
                test_script=[
                    "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                    "var jsonData = pm.response.json();",
                    "pm.test('Schedule rows returned', function () { pm.expect(jsonData.rows).to.be.an('array'); });"
                ],
                description="Returns monthly payment breakdown (principal, interest, balance) for a loan."
            ),
            make_req(
                "POST Repay Loan Installment (/api/v1/loans/{loanId}/repayments)",
                "POST",
                "api/v1/loans/{{loan_id}}/repayments",
                headers={
                    "Authorization": "Bearer {{customer_token}}",
                    "Content-Type": "application/json",
                    "Idempotency-Key": "{{$guid}}"
                },
                body={"amount": 4500.00},
                test_script=[
                    "pm.test('Status code is 201 Created or 200 OK', function () {",
                    "    pm.expect(pm.response.code).to.be.oneOf([200, 201]);",
                    "});"
                ],
                description="Repays scheduled monthly installment via internal core transfer."
            ),
            make_req(
                "POST Run Loan EOD Overdue Pass [Admin Only 👑] (/api/v1/loans/eod/run)",
                "POST",
                "api/v1/loans/eod/run",
                headers={"Authorization": "Bearer {{admin_token}}"},
                query_params={"businessDate": "2026-11-06"},
                test_script=["pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });"],
                description="Runs end-of-day overdue penalty assessments and auto-debit collection."
            )
        ]
    }
    collection["item"].append(loan_folder)

    # ─────────────────────────────────────────────────────────────────────────────
    # Folder 9: Fraud & Risk Engine (risk-engine :8000)
    # ─────────────────────────────────────────────────────────────────────────────
    risk_folder = {
        "name": "9. Fraud & Risk Engine (risk-engine :8000)",
        "item": [
            make_req(
                "GET Risk Engine Health (Public) (/api/v1/risk/health)",
                "GET",
                "api/v1/risk/health",
                test_script=[
                    "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                    "pm.test('Scorers active', function () { pm.expect(pm.response.json().status).to.be.oneOf(['UP', 'ok']); });"
                ],
                description="Public liveness and active models verification."
            ),
            make_req(
                "POST Evaluate Transaction Fraud Score [Admin Only 👑] (/api/v1/risk/score)",
                "POST",
                "api/v1/risk/score",
                headers={"Authorization": "Bearer {{admin_token}}", "Content-Type": "application/json"},
                body={
                    "sourceAccountId": "{{source_account_no}}",
                    "targetAccountId": "{{target_account_no}}",
                    "amount": 2500.00,
                    "accountAgeHours": 720.0,
                    "recentTxCount": 2,
                    "amountVsAvgRatio": 1.1
                },
                test_script=[
                    "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                    "var jsonData = pm.response.json();",
                    "pm.test('Fraud decision rendered', function () {",
                    "    pm.expect(jsonData.decision).to.be.oneOf(['APPROVE', 'REVIEW', 'REJECT']);",
                    "    pm.expect(jsonData.score).to.be.a('number');",
                    "});"
                ],
                description="Two-layer real-time fraud scoring combining deterministic business rules and Isolation Forest ML."
            )
        ]
    }
    collection["item"].append(risk_folder)

    # ─────────────────────────────────────────────────────────────────────────────
    # Folder 10: Supporting Services & Observability
    # ─────────────────────────────────────────────────────────────────────────────
    support_folder = {
        "name": "10. Supporting Services & Observability",
        "item": [
            make_req(
                "POST Trigger Reconciliation Sweep [Admin Only 👑] (/api/v1/reconciliation/run)",
                "POST",
                "api/v1/reconciliation/run",
                headers={"Authorization": "Bearer {{admin_token}}"},
                test_script=["pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });"],
                description="Triggers outbox vs. T24 posting journal reconciliation sweep."
            ),
            make_req(
                "GET Reconciliation Logs [Admin Only 👑] (/api/v1/reconciliation/logs)",
                "GET",
                "api/v1/reconciliation/logs",
                headers={"Authorization": "Bearer {{admin_token}}"},
                test_script=["pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });"],
                description="Returns results and discrepancies from recent sweeps."
            ),
            make_req(
                "GET Immutable Risk Audit Log [Admin Only 👑] (/api/v1/audit/risk-decisions)",
                "GET",
                "api/v1/audit/risk-decisions",
                headers={"Authorization": "Bearer {{admin_token}}"},
                test_script=[
                    "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                    "var jsonData = pm.response.json();",
                    "pm.test('Audit decisions list returned', function () { pm.expect(jsonData.content || jsonData).to.not.be.undefined; });"
                ],
                description="Retrieves immutable audit records for fraud decisions stored in PostgreSQL."
            ),
            make_req(
                "GET Risk Decision Stats [Admin Only 👑] (/api/v1/audit/risk-decisions/stats)",
                "GET",
                "api/v1/audit/risk-decisions/stats",
                headers={"Authorization": "Bearer {{admin_token}}"},
                test_script=["pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });"],
                description="Returns aggregated decision counts (APPROVE, REVIEW, REJECT) over 24h and 7d."
            ),
            make_req(
                "GET Analytics Real-Time Summary [Admin Only 👑] (/api/v1/analytics/summary)",
                "GET",
                "api/v1/analytics/summary",
                headers={"Authorization": "Bearer {{admin_token}}"},
                test_script=["pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });"],
                description="Aggregated transaction volumes, velocities, and throughput rates from Kafka streams."
            ),
            make_req(
                "GET Analytics Accounts Breakdown [Admin Only 👑] (/api/v1/analytics/accounts)",
                "GET",
                "api/v1/analytics/accounts",
                headers={"Authorization": "Bearer {{admin_token}}"},
                test_script=["pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });"],
                description="Per-account debit and credit metrics."
            ),
            make_req(
                "GET Analytics Recent Live Feed [Admin Only 👑] (/api/v1/analytics/recent)",
                "GET",
                "api/v1/analytics/recent",
                headers={"Authorization": "Bearer {{admin_token}}"},
                test_script=["pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });"],
                description="Last 50 streamed events from Kafka in-memory circular buffer."
            ),
            make_req(
                "GET Gateway Actuator Health (Public) (/actuator/health)",
                "GET",
                "actuator/health",
                test_script=[
                    "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                    "pm.test('Health UP', function () { pm.expect(pm.response.json().status).to.eql('UP'); });"
                ],
                description="Spring Boot Actuator health endpoint for API Gateway."
            ),
            make_req(
                "GET Gateway Actuator Prometheus (Public) (/actuator/prometheus)",
                "GET",
                "actuator/prometheus",
                test_script=["pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });"],
                description="Prometheus metric scrape endpoint."
            ),
            make_req(
                "GET Centralized Swagger UI Dashboard (Public) (/swagger-ui.html)",
                "GET",
                "swagger-ui.html",
                test_script=["pm.test('Status code is 200 OK or 302 Redirect', function () { pm.expect(pm.response.code).to.be.oneOf([200, 302]); });"],
                description="Centralized OpenAPI 3.0 / Swagger UI dashboard aggregating all PayPink 2.0 microservice APIs."
            ),
            make_req(
                "GET Aggregated OpenAPI Spec - Auth Service (Public) (/v3/api-docs/auth)",
                "GET",
                "v3/api-docs/auth",
                test_script=[
                    "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                    "var jsonData = pm.response.json();",
                    "pm.test('Contains OpenAPI specification', function () { pm.expect(jsonData.openapi).to.not.be.undefined; });"
                ],
                description="Returns raw OpenAPI 3.0 JSON specification for auth-service proxied through the Gateway."
            ),
            make_req(
                "GET Aggregated OpenAPI Spec - Account Service (Public) (/v3/api-docs/account)",
                "GET",
                "v3/api-docs/account",
                test_script=[
                    "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                    "var jsonData = pm.response.json();",
                    "pm.test('Contains OpenAPI specification', function () { pm.expect(jsonData.openapi).to.not.be.undefined; });"
                ],
                description="Returns raw OpenAPI 3.0 JSON specification for account-service proxied through the Gateway."
            ),
            make_req(
                "GET Aggregated OpenAPI Spec - Transaction Service (Public) (/v3/api-docs/transaction)",
                "GET",
                "v3/api-docs/transaction",
                test_script=[
                    "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                    "var jsonData = pm.response.json();",
                    "pm.test('Contains OpenAPI specification', function () { pm.expect(jsonData.openapi).to.not.be.undefined; });"
                ],
                description="Returns raw OpenAPI 3.0 JSON specification for transaction-service proxied through the Gateway."
            ),
            make_req(
                "GET Aggregated OpenAPI Spec - Loan Service (Public) (/v3/api-docs/loan)",
                "GET",
                "v3/api-docs/loan",
                test_script=[
                    "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                    "var jsonData = pm.response.json();",
                    "pm.test('Contains OpenAPI specification', function () { pm.expect(jsonData.openapi).to.not.be.undefined; });"
                ],
                description="Returns raw OpenAPI 3.0 JSON specification for loan-service proxied through the Gateway."
            ),
            make_req(
                "GET Aggregated OpenAPI Spec - T24 Core Adapter (Public) (/v3/api-docs/t24)",
                "GET",
                "v3/api-docs/t24",
                test_script=[
                    "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                    "var jsonData = pm.response.json();",
                    "pm.test('Contains OpenAPI specification', function () { pm.expect(jsonData.openapi).to.not.be.undefined; });"
                ],
                description="Returns raw OpenAPI 3.0 JSON specification for t24-adapter proxied through the Gateway."
            ),
            make_req(
                "GET Aggregated OpenAPI Spec - Risk Engine (Public) (/v3/api-docs/risk)",
                "GET",
                "v3/api-docs/risk",
                test_script=[
                    "pm.test('Status code is 200 OK', function () { pm.response.to.have.status(200); });",
                    "var jsonData = pm.response.json();",
                    "pm.test('Contains OpenAPI specification', function () { pm.expect(jsonData.openapi).to.not.be.undefined; });"
                ],
                description="Returns raw OpenAPI 3.0 JSON specification for risk-engine proxied through the Gateway."
            )
        ]
    }
    collection["item"].append(support_folder)

    return collection

if __name__ == "__main__":
    col = create_collection()
    out_path = "postman/PayPink_2.0_API_Reference_Collection.json"
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(col, f, indent=2)
    print(f"Generated {out_path} with {len(col['item'])} top-level categories and {sum(len(cat['item']) for cat in col['item'])} API requests.")
