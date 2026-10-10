import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import {webcrypto} from 'node:crypto';

const bank=readFileSync(new URL('../frontend/bank/bank.js',import.meta.url),'utf8');
const loans=readFileSync(new URL('../frontend/bank/loans.js',import.meta.url),'utf8');
const flush=()=>new Promise(resolve=>setImmediate(resolve));
const loan={loanId:1,referenceNo:'A-PRIVATE-LOAN',status:'OVERDUE',accountNo:'A-ACCOUNT',
  penaltyDue:10,outstandingPrincipal:100,nextDue:{amount:110,dueDate:'2026-10-01'},
  lastAutoDebit:{status:'INSUFFICIENT_FUNDS',date:'2026-10-01',amount:110}};
const schedule={referenceNo:'A-PRIVATE-LOAN',installments:[
  {installmentNo:1,status:'PENDING',dueDate:'2026-10-01',principalDue:100,interestDue:10,totalDue:110,amountPaid:0}
]};

function banking() {
  const calls=[],dialogs=[],notices=[];
  let refreshes=0,closes=0;
  const node={innerHTML:'',open:false,addEventListener(){},focus(){},close(){closes++;this.open=false;}};
  const context=vm.createContext({
    console,Intl,Date,URL,AbortSignal,crypto:webcrypto,
    setTimeout(){},clearTimeout(){},setInterval(){},queueMicrotask(){},
    FormData:class {
      constructor(form){this.values=new Map(Object.entries(form.values));}
      get(key){return this.values.get(key);}
      [Symbol.iterator](){return this.values[Symbol.iterator]();}
    },
    document:{querySelector:()=>node,addEventListener(){},title:''},
    window:{PayPinkSavingsLive:{reset(){}}},
    sessionStorage:{getItem:()=>null,setItem(){},removeItem(){}},
    dismissTransferPopups(){},
    fetch:(url,options)=>new Promise((resolve,reject)=>calls.push({url,options,resolve,reject})),
    show:(...args)=>dialogs.push(args),notify:message=>notices.push(message),refreshCounter:()=>refreshes++
  });
  vm.runInContext(bank,context);
  vm.runInContext(loans,context);
  vm.runInContext(`
    renderPage=()=>{}; renderShell=()=>{}; renderAuth=()=>{};
    refresh=async()=>refreshCounter();
    toast=message=>notify(message);
    showDialog=(...args)=>show(...args);
    globalThis.subject={state,loanState,startSession,logout,loadLoans,loansPage,loanAlertsBanner,
      showSchedule,showPayForm,payLoan,applyForLoan,acceptOffer,ensureLoanOwner};
  `,context);
  const s=context.subject;
  function login(username='A') {
    s.startSession({token:username+'-'+s.state.generation,fullName:username,expiresInMs:60000});
    s.state.profile={username,fullName:username,accounts:[]};
    s.ensureLoanOwner();
  }
  function respond(i,body,status=200) {
    calls[i].resolve({status,ok:status>=200&&status<300,json:async()=>body});
  }
  function seed() {
    Object.assign(s.loanState,{loans:[structuredClone(loan)],progress:{1:{owed:120,total:1,remaining:1}},
      eligibility:{eligible:true,available:5000},loaded:true,offer:{referenceNo:'A-OFFER'},
      applyKey:'A-APPLY',applyFingerprint:'A-FINGERPRINT',payKeys:{1:{amount:20,key:'A-PAY'}},
      form:{accountNo:'A-ACCOUNT'},formError:'A-ERROR',error:'A-LOAD-ERROR'});
  }
  login();
  return {...s,context,calls,dialogs,notices,login,respond,seed,
    refreshes:()=>refreshes,closes:()=>closes};
}

test('logout clears all loan data, drafts, errors and payment keys immediately',()=>{
  const b=banking(); b.seed(); b.loanState.loading=true; b.loanState.busy=true;
  b.logout();
  assert.equal(b.loanState.owner,null);
  for(const key of ['loans']) assert.equal(b.loanState[key].length,0);
  for(const key of ['progress','payKeys','form']) assert.equal(Object.keys(b.loanState[key]).length,0);
  for(const key of ['offer','eligibility','applyKey']) assert.equal(b.loanState[key],null);
  for(const key of ['error','formError','applyFingerprint']) assert.equal(b.loanState[key],'');
  assert.equal(b.loanState.loaded,false); assert.equal(b.loanState.loading,false); assert.equal(b.loanState.busy,false);
  assert.equal(b.loanAlertsBanner(),'');
});

test('overview resets an old owner before rendering a missed-payment alert',()=>{
  const b=banking(); b.seed();
  assert.match(b.loanAlertsBanner(),/Loan payment not collected/);
  b.state.profile={username:'B',accounts:[]};
  assert.equal(b.loanAlertsBanner(),'');
  assert.equal(b.loanState.owner,'B');
  assert.equal(b.loanState.loans.length,0);
});

test('a fresh session for the same username also discards its old loan cache',()=>{
  const b=banking(); b.seed(); b.login('A');
  assert.equal(b.loanState.loaded,false);
  assert.equal(b.loanState.loans.length,0);
  assert.equal(b.loanState.offer,null);
});

test('old loan-list response cannot populate B or clear B loading flag',async()=>{
  const b=banking();
  const old=b.loadLoans();
  b.logout(); b.login('B');
  const current=b.loadLoans();
  assert.equal(b.calls.length,4);
  b.respond(0,[loan]); b.respond(1,{eligible:true,available:99999});
  await old;
  assert.equal(b.loanState.loading,true);
  assert.equal(b.loanState.loans.length,0);
  b.respond(2,[]); b.respond(3,{eligible:true,available:5000});
  await current;
  assert.equal(b.loanState.owner,'B');
  assert.equal(b.loanState.eligibility.available,5000);
  assert.equal(b.loanState.loading,false);
});

test('late progress response cannot overwrite B schedule cache or loading flag',async()=>{
  const b=banking();
  const old=b.loadLoans();
  b.respond(0,[loan]); b.respond(1,{eligible:true});
  await flush();
  assert.equal(b.calls[2].url,'/api/v1/loans/1/schedule');
  b.logout(); b.login('B');
  const current=b.loadLoans();
  b.loanState.progress={2:{owed:200}};
  b.respond(2,schedule);
  await old;
  assert.equal(b.loanState.loading,true);
  assert.equal(b.loanState.progress[1],undefined);
  assert.equal(b.loanState.progress[2].owed,200);
  b.respond(3,[]); b.respond(4,{eligible:true});
  await current;
});

for(const action of ['showSchedule','showPayForm']) {
  for(const outcome of ['success','network failure','401']) {
    test(`late ${action} ${outcome} cannot show A data or log B out`,async()=>{
      const b=banking(); b.seed();
      const pending=b[action](1);
      b.logout(); b.login('B');
      b.notices.length=0;
      const token=b.state.session.token;
      if(outcome==='network failure') b.calls[0].reject(new Error('network'));
      else b.respond(0,schedule,outcome==='401'?401:200);
      await pending;
      assert.equal(b.dialogs.length,0);
      assert.equal(b.notices.length,0);
      assert.equal(b.state.session.token,token);
      assert.equal(b.loanState.progress[1],undefined);
    });
  }
}

function payForm() {
  const error={hidden:true,textContent:''},button={disabled:false,textContent:'Pay now'};
  return {dataset:{id:'1'},values:{amount:'20'},isConnected:true,error,button,
    querySelector:selector=>selector==='#loan-pay-error'?error:button};
}

for(const status of [200,422,401]) {
  test(`late payment response ${status} cannot touch B keys, dialog or messages`,async()=>{
    const b=banking(); b.seed();
    const form=payForm();
    const pending=b.payLoan(form);
    b.logout(); b.login('B'); b.notices.length=0;
    b.loanState.payKeys={1:{amount:30,key:'B-PAY'}};
    form.error.textContent='B-visible-error';
    const closes=b.closes(),refreshes=b.refreshes();
    b.respond(0,{loanStatus:'CLOSED',amount:20,detail:'A-private-error'},status);
    await pending;
    assert.equal(b.loanState.payKeys[1].key,'B-PAY');
    assert.equal(form.error.textContent,'B-visible-error');
    assert.equal(b.closes(),closes);
    assert.equal(b.refreshes(),refreshes);
    assert.equal(b.notices.length,0);
    assert.ok(b.state.session);
  });
}

test('late application and acceptance cannot replace B offer or busy state',async()=>{
  const b=banking();
  const apply=b.applyForLoan({values:{amount:'5000',accountNo:'A-ACCOUNT',termMonths:'12'}});
  const accept=b.acceptOffer('A-OFFER');
  b.logout(); b.login('B');
  b.notices.length=0;
  b.loanState.offer={referenceNo:'B-OFFER'}; b.loanState.busy=true;
  b.respond(0,{referenceNo:'A-OFFER'}); b.respond(1,{});
  await Promise.all([apply,accept]);
  assert.equal(b.loanState.offer.referenceNo,'B-OFFER');
  assert.equal(b.loanState.busy,true);
  assert.equal(b.notices.length,0);
});

test('current-session loads and dialogs still work',async()=>{
  const b=banking();
  const pending=b.loadLoans();
  b.respond(0,[loan]);b.respond(1,{eligible:true});
  await flush();
  b.respond(2,schedule);
  await pending;
  assert.equal(b.loanState.loaded,true);
  assert.equal(b.loanState.progress[1].owed,120);
  const show=b.showSchedule(1);
  b.respond(3,schedule);await show;
  assert.match(b.dialogs[0][0],/A-PRIVATE-LOAN/);
  const pay=b.showPayForm(1);
  b.respond(4,schedule);await pay;
  assert.match(b.dialogs[1][0],/Pay loan A-PRIVATE-LOAN/);
});

test('a current-session 401 still logs the customer out and clears loan state',async()=>{
  const b=banking();b.seed();
  const pending=b.showSchedule(1);
  b.respond(0,{},401);await pending;
  assert.equal(b.state.session,null);
  assert.equal(b.loanState.loans.length,0);
});
