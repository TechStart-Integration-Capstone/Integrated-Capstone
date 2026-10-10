import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import {webcrypto} from 'node:crypto';

const source = readFileSync(new URL('../frontend/bank/bank.js', import.meta.url), 'utf8');
const externalSource = readFileSync(new URL('../frontend/bank/external.js', import.meta.url), 'utf8');
for (const message of [
  'A transfer request with this Idempotency-Key is currently in progress. Please retry shortly.',
  'This Idempotency-Key was already used for a transfer with different request details.',
  'Core banking rejected a concurrent request because the original is still processing.'
]) {
  test(`409 preserves the original request: ${message}`,async()=>{
    const b=banking([{http:409,body:{message}},{body:{status:'POSTED',referenceNo:'REF-409'}}]);
    await b.sendTransfer();
    assert.equal(b.state.session.pendingTransfer.idempotencyKey,'original-key');
    assert.equal(b.state.transfer.reversed,undefined);
    assert.equal(b.broadcasts.length,0);
    assert.equal(b.notices.length,0);
    assert.match(b.transferPage(),/<fieldset disabled/);
    assert.equal(JSON.parse(b.storage.get('paypink.customer.session')).pendingTransfer.idempotencyKey,'original-key');
    const body=b.calls[0].body;
    // Editing an in-memory review must not replace the submitted instruction.
    b.state.transfer.review={sourceAccountId:2,destinationAccountNumber:'OTHER',amount:'500',idempotencyKey:'new-key'};
    await b.sendTransfer();
    assert.equal(b.calls[1].body,body);
    assert.equal(b.calls[1].headers['Idempotency-Key'],'original-key');
    assert.equal(b.state.session.pendingTransfer,undefined);
    assert.equal(b.broadcasts.length,1);
  });
}

test('repeated 409 responses and a reload preserve the original POST key and details',async()=>{
  const b=banking([{http:409,body:{}},{http:409,body:{}}]);
  await b.sendTransfer();
  await b.sendTransfer();
  assert.equal(b.calls[0].body,b.calls[1].body);
  const restored=banking([{body:{status:'POSTED',referenceNo:'REF-409'}}]);
  restored.state.session=JSON.parse(b.storage.get('paypink.customer.session'));
  restored.state.transfer=null;
  assert.match(restored.transferPage(),/<fieldset disabled/);
  await restored.sendTransfer();
  assert.equal(restored.calls[0].body,b.calls[0].body);
  assert.equal(restored.calls[0].headers['Idempotency-Key'],'original-key');
});

test('409 on a status check retains the confirmed reference and keeps using GET',async()=>{
  const b=banking([
    {http:202,body:{status:'PROCESSING',referenceNo:'REF-409'}},
    {http:409,body:{}},
    {body:{status:'POSTED',referenceNo:'REF-409'}}
  ]);
  await b.sendTransfer(); await b.sendTransfer();
  assert.equal(b.state.session.pendingTransfer.referenceNo,'REF-409');
  await b.sendTransfer();
  assert.equal(b.calls[1].method,'GET');
  assert.equal(b.calls[2].method,'GET');
  assert.equal(b.calls[1].url,b.calls[2].url);
});

for (const rail of ['INSTAPAY','PESONET']) {
  test(`${rail} conflict retains its body/key through repeated retries and reload`,async()=>{
    const b=banking([{http:409,body:{message:'Conflict'}},{http:409,body:{message:'Conflict'}}]);
    b.state.transfer.mode='external';
    b.state.transfer.externalReview={sourceAccountId:1,destinationAccountNumber:'001234567890',
      amount:20,rail,reference:'external-original-key'};
    await b.sendTransfer();
    const body=b.calls[0].body;
    assert.equal(b.state.session.pendingExternal.idempotencyKey,'external-original-key');
    const pendingPage=b.transferPage();
    assert.match(pendingPage,/<fieldset disabled/);
    assert.match(pendingPage,/Check transfer status/);
    assert.doesNotMatch(pendingPage,/dismiss-pending-external|Start new transfer/);
    b.state.transfer.externalReview.reference='replacement-key';
    await b.sendTransfer();
    assert.equal(b.calls[1].body,body);
    assert.equal(b.broadcasts.length,0);
    const restored=banking([
      {body:{reference:'EXT-DONE',status:'COMPLETED',amount:20,rail,mock:true}},
      {body:[]}
    ]);
    restored.state.session=JSON.parse(b.storage.get('paypink.customer.session'));
    restored.state.transfer=null;
    restored.transferPage();
    await restored.sendTransfer();
    assert.equal(restored.calls[0].body,body);
    assert.equal(restored.state.session.pendingExternal,undefined);
  });
}

test('definitive insufficient-funds responses still release the form for correction',async()=>{
  const b=banking([{http:422,body:{message:'Insufficient available funds'}}]);
  await b.sendTransfer();
  assert.equal(b.state.session.pendingTransfer,undefined);
  assert.equal(b.state.transfer.review,null);
  assert.equal(b.broadcasts.length,0);
});
function banking(responses) {
  const calls=[], broadcasts=[], notices=[], storage=new Map(), local=new Map();
  const listeners={};
  const node={innerHTML:'',addEventListener(){},close(){},focus(){},showModal(){}};
  const context=vm.createContext({
    console, Intl, Date, URL, AbortSignal, crypto:webcrypto,
    setTimeout(){}, clearTimeout(){}, setInterval(){}, queueMicrotask(){},
    document:{querySelector:()=>node,addEventListener(type,callback){(listeners[type] ||= []).push(callback);},title:''},
    window:{crypto:webcrypto,BroadcastChannel:true},
    BroadcastChannel:class { postMessage(event){broadcasts.push(event);} },
    sessionStorage:{getItem:key=>storage.get(key)||null,setItem:(key,value)=>storage.set(key,value),removeItem:key=>storage.delete(key)},
    localStorage:{setItem:(key,value)=>local.set(key,value)},
    fetch:async (url,options)=>{
      calls.push({url,...options});
      const next=responses.shift();
      if (!next) throw new Error('Unexpected request');
      if (next.throw) throw new Error('Network unavailable');
      const status=next.http||200;
      return {ok:status>=200&&status<300,status,json:async()=>next.body};
    },
    notify:(message,error)=>notices.push({message,error})
  });
  vm.runInContext(source,context);
  vm.runInContext(externalSource,context);
  vm.runInContext(`
    renderPage = () => {};
    refresh = async () => {};
    toast = (message,error=false) => notify(message,error);
    state.session = {token:'test-token',expiresAt:Date.now()+60000};
    state.profile = {customerId:1,fullName:'Sender',accounts:[
      {accountId:1,accountNumber:'SOURCE',accountType:'EVERYDAY_ACCOUNT',currency:'PHP',status:'ACTIVE',currentBalance:500},
      {accountId:2,accountNumber:'OWN',accountType:'SAVINGS_ACCOUNT',currency:'PHP',status:'ACTIVE',currentBalance:100}
    ]};
    state.transfer = {mode:'other',sending:false,error:'',recipient:{fullName:'Recipient'},
      review:{sourceAccountId:1,destinationAccountNumber:'TARGET',amount:'20',idempotencyKey:'original-key'}};
    globalThis.subject={state,sendTransfer,transferReceipt,transferPage};
  `,context);
  return {...context.subject,context,calls,broadcasts,notices,storage,local,listeners,node,
    receiptHtml(){return context.subject.transferReceipt(context.subject.state.transfer.receipt);}};
}

test('unposted history rows open their own details without colliding with ledger IDs',async()=>{
  const b=banking([]);
  b.state.activity=[
    {transactionId:10,reference:'LEDGER',status:'SUCCESS'},
    {transactionId:-10,reference:'LEDGER-IN',status:'SUCCESS'},
    {transactionId:null,reference:'REQUEST-10',status:'PENDING'},
    {transactionId:null,reference:'REQUEST-11',status:'FAILED'},
    {transactionId:null,reference:'REQUEST-12',status:'CANCELLED'}
  ].map(tx=>({...tx,accountId:1,accountNumber:'SOURCE',type:'P2P_REMITTANCE',operation:'DEBIT',
    amount:20,currency:'PHP',date:'2026-10-11T00:00:00',counterpartyName:'Recipient',
    counterpartyAccountNumber:'TARGET'}));
  const html=vm.runInContext('transactionTable(state.activity)',b.context);
  for(const id of ['10','-10','remittance:REQUEST-10','remittance:REQUEST-11','remittance:REQUEST-12']){
    assert.ok(html.includes('data-id="'+id+'"'));
    await b.listeners.click[0]({target:{closest:()=>({dataset:{action:'transaction-details',id}})}});
    const reference=id.startsWith('remittance:')?id.slice(11):id==='10'?'LEDGER':'LEDGER-IN';
    assert.equal(b.state.dialogReceipt.reference,reference);
    assert.ok(b.node.innerHTML.includes(reference));
  }
  b.state.statusFilter='PENDING';
  assert.equal(vm.runInContext('filteredActivity()[0].reference',b.context),'REQUEST-10');
  b.state.statusFilter='FAILED';
  assert.equal(vm.runInContext('filteredActivity()[0].reference',b.context),'REQUEST-11');
  assert.equal(vm.runInContext('state.activity.filter(isSuccess).length',b.context),2);
});

for (const status of ['PROCESSING','Processing','PENDING_CORE','T24_POSTED','RESERVED','Authorized','unknown',null]) {
  test(`${status}: keep request pending without a completion message or broadcast`,async()=>{
    const b=banking([{http:202,body:{status,referenceNo:'REF-1',amount:20}}]);
    await b.sendTransfer();
    assert.equal(b.state.session.pendingTransfer.idempotencyKey,'original-key');
    assert.equal(b.state.session.pendingTransfer.referenceNo,'REF-1');
    assert.equal(JSON.parse(b.storage.get('paypink.customer.session')).pendingTransfer.referenceNo,'REF-1');
    assert.match(b.receiptHtml(),/Awaiting confirmation/);
    assert.match(b.receiptHtml(),/data-action="retry-transfer"/);
    assert.doesNotMatch(b.receiptHtml(),/Transfer successful|All sent|Both account balances have been updated/);
    assert.match(b.notices[0].message,/still processing/);
    assert.equal(b.broadcasts.length,0);
    assert.equal(b.local.has('paypink_last_transfer_event'),false);
  });
}

test('pending to posted uses GET status, preserves receipt identity, and announces completion once',async()=>{
  const b=banking([
    {http:202,body:{status:'PROCESSING',referenceNo:'REF-1'}},
    {body:{status:'Posted',referenceNo:'REF-1',ftReference:'FT-1'}}
  ]);
  await b.sendTransfer();
  const date=b.state.transfer.receipt.date;
  await b.sendTransfer();
  await b.sendTransfer(); // No request remains to resubmit.
  assert.equal(b.calls.length,2);
  assert.equal(b.calls[0].method,'POST');
  assert.equal(b.calls[1].method,'GET');
  assert.equal(b.calls[1].url,'/api/v1/remittance/REF-1/status');
  assert.equal(b.state.session.pendingTransfer,undefined);
  assert.equal(b.state.transfer.receipt.date,date);
  assert.equal(b.state.transfer.receipt.recipientName,'Recipient');
  assert.match(b.receiptHtml(),/Transfer successful/);
  assert.equal(b.broadcasts.length,1);
  assert.equal(b.broadcasts[0].status,'SUCCESS');
});

for (const status of ['POSTED','Posted','SUCCESS','COMPLETED']) {
  test(`${status}: immediate confirmed completion stays successful`,async()=>{
    const b=banking([{body:{status,referenceNo:'REF-1'}}]);
    await b.sendTransfer();
    assert.equal(b.state.session.pendingTransfer,undefined);
    assert.match(b.receiptHtml(),/Transfer successful/);
    assert.equal(b.broadcasts.length,1);
  });
}

for (const status of ['FAILED','Failed','REJECTED','CANCELLED','Cancelled','REVERSED']) {
  test(`${status}: terminal failure never becomes a success receipt`,async()=>{
    const b=banking([{body:{status,referenceNo:'REF-1',reason:'<script>untrusted</script>'}}]);
    await b.sendTransfer();
    assert.equal(b.state.session.pendingTransfer,undefined);
    assert.match(b.receiptHtml(),/Transfer unsuccessful/);
    assert.doesNotMatch(b.receiptHtml(),/Transfer successful|All sent|<script>/);
    assert.equal(b.broadcasts.length,0);
    assert.equal(b.notices[0].error,true);
  });
}

test('pending to failed clears only after the terminal status arrives',async()=>{
  const b=banking([{body:{status:'PROCESSING',referenceNo:'REF-1'}},{body:{status:'Failed',referenceNo:'REF-1'}}]);
  await b.sendTransfer();
  assert.ok(b.state.session.pendingTransfer);
  await b.sendTransfer();
  assert.equal(b.state.session.pendingTransfer,undefined);
  assert.match(b.receiptHtml(),/Transfer unsuccessful/);
  assert.equal(b.broadcasts.length,0);
});

test('failed status checks preserve the reference and permit another check',async()=>{
  const b=banking([
    {body:{status:'PROCESSING',referenceNo:'REF-1'}},
    {http:404,body:{message:'Status temporarily unavailable'}},
    {http:503,body:{message:'T24 Core banking unavailable'}},
    {throw:true},
    {body:{status:'POSTED',referenceNo:'REF-1'}}
  ]);
  await b.sendTransfer();
  for (let i=0;i<3;i++) {
    await b.sendTransfer();
    assert.equal(b.state.session.pendingTransfer.referenceNo,'REF-1');
    assert.match(b.receiptHtml(),/role="alert"/);
    assert.equal(b.state.transfer.reversed,undefined);
    assert.equal(b.broadcasts.length,0);
  }
  await b.sendTransfer();
  assert.equal(b.broadcasts.length,1);
});

test('restored pending request blocks a new payment and checks the saved reference',async()=>{
  const initial=banking([{body:{status:'PROCESSING',referenceNo:'REF-1'}}]);
  await initial.sendTransfer();
  const b=banking([{body:{status:'POSTED',referenceNo:'REF-1'}}]);
  b.state.session=JSON.parse(initial.storage.get('paypink.customer.session'));
  b.state.transfer=null;
  const page=b.transferPage();
  assert.match(page,/<fieldset disabled/);
  assert.match(page,/Check transfer status/);
  await b.sendTransfer();
  assert.equal(b.calls[0].method,'GET');
  assert.equal(b.state.transfer.receipt.recipientName,'Recipient');
});

test('missing reference retains the same POST key; unknown status cannot imply completion',async()=>{
  const b=banking([{body:{}},{body:{status:'POSTED',referenceNo:'REF-1'}}]);
  delete b.state.transfer.review.idempotencyKey;
  await b.sendTransfer();
  const key=b.calls[0].headers['Idempotency-Key'];
  assert.ok(key);
  assert.equal(b.state.session.pendingTransfer.idempotencyKey,key);
  await b.sendTransfer();
  assert.equal(b.calls[1].headers['Idempotency-Key'],key);
});

test('session change during refresh cannot announce or broadcast an old transfer',async()=>{
  const b=banking([{body:{status:'POSTED',referenceNo:'REF-1'}}]);
  vm.runInContext('refresh=async()=>{state.generation++;state.session=null;};',b.context);
  await b.sendTransfer();
  assert.equal(b.notices.length,0);
  assert.equal(b.broadcasts.length,0);
});
