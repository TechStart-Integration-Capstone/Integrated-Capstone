import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';

const sources=['bank.js','loans.js'].map(file=>readFileSync(new URL('../frontend/bank/'+file,import.meta.url),'utf8'));
const flush=()=>new Promise(resolve=>setImmediate(resolve));
const profile={username:'aly',fullName:'Aly Rosales',accounts:[
  {accountId:1,accountNumber:'001100001234',accountType:'EVERYDAY_ACCOUNT',status:'ACTIVE',currency:'PHP',currentBalance:1000},
  {accountId:2,accountNumber:'001100005678',accountType:'SAVINGS_ACCOUNT',status:'ACTIVE',currency:'PHP',currentBalance:500}
]};
function banking(){
  const calls=[],notices=[];
  const node={innerHTML:'',addEventListener(){},focus(){},close(){}};
  const context=vm.createContext({
    console,Intl,Date,URL,AbortSignal,
    setTimeout(){},clearTimeout(){},setInterval(){},queueMicrotask(){},
    document:{querySelector:()=>node,addEventListener(){},title:''},window:{},
    sessionStorage:{getItem:()=>null,setItem(){},removeItem(){}},
    fetch:(url,options)=>new Promise((resolve,reject)=>calls.push({url,options,resolve,reject})),
    notify:(message,error)=>notices.push({message,error}),
    updateTransferNotifications(){}
  });
  sources.forEach(source=>vm.runInContext(source,context));
  vm.runInContext(`
    renderPage=()=>{}; renderAuth=()=>{};
    toast=(message,error)=>notify(message,error);
    state.session={token:'A'}; state.generation=1;
    globalThis.subject={state,loanState,api,refresh,loadLoans,isSpending,spendingPattern,ensureLoanOwner};
  `,context);
  const s=context.subject;
  s.state.profile=structuredClone(profile);s.ensureLoanOwner();
  s.loanState.loaded=true;s.loanState.form={amount:'12345.67',accountNo:profile.accounts[1].accountNumber,termMonths:'24'};
  function respond(index,body,status=200){
    calls[index].resolve({ok:status>=200&&status<300,status,json:async()=>body});
  }
  async function accounts(){
    respond(0,structuredClone(profile));respond(1,[]);respond(2,{favorites:[],recent:[]});
    await flush();
  }
  return {...s,context,calls,notices,respond,accounts};
}

for(const manual of [false,true]){
  test((manual?'manual':'periodic')+' refresh reloads existing loan data, eligibility and schedule',async()=>{
    const b=banking();
    b.loanState.loans=[{loanId:1,status:'ACTIVE',outstandingPrincipal:1000}];
    const pending=b.refresh(manual);
    await b.accounts();
    assert.equal(b.calls[3].url,'/api/v1/loans');
    assert.equal(b.calls[4].url,'/api/v1/loans/eligibility');
    b.respond(3,[{loanId:1,status:'OVERDUE',outstandingPrincipal:900,penaltyDue:10}]);
    b.respond(4,{available:30000});
    await flush();
    assert.equal(b.calls[5].url,'/api/v1/loans/1/schedule');
    b.respond(5,{installments:[{status:'PENDING',totalDue:100,amountPaid:25}]});
    await pending;
    assert.equal(b.loanState.loans[0].status,'OVERDUE');
    assert.equal(b.loanState.eligibility.available,30000);
    assert.equal(b.loanState.progress[1].owed,85);
    assert.equal(b.loanState.form.amount,'12345.67');
    assert.equal(b.state.busy,false);
    assert.equal(b.notices.length,manual?1:0);
  });
}

test('overlapping loan reads share a request and later refreshes can reload',async()=>{
  const b=banking();
  const first=b.loadLoans(),second=b.loadLoans();
  assert.equal(first,second);
  assert.equal(b.calls.length,2);
  b.respond(0,[]);b.respond(1,{available:10});await first;
  const third=b.loadLoans();
  assert.notEqual(third,first);assert.equal(b.calls.length,4);
  b.respond(2,[]);b.respond(3,{available:20});await third;
  assert.equal(b.loanState.eligibility.available,20);
});

test('failed loan refresh preserves prior data and reports partial refresh',async()=>{
  const b=banking();
  b.loanState.loans=[{loanId:99}];
  const pending=b.refresh(true);await b.accounts();
  b.respond(3,{detail:'Loan service unavailable'},503);b.respond(4,{available:10});
  await pending;
  assert.equal(b.loanState.loans[0].loanId,99);
  assert.equal(b.loanState.error,'Loan service unavailable');
  assert.equal(b.state.busy,false);
  assert.equal(b.state.error,'');
  assert.match(b.notices[0].message,/loan data could not be updated/);
  assert.equal(b.notices[0].error,true);
});

test('session change during loan refresh cannot announce completion for another user',async()=>{
  const b=banking();
  const pending=b.refresh(true);await b.accounts();
  b.state.generation++;b.state.session={token:'B'};b.state.profile={username:'maria',accounts:[]};
  b.ensureLoanOwner();b.state.busy=true;
  b.respond(3,[]);b.respond(4,{available:10});await pending;
  assert.equal(b.notices.length,0);
  assert.equal(b.loanState.owner,'maria');
  assert.equal(b.loanState.loaded,false);
  assert.equal(b.state.busy,true);
});

for(const [body,expected] of [
  [{detail:'Insufficient available funds',title:'Unprocessable Entity',error:'Generic'},'Insufficient available funds'],
  [{message:'Account is inactive'},'Account is inactive'],
  [{reason:'Risk screening unavailable'},'Risk screening unavailable'],
  [{error:'Invalid request'},'Invalid request'],
  [{title:'Service unavailable'},'Service unavailable'],
  [{detail:{unexpected:true},message:'Usable message'},'Usable message'],
  [{detail:'   ',error:['not a message']},'We couldn’t complete your request. Please try again.'],
  [null,'We couldn’t complete your request. Please try again.']
]){
  test('API error displays '+expected,async()=>{
    const b=banking();const pending=b.api('/test');
    b.respond(0,body,422);
    await assert.rejects(pending,error=>{
      assert.equal(error.message,expected);assert.equal(error.status,422);assert.equal(error.data,body);return true;
    });
  });
}

test('rate-limit, network and invalid JSON responses keep useful fallback messages',async()=>{
  const b=banking();
  const limited=b.api('/test');b.respond(0,{},429);
  await assert.rejects(limited,/Too many requests/);
  const network=b.api('/test');b.calls[1].reject(new Error('offline'));
  await assert.rejects(network,/couldn’t reach the bank/);
  const invalid=b.api('/test');
  b.calls[2].resolve({ok:false,status:500,json:async()=>{throw new Error('not JSON');}});
  await assert.rejects(invalid,/couldn’t complete your request/);
});

const transfer={type:'P2P_REMITTANCE',status:'SUCCESS',operation:'DEBIT',amount:250,
  currency:'PHP',date:new Date().toISOString(),counterpartyName:'Aly Rosales',counterpartyAccountNumber:'001100009999'};
for(const type of ['P2P_REMITTANCE','TRANSFER_OUT','TRANSFER']){
  test(type+' spending uses destination ownership, not a matching name',()=>{
    const b=banking();
    assert.equal(b.isSpending({...transfer,type}),true);
    assert.equal(b.isSpending({...transfer,type,counterpartyName:'Old Name',counterpartyAccountNumber:profile.accounts[1].accountNumber}),false);
    assert.equal(b.isSpending({...transfer,type,counterpartyAccountNumber:null}),true);
    assert.equal(b.isSpending({...transfer,type,status:'PENDING'}),false);
    assert.equal(b.isSpending({...transfer,type,status:'FAILED'}),false);
    assert.equal(b.isSpending({...transfer,type,operation:'CREDIT'}),false);
  });
}

test('spending chart includes same-name recipient and omits transfers to owned accounts',()=>{
  const b=banking();
  b.state.activity=[transfer,{...transfer,amount:900,counterpartyName:'Different Name',
    counterpartyAccountNumber:profile.accounts[1].accountNumber}];
  const html=b.spendingPattern();
  assert.match(html,/250\.00/);
  assert.doesNotMatch(html,/900\.00|1,150\.00/);
});
