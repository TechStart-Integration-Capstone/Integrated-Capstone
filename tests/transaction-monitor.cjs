// Read-only live-stack check. Uses the existing admin sign-in; creates no ledger/test records.
const assert = require('node:assert/strict');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');

(async () => {
    const browser = await chromium.launch({ channel: 'chrome', headless: true });
    try {
        const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
        const errors = [];
        page.on('pageerror', error => errors.push(error.message));
        await page.goto('http://localhost:3001/');
        await page.waitForFunction(() => monitorLoadState === 'error');
        await page.locator('#btn-admin-submit-login').click();
        await page.waitForFunction(() => monitorLoadState === 'ready');
        await page.locator('#tab-btn-transactions').click();
        const result = await page.evaluate(async () => {
            await loadRecentTransactions();
            const response = await fetch('/api/v1/auth/admin/transactions/today', { headers: getAuthHeaders() });
            const rows = await response.json();
            const today = monitorDateKey(new Date());
            return {
                status: response.status,
                cache: response.headers.get('cache-control'),
                count: rows.length,
                sameDay: rows.every(row => monitorDateKey(row.transactionDate) === today),
                sorted: rows.every((row, index) => !index ||
                    Date.parse(rows[index - 1].transactionDate) > Date.parse(row.transactionDate) ||
                    (rows[index - 1].transactionDate === row.transactionDate &&
                        BigInt(rows[index - 1].transactionId) >= BigInt(row.transactionId))),
                visibleReferences: [...document.querySelectorAll('#monitor-transactions-body td:first-child code')]
                    .map(cell => cell.textContent),
                references: rows.map(row => row.referenceNo),
                text: document.getElementById('monitor-transactions-body').textContent
            };
        });
        assert.equal(result.status, 200);
        assert.equal(result.cache, 'no-store');
        assert.equal(result.sameDay, true);
        assert.equal(result.sorted, true);
        assert.deepEqual(result.visibleReferences, result.references);
        assert.doesNotMatch(result.text, /TX-PH-INIT|IDEMP-PH/);
        if (!result.count) assert.match(result.text, /No transactions today/);

        const automaticRefresh = page.waitForResponse(response =>
            response.url().endsWith('/admin/transactions/today') && response.status() === 200);
        await automaticRefresh;
        await page.locator('#monitor-type-filter').selectOption('CREDIT');
        assert.equal(await page.evaluate(() => [...document.querySelectorAll('#monitor-transactions-body tr')]
            .every(row => row.children.length === 1 || row.children[5].textContent === 'CREDIT')), true);
        await page.locator('#monitor-type-filter').selectOption('ALL');
        await page.locator('#btn-admin-logout').click();
        assert.match(await page.locator('#monitor-transactions-body').textContent(), /Please sign in/);
        assert.deepEqual(errors, []);
        console.log(`PASS: ${result.count} live database rows; PHT day, newest first, filters, automatic refresh, login/logout, no sample rows.`);
    } finally {
        await browser.close();
    }
})().catch(error => { console.error(error); process.exitCode = 1; });
