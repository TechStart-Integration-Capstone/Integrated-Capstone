// Run with PAYPINK_PLAYWRIGHT_PATH pointing to an installed playwright package, and Edge installed.
// A local static server and mocked API isolate this UI test from real ledger data.
const { chromium } = require(process.env.PAYPINK_PLAYWRIGHT_PATH || 'playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const os = require('node:os');
const root = path.resolve(__dirname, '..');
const server = http.createServer((req, res) => {
    const target = path.resolve(root, '.' + new URL(req.url, 'http://localhost').pathname.replace(/\/$/, '/index.html'));
    if (!target.startsWith(root + path.sep) || !fs.existsSync(target)) { res.writeHead(404).end(); return; }
    const type = {'.js':'text/javascript','.css':'text/css','.html':'text/html'}[path.extname(target)] || 'text/plain';
    res.writeHead(200, {'Content-Type':type}); fs.createReadStream(target).pipe(res);
});
(async () => {
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    const browser = await chromium.launch({ channel: 'msedge', headless: true });
    try {
        const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
        const errors = [];
        page.on('pageerror', error => errors.push(error.message));
        const day = '2026-09-25', id = '11111111-1111-1111-1111-111111111111';
        let proposal = null, approved = false, posted = false, unavailable = false, approvalCalls = 0;
        const calls = [];
        await page.addInitScript(() => {
            const token = 'test.' + btoa(JSON.stringify({sub:'aly',roles:['ROLE_ADMIN'],exp:Math.floor(Date.now()/1000)+3600})) + '.test';
            sessionStorage.setItem('paypink_admin_jwt', token);
            sessionStorage.setItem('paypink_admin_user', JSON.stringify({username:'aly',fullName:'Aly',roles:['ROLE_ADMIN']}));
        });
        await page.route('**/api/**', async route => {
            const req = route.request(), url = new URL(req.url());
            const reply = (data, status = 200) => route.fulfill({status, contentType:'application/json', body:JSON.stringify(data)});
            if (!url.pathname.includes('/interest/eod')) return reply(url.pathname.endsWith('/health') ? {status:'UP'} : []);
            calls.push(req.method() + ' ' + url.pathname);
            assert.ok(req.headers().authorization.startsWith('Bearer '));
            if (url.pathname.endsWith('/overview')) {
                if (unavailable) return reply({error:'Audit database unavailable'}, 503);
                return reply({startDate:'2026-09-01',businessDate:'2026-10-06',periodStart:'2026-09-01',periodEnd:'2026-09-30',
                    postingStatus:posted ? 'POSTED' : approved ? 'READY' : 'BLOCKED', missingDays:approved ? [] : [day],completedDays:[],
                    proposals:proposal ? [{proposalId:id,businessDate:day,preparedBy:'aly',status:approved?'APPROVED':'PENDING_APPROVAL'}] : []});
            }
            if (url.pathname.endsWith('/resolve')) {
                assert.equal(url.searchParams.get('businessDate'), day);
                const body = req.postDataJSON();
                assert.equal(body.accounts[0].eodBalance, '99999999999999.9999');
                assert.equal(body.accounts[0].accountId, '9007199254740993');
                proposal = {proposalId:id,businessDate:day,preparedBy:'aly',preparedAt:'2026-10-06T02:00:00Z',request:body,status:'PENDING_APPROVAL',approvedBy:null};
                return reply(proposal);
            }
            if (url.pathname.endsWith('/approve')) {
                approvalCalls++;
                assert.equal(req.postDataJSON().confirmed, true);
                approved = true;
                proposal = {...proposal,status:'APPROVED',approvedBy:'aly',approvedAt:'2026-10-06T03:00:00Z',approvalReason:req.postDataJSON().reason};
                return reply({businessDate:day,accounts:1,replayed:false});
            }
            if (url.pathname.endsWith('/post')) { posted = true; return reply({businessDate:'2026-09-30',accounts:1,replayed:false}); }
            if (url.pathname.endsWith(id)) return reply(proposal);
            return reply({},404);
        });
        await page.goto(`http://127.0.0.1:${server.address().port}`);
        await page.locator('#tab-btn-interest').click();
        await page.getByRole('button', {name:'25/09/2026 · File balances'}).waitFor();
        assert.equal(await page.locator('#interest-post').isDisabled(), true);
        await page.getByRole('button', {name:'25/09/2026 · File balances'}).click();
        await page.locator('#interest-source').fill('<img src=x onerror=alert(1)> verified-export');
        await page.locator('#interest-reason').fill('Recovered complete historical source after outage');
        await page.getByLabel('Account ID', {exact:true}).fill('9007199254740993');
        await page.getByLabel('Historical EOD balance in PHP').fill('99999999999999.9999');
        await page.locator('#interest-file-confirm').check();
        await page.getByRole('button', {name:'File for review', exact:true}).click();
        await page.getByRole('button', {name:'Review', exact:true}).waitFor();
        assert.equal(approvalCalls, 0, 'Filing must not auto-approve');
        assert.equal(await page.locator('#interest-post').isDisabled(), true);
        // Pending queue survives a browser reload; it comes from the API, not browser storage.
        await page.reload();
        await page.locator('#tab-btn-interest').click();
        await page.getByRole('button', {name:'Review', exact:true}).click();
        await page.locator('#interest-approve-submit').waitFor();
        assert.match(await page.locator('#interest-review-details').innerText(), /₱99,999,999,999,999\.9999/);
        assert.equal(await page.locator('#interest-review-details img').count(), 0, 'Source text must be escaped');
        await page.locator('#interest-approve-submit').click();
        assert.equal(approvalCalls, 0, 'Approval requires explicit reason and confirmation');
        await page.locator('#interest-approval-reason').fill('Reviewed the stored export and full account coverage');
        await page.locator('#interest-approve-confirm').check();
        await page.screenshot({path:path.join(os.tmpdir(),'paypink-interest-review.png'),fullPage:true});
        await page.locator('#interest-approve-submit').click();
        await page.waitForFunction(() => !document.getElementById('interest-post').disabled);
        assert.equal(approvalCalls, 1);
        assert.ok(calls.includes('GET /api/v1/interest/eod/backfills/' + id));
        await page.getByRole('button', {name:'View',exact:true}).click();
        await page.waitForFunction(() => document.getElementById('interest-review-details').textContent.includes('Approved by'));
        assert.match(await page.locator('#interest-review-details').innerText(), /aly/);
        assert.equal(await page.locator('#interest-approve-form').isVisible(), false);
        await page.locator('#interest-close-review').click();
        page.once('dialog', dialog => dialog.accept());
        await page.locator('#interest-post').click();
        await page.waitForFunction(() => document.getElementById('interest-summary').textContent.includes('Posted'));
        assert.equal(await page.locator('#interest-post').isDisabled(), true);
        await page.setViewportSize({width:390,height:844});
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), true, 'Mobile page must not overflow horizontally');
        await page.screenshot({path:path.join(os.tmpdir(),'paypink-interest-mobile.png'),fullPage:true});
        unavailable = true;
        await page.locator('#interest-refresh').click();
        await page.waitForFunction(() => document.getElementById('interest-message').textContent.includes('Audit database unavailable'));
        assert.equal(await page.locator('#interest-post').isDisabled(), true);
        assert.equal(await page.locator('#interest-proposals').innerText(), '');
        assert.deepEqual(errors, []);
        console.log('PASS: missing days, separate file/review/approve, same-admin audit, exact decimals, reload persistence, XSS, posting, mobile rendering, API failure.');
    } finally { await browser.close(); server.close(); }
})().catch(error => { console.error(error); server.close(); process.exitCode = 1; });
