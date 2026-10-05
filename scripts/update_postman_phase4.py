import json

with open('postman/PayPink_Retail_Ledger_Postman_Collection.json', 'r', encoding='utf-8') as f:
    data = json.load(f)

# Remove any existing Folder 9 if re-running
data['item'] = [item for item in data['item'] if not item['name'].startswith('9.')]

t24_folder = {
    'name': '9. Phase 4 — T24 Core Adapter & Simulator',
    'item': [
        {
            'name': 'GET T24 Adapter Health (no JWT needed)',
            'request': {
                'method': 'GET',
                'header': [],
                'url': {
                    'raw': '{{base_url}}/api/v1/t24/health',
                    'host': ['{{base_url}}'],
                    'path': ['api', 'v1', 't24', 'health']
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
                            'pm.test("service is t24-adapter", () => pm.expect(j.service).to.eql("t24-adapter"));',
                            'pm.test("version is 2.0.0", () => pm.expect(j.version).to.eql("2.0.0"));'
                        ],
                        'type': 'text/javascript'
                    }
                }
            ]
        },
        {
            'name': 'POST Transfer — Happy Path (T24 /1 POSTED)',
            'request': {
                'method': 'POST',
                'header': [
                    {'key': 'Authorization', 'value': 'Bearer {{jwt_token}}', 'type': 'text'},
                    {'key': 'Content-Type', 'value': 'application/json', 'type': 'text'}
                ],
                'body': {
                    'mode': 'raw',
                    'raw': '{\n  "referenceNo": "TX-PH-1791004427-abc1234",\n  "debitAccountNo": "1000100001",\n  "creditAccountNo": "1000100002",\n  "amount": 15000.0000,\n  "currency": "PHP"\n}'
                },
                'url': {
                    'raw': '{{base_url}}/api/v1/t24/transfer',
                    'host': ['{{base_url}}'],
                    'path': ['api', 'v1', 't24', 'transfer']
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
                            'pm.test("ftReference generated", () => pm.expect(j.ftReference).to.include("FT20261004"));',
                            'pm.test("ofsResponse format /1", () => pm.expect(j.ofsResponse).to.include("/1"));',
                            'pm.test("cachedResponse is false", () => pm.expect(j.cachedResponse).to.be.false);'
                        ],
                        'type': 'text/javascript'
                    }
                }
            ]
        },
        {
            'name': 'POST Transfer — Idempotency Replay (same ref)',
            'request': {
                'method': 'POST',
                'header': [
                    {'key': 'Authorization', 'value': 'Bearer {{jwt_token}}', 'type': 'text'},
                    {'key': 'Content-Type', 'value': 'application/json', 'type': 'text'}
                ],
                'body': {
                    'mode': 'raw',
                    'raw': '{\n  "referenceNo": "TX-PH-1791004427-abc1234",\n  "debitAccountNo": "1000100001",\n  "creditAccountNo": "1000100002",\n  "amount": 15000.0000,\n  "currency": "PHP"\n}'
                },
                'url': {
                    'raw': '{{base_url}}/api/v1/t24/transfer',
                    'host': ['{{base_url}}'],
                    'path': ['api', 'v1', 't24', 'transfer']
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
                            'pm.test("cachedResponse is TRUE (T24 Idempotency hit)", () => pm.expect(j.cachedResponse).to.be.true);'
                        ],
                        'type': 'text/javascript'
                    }
                }
            ]
        },
        {
            'name': 'POST Transfer — Simulated T24 Rejection (SIM-REJECT)',
            'request': {
                'method': 'POST',
                'header': [
                    {'key': 'Authorization', 'value': 'Bearer {{jwt_token}}', 'type': 'text'},
                    {'key': 'Content-Type', 'value': 'application/json', 'type': 'text'}
                ],
                'body': {
                    'mode': 'raw',
                    'raw': '{\n  "referenceNo": "TX-SIM-REJECT-001",\n  "debitAccountNo": "1000100001",\n  "creditAccountNo": "1000100002",\n  "amount": 5000.0000,\n  "currency": "PHP"\n}'
                },
                'url': {
                    'raw': '{{base_url}}/api/v1/t24/transfer',
                    'host': ['{{base_url}}'],
                    'path': ['api', 'v1', 't24', 'transfer']
                }
            },
            'event': [
                {
                    'listen': 'test',
                    'script': {
                        'exec': [
                            'pm.test("Status 422 Unprocessable Entity", () => pm.response.to.have.status(422));',
                            'var j = pm.response.json();',
                            'pm.test("status is REJECTED", () => pm.expect(j.status).to.eql("REJECTED"));',
                            'pm.test("ofsResponse format /-1", () => pm.expect(j.ofsResponse).to.include("/-1"));',
                            'pm.test("reason present", () => pm.expect(j.reason).to.include("Account Closed"));'
                        ],
                        'type': 'text/javascript'
                    }
                }
            ]
        },
        {
            'name': 'POST Transfer — Simulated SLA Timeout (SIM-TIMEOUT)',
            'request': {
                'method': 'POST',
                'header': [
                    {'key': 'Authorization', 'value': 'Bearer {{jwt_token}}', 'type': 'text'},
                    {'key': 'Content-Type', 'value': 'application/json', 'type': 'text'}
                ],
                'body': {
                    'mode': 'raw',
                    'raw': '{\n  "referenceNo": "TX-SIM-TIMEOUT-001",\n  "debitAccountNo": "1000100001",\n  "creditAccountNo": "1000100002",\n  "amount": 5000.0000,\n  "currency": "PHP"\n}'
                },
                'url': {
                    'raw': '{{base_url}}/api/v1/t24/transfer',
                    'host': ['{{base_url}}'],
                    'path': ['api', 'v1', 't24', 'transfer']
                }
            },
            'event': [
                {
                    'listen': 'test',
                    'script': {
                        'exec': [
                            'pm.test("Status 202 Accepted (Processing)", () => pm.response.to.have.status(202));',
                            'var j = pm.response.json();',
                            'pm.test("status is PROCESSING", () => pm.expect(j.status).to.eql("PROCESSING"));',
                            'pm.test("reason indicates SLA timeout", () => pm.expect(j.reason).to.include("timeout"));'
                        ],
                        'type': 'text/javascript'
                    }
                }
            ]
        },
        {
            'name': 'POST Transfer — Missing Field (400 expected)',
            'request': {
                'method': 'POST',
                'header': [
                    {'key': 'Authorization', 'value': 'Bearer {{jwt_token}}', 'type': 'text'},
                    {'key': 'Content-Type', 'value': 'application/json', 'type': 'text'}
                ],
                'body': {
                    'mode': 'raw',
                    'raw': '{\n  "amount": 5000.0000\n}'
                },
                'url': {
                    'raw': '{{base_url}}/api/v1/t24/transfer',
                    'host': ['{{base_url}}'],
                    'path': ['api', 'v1', 't24', 'transfer']
                }
            },
            'event': [
                {
                    'listen': 'test',
                    'script': {
                        'exec': [
                            'pm.test("Status 400 Bad Request", () => pm.response.to.have.status(400));'
                        ],
                        'type': 'text/javascript'
                    }
                }
            ]
        },
        {
            'name': 'POST Transfer — No JWT (401 expected)',
            'request': {
                'method': 'POST',
                'header': [
                    {'key': 'Content-Type', 'value': 'application/json', 'type': 'text'}
                ],
                'body': {
                    'mode': 'raw',
                    'raw': '{\n  "referenceNo": "TX-001",\n  "debitAccountNo": "1000100001",\n  "creditAccountNo": "1000100002",\n  "amount": 5000.0000\n}'
                },
                'url': {
                    'raw': '{{base_url}}/api/v1/t24/transfer',
                    'host': ['{{base_url}}'],
                    'path': ['api', 'v1', 't24', 'transfer']
                }
            },
            'event': [
                {
                    'listen': 'test',
                    'script': {
                        'exec': [
                            'pm.test("Status 401 Unauthorized", () => pm.response.to.have.status(401));'
                        ],
                        'type': 'text/javascript'
                    }
                }
            ]
        }
    ]
}

data['item'].append(t24_folder)

with open('postman/PayPink_Retail_Ledger_Postman_Collection.json', 'w', encoding='utf-8') as f:
    json.dump(data, f, indent=2)

print('Successfully added Folder 9 for Phase 4 to Postman collection!')
