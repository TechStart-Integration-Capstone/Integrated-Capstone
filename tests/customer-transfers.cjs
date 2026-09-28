// Requires the running stack and Playwright with installed Chrome.
// All transfers use two newly created, isolated test customers.
const assert = require('node:assert/strict');
const { randomUUID } = require('node:crypto');
const { mkdirSync, readFileSync } = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');

(async () => {
  const browser = await chromium.launch({channel:'chrome',headless:true});
  const context = await browser.newContext({viewport:{width:1440,height:1000}});
  const page = await context.newPage();
  const base = process.env.BANK_URL || 'http://localhost:3001';
  const api = `${base}/api/v1/auth/banking`;
  const artifacts = path.join(os.tmpdir(),'paypink-browser-check','artifacts');
  mkdirSync(artifacts,{recursive:true});
  const errors=[]; page.on('pageerror',e=>errors.push(e.message));
  async function register(suffix) {
    const username=`transfer_${Date.now()}_${suffix}`;
    const password='Welcome-to-PayPink-51';
    const response=await context.request.post(`${api}/register`,{data:{username,password,firstName:'Jamie',lastName:suffix,email:`${username}@example.com`,phone:'+639171234567'}});
    assert.equal(response.status(),201,await response.text());
    const session=await response.json();
    const headers={Authorization:`Bearer ${session.token}`};
    const customer={username,password,headers};
    const profile=await me(customer);
    assert.equal(profile.accounts.length,2);
    customer.savings=profile.accounts.find(a=>a.accountType==='SAVINGS_ACCOUNT');
    customer.everyday=profile.accounts.find(a=>a.accountType==='EVERYDAY_ACCOUNT');
    assert.equal(customer.savings.currentBalance,0);
    assert.equal(customer.everyday.currentBalance,50);
    const history=await (await context.request.get(`${api}/transactions`,{headers})).json();
    assert.equal(history.length,1); assert.equal(history[0].type,'WELCOME_GIFT'); assert.equal(history[0].operation,'CREDIT'); assert.equal(history[0].amount,50);
    return customer;
  }
  async function me(customer) { const response=await context.request.get(`${api}/me`,{headers:customer.headers}); assert.equal(response.status(),200); return response.json(); }
  const transfer=(customer,source,target,amount,key=randomUUID())=>context.request.post(`${api}/transfers`,{headers:customer.headers,data:{sourceAccountId:source,destinationAccountNumber:target,amount,idempotencyKey:key}});
  async function verifyMoney(a,b) {
    const [one,two]=await Promise.all([me(a),me(b)]);
    assert.equal([...one.accounts,...two.accounts].reduce((sum,item)=>sum+item.currentBalance,0),100);
    assert([...one.accounts,...two.accounts].every(item=>item.currentBalance>=0));
  }
  try {
    const sender=await register('sender');
    const recipient=await register('recipient');
    await page.goto(`${base}/bank/`);
    await page.getByLabel('Username',{exact:true}).fill(sender.username);
    await page.getByLabel('Password',{exact:true}).fill(sender.password);
    await page.getByRole('button',{name:'Log in',exact:true}).click();
    await page.getByRole('heading',{name:'Hello, Jamie.'}).waitFor();
    await page.getByRole('button',{name:'Transfer money',exact:true}).click();
    assert.equal(await page.getByLabel('Transfer from',{exact:true}).inputValue(),String(sender.everyday.accountId));
    assert.equal(await page.getByLabel('Transfer from',{exact:true}).locator('option').count(),2);
    assert.equal(await page.getByLabel('Transfer to',{exact:true}).inputValue(),String(sender.savings.accountId));
    await page.getByLabel('Amount',{exact:true}).fill('20');
    await page.screenshot({path:path.join(artifacts,'transfer-desktop.png'),fullPage:true});
    await page.getByRole('button',{name:'Review transfer'}).click();
    await page.getByRole('dialog').getByRole('button',{name:'Go back'}).click();
    assert.equal((await me(sender)).accounts.find(a=>a.accountId===sender.everyday.accountId).currentBalance,50);
    await page.getByRole('button',{name:'Review transfer'}).click();
    await page.getByRole('button',{name:'Confirm transfer'}).click();
    await page.getByRole('heading',{name:'Transfer successful'}).waitFor();
    let profile=await me(sender);
    assert.equal(profile.accounts.find(a=>a.accountId===sender.everyday.accountId).currentBalance,30);
    assert.equal(profile.accounts.find(a=>a.accountId===sender.savings.accountId).currentBalance,20);
    await page.screenshot({path:path.join(artifacts,'transfer-receipt.png'),fullPage:true});
    await page.getByRole('button',{name:'Done',exact:true}).click();
    await page.getByLabel('Transfer from',{exact:true}).selectOption(String(sender.savings.accountId));
    await page.getByRole('button',{name:'Another PayPink account',exact:true}).click();
    await page.getByLabel('Recipient account number').fill(recipient.everyday.accountNumber);
    await page.locator('.verified-recipient strong').filter({hasText:'Jamie recipient'}).waitFor();
    await page.getByRole('button',{name:'Save favorite',exact:true}).click();
    await page.getByRole('button',{name:'Remove favorite',exact:true}).waitFor();
    const saved=await (await context.request.get(`${api}/recipients`,{headers:sender.headers})).json();
    assert.equal(saved.favorites[0].accountNumber,recipient.everyday.accountNumber);
    assert.equal((await (await context.request.get(`${api}/recipients`,{headers:recipient.headers})).json()).favorites.length,0);
    await page.getByLabel('Amount',{exact:true}).fill('5');
    await page.getByRole('button',{name:'Review transfer'}).click();
    await page.getByRole('button',{name:'Confirm transfer'}).click();
    await page.getByRole('heading',{name:'Transfer successful'}).waitFor();
    assert.equal((await me(recipient)).accounts.find(a=>a.accountId===recipient.everyday.accountId).currentBalance,55);
    assert.equal(await page.locator('.receipt-recipient').innerText(),'Jamie recipient');
    assert(!(await page.locator('.transfer-receipt').innerText()).includes(recipient.everyday.accountNumber));
    assert((await page.locator('.transfer-receipt').innerText()).includes(recipient.everyday.accountNumber.slice(-4)));
    const downloadPromise=page.waitForEvent('download');
    await page.getByRole('button',{name:'Save receipt',exact:true}).click();
    const download=await downloadPromise;
    await download.saveAs(path.join(artifacts,'saved-receipt.png'));
    assert.equal(readFileSync(path.join(artifacts,'saved-receipt.png')).subarray(1,4).toString(),'PNG');
    const received=await (await context.request.get(`${api}/transactions`,{headers:recipient.headers})).json();
    assert(received.some(tx=>tx.type==='TRANSFER_IN'&&tx.operation==='CREDIT'&&tx.amount===5));
    await page.getByRole('button',{name:'Done',exact:true}).click();
    await page.reload();
    await page.getByRole('heading',{name:'Hello, Jamie.'}).waitFor();
    await page.getByRole('button',{name:'Transfers',exact:true}).click();
    await page.getByRole('button',{name:'Another PayPink account',exact:true}).click();
    await page.locator('.recipient-picker summary').click();
    assert.equal(await page.locator('.recipient-list').first().locator('.recipient-option').count(),1);
    assert.equal(await page.locator('.recipient-list').nth(1).locator('.recipient-option').count(),1);
    await page.locator('.recipient-list').first().locator('[data-action="choose-recipient"]').click();
    await page.locator('.verified-recipient strong').filter({hasText:'Jamie recipient'}).waitFor();
    assert.equal(await page.getByLabel('Recipient account number').inputValue(),recipient.everyday.accountNumber);
    await page.screenshot({path:path.join(artifacts,'recipient-favorites.png'),fullPage:true});
    // A late response for an old account number must never fill the current recipient.
    let release; const gate=new Promise(resolve=>{release=resolve});
    let lookupStarted; const started=new Promise(resolve=>{lookupStarted=resolve});
    const lookupPattern=`**/recipients/lookup?accountNumber=${sender.savings.accountNumber}`;
    await page.route(lookupPattern,async route=>{lookupStarted();await gate;await route.continue();});
    await page.getByLabel('Recipient account number').fill(sender.savings.accountNumber);
    await started;
    await page.getByLabel('Recipient account number').fill('PP-NOT-AN-ACCOUNT');
    await page.getByRole('alert').filter({hasText:'No active PayPink account'}).waitFor();
    const staleResponse=page.waitForResponse(response=>response.url().includes(sender.savings.accountNumber));
    release(); await staleResponse; await page.waitForTimeout(100); await page.unroute(lookupPattern);
    assert.equal(await page.locator('.verified-recipient').count(),0);
    await page.getByLabel('Recipient account number').fill(recipient.everyday.accountNumber);
    await page.getByRole('button',{name:'Remove favorite',exact:true}).waitFor();
    await page.getByRole('button',{name:'Remove favorite',exact:true}).click();
    await page.getByRole('button',{name:'Save favorite',exact:true}).waitFor();
    assert.equal((await (await context.request.get(`${api}/recipients`,{headers:sender.headers})).json()).favorites.length,0);
    await page.getByRole('button',{name:'My own account',exact:true}).click();
    assert.equal(await page.getByLabel('Transfer from',{exact:true}).inputValue(),String(sender.everyday.accountId));
    await page.getByLabel('Amount',{exact:true}).fill('999');
    await page.getByRole('button',{name:'Review transfer'}).click();
    await page.getByRole('alert').filter({hasText:'Not enough money'}).waitFor();
    await verifyMoney(sender,recipient);

    const key=randomUUID();
    const [first,retry]=await Promise.all([transfer(sender,sender.everyday.accountId,recipient.savings.accountNumber,3,key),transfer(sender,sender.everyday.accountId,recipient.savings.accountNumber,3,key)]);
    assert.equal(first.status(),200,await first.text()); assert.equal(retry.status(),200,await retry.text());
    assert.equal((await first.json()).reference,(await retry.json()).reference);
    assert.equal((await me(sender)).accounts.find(a=>a.accountId===sender.everyday.accountId).currentBalance,27);
    assert.equal((await transfer(sender,sender.everyday.accountId,recipient.savings.accountNumber,4,key)).status(),409);
    assert.equal((await transfer(sender,recipient.everyday.accountId,sender.savings.accountNumber,1)).status(),403);
    assert.equal((await transfer(sender,sender.everyday.accountId,sender.everyday.accountNumber,1)).status(),400);
    assert.equal((await transfer(sender,sender.everyday.accountId,'PP-NOT-AN-ACCOUNT',1)).status(),404);
    assert.equal((await transfer(sender,sender.everyday.accountId,sender.savings.accountNumber,10000)).status(),422);
    assert.equal((await transfer(sender,sender.everyday.accountId,sender.savings.accountNumber,-1)).status(),400);
    const competition=await Promise.all([transfer(sender,sender.everyday.accountId,sender.savings.accountNumber,20),transfer(sender,sender.everyday.accountId,recipient.savings.accountNumber,20)]);
    assert.deepEqual(competition.map(r=>r.status()).sort(),[200,422]);
    assert.equal((await me(sender)).accounts.find(a=>a.accountId===sender.everyday.accountId).currentBalance,7);
    await verifyMoney(sender,recipient);

    await page.getByLabel('Amount',{exact:true}).fill('1');
    await page.getByRole('button',{name:'Refresh balances and transactions'}).click();
    await page.waitForFunction(()=>document.querySelector('#transfer-source')?.selectedOptions[0].text.includes('7.00'));
    // Let the server commit, then lose the response. A reload/retry must use the exact same key.
    let lostReceipt;
    await page.route('**/api/v1/auth/banking/transfers',async route=>{
      const response=await route.fetch(); assert.equal(response.status(),200,await response.text());
      lostReceipt=await response.json(); await route.abort();
    },{times:1});
    await page.getByRole('button',{name:'Review transfer'}).click();
    await page.getByRole('button',{name:'Confirm transfer'}).click();
    await page.getByRole('button',{name:'Check transfer status'}).waitFor();
    assert.equal((await me(sender)).accounts.find(a=>a.accountId===sender.everyday.accountId).currentBalance,6);
    await page.reload();
    await page.getByRole('button',{name:'Check transfer status'}).waitFor();
    await page.getByRole('button',{name:'Check transfer status'}).click();
    await page.getByRole('heading',{name:'Transfer successful'}).waitFor();
    assert.match(await page.locator('.transfer-receipt').innerText(),new RegExp(lostReceipt.reference));
    assert.equal((await me(sender)).accounts.find(a=>a.accountId===sender.everyday.accountId).currentBalance,6);
    await verifyMoney(sender,recipient);
    await page.getByRole('button',{name:'Done',exact:true}).click();
    await page.setViewportSize({width:390,height:844});
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
    await page.screenshot({path:path.join(artifacts,'transfer-mobile.png'),fullPage:true});
    await page.getByRole('button',{name:'Transactions',exact:true}).click();
    await page.getByRole('searchbox',{name:'Search transactions'}).fill('Transfer');
    assert((await page.locator('tbody tr').count())>=4);
    await page.locator('.transaction-link').filter({hasText:'Transfer sent'}).first().click();
    await page.getByRole('dialog').waitFor();
    assert((await page.getByRole('dialog').innerText()).includes('Recipient account'));
    await page.screenshot({path:path.join(artifacts,'history-receipt-mobile.png'),fullPage:true});
    const historyDownload=page.waitForEvent('download');
    await page.getByRole('dialog').getByRole('button',{name:'Save receipt'}).click();
    await (await historyDownload).saveAs(path.join(artifacts,'history-receipt.png'));
    await page.getByRole('dialog').getByRole('button',{name:'Done'}).click();
    assert.deepEqual(errors,[]);
    console.log(JSON.stringify({passed:true,sender:sender.username,recipient:recipient.username,artifacts,
      checks:['two automatic accounts','PHP 50 welcome ledger credit','Everyday default','source selection','own-account transfer',
      'other-customer transfer','recipient history','review cancellation','insufficient funds','ownership validation','concurrent duplicate protection',
      'concurrent overspend prevention','money conservation','lost-response recovery after reload','mobile transfer layout',
      'recipient name lookup','favorite persistence and removal','recent recipients','stale lookup protection','masked receipts','PNG download','history receipt and Done']},null,2));
  } finally { await browser.close(); }
})().catch(error=>{console.error(error);process.exitCode=1;});
