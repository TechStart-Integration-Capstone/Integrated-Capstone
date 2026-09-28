// Requires Playwright and an already running local stack.
// Creates an isolated customer and two ledger mutations for that customer's account.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');

(async () => {
  const artifacts = path.join(os.tmpdir(), 'paypink-browser-check', 'artifacts');
  fs.mkdirSync(artifacts, { recursive: true });
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  const base = process.env.BANK_URL || 'http://localhost:3001';
  const endpoint = `${base}/api/v1/auth/banking`;
  try {
    await page.goto(`${base}/bank/`);
    await page.getByRole('heading', { name: 'Welcome back.' }).waitFor();
    await page.screenshot({ path: path.join(artifacts,'login-desktop.png'), fullPage: true });
    await page.getByLabel('Username', { exact: true }).fill('not_a_customer');
    await page.getByLabel('Password', { exact: true }).fill('wrong-password');
    await page.getByRole('button', { name: 'Log in', exact: true }).click();
    await page.getByRole('alert').filter({ hasText: 'incorrect' }).waitFor();
    await page.getByRole('button', { name: 'Open an account' }).click();
    const username = `uitest_${Date.now()}`;
    const password = 'Unique-customer-password-29';
    await page.getByLabel('First name', { exact: true }).fill('Jamie');
    await page.getByLabel('Last name', { exact: true }).fill('Rivera');
    await page.getByLabel('Email address', { exact: true }).fill(`${username}@example.com`);
    await page.getByLabel('Mobile number', { exact: true }).fill('+639171234567');
    await page.getByLabel('Username', { exact: true }).fill(username);
    await page.getByLabel('Password', { exact: true }).fill(password);
    await page.getByLabel('Confirm password', { exact: true }).fill('does-not-match');
    await page.getByRole('button', { name: 'Create my account' }).click();
    await page.getByRole('alert').filter({ hasText: 'don’t match' }).waitFor();
    await page.getByLabel('Confirm password', { exact: true }).fill(password);
    await page.getByRole('button', { name: 'Create my account' }).click();
    await page.getByRole('heading', { name: 'Hello, Jamie.' }).waitFor();
    assert.match(await page.locator('.big-balance').innerText(), /50\.00/);
    assert.equal(await page.locator('.account-card').count(), 2);
    await page.getByRole('button', { name: /Welcome gift/ }).waitFor();
    const session = await page.evaluate(() => JSON.parse(sessionStorage.getItem('paypink.customer.session')));
    const headers = { Authorization: `Bearer ${session.token}` };
    const me = await (await context.request.get(`${endpoint}/me`,{ headers })).json();
    const account = me.accounts[0];
    assert.equal(me.username,username);
    assert.equal(account.currentBalance,0);
    // Reads derive identity from the token, not spoofed IDs or user headers.
    const spoofed = await (await context.request.get(`${endpoint}/me?customerId=1`, {
      headers: { ...headers, 'X-Auth-Customer-Id': '1' }
    })).json();
    assert.equal(spoofed.username, username);
    assert.equal((await context.request.get(`${endpoint}/me`)).status(), 401);
    assert.equal((await context.request.get('http://localhost:8081/api/v1/auth/banking/me')).status(), 401);
    const duplicate = await context.request.post(`${endpoint}/register`, { data: {
      firstName:'Jamie',lastName:'Rivera',email:`${username}@example.com`,phone:'+639171234567',username,password
    }});
    assert.equal(duplicate.status(),409);
    assert.match(await page.locator('.account-number').first().innerText(), /••••/);
    await page.getByRole('button', { name: `Show account number ending ${account.accountNumber.slice(-4)}` }).click();
    assert.match(await page.locator('.account-number').first().innerText(), new RegExp(account.accountNumber));
    await page.getByRole('button', { name: `Hide account number ending ${account.accountNumber.slice(-4)}` }).click();
    await page.getByRole('button', { name: 'Hide balances', exact:true }).click();
    assert.equal(await page.locator('.big-balance').innerText(),'••••••');
    await page.getByRole('button', { name: 'Show balances', exact:true }).click();
    // Exercise the existing simulation ledger on this new, isolated account only.
    for (const [operation,amount] of [['CREDIT',25],['DEBIT',10]]) {
      const mutation = await context.request.post(`${base}/api/v1/ledger/mutate`, { headers, data: {
        accountId:account.accountId,mutationAmount:amount,operation,transactionType:operation,
        currency:'PHP',idempotencyKey:`${username}-${operation}`
      }});
      assert.equal(mutation.status(),200,await mutation.text());
    }
    await page.getByRole('button', { name: 'Refresh balances and transactions' }).click();
    await page.waitForFunction(() => document.querySelector('.big-balance')?.textContent.includes('65.00'));
    assert.equal(await page.locator('tbody tr').count(),3);
    await page.getByRole('button', { name:'View all transactions' }).click();
    await page.getByRole('searchbox', { name:'Search transactions' }).fill('nothing-will-match');
    await page.getByRole('heading', { name:'No matching transactions.' }).waitFor();
    await page.getByRole('searchbox', { name:'Search transactions' }).fill('Money received');
    assert.equal(await page.locator('tbody tr').count(),1);
    await page.locator('.transaction-link').click();
    await page.getByRole('dialog').waitFor();
    assert.match(await page.getByRole('dialog').innerText(), /25\.00/);
    await page.getByRole('button', { name:'Close dialog' }).click();
    await page.getByRole('searchbox', { name:'Search transactions' }).fill('');
    await page.getByRole('combobox', { name:'Filter by status' }).selectOption('FAILED');
    await page.getByRole('heading', { name:'No matching transactions.' }).waitFor();
    await page.getByRole('combobox', { name:'Filter by status' }).selectOption('');
    assert.equal(await page.locator('tbody tr').count(),3);
    await page.reload();
    await page.getByRole('heading', { name:'Hello, Jamie.' }).waitFor();
    await page.getByRole('button', { name:'Log out',exact:true }).click();
    await page.getByRole('dialog').getByRole('button', { name:'Log out',exact:true }).click();
    await page.getByRole('heading', { name:'Welcome back.' }).waitFor();
    assert.equal(await page.evaluate(() => sessionStorage.getItem('paypink.customer.session')),null);
    assert.equal(await page.locator('.account-card').count(),0);
    await page.reload();
    await page.getByRole('heading', { name:'Welcome back.' }).waitFor();
    await page.getByLabel('Username', { exact:true }).fill(username);
    await page.getByLabel('Password', { exact:true }).fill(password);
    await page.getByRole('button', { name:'Log in',exact:true }).click();
    await page.getByRole('heading', { name:'Hello, Jamie.' }).waitFor();
    const transactions = await (await context.request.get(`${endpoint}/transactions`,{headers})).json();
    assert.equal(transactions.length,3);
    assert(transactions.every(tx => me.accounts.some(a => a.accountId === tx.accountId)));
    // Compare a different customer's view; our new customer's account must never appear there.
    const demo = await (await context.request.post(`${endpoint}/login`,{data:{username:'lviernes',password:'password123'}})).json();
    const demoProfile = await (await context.request.get(`${endpoint}/me`,{headers:{Authorization:`Bearer ${demo.token}`}})).json();
    assert(demoProfile.accounts.every(a => a.accountId !== account.accountId));
    await page.evaluate(session => sessionStorage.setItem('paypink.customer.session',JSON.stringify(session)),
      { token:demo.token,fullName:demo.fullName,expiresAt:Date.now()+demo.expiresInMs });
    await page.reload();
    await page.getByRole('heading', { name:/Hello, Levi/ }).waitFor();
    await page.screenshot({path:path.join(artifacts,'dashboard-desktop.png'),fullPage:true});
    for (const width of [390,768,1024]) {
      await page.setViewportSize({width,height:844});
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth),false,`Horizontal overflow at ${width}px`);
      await page.screenshot({path:path.join(artifacts,`dashboard-${width}.png`),fullPage:true});
    }
    await page.setViewportSize({width:390,height:844});
    await page.getByRole('button',{name:'My accounts',exact:true}).click();
    await page.getByRole('heading',{name:'A home for your money.'}).waitFor();
    await page.getByRole('button',{name:'Account details',exact:true}).first().click();
    await page.getByRole('dialog').waitFor();
    await page.screenshot({path:path.join(artifacts,'account-mobile.png'),fullPage:true});
    await page.getByRole('button',{name:'Close dialog'}).click();
    await page.getByRole('button',{name:'Transactions',exact:true}).click();
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth),false);
    await page.screenshot({path:path.join(artifacts,'transactions-mobile.png'),fullPage:true});
    // Simulate a failed refresh: existing information should remain with a clear warning.
    await page.route('**/api/v1/auth/banking/me',route => route.abort());
    await page.getByRole('button',{name:'Refresh balances and transactions'}).click();
    await page.getByRole('alert').filter({hasText:'last loaded'}).waitFor();
    await page.unroute('**/api/v1/auth/banking/me');
    await page.getByRole('button',{name:'Refresh balances and transactions'}).click();
    await page.waitForFunction(() => !document.querySelector('.notice'));
    // A rejected or expired session clears sensitive data rather than keeping stale accounts visible.
    await page.route('**/api/v1/auth/banking/me',route => route.fulfill({status:401,contentType:'application/json',body:'{}'}));
    await page.getByRole('button',{name:'Refresh balances and transactions'}).click();
    await page.getByRole('heading',{name:'Welcome back.'}).waitFor();
    assert.equal(await page.locator('.account-card').count(),0);
    await page.unroute('**/api/v1/auth/banking/me');
    await page.screenshot({path:path.join(artifacts,'login-mobile.png'),fullPage:true});
    const original = await context.request.get(`${base}/`);
    assert.equal(original.status(),200);
    assert.match(await original.text(),/Core Retail Ledger/);
    assert.deepEqual(errors,[]);
    console.log(JSON.stringify({passed:true,customer:username,accountId:account.accountId,artifacts,
      checks:['registration','validation','duplicate prevention','login','masking','live ledger balances','history and filters',
      'customer isolation','unauthorized direct access','reload persistence','logout','responsive layouts','error recovery','session expiry','simulation unchanged']},null,2));
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
