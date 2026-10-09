'use strict';
// Offline prototype only. No API, storage, or real reservations.
window.PayPinkSavings = (() => {
  const money = n => new Intl.NumberFormat('en-PH', {style:'currency',currency:'PHP'}).format(n);
  const esc = s => String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const date = s => new Date(s + 'T00:00:00+08:00').toLocaleDateString('en-PH',{timeZone:'Asia/Manila',year:'numeric',month:'long',day:'2-digit'});
  const today = () => new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Manila',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
  const seed = () => ({tab:'personal',summary:'personal',spendable:15000,selected:'bora',goals:[
    {id:'emergency',name:'Emergency Fund',category:'Emergency',emoji:'🚨',target:20000,amount:12500,streak:4,initial:0,auto:500,frequency:'payday',deadline:'2027-06-15',history:[]},
    {id:'christmas',name:'Christmas Fund',category:'Holiday / Gifts',emoji:'🎄',target:10000,amount:4500,streak:2,initial:0,auto:500,frequency:'payday',deadline:'2026-12-15',history:[]},
    {id:'concert',name:'Concert Fund',category:'Lifestyle / Concert',emoji:'🎫',target:8000,amount:3500,streak:0,initial:0,auto:0,frequency:'monthly',deadline:'2027-03-01',history:[]},
    {id:'rainy',name:'Rainy Day Fund',category:'Other',emoji:'✨',target:6000,amount:3000,streak:1,initial:0,auto:0,frequency:'weekly',deadline:'2027-06-01',history:[]}
  ],circles:[
    {id:'bora',name:'Boracay 2027',emoji:'✈️',target:40000,deadline:'2027-05-01',members:[{name:'Levi',amount:7500,target:10000,visible:true,history:[]},{name:'Maria',amount:5000,target:10000,visible:true},{name:'Alyssa',amount:3750,target:10000,visible:true},{name:'Juan',amount:2000,target:10000,visible:true}]},
    {id:'gift',name:"Mom's Birthday Gift",emoji:'🎁',target:6000,deadline:'2027-01-15',members:[{name:'Levi',amount:1200,target:2000,visible:true,history:[]},{name:'Maria',amount:1800,target:2500,visible:true},{name:'Juan',amount:1200,target:1500,visible:true}]},
    {id:'grad',name:'Graduation Trip',emoji:'🎓',target:30000,deadline:'2027-06-15',members:['Levi','Maria','Alyssa','Juan','Ana','Jose'].map(name=>({name,amount:2000,target:5000,visible:true,history:[]}))}
  ]});
  let state = seed(), draft = null, origin;
  const sum = c => c.members.reduce((n,m)=>n+m.amount,0);
  const personal = () => state.goals.reduce((n,g)=>n+g.amount,0);
  const groupSavings = () => state.circles.reduce((n,c)=>n+c.members[0].amount,0);
  function badges() {
    return [
      ['🌱','First ₱1K','Save your first ₱1,000.',personal()+groupSavings()>=1000,'mint'],
      ['🌷','Emergency Era','Build a ₱100,000 emergency cushion.',state.goals.some(g=>g.category==='Emergency' && g.amount>=100000),'rose'],
      ['👑','Consistency Queen','Make four consecutive scheduled contributions to a goal.',state.goals.some(g=>g.streak>=4),'lilac'],
      ['💎','Million Club','Reach ₱1,000,000 in personal savings.',personal()>=1000000,'peach']
    ].map(([emoji,name,description,earned,color])=>`<button type="button" class="sv-badge ${earned?'':'sv-locked'}" data-savings="badge" data-name="${esc(name)}" data-description="${esc(description)}" data-earned="${earned}" data-emoji="${emoji}"><span class="sv-medal sv-${color}" aria-hidden="true">${emoji}<span>${earned?'✓':'◇'}</span></span><strong>${name}</strong><small>${earned?'Earned':'Still growing'}</small></button>`).join('');
  }
  function overview() {
    const grouped=state.summary==='groups';
    return `<div class="sv-overview-grid"><section class="sv-total-card" aria-label="Savings total"><div class="sv-total-top"><span class="sv-eyebrow">YOUR SAVINGS</span><span aria-hidden="true">♡</span></div><div class="sv-total-switch" role="group" aria-label="Choose savings total"><button data-savings="summary-personal" aria-pressed="${!grouped}">Personal</button><button data-savings="summary-groups" aria-pressed="${grouped}">My group savings</button></div><p class="sv-total-label">${grouped?'My total group savings':'Total personal savings'}</p><h2 class="sv-total-amount">${money(grouped?groupSavings():personal())}</h2><p>Across ${grouped?state.circles.length:state.goals.length} ${grouped?'PinkCircles':'savings goals'}</p><div class="sv-total-note">${grouped?'Your contributions only. Every member keeps their own savings.':'A little today, a little closer to your goals.'}</div></section><section class="sv-card sv-badge-card" aria-labelledby="sv-badges-title"><div class="sv-card-top"><div><div class="sv-eyebrow">SMALL WINS, BIG ENERGY</div><h2 id="sv-badges-title">Your little hall of fame</h2></div><span aria-hidden="true">✨</span></div><p>Good habits deserve a cute little celebration.</p><div class="sv-badges">${badges()}</div><div class="sv-badge-note">♡ Yours to feel proud of. Badges are keepsakes with no cash rewards.</div></section></div>`;
  }
  const bar = (n,t,label) => `<div class="sv-progress" role="progressbar" aria-label="${esc(label)}" aria-valuemin="0" aria-valuemax="${t}" aria-valuenow="${Math.min(n,t)}" aria-valuetext="${esc(money(n))} of ${esc(money(t))}"><span style="width:${Math.min(100,n/t*100)}%"></span></div>`;
  const button = (action,label,id='') => `<button type="button" class="btn btn-secondary" data-savings="${action}" data-id="${esc(id)}">${label}</button>`;
  function forecast(g) {
    if(g.amount>=g.target) return 'Goal reached';
    if(!g.auto) return 'Add a contribution schedule to estimate completion.';
    let d = new Date(today()+'T00:00:00+08:00');
    const count = Math.ceil((g.target-g.amount)/g.auto);
    if(count>1200) return 'More than 100 years at this pace. Consider reviewing your plan.';
    for(let i=0;i<count;i++) {
      // UTC clock starts at 16:00 on the previous day; use a date-only UTC anchor.
      if(i===0) d=new Date(today()+'T00:00:00Z');
      if(g.frequency==='weekly') d.setUTCDate(d.getUTCDate()+7);
      else if(g.frequency==='monthly') {const day=d.getUTCDate(); d.setUTCDate(1); d.setUTCMonth(d.getUTCMonth()+1); const last=new Date(Date.UTC(d.getUTCFullYear(),d.getUTCMonth()+1,0)).getUTCDate(); d.setUTCDate(Math.min(day,last));}
      else if(d.getUTCDate()<15) d.setUTCDate(15);
      else if(d.getUTCDate()<new Date(Date.UTC(d.getUTCFullYear(),d.getUTCMonth()+1,0)).getUTCDate()) d.setUTCDate(new Date(Date.UTC(d.getUTCFullYear(),d.getUTCMonth()+1,0)).getUTCDate());
      else {d.setUTCDate(1);d.setUTCMonth(d.getUTCMonth()+1);d.setUTCDate(15);}
    }
    const end=d.toISOString().slice(0,10);
    return `Estimated completion: ${date(end)}${end>g.deadline?' — after your target date. Increase contributions or adjust the deadline.':'.'} Assumes every scheduled contribution succeeds; payday means the 15th and month-end.`;
  }
  function render() {
    const c=state.circles.find(c=>c.id===state.selected);
    return `<div class="savings-view"><div class="sv-heading"><div><h1>PayPink Savings Hub</h1><p>Your goals, your pace.</p></div>${button('reset','↻ Reset demo')}</div>
    ${overview()}<div class="sv-circle-tabs" role="group" aria-label="Savings views"><button data-savings="personal" aria-pressed="${state.tab==='personal'}">My Savings</button><button data-savings="circles" aria-pressed="${state.tab==='circles'}">PinkCircles</button></div>${state.tab==='personal'?`<section class="sv-summary"><span>Your savings goals</span><h2>${state.goals.length} savings ${state.goals.length===1?'goal':'goals'}</h2><p>${state.goals.filter(g=>g.amount<g.target).length} in progress &middot; ${state.goals.filter(g=>g.amount>=g.target).length} completed</p></section><div class="sv-goals">${state.goals.map(g=>`<article class="sv-card"><div class="sv-card-top"><h2>${esc(g.emoji)} ${esc(g.name)}</h2><strong>${Math.round(g.amount/g.target*100)}%</strong></div><p><strong>${money(g.amount)}</strong> / ${money(g.target)}</p>${bar(g.amount,g.target,g.name)}<p class="sv-plan">Target: ${date(g.deadline)} · ${g.auto?`${money(g.auto)} / ${esc(g.frequency)}`:'Manual contributions'}</p><p class="sv-plan">${forecast(g)}</p><div class="sv-actions">${button('add','＋ Add Money',g.id)}${button('release','Release',g.id)}${button('edit','Edit Goal',g.id)}${button('activity','View Activity',g.id)}</div></article>`).join('')}</div><div class="sv-actions">${button('new','＋ New savings goal')}${button('split','Smart Split')}</div><section class="sv-card sv-separate"><h2>Savings Streaks</h2><p>Consecutive scheduled contributions, per goal. Manual top-ups do not extend streaks.</p>${state.goals.map(g=>`<p>${esc(g.name)} <strong>${g.streak} paydays</strong></p>`).join('')}<small>Save when you can. Skipping a payday is okay; your saved amounts and milestones remain yours.</small></section><section class="sv-card sv-separate"><h2>Savings Milestones</h2><p>🌱 First ₱1,000 ${personal()>=1000?'✓':'◇'} · 🏁 Completed goals: ${state.goals.filter(g=>g.amount>=g.target).length}</p><p>Badges celebrate amounts saved and goals achieved. No cash value.</p>${window.PayPinkSavingsMilestones(personal())}<p><strong>Illustrative rate only: 5% p.a.</strong> The proposed +1 percentage point is not active. Reaching this milestone or earning a badge does not establish eligibility or change your rate. Any future offer requires published product terms and qualifying account balances.</p>${button('tiers','View existing interest tiers')}</section>`:
    `<section class="sv-summary"><span>Your PinkCircles</span><h2>${state.circles.length} active circles</h2><p>Your personal contributions stay in your account.</p></section><div class="sv-goals">${state.circles.map(c=>`<button class="sv-card sv-circle-choice" data-savings="select" data-id="${c.id}" aria-pressed="${c.id===state.selected}"><div class="sv-card-top"><h2>${esc(c.emoji)} ${esc(c.name)}</h2><span>${c.members.length} members</span></div><p>${money(sum(c))} / ${money(c.target)} <span>${Math.round(sum(c)/c.target*100)}%</span></p>${bar(sum(c),c.target,c.name)}</button>`).join('')}</div>${button('create','＋ Create PinkCircle')}<section class="sv-card sv-separate"><div class="sv-card-top"><h2>${esc(c.emoji)} ${esc(c.name)}</h2><span class="sv-soft-pill">Shared goal</span></div><p>PinkCircle · ${c.members.length} members · Target: ${date(c.deadline)}</p><div class="sv-goal-amount">${money(sum(c))} <span>/ ${money(c.target)}</span></div>${bar(sum(c),c.target,c.name)}<div class="sv-progress-caption"><span>${(sum(c)/c.target*100).toFixed(1)}% completed</span><span>${money(Math.max(0,c.target-sum(c)))} remaining</span></div><h3 class="sv-separate">Member contributions</h3><p class="sv-plan">Amounts are shared with member consent. Individual targets may differ.</p>${c.members.map((m,i)=>`<div class="sv-member"><div class="sv-card-top"><strong>${esc(m.name)}${i===0?' (you)':''}</strong><span>${m.visible?`${money(m.amount)} / ${money(m.target)}`:'Contribution hidden'}</span></div>${m.visible?bar(m.amount,m.target,m.name):''}</div>`).join('')}<div class="sv-actions">${button('circle-add','＋ Add my contribution',c.id)}${button('circle-release','Release my savings',c.id)}${button('circle-activity','My activity',c.id)}${button('visibility','Toggle my amount visibility',c.id)}</div><div class="sv-privacy sv-separate"><div><strong>♧ Your money stays yours</strong><p>Each member saves in their own personal PinkPocket. Only savings progress is shared. Members control their own releases; shared progress adjusts. Circle totals remain visible when individual amounts are hidden.</p><p>Only admins may change settings; agreed target changes require member approval. Payments remain separate transactions authorized by each account holder.</p></div></div></section>`}</div>`;
  }
  function refresh(action) {const view=document.querySelector('.savings-view');if(view)view.outerHTML=render();if(action)document.querySelector(`[data-savings="${action}"]`)?.focus();}
  function modal(title,body) {
    let d=document.querySelector('#savings-dialog');
    if(!d){d=document.createElement('dialog');d.id='savings-dialog';document.body.append(d);d.addEventListener('close',()=>{draft=null;if(origin?.isConnected)origin.focus();else document.querySelector('.savings-view button')?.focus();});}
    if(!d.open)origin=document.activeElement;
    d.className='sv-dialog';d.setAttribute('aria-labelledby','sv-dialog-title');
    d.innerHTML=`<button class="sv-dialog-close" data-savings="close" aria-label="Close dialog">×</button><h2 id="sv-dialog-title">${esc(title)}</h2>${body}`;
    if(!d.open)d.showModal();
  }
  const input=(name,label,value,type='text',attrs='')=>`<label for="sv-${name}">${label}</label><input id="sv-${name}" name="${name}" type="${type}" value="${esc(value)}" ${attrs}>`;
  const amountInput=(name,label,value,min=0)=>input(name,label,value,'number',`min="${min}" max="10000000" step="0.01" required`);
  const frequencies=value=>`<label for="sv-frequency">Contribution frequency</label><select id="sv-frequency" name="frequency">${['payday','weekly','monthly'].map(f=>`<option value="${f}" ${f===value?'selected':''}>${f==='payday'?'Every payday (15th and month-end)':f}</option>`).join('')}</select>`;
  const error='<p class="sv-form-error" role="alert" hidden></p>';
  const note='<p class="sv-modal-note">Front-end concept using sample funds. No real savings reservations or transactions are created.</p>';
  const form=(kind,body,submit)=>`<form data-savings-form="${kind}">${body}${error}<button class="btn btn-primary" type="submit">${submit}</button></form>${note}`;
  function wizard(step) {
    draft.step=step;
    const stages=`<div class="sv-steps" aria-label="Step ${step} of 3">${['Set your goal','Savings plan','Review'].map((s,i)=>`<span class="${i<step?'active':''}">${s}</span>`).join('')}</div>`;
    let body;
    if(step===1)body=form('goal',`${input('name','Goal name',draft.name,'text','maxlength="50" required')}<label for="sv-category">Category</label><select name="category" id="sv-category">${['Emergency','Holiday / Gifts','Travel','Lifestyle / Concert','Other'].map(c=>`<option ${c===draft.category?'selected':''}>${c}</option>`).join('')}</select>${amountInput('target','Target amount (₱)',draft.target,0.01)}${input('deadline','Target date',draft.deadline,'date',`min="${today()}" required`)}`,'Continue →');
    if(step===2)body=form('plan',`<p>Sample available balance: <strong>${money(state.spendable)}</strong></p>${amountInput('initial','Initial amount to set aside (₱)',draft.initial)}<label class="sv-checkbox"><input name="enabled" type="checkbox" ${draft.auto?'checked':''}> Enable automatic contributions</label>${amountInput('auto','Contribution amount (₱)',draft.auto)}${frequencies(draft.frequency)}${button('back','Back')}`,'Review goal');
    if(step===3)body=form('confirm',`<div class="sv-card"><h3>${esc(draft.name)}</h3><p>${esc(draft.category)}</p><h2>${money(draft.target)}</h2>${bar(draft.initial,draft.target,'Initial allocation')}<p>Target date: ${date(draft.deadline)}</p><p>Initial allocation: ${money(draft.initial)}</p><p>Auto-save: ${draft.auto?`${money(draft.auto)} / ${esc(draft.frequency)}`:'Off'}</p><p>Remaining spendable balance: ${money(state.spendable-draft.initial)}</p><p>${forecast({...draft,amount:draft.initial})}</p></div><p>Your savings stay yours. Future automatic contributions require sufficient available funds. A goal may start at ₱0.</p>${button('back','Back')}`,'Create savings goal');
    modal(step===1?'What are you saving for?':step===2?'How would you like to save?':'Review your savings goal',stages+body);
  }
  function activity(g) {modal('Contribution history',`<p>${esc(g.name||'Your circle savings')}</p><p class="sv-modal-note">Opening sample balances predate this session. Only actions taken here have activity records.</p>${(g.history||[]).map(h=>`<p>${esc(h.time)} · ${esc(h.type)} · ${money(h.amount)}</p>`).join('')||'<p>No activity in this demo session.</p>'}`);}
  function record(g,type,amount){g.history ||= [];g.history.unshift({type,amount,time:new Date().toLocaleString('en-PH',{timeZone:'Asia/Manila'})+' PHT'});}
  function announce(message){let n=document.querySelector('#savings-status');if(!n){n=document.createElement('div');n.id='savings-status';n.className='sv-status';n.setAttribute('role','status');document.body.append(n);}n.textContent=message;}
  document.addEventListener('click',event=>{
    const b=event.target.closest('[data-savings]');if(!b)return;
    const action=b.dataset.savings,id=b.dataset.id,g=state.goals.find(g=>g.id===id),c=state.circles.find(c=>c.id===id);
    if(action==='summary-personal'||action==='summary-groups'){state.summary=action==='summary-groups'?'groups':'personal';refresh(action);return;}
    if(action==='badge')return modal(b.dataset.name,`<div class="sv-badge-detail" aria-hidden="true">${b.dataset.emoji}</div><p>${esc(b.dataset.description)}</p><p class="sv-modal-note">${b.dataset.earned==='true'?'You earned this badge.':'Still growing - save at your own pace.'} Badges have no cash value.</p>`);
    if(action==='close')return document.querySelector('#savings-dialog')?.close();
    if(action==='reset'){reset();refresh('reset');return;}
    if(action==='personal'||action==='circles'){state.tab=action;refresh(action);return;}
    if(action==='select'){state.selected=id;refresh();document.querySelector(`[data-savings="select"][data-id="${id}"]`)?.focus();return;}
    if(action==='new'){draft={name:'',category:'Other',target:10000,deadline:'2026-12-15',initial:0,auto:0,frequency:'payday'};wizard(1);return;}
    if(action==='back'){const f=b.closest('form');if(draft.step===2){const data=new FormData(f);draft.initial=Number(data.get('initial'));draft.auto=data.has('enabled')?Number(data.get('auto')):0;draft.frequency=data.get('frequency');}wizard(draft.step-1);return;}
    if(action==='tiers')return modal('Existing interest tiers','<p>Below ₱1,000: 1% p.a.</p><p>₱1,000–₱9,999.99: 2.5% p.a.</p><p>₱10,000 and above: 4% p.a.</p><p>The illustrative 5% rate is not an active rate. Badges do not change interest.</p>');
    if(action==='activity')return activity(g);
    if(action==='circle-activity')return activity(c.members[0]);
    if(action==='visibility'){c.members[0].visible=!c.members[0].visible;refresh();return;}
    if(action==='maximum'){const f=b.closest('form');f.elements.amount.value=Number(f.dataset.maximum).toFixed(2);f.elements.amount.dispatchEvent(new Event('input',{bubbles:true}));return;}
    if(action==='add'||action==='circle-add'){
      const goal=action==='add'?g:c.members[0],remaining=Math.max(0,goal.target-goal.amount),max=Math.max(0,Math.floor(Math.min(state.spendable,remaining)*100)/100),reserved=personal()+groupSavings();
      const row=(label,value)=>`<div><dt>${label}</dt><dd>${money(value)}</dd></div>`;
      modal(action==='add'?'Set aside money':'Add my contribution',form(action,`<input name="id" type="hidden" value="${esc(id)}"><p>From: Sample savings account</p><dl class="sv-funding">${row('Account balance',state.spendable+reserved)}${row('Already set aside for goals & PinkCircles',reserved)}${row('Available to set aside',state.spendable)}${row('Remaining contribution target',remaining)}${row('Maximum you can add',max)}</dl>${max>0?`${amountInput('amount','Amount (₱)',Math.min(500,max),0.01)}${button('maximum','Add maximum')}`:'<p>No further funds can be allocated to this goal right now.</p>'}`,'Confirm'));
      const f=document.querySelector('#savings-dialog form');f.dataset.maximum=String(max);if(max>0)f.elements.amount.max=String(max);f.querySelector('[type="submit"]').disabled=max<=0;return;
    }
    if(['release','circle-release'].includes(action))return modal('Release your savings',form(action,`<input name="id" type="hidden" value="${esc(id)}"><p>Sample spendable balance: ${money(state.spendable)}</p>${amountInput('amount','Amount (₱)',500,0.01)}`,'Confirm'));
    if(action==='edit')return modal('Edit savings goal',form('edit',`<input name="id" type="hidden" value="${esc(id)}">${input('name','Goal name',g.name,'text','maxlength="50" required')}${amountInput('target','Target amount (₱)',g.target,0.01)}${input('deadline','Target date',g.deadline,'date',`min="${today()}" required`)}${amountInput('auto','Automatic contribution (₱), 0 to disable',g.auto)}${frequencies(g.frequency)}`,'Save changes'));
    if(action==='split')return modal('Smart Split',form('split',`${amountInput('budget','Payday savings budget (₱)',3000)}<p>Allocate within this budget and your sample spendable balance.</p>${state.goals.map((g,i)=>amountInput(g.id,esc(g.name),[1500,1000,500,0][i]||0)).join('')}`,'Reserve sample savings'));
    if(action==='create')return modal('Create PinkCircle',form('circle',`${input('name','Circle name','','text','maxlength="50" required')}${amountInput('target','Shared target (₱)',40000,0.01)}${amountInput('mine','Your agreed target (₱)',10000,0.01)}${input('deadline','Target date','2027-06-15','date',`min="${today()}" required`)}<p>Demo members: Levi, Maria, Alyssa and Juan. Remaining target is divided among the other sample members. Invitations and target approvals require a future backend.</p>`,'Create demo circle'));
  });
  document.addEventListener('input',event=>{
    const f=event.target.closest('[data-savings-form][data-maximum]');if(!f)return;
    const value=Number(f.elements.amount.value),max=Number(f.dataset.maximum),error=f.querySelector('.sv-form-error');
    error.textContent=value>max?`You can add up to ${money(max)}, based on available funds and your remaining target.`:'';error.hidden=value<=max;
    f.querySelector('[type="submit"]').disabled=!Number.isFinite(value)||value<=0||value>max;
  });
  document.addEventListener('submit',event=>{
    const f=event.target.closest('[data-savings-form]');if(!f)return;event.preventDefault();if(!f.reportValidity())return;
    const data=new FormData(f),kind=f.dataset.savingsForm,num=n=>Number(data.get(n));
    const fail=s=>{const e=f.querySelector('.sv-form-error');e.hidden=false;e.textContent=s;};
    const valid=n=>Number.isFinite(n)&&n>=0&&n<=10000000&&Math.abs(n*100-Math.round(n*100))<0.00001;
    if(kind==='goal'){if(!String(data.get('name')).trim()||!valid(num('target'))||num('target')<=0||data.get('deadline')<today())return fail('Enter a name, positive target and a future target date.');Object.assign(draft,{name:String(data.get('name')).trim(),category:data.get('category'),target:num('target'),deadline:data.get('deadline')});wizard(2);return;}
    if(kind==='plan'){const auto=data.has('enabled')?num('auto'):0;if(!valid(num('initial'))||num('initial')>state.spendable||num('initial')>draft.target||!valid(auto)||(data.has('enabled')&&auto<=0))return fail('Initial savings must fit your available balance and target. Enable auto-save with a positive contribution.');Object.assign(draft,{initial:num('initial'),auto,frequency:data.get('frequency')});wizard(3);return;}
    if(kind==='confirm'){if(draft.initial>state.spendable)return fail('Insufficient sample funds.');const g={...draft,id:`goal-${state.goals.length}`,emoji:({'Emergency':'🚨','Holiday / Gifts':'🎄','Travel':'✈️','Lifestyle / Concert':'🎫'})[draft.category]||'✨',amount:draft.initial,streak:0,history:[]};state.goals.push(g);state.spendable-=g.amount;if(g.amount)record(g,'Initial allocation',g.amount);state.tab='personal';}
    else if(kind==='edit'){const g=state.goals.find(g=>g.id===data.get('id'));if(!String(data.get('name')).trim()||!valid(num('target'))||num('target')<g.amount||num('target')<=0||!valid(num('auto'))||data.get('deadline')<today())return fail('Use a name, future date and a target at least equal to saved funds.');Object.assign(g,{name:String(data.get('name')).trim(),target:num('target'),deadline:data.get('deadline'),auto:num('auto'),frequency:data.get('frequency')});}
    else if(kind==='split'){const allocations=state.goals.map(g=>[g,num(g.id)]),amount=allocations.reduce((n,[,a])=>n+a,0);if(!valid(num('budget'))||num('budget')>state.spendable||amount>num('budget')||allocations.some(([g,a])=>!valid(a)||a>g.target-g.amount))return fail('Allocations must fit the budget, available balance and each remaining goal target.');state.spendable=Math.round((state.spendable-amount)*100)/100;allocations.forEach(([g,a])=>{if(a){g.amount=Math.round((g.amount+a)*100)/100;record(g,'Smart Split allocation',a);}});}
    else if(kind==='circle'){const target=num('target'),mine=num('mine');if(!String(data.get('name')).trim()||!valid(target)||!valid(mine)||mine<=0||mine>=target||data.get('deadline')<today())return fail('Enter a name and future date, with your target below the group target.');const remaining=Math.round((target-mine)*100),base=Math.floor(remaining/3);const c={id:`circle-${state.circles.length}`,name:String(data.get('name')).trim(),emoji:'🌴',target,deadline:data.get('deadline'),members:['Levi','Maria','Alyssa','Juan'].map((name,i)=>({name,amount:0,target:i===0?mine:(base+(i===3?remaining%3:0))/100,visible:true,history:[]}))};state.circles.push(c);state.selected=c.id;state.tab='circles';}
    else {const c=state.circles.find(c=>c.id===data.get('id')),g=kind.startsWith('circle-')?c?.members[0]:state.goals.find(g=>g.id===data.get('id')),a=num('amount'),release=kind.includes('release');if(!g||!valid(a)||a<=0||a>(release?g.amount:Math.min(state.spendable,g.target-g.amount)))return fail('Amount exceeds your available funds or remaining target.');g.amount=Math.round((g.amount+(release?-a:a))*100)/100;state.spendable=Math.round((state.spendable+(release?a:-a))*100)/100;record(g,release?'Release':'Allocation',a);if(c&&sum(c)>=c.target)announce(`${c.name}: shared goal reached. Payments require separate authorization.`);}
    document.querySelector('#savings-dialog').close();refresh();announce('Sample savings updated. Your account balance is unchanged.');
  });
  function reset(){state=seed();draft=null;}
  return {render,reset};
})();
