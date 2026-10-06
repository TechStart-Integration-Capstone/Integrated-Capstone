const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function app() {
    const session = new Map();
    const elements = {
        'monitor-transactions-body': { innerHTML: '' },
        'monitor-status-filter': { value: 'ALL' },
        'monitor-type-filter': { value: 'ALL' },
        'modal-transaction-lifecycle': {
            classList: {
                classes: new Set(),
                add(c) { this.classes.add(c); },
                remove(c) { this.classes.delete(c); },
                contains(c) { return this.classes.has(c); }
            }
        },
        'lifecycle-metadata': { innerHTML: '' },
        'lifecycle-stepper-container': { innerHTML: '' }
    };
    const context = vm.createContext({
        window: {}, console, AbortSignal, atob,
        document: { addEventListener() {}, getElementById: id => elements[id] },
        sessionStorage: {
            getItem: key => session.get(key) ?? null,
            setItem: (key, value) => session.set(key, value),
            removeItem: key => session.delete(key)
        },
        localStorage: { getItem() { throw new Error('Monitor must not load cached simulation rows'); } }
    });
    vm.runInContext(fs.readFileSync(path.join(__dirname, '../src/js/app.js'), 'utf8'), context);
    return { context, elements, session, run: code => vm.runInContext(code, context), html: () => elements['monitor-transactions-body'].innerHTML };
}

function row(id, reference, date = new Date().toISOString(), operation = 'DEBIT') {
    return { transactionId: String(id), referenceNo: reference, transactionDate: date,
        accountNumber: '001181233469', transactionType: 'TRANSFER_INSTAPAY',
        operation, amount: 10, currency: 'PHP', status: 'SUCCESS' };
}

test('only API rows from today are shown, newest first with stable ID ties', async () => {
    const a = app();
    const now = new Date();
    const earlierToday = new Date(now.getTime() - 1).toISOString();
    const rows = [row(1, 'OLDER', earlierToday), row(2, 'TIE-LOW'), row(3, 'TIE-HIGH'),
        row(4, 'YESTERDAY', new Date(now.getTime() - 86400000).toISOString()),
        row(5, 'TOMORROW', new Date(now.getTime() + 86400000).toISOString())];
    rows[1].transactionDate = rows[2].transactionDate = now.toISOString();
    a.context.fetch = async (url, options) => {
        assert.equal(url, '/api/v1/auth/admin/transactions/today');
        assert.equal(options.cache, 'no-store');
        return { ok: true, json: async () => rows };
    };
    a.run("recentTransactions = [{ref:'LOCAL-FAKE', type:'CREDIT', amount:1000}]");
    await a.run('loadRecentTransactions()');
    assert.ok(a.html().indexOf('TIE-HIGH') < a.html().indexOf('TIE-LOW'));
    assert.ok(a.html().indexOf('TIE-LOW') < a.html().indexOf('OLDER'));
    assert.doesNotMatch(a.html(), /LOCAL-FAKE|YESTERDAY|TOMORROW|IDEMP-PH/);
});

test('today uses Philippine midnight regardless of browser timezone', () => {
    const a = app();
    assert.equal(a.run("monitorDateKey('2026-10-02T16:00:00Z') === monitorDateKey('2026-10-03T15:59:59Z')"), true);
    assert.equal(a.run("monitorDateKey('2026-10-03T16:00:00Z') === monitorDateKey('2026-10-03T15:59:59Z')"), false);
});

test('uses stored operation, filters rows, and escapes database text', async () => {
    const a = app();
    a.context.fetch = async () => ({ ok: true, json: async () => [row(1, '<img src=x onerror=alert(1)>')] });
    await a.run('loadRecentTransactions()');
    assert.match(a.html(), /&lt;img/);
    assert.doesNotMatch(a.html(), /<img|\+₱/); // INSTAPAY must not imply a credit.
    assert.match(a.html(), /-₱10/);
    a.elements['monitor-type-filter'].value = 'CREDIT';
    a.run('renderTransactionMonitor()');
    assert.match(a.html(), /No transactions today/);
    a.elements['monitor-type-filter'].value = 'DEBIT';
    a.run('renderTransactionMonitor()');
    assert.match(a.html(), /&lt;img/);
    a.elements['monitor-status-filter'].value = 'PENDING';
    a.run('renderTransactionMonitor()');
    assert.match(a.html(), /No transactions today/);
});

test('empty or failed API responses never display cached or stale records', async () => {
    const a = app();
    a.context.fetch = async () => ({ ok: true, json: async () => [row(1, 'REAL-ROW')] });
    await a.run('loadRecentTransactions()');
    assert.match(a.html(), /REAL-ROW/);
    a.context.fetch = async () => ({ ok: false, status: 503 });
    await a.run('loadRecentTransactions()');
    assert.match(a.html(), /Unable to load/);
    assert.doesNotMatch(a.html(), /REAL-ROW/);
    a.context.fetch = async () => ({ ok: true, json: async () => [] });
    await a.run('loadRecentTransactions()');
    assert.match(a.html(), /No transactions today/);
    assert.doesNotMatch(a.html(), /REAL-ROW|TX-PH-INIT/);
});

test('overlapping refreshes share one request', async () => {
    const a = app();
    let resolve;
    let calls = 0;
    a.context.fetch = () => { calls++; return new Promise(r => { resolve = r; }); };
    const first = a.run('loadRecentTransactions()');
    const second = a.run('loadRecentTransactions()');
    assert.equal(calls, 1);
    resolve({ ok: true, json: async () => [] });
    await Promise.all([first, second]);
    assert.match(a.html(), /No transactions today/);
});

test('simulation events cannot replace database rows when the monitor rerenders', async () => {
    const a = app();
    a.context.fetch = async () => ({ ok: true, json: async () => [row(1, 'DB-ROW')] });
    await a.run('loadRecentTransactions()');
    a.run("recentTransactions.unshift({ref:'SIMULATED',type:'CREDIT',amount:1000}); renderTransactionMonitor()");
    assert.match(a.html(), /DB-ROW/);
    assert.doesNotMatch(a.html(), /SIMULATED/);
});

test('expired authorization clears rows and asks for administrator sign-in', async () => {
    const a = app();
    a.run("currentJwtToken = 'expired-admin-token'");
    a.context.fetch = async () => ({ ok: true, json: async () => [row(1, 'DB-ROW')] });
    await a.run('loadRecentTransactions()');
    a.context.fetch = async (url, options) => {
        assert.equal(options.headers.Authorization, 'Bearer expired-admin-token');
        return { ok: false, status: 401 };
    };
    await a.run('loadRecentTransactions()');
    assert.match(a.html(), /Please sign in as an administrator/);
    assert.doesNotMatch(a.html(), /DB-ROW/);
});

test('logout clears the monitor and discards responses still in flight', async () => {
    const a = app();
    let resolve;
    a.run("currentJwtToken = 'admin-token'; showAdminToast = () => {}");
    a.context.fetch = () => new Promise(r => { resolve = r; });
    const pending = a.run('loadRecentTransactions()');
    a.run('handleAdminLogout()');
    resolve({ ok: true, json: async () => [row(1, 'PRIVATE-ROW')] });
    await pending;
    assert.match(a.html(), /Please sign in as an administrator/);
    assert.doesNotMatch(a.html(), /PRIVATE-ROW/);
});

test('a cached customer token cannot masquerade as an administrator session', async () => {
    const a = app();
    const claims = Buffer.from(JSON.stringify({roles:['ROLE_CUSTOMER'],exp:Date.now()/1000+3600})).toString('base64url');
    a.session.set('paypink_admin_jwt', `header.${claims}.signature`);
    a.session.set('paypink_admin_user', JSON.stringify({ roles: ['ROLE_ADMIN'] }));
    await a.run('initializeAuthSession()');
    assert.equal(a.run('currentJwtToken'), null);
    assert.equal(a.session.has('paypink_admin_jwt'), false);
});

test('dashboard status normalization and filtering for the 7 statuses', async () => {
    const a = app();
    // Test direct normalization function
    assert.equal(a.run("normalizeDashboardStatus('SUCCESS')"), 'Posted');
    assert.equal(a.run("normalizeDashboardStatus('COMPLETED')"), 'Posted');
    assert.equal(a.run("normalizeDashboardStatus('RESERVED')"), 'Reserved');
    assert.equal(a.run("normalizeDashboardStatus('PENDING')"), 'Reserved');
    assert.equal(a.run("normalizeDashboardStatus('AUTHORIZED')"), 'Authorized');
    assert.equal(a.run("normalizeDashboardStatus('PROCESSING')"), 'Processing');
    assert.equal(a.run("normalizeDashboardStatus('FAILED')"), 'Failed');
    assert.equal(a.run("normalizeDashboardStatus('CANCELLED')"), 'Cancelled');
    assert.equal(a.run("normalizeDashboardStatus('INITIATED')"), 'Initiated');

    // Test row rendering and filtering
    const rows = [
        { transactionId: '1', referenceNo: 'TX-INIT', transactionDate: new Date().toISOString(), accountNumber: '001181233469', transactionType: 'TRANSFER', operation: 'DEBIT', amount: 100, currency: 'PHP', status: 'Initiated' },
        { transactionId: '2', referenceNo: 'TX-AUTH', transactionDate: new Date().toISOString(), accountNumber: '001181233469', transactionType: 'TRANSFER', operation: 'DEBIT', amount: 200, currency: 'PHP', status: 'Authorized' },
        { transactionId: '3', referenceNo: 'TX-RESV', transactionDate: new Date().toISOString(), accountNumber: '001181233469', transactionType: 'TRANSFER', operation: 'DEBIT', amount: 300, currency: 'PHP', status: 'Reserved' },
        { transactionId: '4', referenceNo: 'TX-PROC', transactionDate: new Date().toISOString(), accountNumber: '001181233469', transactionType: 'TRANSFER', operation: 'DEBIT', amount: 400, currency: 'PHP', status: 'Processing' },
        { transactionId: '5', referenceNo: 'TX-POST', transactionDate: new Date().toISOString(), accountNumber: '001181233469', transactionType: 'TRANSFER', operation: 'DEBIT', amount: 500, currency: 'PHP', status: 'Posted' },
        { transactionId: '6', referenceNo: 'TX-FAIL', transactionDate: new Date().toISOString(), accountNumber: '001181233469', transactionType: 'TRANSFER', operation: 'DEBIT', amount: 600, currency: 'PHP', status: 'Failed' },
        { transactionId: '7', referenceNo: 'TX-CANC', transactionDate: new Date().toISOString(), accountNumber: '001181233469', transactionType: 'TRANSFER', operation: 'DEBIT', amount: 700, currency: 'PHP', status: 'Cancelled' }
    ];
    a.context.fetch = async () => ({ ok: true, json: async () => rows });
    await a.run('loadRecentTransactions()');

    // All 7 should be rendered
    assert.match(a.html(), /TX-INIT/);
    assert.match(a.html(), /TX-AUTH/);
    assert.match(a.html(), /TX-RESV/);
    assert.match(a.html(), /TX-PROC/);
    assert.match(a.html(), /TX-POST/);
    assert.match(a.html(), /TX-FAIL/);
    assert.match(a.html(), /TX-CANC/);

    // Filter by Failed
    a.elements['monitor-status-filter'].value = 'Failed';
    a.run('renderTransactionMonitor()');
    assert.match(a.html(), /TX-FAIL/);
    assert.doesNotMatch(a.html(), /TX-POST|TX-AUTH|TX-INIT|TX-RESV/);

    // Filter by Posted
    a.elements['monitor-status-filter'].value = 'Posted';
    a.run('renderTransactionMonitor()');
    assert.match(a.html(), /TX-POST/);
    assert.doesNotMatch(a.html(), /TX-FAIL|TX-AUTH/);
});

test('11-step lifecycle trace displays complete microservice attribution and failure reasons', async () => {
    const a = app();
    const rows = [
        {
            transactionId: '101',
            referenceNo: 'TX-PH-FAILED-LIMIT',
            transactionDate: new Date().toISOString(),
            accountNumber: '001181233469',
            targetAccountNumber: '001181233470',
            transactionType: 'TRANSFER',
            operation: 'DEBIT',
            amount: 30000,
            currency: 'PHP',
            status: 'Failed',
            internalStatus: 'LIMIT_CHECK',
            currentService: 'transaction-service',
            reason: 'Per-transaction limit of ₱25,000.00 exceeded'
        },
        {
            transactionId: '102',
            referenceNo: 'TX-PH-POSTED-OK',
            transactionDate: new Date().toISOString(),
            accountNumber: '001181233469',
            targetAccountNumber: '001181233470',
            transactionType: 'TRANSFER',
            operation: 'DEBIT',
            amount: 5000,
            currency: 'PHP',
            status: 'Posted',
            internalStatus: 'RECONCILIATION',
            currentService: 'settlement-service'
        }
    ];
    a.context.fetch = async () => ({ ok: true, json: async () => rows });
    await a.run('loadRecentTransactions()');

    // 1. Trace the Failed transaction
    a.run("showTransactionLifecycle('TX-PH-FAILED-LIMIT')");
    const metaHtml = a.elements['lifecycle-metadata'].innerHTML;
    const stepperHtml = a.elements['lifecycle-stepper-container'].innerHTML;

    assert.equal(a.elements['modal-transaction-lifecycle'].classList.contains('active'), true);
    assert.match(metaHtml, /TX-PH-FAILED-LIMIT/);
    assert.match(metaHtml, /Per-transaction limit of ₱25,000\.00 exceeded/);
    assert.match(metaHtml, /LIMIT_CHECK/);

    // Stepper must contain all 11 steps
    assert.match(stepperHtml, /1\. Initiated/);
    assert.match(stepperHtml, /2\. Validated/);
    assert.match(stepperHtml, /3\. Authenticated/);
    assert.match(stepperHtml, /4\. Fraud Check/);
    assert.match(stepperHtml, /5\. Limit Check/);
    assert.match(stepperHtml, /6\. Funds Check/);
    assert.match(stepperHtml, /7\. Authorized/);
    assert.match(stepperHtml, /8\. Posted/);
    assert.match(stepperHtml, /9\. Ledger Update/);
    assert.match(stepperHtml, /10\. Notification/);
    assert.match(stepperHtml, /11\. Reconciliation/);

    // Service attributions must be displayed
    assert.match(stepperHtml, /api-gateway \/ transaction-service/);
    assert.match(stepperHtml, /fraud-detection-service/);
    assert.match(stepperHtml, /t24-adapter/);
    assert.match(stepperHtml, /settlement-service/);

    // Limit check (step 5) must be marked as failed with reason, and subsequent skipped
    assert.match(stepperHtml, /stepper-step failed/);
    assert.match(stepperHtml, /Reason: Per-transaction limit of ₱25,000\.00 exceeded/);
    assert.match(stepperHtml, /Skipped due to pipeline failure at Step 5/);

    // 2. Trace the Posted transaction
    a.run("showTransactionLifecycle('TX-PH-POSTED-OK')");
    const okStepperHtml = a.elements['lifecycle-stepper-container'].innerHTML;
    // None should be failed, all steps completed
    assert.doesNotMatch(okStepperHtml, /stepper-step failed/);
    assert.doesNotMatch(okStepperHtml, /Skipped/);
});

