import json

with open('postman/PayPink_Retail_Ledger_Postman_Collection.json', 'r', encoding='utf-8') as f:
    data = json.load(f)

# Remove any existing Folder 10 if re-running
data['item'] = [item for item in data['item'] if not item['name'].startswith('10.')]

phase5_folder = {
    'name': '10. Phase 5 — Remittance Orchestrator (Saga Engine)',
    'item': [
        {
            'name': 'GET Remittance Orchestrator Health (no JWT needed)',
            'request': {
                'method': 'GET',
                'header': [],
                'url': {
                    'raw': '{{base_url}}/api/v1/remittance/health',
                    'host': ['{{base_url}}'],
                    'path': ['api', 'v1', 'remittance', 'health']
                }
            },
            'event': [
                {
                    'listen': 'test',
                    'script': {
                        'exec': [
                            'pm.test("Status 200 OK", () => pm.response.to.have.status(200));',
                            'var j = pm.response.json();',
                            'pm.test("status is UP", () => pm.expect(j.status).to.eql("UP"));',
                            'pm.test("service is remittance-orchestrator", () => pm.expect(j.service).to.eql("remittance-orchestrator"));',
                            'pm.test("version is 2.0.0", () => pm.expect(j.version).to.eql("2.0.0"));'
                        ],
                        'type': 'text/javascript'
                    }
                }
            ]
        },
        {
            'name': 'POST Remittance Transfer — Happy Path (Saga Full Execution)',
            'request': {
                'method': 'POST',
                'header': [
                    {'key': 'Authorization', 'value': 'Bearer {{jwt_token}}', 'type': 'text'},
                    {'key': 'Content-Type', 'value': 'application/json', 'type': 'text'},
                    {'key': 'Idempotency-Key', 'value': 'SAGA-KEY-1001-HAPPY', 'type': 'text'}
                ],
                'body': {
                    'mode': 'raw',
                    'raw': '{\n  "sourceAccountId": "1000100001",\n  "targetAccountId": "1000100002",\n  "amount": 1500.00,\n  "currency": "PHP",\n  "description": "Family remittance via Saga Engine"\n}'
                },
                'url': {
                    'raw': '{{base_url}}/api/v1/remittance/transfer',
                    'host': ['{{base_url}}'],
                    'path': ['api', 'v1', 'remittance', 'transfer']
                }
            },
            'event': [
                {
                    'listen': 'test',
                    'script': {
                        'exec': [
                            'pm.test("Status 200 OK", () => pm.response.to.have.status(200));',
                            'var j = pm.response.json();',
                            'pm.test("status is POSTED", () => pm.expect(j.status).to.eql("POSTED"));',
                            'pm.test("riskDecision is PASS", () => pm.expect(j.riskDecision).to.eql("PASS"));',
                            'pm.test("ftReference generated", () => pm.expect(j.ftReference).to.include("FT20261004"));',
                            'pm.test("remittanceId exists", () => pm.expect(j.remittanceId).to.exist);',
                            'pm.test("cachedIdempotentResponse is false", () => pm.expect(j.cachedIdempotentResponse).to.be.false);'
                        ],
                        'type': 'text/javascript'
                    }
                }
            ]
        },
        {
            'name': 'POST Remittance Transfer — Idempotency Replay (Same Key)',
            'request': {
                'method': 'POST',
                'header': [
                    {'key': 'Authorization', 'value': 'Bearer {{jwt_token}}', 'type': 'text'},
                    {'key': 'Content-Type', 'value': 'application/json', 'type': 'text'},
                    {'key': 'Idempotency-Key', 'value': 'SAGA-KEY-1001-HAPPY', 'type': 'text'}
                ],
                'body': {
                    'mode': 'raw',
                    'raw': '{\n  "sourceAccountId": "1000100001",\n  "targetAccountId": "1000100002",\n  "amount": 1500.00,\n  "currency": "PHP",\n  "description": "Family remittance via Saga Engine"\n}'
                },
                'url': {
                    'raw': '{{base_url}}/api/v1/remittance/transfer',
                    'host': ['{{base_url}}'],
                    'path': ['api', 'v1', 'remittance', 'transfer']
                }
            },
            'event': [
                {
                    'listen': 'test',
                    'script': {
                        'exec': [
                            'pm.test("Status 200 OK", () => pm.response.to.have.status(200));',
                            'var j = pm.response.json();',
                            'pm.test("status is POSTED", () => pm.expect(j.status).to.eql("POSTED"));',
                            'pm.test("cachedIdempotentResponse is TRUE (Saga Idempotency hit)", () => pm.expect(j.cachedIdempotentResponse).to.be.true);'
                        ],
                        'type': 'text/javascript'
                    }
                }
            ]
        },
        {
            'name': 'POST Remittance Transfer — Risk Rejection (>100k Amount)',
            'request': {
                'method': 'POST',
                'header': [
                    {'key': 'Authorization', 'value': 'Bearer {{jwt_token}}', 'type': 'text'},
                    {'key': 'Content-Type', 'value': 'application/json', 'type': 'text'},
                    {'key': 'Idempotency-Key', 'value': 'SAGA-KEY-1002-RISK', 'type': 'text'}
                ],
                'body': {
                    'mode': 'raw',
                    'raw': '{\n  "sourceAccountId": "1000100001",\n  "targetAccountId": "1000100002",\n  "amount": 150000.00,\n  "currency": "PHP",\n  "description": "High amount suspicious transfer"\n}'
                },
                'url': {
                    'raw': '{{base_url}}/api/v1/remittance/transfer',
                    'host': ['{{base_url}}'],
                    'path': ['api', 'v1', 'remittance', 'transfer']
                }
            },
            'event': [
                {
                    'listen': 'test',
                    'script': {
                        'exec': [
                            'pm.test("Status 422 Unprocessable Entity", () => pm.response.to.have.status(422));',
                            'var j = pm.response.json();',
                            'pm.test("error mentions risk screening", () => pm.expect(j.error).to.include("rejected by risk screening"));'
                        ],
                        'type': 'text/javascript'
                    }
                }
            ]
        },
        {
            'name': 'POST Remittance Transfer — Core T24 Rejection (SIM-REJECT)',
            'request': {
                'method': 'POST',
                'header': [
                    {'key': 'Authorization', 'value': 'Bearer {{jwt_token}}', 'type': 'text'},
                    {'key': 'Content-Type', 'value': 'application/json', 'type': 'text'},
                    {'key': 'Idempotency-Key', 'value': 'SAGA-KEY-1003-SIMREJECT', 'type': 'text'}
                ],
                'body': {
                    'mode': 'raw',
                    'raw': '{\n  "sourceAccountId": "1000100001",\n  "targetAccountId": "SIM-REJECT",\n  "amount": 500.00,\n  "currency": "PHP",\n  "description": "Core bank rejection simulation"\n}'
                },
                'url': {
                    'raw': '{{base_url}}/api/v1/remittance/transfer',
                    'host': ['{{base_url}}'],
                    'path': ['api', 'v1', 'remittance', 'transfer']
                }
            },
            'event': [
                {
                    'listen': 'test',
                    'script': {
                        'exec': [
                            'pm.test("Status 422 Unprocessable Entity", () => pm.response.to.have.status(422));',
                            'var j = pm.response.json();',
                            'pm.test("error mentions T24 rejected transfer", () => pm.expect(j.error).to.include("Core banking T24 rejected transfer"));'
                        ],
                        'type': 'text/javascript'
                    }
                }
            ]
        }
    ]
}

data['item'].append(phase5_folder)

with open('postman/PayPink_Retail_Ledger_Postman_Collection.json', 'w', encoding='utf-8') as f:
    json.dump(data, f, indent=2)

print("Successfully added Folder 10 (Phase 5 — Remittance Orchestrator) to Postman collection!")
