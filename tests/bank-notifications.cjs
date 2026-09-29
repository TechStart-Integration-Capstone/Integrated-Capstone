// Creates two isolated customers and a PHP 1 transfer in the local running stack.
const assert=require('node:assert/strict');
const {chromium}=require(process.env.PLAYWRIGHT_MODULE || 'playwright');
(async()=>{
  const browser=await chromium.launch({channel:'chrome',headless:true});
  try {
    const base='http://localhost:3001'; const api=base+'/api/v1/auth/banking';
    const errors=[]; const users=[];
    for (const name of ['Sender','Receiver']) {
      const context=await browser.newContext({viewport:{width:1440,height:1000}});
      const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
      const username=`notif_${name.toLowerCase()}_${Date.now()}`;
      const response=await context.request.post(api+'/register',{data:{firstName:name,lastName:'Notice',username,email:username+'@example.com',phone:'+639171234567',password:'Notification-test-123'}});
      assert.equal(response.status(),201,await response.text());
      const session=await response.json();const headers={Authorization:`Bearer ${session.token}`};
      const profile=await (await context.request.get(api+'/me',{headers})).json();
      await page.goto(base+'/bank/');
      await page.evaluate(s=>sessionStorage.setItem('paypink.customer.session',JSON.stringify({token:s.token,fullName:s.fullName,expiresAt:Date.now()+s.expiresInMs})),session);
      await page.reload();await page.locator('.account-card').first().waitFor();
      await page.locator('#notification-button').click();
      await page.getByText('You’re all caught up.').waitFor();
      await page.getByRole('button',{name:'Done',exact:true}).click();
      users.push({page,context,profile,headers});
    }
    const [sender,receiver]=users;
    const source=sender.profile.accounts.find(a=>a.accountType==='EVERYDAY_ACCOUNT');
    const target=receiver.profile.accounts.find(a=>a.accountType==='EVERYDAY_ACCOUNT');
    const sent=await sender.context.request.post(api+'/transfers',{headers:sender.headers,data:{sourceAccountId:source.accountId,destinationAccountNumber:target.accountNumber,amount:'1.00',idempotencyKey:'notification_'+Date.now()}});
    assert.equal(sent.status(),200,await sent.text());
    await Promise.all(users.map(user=>user.page.locator('.transfer-popup').waitFor({timeout:20000})));
    await sender.page.locator('.transfer-popup-open').click();
    await sender.page.getByRole('heading',{name:'Money sent',exact:true}).waitFor();
    await sender.page.getByRole('button',{name:'Done',exact:true}).click();
    await receiver.page.locator('.transfer-popup').waitFor({state:'detached',timeout:10000});
    assert.equal(await receiver.page.locator('.notification-count').innerText(),'1');
    for (const [user,title] of [[sender,'Money sent'],[receiver,'Money received']]) {

      await user.page.locator('#notification-button').click();
      await user.page.locator('.notification-item').filter({hasText:title}).waitFor();
      const text=await user.page.locator('#notification-inbox').innerText();
      assert.doesNotMatch(text,new RegExp(target.accountNumber));
      assert.equal(await user.page.locator('.notification-item').count(),1);
      await user.page.locator('.notification-item').click();
      await user.page.getByRole('heading',{name:title,exact:true}).waitFor();
      await user.page.getByRole('button',{name:'All notifications'}).click();
      assert.equal(await user.page.locator('.notification-item.unread').count(),0);
      await user.page.reload();await user.page.locator('.account-card').first().waitFor();
      await user.page.locator('#notification-button').click();
      assert.equal(await user.page.locator('.notification-item.unread').count(),0);
    }
    await receiver.page.setViewportSize({width:390,height:844});
    assert.equal(await receiver.page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
    assert.deepEqual(errors,[]);
    console.log('PASS: sender/receiver alerts, customer isolation, unread badge, details, masked accounts, read persistence, mobile layout');
  } finally { await browser.close(); }
})().catch(e=>{console.error(e);process.exit(1)});
