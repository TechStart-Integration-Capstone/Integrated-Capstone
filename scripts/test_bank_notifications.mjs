import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';

const sources=['bank.js','notifications.js'].map(file=>
  readFileSync(new URL('../frontend/bank/'+file,import.meta.url),'utf8'));
function banking(storage=new Map()) {
  const listeners={},popups=[];
  const badge={hidden:true,textContent:''};
  const button={setAttribute(){},querySelector:()=>badge};
  const dialog={innerHTML:'',open:false,dataset:{},addEventListener(){},
    showModal(){this.open=true;},close(){this.open=false;},querySelector:()=>null};
  const node={innerHTML:'',addEventListener(){}};
  const context=vm.createContext({
    console,Intl,Date,URL,AbortSignal,
    setTimeout(){},clearTimeout(){},setInterval(){},queueMicrotask(){},
    document:{querySelector:selector=>selector==='#details'?dialog:
      selector==='#notification-button'?button:selector==='#transfer-popups'?null:node,
      addEventListener(type,fn){(listeners[type] ||= []).push(fn);}},
    window:{},sessionStorage:{getItem:()=>null},
    localStorage:{getItem:key=>storage.get(key)||null,setItem:(key,value)=>storage.set(key,value)},
    capturePopup:item=>popups.push(item)
  });
  sources.forEach(source=>vm.runInContext(source,context));
  vm.runInContext(`
    showTransferPopup=item=>capturePopup(item);
    state.session={token:'A'};
    state.profile={username:'aly'};
    state.generation=1;
    globalThis.subject={state,transferNotifications,updateTransferNotifications,
      notificationList,persistNotificationReads};
  `,context);
  return {...context.subject,storage,popups,badge,dialog,listeners};
}
const transfer=(overrides={})=>({
  transactionId:10,reference:'TX-10',type:'P2P_REMITTANCE',operation:'DEBIT',
  status:'SUCCESS',amount:250,currency:'PHP',accountNumber:'001100001234',
  counterpartyName:'Maria Santos',counterpartyAccountNumber:'001100005678',
  date:'2026-10-11T00:00:00',...overrides
});

test('posted PayPink transfer appears in inbox and unread badge without an initial popup',()=>{
  const b=banking();
  b.updateTransferNotifications([transfer()]);
  assert.equal(b.transferNotifications.items.length,1);
  assert.equal(b.badge.textContent,'1');
  assert.equal(b.badge.hidden,false);
  assert.equal(b.popups.length,0);
  assert.match(b.notificationList(),/Money sent/);
  assert.match(b.notificationList(),/Maria Santos/);
  assert.match(b.notificationList(),/1234/);
});

for(const [status,title] of [['PENDING','Transfer pending'],['Reserved','Transfer pending'],
  ['PROCESSING','Transfer pending'],['Processing','Transfer pending'],['FAILED','Transfer failed'],
  ['Failed','Transfer failed'],['CANCELLED','Transfer cancelled'],['Cancelled','Transfer cancelled']]){
  test(status+' request is notified without claiming success, a hold or a refund',()=>{
    const b=banking();
    b.updateTransferNotifications([transfer({transactionId:null,status})]);
    const item=b.transferNotifications.items[0];
    assert.equal(item.title,title);
    assert.doesNotMatch(item.message,/was sent|was credited|on hold|restored|reversed|No funds were lost/);
  });
}

test('separate unposted requests retain their own notifications and details',()=>{
  const b=banking();
  b.updateTransferNotifications([
    transfer({transactionId:null,reference:'REQUEST-A',status:'FAILED'}),
    transfer({transactionId:null,reference:'REQUEST-B',status:'PENDING'})
  ]);
  const [a,c]=b.transferNotifications.items;
  assert.notEqual(a.id,c.id);
  const button={dataset:{notificationId:c.id},closest:()=>null};
  b.listeners.click.forEach(fn=>fn({target:{closest:selector=>
    selector==='[data-notification-id]'?button:null}}));
  assert.match(b.dialog.innerHTML,/REQUEST-B/);
  assert.doesNotMatch(b.dialog.innerHTML,/REQUEST-A/);
  assert.equal(b.badge.textContent,'1');
});

for(const status of ['SUCCESS','FAILED']){
  test('pending to '+status+' announces once and read state survives reload',()=>{
    const b=banking();
    b.updateTransferNotifications([transfer({transactionId:null,status:'PENDING'})]);
    const terminal=transfer({transactionId:status==='SUCCESS'?10:null,status});
    b.updateTransferNotifications([terminal]);
    b.updateTransferNotifications([terminal]);
    assert.equal(b.popups.length,1);
    const item=b.transferNotifications.items[0];
    assert.equal(item.title,status==='SUCCESS'?'Money sent':'Transfer failed');
    b.transferNotifications.read.add(item.id);
    b.persistNotificationReads();
    const restored=banking(b.storage);
    restored.updateTransferNotifications([terminal]);
    assert.equal(restored.badge.hidden,true);
    assert.equal(restored.popups.length,0);
  });
}

test('own-account debit and credit have distinct notifications',()=>{
  const b=banking();
  b.updateTransferNotifications([transfer(),transfer({
    transactionId:-10,type:'TRANSFER_IN',operation:'CREDIT',accountNumber:'001100009876'
  })]);
  const [sent,received]=b.transferNotifications.items;
  assert.notEqual(sent.id,received.id);
  assert.equal(sent.title,'Money sent');
  assert.equal(received.title,'Money received');
  assert.match(received.message,/was credited.*9876/);
});

test('existing transfer and loan notifications remain visible; unrelated entries are excluded',()=>{
  const b=banking();
  b.updateTransferNotifications(['TRANSFER_OUT','TRANSFER_IN','EXT_INSTAPAY_BDO','EXT_PESONET_BPI',
    'LOAN_DISBURSEMENT','LOAN_REPAYMENT','WELCOME_GIFT'].map((type,i)=>transfer({type,transactionId:i+1})));
  assert.equal(b.transferNotifications.items.length,6);
  assert.equal(b.transferNotifications.items[4].title,'Loan approved and received');
  assert.equal(b.transferNotifications.items[5].title,'Loan payment received');
});

test('new customer does not inherit read markers or announcements from the previous customer',()=>{
  const b=banking();
  b.updateTransferNotifications([transfer()]);
  b.transferNotifications.read.add(b.transferNotifications.items[0].id);
  b.persistNotificationReads();
  b.state.profile={username:'maria'};
  b.state.generation++;
  b.updateTransferNotifications([transfer()]);
  assert.equal(b.badge.textContent,'1');
  assert.equal(b.popups.length,0);
  assert.equal(b.transferNotifications.owner,'maria');
});

test('notification inbox escapes names and references',()=>{
  const b=banking();
  b.updateTransferNotifications([transfer({transactionId:null,reference:'"><img src=x>',
    counterpartyName:'<script>alert(1)</script>'})]);
  const html=b.notificationList();
  assert.doesNotMatch(html,/<script>|<img/);
  assert.match(html,/&lt;script&gt;/);
});
