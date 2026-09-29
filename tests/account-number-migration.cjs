// Run prepare against the old deployment, deploy the change, then run verify.
// Uses isolated customers; the temporary fixture contains their local test sessions.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const artifacts = path.join(os.tmpdir(), 'paypink-browser-check', 'artifacts');
const fixture = path.join(artifacts, 'account-migration-fixture.json');
const base = 'http://localhost:3001/api/v1/auth/banking';
async function api(route, session, data) {
  const response = await fetch(base + route, {method:data ? 'POST' : 'GET',
    headers:{'Content-Type':'application/json', ...(session ? {Authorization:`Bearer ${session.token}`} : {})},
    body:data ? JSON.stringify(data) : undefined});
  assert(response.ok, `${route}: ${response.status} ${await response.clone().text()}`);
  return response.json();
}
function checkAccount(account) {
  assert.match(account.accountNumber, account.accountType === 'SAVINGS_ACCOUNT' ? /^0011\d{8}$/ : /^0012\d{8}$/);
  const sum=[...account.accountNumber].reverse().reduce((total,char,index)=>{
    const digit=Number(char)*(index%2?2:1);return total+(digit>9?digit-9:digit);
  },0);
  assert.equal(sum%10,0);
}
(async()=>{
  fs.mkdirSync(artifacts,{recursive:true});
  if(process.argv[2]==='prepare') {
    async function register(label) {
      const username=`migration_${Date.now()}_${label}`;
      const session=await api('/register',null,{firstName:'Migration',lastName:label,username,
        email:username+'@example.com',phone:'+639171234567',password:'Migration-test-123'});
      return {session,profile:await api('/me',session)};
    }
    const sender=await register('sender'), recipient=await register('recipient');
    const source=sender.profile.accounts.find(a=>a.accountType==='EVERYDAY_ACCOUNT');
    const destination=recipient.profile.accounts.find(a=>a.accountType==='EVERYDAY_ACCOUNT');
    await api('/favorites',sender.session,{accountNumber:destination.accountNumber});
    const request={sourceAccountId:source.accountId,destinationAccountNumber:destination.accountNumber,
      amount:1,idempotencyKey:`migration-retry-${Date.now()}`};
    const receipt=await api('/transfers',sender.session,request);
    sender.profile=await api('/me',sender.session);recipient.profile=await api('/me',recipient.session);
    fs.writeFileSync(fixture,JSON.stringify({sender,recipient,request,receipt}));
    console.log('PASS: prepared old-number transfer, balances and favorite before deployment.');
    return;
  }
  assert.equal(process.argv[2],'verify','Use prepare or verify');
  const {sender,recipient,request,receipt}=JSON.parse(fs.readFileSync(fixture));
  for(const customer of [sender,recipient]) {
    customer.current=await api('/me',customer.session);
    for(const account of customer.current.accounts) {
      checkAccount(account);
      const previous=customer.profile.accounts.find(a=>a.accountId===account.accountId);
      assert.deepEqual({...account,accountNumber:previous.accountNumber},previous,'Migration must preserve ownership, balance and status');
      assert.notEqual(account.accountNumber,previous.accountNumber);
    }
    assert.equal(customer.current.accounts[0].accountNumber.slice(4,11),customer.current.accounts[1].accountNumber.slice(4,11));
  }
  const target=recipient.current.accounts.find(a=>a.accountType==='EVERYDAY_ACCOUNT');
  const lookup=await api('/recipients/lookup?accountNumber='+request.destinationAccountNumber,sender.session);
  assert.equal(lookup.accountNumber,target.accountNumber);
  assert.equal(lookup.favorite,true);
  assert.equal((await api('/recipients',sender.session)).favorites[0].accountNumber,target.accountNumber);
  for(const number of [request.destinationAccountNumber,target.accountNumber]) {
    const retry=await api('/transfers',sender.session,{...request,destinationAccountNumber:number});
    assert.equal(retry.reference,receipt.reference);
    assert.equal(retry.destinationAccountNumber,target.accountNumber);
  }
  assert.deepEqual((await api('/me',sender.session)).accounts,sender.current.accounts);
  const {chromium}=require(process.env.PLAYWRIGHT_MODULE || 'playwright');
  const browser=await chromium.launch({channel:'chrome',headless:true});
  try {
    const page=await browser.newPage({viewport:{width:1440,height:1000}});
    await page.goto('http://localhost:3001/bank/');
    await page.evaluate(session=>sessionStorage.setItem('paypink.customer.session',JSON.stringify({
      token:session.token,fullName:session.fullName,expiresAt:Date.now()+session.expiresInMs})),sender.session);
    await page.reload();
    await page.getByRole('button',{name:'Transfer money',exact:true}).click();
    await page.getByRole('button',{name:'Another PayPink account',exact:true}).click();
    await page.getByLabel('Recipient account number').fill(request.destinationAccountNumber);
    await page.locator('.verified-recipient').waitFor();
    await page.getByLabel('Amount',{exact:true}).fill('1');
    await page.getByRole('button',{name:'Review transfer'}).click();
    const dialog=page.getByRole('dialog');await dialog.waitFor();
    assert((await dialog.innerText()).includes(target.accountNumber.slice(-4)));
    await dialog.getByRole('button',{name:'Go back'}).click();
    await page.screenshot({path:path.join(artifacts,'migrated-recipient.png')});
  } finally { await browser.close(); }
  const today=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Manila',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
  const response=await fetch(`${base}/reports/transactions.pdf?accountId=${request.sourceAccountId}&from=${today}&to=${today}`,
    {headers:{Authorization:`Bearer ${sender.session.token}`}});
  assert.equal(response.status,200);
  const pdf=Buffer.from(await response.arrayBuffer());assert.equal(pdf.subarray(0,5).toString(),'%PDF-');
  fs.writeFileSync(path.join(artifacts,'migrated-account-report.pdf'),pdf);
  fs.writeFileSync(path.join(artifacts,'migrated-account-report-expected.json'),JSON.stringify({reference:receipt.reference,
    accountNumber:sender.current.accounts.find(a=>a.accountId===request.sourceAccountId).accountNumber}));
  console.log('PASS: migrated account formats, preserved balances/ownership/favorites, old/new-number retries, legacy-number browser review and migrated-account PDF.');
})().catch(error=>{console.error(error);process.exitCode=1;});
