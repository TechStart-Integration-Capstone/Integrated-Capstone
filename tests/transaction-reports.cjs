// Live-stack test: creates one isolated customer with its normal welcome credit.
const assert=require('node:assert/strict');
const path=require('node:path');const os=require('node:os');const fs=require('node:fs');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE || 'playwright');
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});
 try {
  const page=await browser.newPage({viewport:{width:1440,height:1000}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
  const base='http://localhost:3001/api/v1/auth/banking',username=`report_${Date.now()}`;
  const response=await page.request.post(base+'/register',{data:{firstName:'Report',lastName:'Customer',username,email:username+'@example.com',phone:'+639171234567',password:'Report-test-123'}});
  assert.equal(response.status(),201,await response.text());const session=await response.json();const headers={Authorization:`Bearer ${session.token}`};
  const profile=await (await page.request.get(base+'/me',{headers})).json();
  const account=profile.accounts.find(a=>a.accountType==='EVERYDAY_ACCOUNT');
  assert.match(account.accountNumber,/^\d{12}$/);
  const destination=profile.accounts.find(a=>a.accountType==='SAVINGS_ACCOUNT');
  const transfer=await page.request.post(base+'/transfers',{headers,data:{sourceAccountId:account.accountId,destinationAccountNumber:destination.accountNumber,amount:1,idempotencyKey:`report-transfer-${Date.now()}`}});
  assert.equal(transfer.status(),200,await transfer.text());
  const receipt=await transfer.json();assert.match(receipt.reference,/^PP-\d{8}-\d{12}$/);
  await page.goto('http://localhost:3001/bank/');await page.evaluate(s=>sessionStorage.setItem('paypink.customer.session',JSON.stringify({token:s.token,fullName:s.fullName,expiresAt:Date.now()+s.expiresInMs})),session);
  await page.reload();await page.locator('[data-generate-report]').waitFor();await page.locator('[data-generate-report]').click();
  await page.locator('#report-account').selectOption(String(account.accountId));
  const today=await page.locator('#report-to').inputValue();await page.locator('#report-from').fill(today);
  const downloaded=page.waitForEvent('download');await page.getByRole('button',{name:'Download PDF',exact:true}).click();const file=await downloaded;
  assert.match(file.suggestedFilename(),/\.pdf$/);
  const artifacts=path.join(os.tmpdir(),'paypink-browser-check','artifacts');fs.mkdirSync(artifacts,{recursive:true});const location=path.join(artifacts,'transaction-report.pdf');await file.saveAs(location);
  assert.equal(fs.readFileSync(location).subarray(0,5).toString(),'%PDF-');
  fs.writeFileSync(path.join(artifacts,'transaction-report-expected.json'),JSON.stringify({accountNumber:account.accountNumber,reference:receipt.reference}));
  const params=`?accountId=${account.accountId}&from=${today}&to=${today}`;
  assert.equal((await page.request.get(base+'/reports/transactions.pdf'+params)).status(),401);
  assert.equal((await page.request.get(base+'/reports/transactions.pdf'+params.replace('accountId='+account.accountId,'accountId=1'),{headers})).status(),403);
  const invalid=await page.request.get(base+`/reports/transactions.pdf?accountId=${account.accountId}&from=2025-01-01&to=2024-01-01`,{headers});assert.equal(invalid.status(),400);
  const empty=await page.request.get(base+`/reports/transactions.pdf?accountId=${account.accountId}&from=2020-01-01&to=2020-01-01`,{headers});assert.equal(empty.status(),200);assert.match(empty.headers()['content-type'],/application\/pdf/);assert.match(empty.headers()['cache-control'],/no-store/);
  await page.setViewportSize({width:390,height:844});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  await page.screenshot({path:path.join(artifacts,'report-dialog-mobile.png'),fullPage:true});
  assert.deepEqual(errors,[]);console.log('PASS: PDF download, date range, account isolation, unauthorized rejection, empty reports, mobile layout. PDF: '+location);
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
