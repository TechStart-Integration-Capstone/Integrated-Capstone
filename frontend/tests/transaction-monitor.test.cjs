const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function app() {
    const elements = {
        'monitor-transactions-body': { innerHTML: '' },
        'monitor-status-filter': { value: 'ALL' },
        'monitor-type-filter': { value: 'ALL' }
    };
    const context = vm.createContext({
        window: {}, console, AbortSignal,
        document: { addEventListener() {}, getElementById: id => elements[id] },
        localStorage: { getItem() { throw new Error('Monitor must not load cached simulation rows'); } }
    });
    vm.runInContext(fs.readFileSync(path.join(__dirname, '../src/js/app.js'), 'utf8'), context);
    return { context, elements, run: code => vm.runInContext(code, context), html: () => elements['monitor-transactions-body'].innerHTML };
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
