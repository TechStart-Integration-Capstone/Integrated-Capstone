#!/usr/bin/env node
/**
 * PayPink 2.0 — End-to-End Distributed Integration Test Suite
 * 
 * Verifies live system behavior across API Gateway (:8080), microservices,
 * databases (Azure SQL & PostgreSQL), Redis idempotency, and Kafka async audit.
 * 
 * Usage:
 *   node scripts/run_integration_tests.mjs
 */

import { randomUUID } from 'node:crypto';

const BASE_URL = process.env.GATEWAY_URL || 'http://localhost:8080';
const PROMETHEUS_URL = process.env.PROMETHEUS_URL || 'http://localhost:9090';

// ANSI color escape codes for terminal formatting
const C = {
  reset: '\x1b[0m',
  bold: '\x1b[1m',
  dim: '\x1b[2m',
  green: '\x1b[32m',
  red: '\x1b[31m',
  yellow: '\x1b[33m',
  cyan: '\x1b[36m',
  magenta: '\x1b[35m',
  bgGreen: '\x1b[42m\x1b[30m',
  bgRed: '\x1b[41m\x1b[37m',
};

let passedCount = 0;
let failedCount = 0;
const testResults = [];

function assert(condition, message) {
  if (!condition) {
    throw new Error(`Assertion failed: ${message}`);
  }
}

async function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function testStep(name, fn) {
  const start = performance.now();
  process.stdout.write(`  ${C.dim}TEST${C.reset} ${name} ... `);
  try {
    const details = await fn();
    const elapsed = (performance.now() - start).toFixed(0);
    console.log(`${C.green}PASSED${C.reset} ${C.dim}(${elapsed}ms)${C.reset}${details ? ` ${C.cyan}[${details}]${C.reset}` : ''}`);
    passedCount++;
    testResults.push({ name, status: 'PASSED', elapsed, details });
  } catch (err) {
    const elapsed = (performance.now() - start).toFixed(0);
    console.log(`${C.red}FAILED${C.reset} ${C.dim}(${elapsed}ms)${C.reset}`);
    console.log(`    ${C.red}Error: ${err.message}${C.reset}`);
    failedCount++;
    testResults.push({ name, status: 'FAILED', elapsed, error: err.message });
  }
}

async function request(endpoint, options = {}) {
  const url = endpoint.startsWith('http') ? endpoint : `${BASE_URL}${endpoint}`;
  const res = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
  });
  let json = null;
  const text = await res.text();
  try {
    json = JSON.parse(text);
  } catch {
    json = text;
  }
  return { status: res.status, ok: res.ok, headers: res.headers, body: json };
}

async function main() {
  console.log(`\n${C.bold}${C.magenta}========================================================================${C.reset}`);
  console.log(`${C.bold}${C.magenta}  PAYPINK 2.0 — END-TO-END DISTRIBUTED INTEGRATION TEST SUITE           ${C.reset}`);
  console.log(`${C.bold}${C.magenta}========================================================================${C.reset}`);
  console.log(`  Gateway Endpoint : ${C.cyan}${BASE_URL}${C.reset}`);
  console.log(`  Execution Time   : ${new Date().toISOString()}`);
  console.log(`  Architecture     : 12 Microservices behind Spring Cloud Gateway\n`);

  let customerToken = '';
  let adminToken = '';
  let initialBalance = 0;
  let sourceAccountId = 1;
  let sourceAcc = '001181233469';
  let targetAcc = '001113321879';
  const transferAmount = 25.00;
  const testIdempotencyKey = `E2E-TEST-${randomUUID()}`;
  let transferReceipt = null;

  // =========================================================================
  // SUITE 1: PERIMETER INGRESS & AUTHENTICATION
  // =========================================================================
  console.log(`${C.bold}1. PERIMETER INGRESS & AUTHENTICATION (RBAC & JWT)${C.reset}`);

  await testStep('Customer Login (lviernes) via /api/v1/auth/banking/login', async () => {
    const res = await request('/api/v1/auth/banking/login', {
      method: 'POST',
      body: JSON.stringify({ username: 'lviernes', password: 'password123' }),
    });
    assert(res.status === 200, `Expected 200 OK, got ${res.status}`);
    assert(res.body && res.body.token, 'Token must not be empty');
    assert(res.body.fullName === 'Levi Viernes', `Expected Levi Viernes, got ${res.body.fullName}`);
    customerToken = res.body.token;
    return `JWT verified for ${res.body.fullName}`;
  });

  await testStep('Admin Login via /api/v1/auth/login with ROLE_ADMIN', async () => {
    const res = await request('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username: 'admin', password: 'Admin@PayPink2026!' }),
    });
    assert(res.status === 200, `Expected 200 OK, got ${res.status}`);
    assert(res.body && res.body.token, 'Admin token must not be empty');
    assert(Array.isArray(res.body.roles) && res.body.roles.includes('ROLE_ADMIN'), 'Must contain ROLE_ADMIN');
    adminToken = res.body.token;
    return `Roles: ${res.body.roles.join(', ')}`;
  });

  await testStep('Authentication Boundary: Reject invalid credentials with 401', async () => {
    const res = await request('/api/v1/auth/banking/login', {
      method: 'POST',
      body: JSON.stringify({ username: 'lviernes', password: 'wrongpassword' }),
    });
    assert(res.status === 401, `Expected 401 Unauthorized, got ${res.status}`);
    return `Correctly blocked with 401`;
  });

  // =========================================================================
  // SUITE 2: ACCOUNT DOMAIN & READ MODELS
  // =========================================================================
  console.log(`\n${C.bold}2. ACCOUNT DOMAIN & LIVE BALANCE QUERIES${C.reset}`);

  await testStep('Customer Profile Inquiry (/api/v1/accounts/me)', async () => {
    const res = await request('/api/v1/accounts/me', {
      headers: { Authorization: `Bearer ${customerToken}` },
    });
    assert(res.status === 200, `Expected 200 OK, got ${res.status}`);
    assert(Array.isArray(res.body.accounts) && res.body.accounts.length > 0, 'Accounts list must not be empty');
    const primary = res.body.accounts[0];
    sourceAccountId = primary.accountId;
    sourceAcc = primary.accountNumber;
    return `Accounts: ${res.body.accounts.length}, Primary: ${sourceAcc}`;
  });

  await testStep(`Live Account Inquiry (/api/v1/accounts/${sourceAccountId})`, async () => {
    const res = await request(`/api/v1/accounts/${sourceAccountId}`, {
      headers: { Authorization: `Bearer ${customerToken}` },
    });
    assert(res.status === 200, `Expected 200 OK, got ${res.status}`);
    assert(typeof res.body.currentBalance === 'number', 'Current balance must be a number');
    initialBalance = res.body.currentBalance;
    return `Account: ${res.body.accountNumber}, Current Balance: ₱${initialBalance.toLocaleString('en-US', { minimumFractionDigits: 2 })}`;
  });

  await testStep('Beneficiaries & Recipients Directory (/api/v1/accounts/recipients)', async () => {
    const res = await request('/api/v1/accounts/recipients', {
      headers: { Authorization: `Bearer ${customerToken}` },
    });
    assert(res.status === 200, `Expected 200 OK, got ${res.status}`);
    assert(res.body && Array.isArray(res.body.recent), 'Expected recent recipients array');
    if (res.body.recent.length > 0) {
      targetAcc = res.body.recent[0].accountNumber;
    }
    return `Found recipient: ${targetAcc}`;
  });

  // =========================================================================
  // SUITE 3: 4-STEP DISTRIBUTED REMITTANCE SAGA
  // =========================================================================
  console.log(`\n${C.bold}3. DISTRIBUTED REMITTANCE SAGA (FRAUD ML -> T24 HOLD -> SETTLEMENT)${C.reset}`);

  await testStep(`Execute ₱${transferAmount} Transfer (${sourceAcc} -> ${targetAcc})`, async () => {
    const res = await request('/api/v1/remittance/transfer', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${customerToken}`,
        'Idempotency-Key': testIdempotencyKey,
      },
      body: JSON.stringify({
        sourceAccountId: sourceAcc,
        targetAccountId: targetAcc,
        amount: transferAmount,
        description: 'Automated E2E Integration Test Run',
        channel: 'WEB',
      }),
    });
    assert(res.status === 200, `Expected 200 OK, got ${res.status}: ${JSON.stringify(res.body)}`);
    transferReceipt = res.body;
    assert(transferReceipt.status === 'POSTED', `Expected POSTED status, got ${transferReceipt.status}`);
    assert(transferReceipt.ftReference && transferReceipt.ftReference.startsWith('FT'), `Expected T24 FT reference, got ${transferReceipt.ftReference}`);
    assert(transferReceipt.riskDecision === 'APPROVE', `Expected APPROVE risk decision, got ${transferReceipt.riskDecision}`);
    assert(typeof transferReceipt.riskScore === 'number', 'Expected numeric risk score from Risk Engine');
    assert(transferReceipt.cachedIdempotentResponse === false, 'First call must not be cached');

    const expectedBalance = initialBalance - transferAmount;
    assert(Math.abs(transferReceipt.afterBalance - expectedBalance) < 0.01,
      `Balance mismatch: expected ${expectedBalance}, got ${transferReceipt.afterBalance}`);

    return `Ref: ${transferReceipt.referenceNo}, T24 FT: ${transferReceipt.ftReference}, Risk: ${transferReceipt.riskScore.toFixed(4)}`;
  });

  // =========================================================================
  // SUITE 4: IDEMPOTENCY & DOUBLE-SPENDING PREVENTION
  // =========================================================================
  console.log(`\n${C.bold}4. CONCURRENCY & IDEMPOTENCY PROTECTION (REDIS MATRIX)${C.reset}`);

  await testStep('Idempotency Replay: Re-submit identical key & payload', async () => {
    const res = await request('/api/v1/remittance/transfer', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${customerToken}`,
        'Idempotency-Key': testIdempotencyKey,
      },
      body: JSON.stringify({
        sourceAccountId: sourceAcc,
        targetAccountId: targetAcc,
        amount: transferAmount,
        description: 'Automated E2E Integration Test Run',
        channel: 'WEB',
      }),
    });
    assert(res.status === 200, `Expected 200 OK, got ${res.status}`);
    assert(res.body.cachedIdempotentResponse === true, 'Must return cachedIdempotentResponse: true');
    assert(res.body.referenceNo === transferReceipt.referenceNo, 'Must return same transaction reference');
    assert(res.body.afterBalance === transferReceipt.afterBalance, 'Balance must NOT be deducted a second time');
    return `Cached receipt returned safely without double-debit`;
  });

  await testStep('Business Rule Guard: Reject transfers exceeding account balance', async () => {
    const hugeAmount = 999999999.00;
    const res = await request('/api/v1/remittance/transfer', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${customerToken}`,
        'Idempotency-Key': `HUGE-TEST-${randomUUID()}`,
      },
      body: JSON.stringify({
        sourceAccountId: sourceAcc,
        targetAccountId: targetAcc,
        amount: hugeAmount,
        description: 'Exceeding balance',
        channel: 'WEB',
      }),
    });
    assert(res.status === 400 || res.status === 422, `Expected 400/422 rejection, got ${res.status}`);
    return `Correctly blocked over-limit transfer with status ${res.status}`;
  });

  // =========================================================================
  // SUITE 5: CQRS READ-MODEL & ASYNC AUDIT CONSISTENCY
  // =========================================================================
  console.log(`\n${C.bold}5. CQRS ACTIVITY FEED & ASYNC AUDIT STREAM${C.reset}`);

  await testStep('CQRS Activity Feed: Verify newly posted transaction appears', async () => {
    const res = await request('/api/v1/transactions/activity', {
      headers: { Authorization: `Bearer ${customerToken}` },
    });
    assert(res.status === 200, `Expected 200 OK, got ${res.status}`);
    assert(Array.isArray(res.body), 'Expected array of activity items');
    const matched = res.body.find(tx => tx.transactionId === transferReceipt.transactionId);
    assert(matched != null, `Transaction ${transferReceipt.transactionId} not found in CQRS activity feed`);
    assert(matched.status === 'SUCCESS', `Expected SUCCESS status, got ${matched.status}`);
    return `Transaction ${matched.transactionId} (${matched.operation} ₱${matched.amount}) indexed in CQRS store`;
  });

  await testStep('Immutable Risk Decisions Audit (PostgreSQL Store)', async () => {
    // Give Kafka consumer a moment to commit to PostgreSQL
    await sleep(500);
    const res = await request('/api/v1/audit/risk-decisions', {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    assert(res.status === 200, `Expected 200 OK, got ${res.status}`);
    const list = Array.isArray(res.body) ? res.body : (res.body?.content || []);
    assert(Array.isArray(list) && list.length > 0, 'Expected non-empty list of risk decisions');
    const matched = list.find(d => d.referenceNo === transferReceipt.referenceNo);
    assert(matched != null, `Audit record for reference ${transferReceipt.referenceNo} not found in PostgreSQL store`);
    assert(matched.decision === 'APPROVE', `Expected APPROVE decision, got ${matched.decision}`);
    return `Audit record verified in PostgreSQL (Ref: ${matched.referenceNo})`;
  });

  // =========================================================================
  // SUITE 6: INTEREST EOD DOMAIN
  // =========================================================================
  console.log(`\n${C.bold}6. INTEREST END-OF-DAY (EOD) DOMAIN${C.reset}`);

  await testStep('Interest EOD Overview API (/api/v1/interest/eod/overview)', async () => {
    const res = await request('/api/v1/interest/eod/overview', {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    assert(res.status === 200, `Expected 200 OK, got ${res.status}`);
    assert(res.body.startDate != null, 'Expected startDate in interest overview');
    assert(res.body.postingStatus != null, 'Expected postingStatus in interest overview');
    assert(Array.isArray(res.body.missingDays), 'Expected missingDays array');
    return `Start: ${res.body.startDate}, Posting Status: ${res.body.postingStatus}, Missing Days: ${res.body.missingDays.length}`;
  });

  // =========================================================================
  // SUITE 7: OBSERVABILITY & SYSTEM HEALTH
  // =========================================================================
  console.log(`\n${C.bold}7. OBSERVABILITY & FLEET HEALTH (PROMETHEUS 12/12)${C.reset}`);

  await testStep('Prometheus Telemetry: Verify all 12 microservice scrape targets are UP', async () => {
    const res = await request(`${PROMETHEUS_URL}/api/v1/targets`);
    assert(res.status === 200, `Expected 200 OK from Prometheus, got ${res.status}`);
    const targets = res.body?.data?.activeTargets || [];
    assert(targets.length >= 12, `Expected at least 12 scrape targets, found ${targets.length}`);
    const unhealthy = targets.filter(t => t.health !== 'up');
    assert(unhealthy.length === 0, `Found unhealthy scrape targets: ${unhealthy.map(t => t.scrapeUrl).join(', ')}`);
    return `12/12 active targets healthy (t24-adapter, gateway, transaction-service, etc.)`;
  });

  // =========================================================================
  // SUMMARY REPORT
  // =========================================================================
  console.log(`\n${C.bold}${C.magenta}========================================================================${C.reset}`);
  console.log(`${C.bold}  INTEGRATION TEST RESULTS SUMMARY${C.reset}`);
  console.log(`${C.bold}${C.magenta}========================================================================${C.reset}`);
  console.log(`  Total Tests  : ${C.bold}${passedCount + failedCount}${C.reset}`);
  console.log(`  Passed       : ${C.green}${C.bold}${passedCount}${C.reset}`);
  console.log(`  Failed       : ${failedCount === 0 ? C.green : C.red}${C.bold}${failedCount}${C.reset}`);

  if (failedCount === 0) {
    console.log(`\n  ${C.bgGreen}${C.bold} ALL INTEGRATION TESTS PASSED SUCCESSFULLY! (100% HEALTHY) ${C.reset}\n`);
    process.exit(0);
  } else {
    console.log(`\n  ${C.bgRed}${C.bold} SOME INTEGRATION TESTS FAILED. CHECK DETAILS ABOVE. ${C.reset}\n`);
    process.exit(1);
  }
}

main().catch(err => {
  console.error(`\n${C.red}Fatal execution error: ${err.message}${C.reset}`);
  process.exit(1);
});
