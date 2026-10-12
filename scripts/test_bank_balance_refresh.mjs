import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';

const source=readFileSync(new URL('../frontend/bank/bank.js',import.meta.url),'utf8');
const flush=()=>new Promise(resolve=>setImmediate(resolve));
const summary={availableBalance:800,totalBalance:1000,accountCount:2};
const breakdown={accountBalance:500,personalReserved:100,circleReserved:0,availableBalance:400,allocations:[]};
const profile={username:'aly',firstName:'Aly',fullName:'Aly Rosales',accounts:[1,2].map(id=>({
  accountId:id,accountNumber:'00110000123'+id,accountType:'SAVINGS_ACCOUNT',status:'ACTIVE',currency:'PHP',currentBalance:500
}))};

test('transfer savings label uses available funds, handles zero, and never falls back to total',async()=>{
  const b=banking();
  const label=()=>vm.runInContext('transferSourceLabel(state.profile.accounts[0])',b.context);
  assert.match(label(),/Checking available balance/);assert.doesNotMatch(label(),/500\.00/);
  b.render('accounts');b.respond(0,{...breakdown,availableBalance:0});await flush();
  assert.match(label(),/₱0\.00 available/);assert.doesNotMatch(label(),/500\.00/);
  b.state.hideBalances=true;assert.doesNotMatch(label(),/0\.00/);
});

test('unavailable savings display never relabels total funds as available',async()=>{
  const b=banking();b.render('accounts');b.respond(0,{},503);await flush();
  const label=vm.runInContext('transferSourceLabel(state.profile.accounts[0])',b.context);
  assert.match(label,/Unavailable/);assert.doesNotMatch(label,/500\.00/);
});

test('transfer labels choose the matching account snapshot and retain it during refresh',async()=>{
  const b=banking();b.render('accounts');b.respond(0,breakdown);await flush();
  b.panel().select.value='2';const second=b.loadSavingsBreakdown();
  b.respond(1,{...breakdown,availableBalance:200});await second;
  const labels=()=>vm.runInContext('state.profile.accounts.map(transferSourceLabel)',b.context);
  assert.match(labels()[0],/400\.00/);assert.match(labels()[1],/200\.00/);
  b.invalidateBalanceDisplays();assert.match(labels()[0],/400\.00/);assert.match(labels()[1],/200\.00/);
  b.state.generation++;b.state.session={token:'B'};
  assert.doesNotMatch(labels()[0],/400\.00/);
});

test('non-savings transfer source retains its total balance label',()=>{
  const b=banking();b.state.profile.accounts[0].accountType='EVERYDAY_ACCOUNT';
  const note=vm.runInContext('transferSourceBalanceNote(state.profile.accounts[0])',b.context);
  assert.match(note,/Total account balance: ₱500\.00/);
});

function banking(){
  const calls=[],intervals=[],listeners={};
  let now=Date.now(),card=null,panel=null,mount;
  const element=()=>({innerHTML:'',textContent:'',isConnected:true,open:false,disabled:false,
    addEventListener(){},focus(){},close(){this.open=false;},classList:{toggle(){}},setAttribute(){},removeAttribute(){}});
  const app=element(),dialog=element(),breadcrumb=element(),refreshButton=element(),toast=element();
  const main=element();
  Object.defineProperty(main,'innerHTML',{set(html){
    if(card)card.isConnected=false;if(panel)panel.isConnected=false;
    card=null;panel=null;
    if(html.includes('id="overview-balance"')){
      const nodes=Object.fromEntries(['[data-overview-available]','[data-overview-total]','[data-overview-count]','.balance-subtitle'].map(k=>[k,element()]));
      card={...element(),querySelector:key=>nodes[key],nodes};
    }
    if(html.includes('id="account-savings-breakdown"')){
      const select={value:html.match(/<option value="([^"]+)" selected>/)?.[1]||'1'},content=element();
      panel={...element(),querySelector:key=>key==='select'?select:content,select,content};
    }
  }});
  class Clock extends Date {static now(){return now;}}
  const context=vm.createContext({
    console,Intl,Date:Clock,URL,AbortSignal,
    setTimeout(){},clearTimeout(){},setInterval(fn){intervals.push(fn);},queueMicrotask(){},
    document:{title:'',hidden:false,activeElement:null,
      querySelector:key=>({'#app':app,'#details':dialog,'#main':main,'#breadcrumb-page':breadcrumb,
        '#overview-balance':card,'#account-savings-breakdown':panel,'[data-action="refresh"]':refreshButton,'#toast':toast}[key]||null),
      querySelectorAll:()=>[],getElementById:()=>null,
      addEventListener(type,fn){(listeners[type]||=[]).push(fn);}
    },
    window:{scrollTo(){},PayPinkSavingsLive:{reset(){},mount(options){mount=options;}}},
    sessionStorage:{getItem:()=>null,setItem(){},removeItem(){}},
    dismissTransferPopups(){},updateTransferNotifications(){},
    fetch:(url,options)=>new Promise((resolve,reject)=>calls.push({url,options,resolve,reject}))
  });
  vm.runInContext(source,context);
  vm.runInContext('globalThis.subject={state,renderPage,loadOverviewBalance,loadSavingsBreakdown,invalidateBalanceDisplays,refresh,logout,startSession};',context);
  const s=context.subject;
  s.state.session={token:'A',expiresAt:now+600000};s.state.generation=1;s.state.profile=structuredClone(profile);
  function respond(i,data,status=200){calls[i].resolve({status,ok:status<400,json:async()=>data});}
  function render(page='overview'){s.state.page=page;s.renderPage();}
  return {...s,context,calls,intervals,listeners,respond,render,card:()=>card,panel:()=>panel,mount:()=>mount,
    advance:ms=>{now+=ms;},available:()=>card.nodes['[data-overview-available]'].innerHTML,
    subtitle:()=>card.nodes['.balance-subtitle'].textContent};
}

test('Overview navigation and privacy redraw reuse the balance without another request',async()=>{
  const b=banking();b.render();assert.equal(b.available(),'Loading…');
  b.respond(0,summary);await flush();assert.match(b.available(),/800\.00/);
  b.render('activity');b.render();assert.match(b.available(),/800\.00/);assert.equal(b.calls.length,1);
  b.state.hideBalances=true;b.render();assert.equal(b.available(),'••••••');
  b.state.hideBalances=false;b.render();assert.match(b.available(),/800\.00/);assert.equal(b.calls.length,1);
});

test('navigation during the initial request shares it and updates the replacement card',async()=>{
  const b=banking();b.render();const old=b.card();b.render('activity');b.render();
  assert.equal(b.calls.length,1);assert.equal(old.isConnected,false);
  b.respond(0,summary);await flush();assert.match(b.available(),/800\.00/);
});

for(const success of [true,false]){
  test('expired Overview read keeps the prior balance during '+(success?'success':'failure'),async()=>{
    const b=banking();b.render();b.respond(0,summary);await flush();
    b.advance(30001);b.render();assert.match(b.available(),/800\.00/);
    b.respond(1,success?{...summary,availableBalance:700}:{},success?200:503);await flush();
    assert.match(b.available(),success?/700\.00/:/800\.00/);
    assert.doesNotMatch(b.subtitle(),/fail|couldn|last|updated/i);
    b.render();assert.equal(b.calls.length,2);
  });
}

for(const manual of [true,false]){
  test((manual?'manual':'30-second periodic')+' refresh updates without returning to loading',async()=>{
    const b=banking();b.render();b.respond(0,summary);await flush();
    const pending=manual?b.refresh(true):b.intervals[0]();
    assert.match(b.available(),/800\.00/);
    b.respond(1,structuredClone(profile));b.respond(2,[]);b.respond(3,{favorites:[],recent:[]});
    if(pending)await pending;await flush();
    assert.equal(b.calls[4].url,'/api/v1/accounts/savings/balance-summary');
    assert.match(b.available(),/800\.00/);
    b.respond(4,{...summary,availableBalance:600});await flush();assert.match(b.available(),/600\.00/);
  });
}

test('breakdown keeps selection, per-account snapshots and privacy across redraws',async()=>{
  const b=banking();b.render('accounts');b.respond(0,breakdown);await flush();
  b.panel().select.value='2';const pending=b.loadSavingsBreakdown();
  b.respond(1,{...breakdown,availableBalance:300});await pending;
  b.render('accounts');assert.equal(b.panel().select.value,'2');
  assert.match(b.panel().content.innerHTML,/300\.00/);assert.equal(b.calls.length,2);
  b.state.hideBalances=true;b.render('accounts');
  assert.doesNotMatch(b.panel().content.innerHTML,/300\.00|500\.00/);
  b.state.hideBalances=false;b.panel().select.value='1';await b.loadSavingsBreakdown();
  assert.match(b.panel().content.innerHTML,/400\.00/);assert.equal(b.calls.length,2);
});

test('breakdown ignores a delayed response for a different selected account',async()=>{
  const b=banking();b.render('accounts');b.panel().select.value='2';
  const pending=b.loadSavingsBreakdown();b.respond(1,{...breakdown,availableBalance:300});await pending;
  b.respond(0,breakdown);await flush();assert.match(b.panel().content.innerHTML,/300\.00/);
  assert.doesNotMatch(b.panel().content.innerHTML,/400\.00/);
});

test('breakdown redraw during an initial read shares the pending request',async()=>{
  const b=banking();b.render('accounts');b.render('accounts');assert.equal(b.calls.length,1);
  b.respond(0,breakdown);await flush();assert.match(b.panel().content.innerHTML,/400\.00/);
});

test('breakdown background failures retain values silently and recover on Refresh',async()=>{
  const b=banking();b.render('accounts');b.respond(0,breakdown);await flush();
  b.advance(30001);b.render('accounts');assert.doesNotMatch(b.panel().content.innerHTML,/Loading/);
  b.respond(1,{},503);await flush();
  assert.match(b.panel().content.innerHTML,/400\.00/);
  assert.doesNotMatch(b.panel().content.innerHTML,/fail|couldn|last updated|role="alert"/i);
  b.invalidateBalanceDisplays();b.render('accounts');
  assert.match(b.panel().content.innerHTML,/400\.00/);
  b.respond(2,{...breakdown,availableBalance:250});await flush();assert.match(b.panel().content.innerHTML,/250\.00/);
});

test('reads begun before invalidation cannot overwrite newer data',async()=>{
  const b=banking();b.render();b.invalidateBalanceDisplays();b.render();
  b.respond(1,{...summary,availableBalance:650});await flush();
  b.respond(0,summary);await flush();assert.match(b.available(),/650\.00/);
  b.render();assert.match(b.available(),/650\.00/);assert.equal(b.calls.length,2);
});

for(const sameUser of [true,false]){
  test('logout/login isolates balances and pending responses for '+(sameUser?'same':'different')+' user',async()=>{
    const b=banking();b.render();b.respond(0,summary);await flush();
    b.advance(30001);b.render();b.logout();
    b.state.session={token:'B'};b.state.generation++;
    b.state.profile={...structuredClone(profile),username:sameUser?'aly':'maria'};b.render();
    assert.equal(b.available(),'Loading…');
    b.respond(1,{...summary,availableBalance:999});await flush();assert.equal(b.available(),'Loading…');
    b.respond(2,{...summary,availableBalance:200});await flush();assert.match(b.available(),/200\.00/);
  });
}

for(const body of [{},{availableBalance:null,totalBalance:1000,accountCount:2},{availableBalance:'bad',totalBalance:1000,accountCount:2}]){
  test('malformed successful summary cannot replace a valid balance with zero',async()=>{
    const b=banking();b.render();b.respond(0,summary);await flush();
    b.invalidateBalanceDisplays();b.render();b.respond(1,body);await flush();assert.match(b.available(),/800\.00/);
  });
}

test('first-load errors exit loading without inventing balances',async()=>{
  const b=banking();b.render();b.respond(0,{},503);await flush();assert.equal(b.available(),'Unavailable');
  b.render('accounts');b.respond(1,{},503);await flush();
  assert.match(b.panel().content.innerHTML,/temporarily unavailable/);
  assert.doesNotMatch(b.panel().content.innerHTML,/Loading|0\.00|fetch|failed/i);
});

test('Savings Hub invalidation makes navigation refresh the balance without blanking it',async()=>{
  const b=banking();b.render();b.respond(0,summary);await flush();
  b.render('savings');b.mount().onBalanceChange();b.render();
  assert.match(b.available(),/800\.00/);assert.equal(b.calls.length,2);
  b.respond(1,{...summary,availableBalance:750});await flush();assert.match(b.available(),/750\.00/);
});

