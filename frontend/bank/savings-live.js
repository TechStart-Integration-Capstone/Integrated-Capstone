'use strict';
// Authenticated customer savings; standalone demo lives in savings.js.
window.PayPinkSavingsLive = (() => {
  const base = '/api/v1/accounts/savings';
  const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const money = value => new Intl.NumberFormat('en-PH', {style:'currency', currency:'PHP'}).format(Number(value || 0));
  const today = () => new Intl.DateTimeFormat('en-CA', {timeZone:'Asia/Manila',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
  const date = value => value ? new Date(String(value).slice(0,10)+'T00:00:00+08:00').toLocaleDateString('en-PH',{timeZone:'Asia/Manila',year:'numeric',month:'long',day:'2-digit'}) : 'No date';
  const categories = {EMERGENCY:['🚨','Emergency'],HOLIDAY:['🎄','Holiday / Gifts'],TRAVEL:['✈️','Travel'],LIFESTYLE:['🎫','Lifestyle / Concert'],OTHER:['✨','Other']};
  let context, data, circles = [], history = [], tab = 'personal', summary = 'personal', selected, draft, retry, notice = '', failure = '', busy = false, loading = false, epoch = 0, readVersion = 0, timer, origin;
  const goals = () => data?.goals || [];
  const personal = () => goals().filter(g => !g.circle_id);
  const accounts = () => (context?.profile()?.accounts || []).filter(a => ['SAVINGS','SAVINGS_ACCOUNT'].includes(a.accountType) && a.status === 'ACTIVE' && a.currency === 'PHP');
  const pending = () => !!retry || history.some(h => h.status === 'PENDING');
  const storageKey = () => 'paypink.savings.retry.' + encodeURIComponent(context?.owner || '');
  const persist = () => { try { if (retry) sessionStorage.setItem(storageKey(), JSON.stringify(retry)); else sessionStorage.removeItem(storageKey()); } catch { /* In-memory retry still protects this session. */ } };
  const active = stamp => stamp === epoch && context;
  async function request(path = '', options = {}) {
    const stamp = epoch, transport = context.api;
    const result = await transport(base + path, options);
    if (!active(stamp)) throw new Error('Session changed.');
    return result;
  }
  const btn = (action, label, id = '', mutation = false) => `<button type="button" class="btn btn-secondary" data-sv="${action}" data-id="${esc(id)}" ${busy || (mutation && pending()) ? 'disabled' : ''}>${label}</button>`;
  const bar = (amount, target, label) => `<div class="sv-progress" role="progressbar" aria-label="${esc(label)}" aria-valuemin="0" aria-valuemax="${Number(target)}" aria-valuenow="${Math.min(Number(target),Number(amount))}"><span style="width:${Math.max(0,Math.min(100,100*Number(amount)/Number(target)||0))}%"></span></div>`;
  const input = (name,label,value='',type='text',attrs='') => `<label for="sv-live-${name}">${label}</label><input id="sv-live-${name}" name="${name}" type="${type}" value="${esc(value)}" ${attrs}>`;
  const amount = (name,label,value='',min='0.01') => input(name,label,value,'number',`min="${min}" max="999999999999.99" step="0.01" required`);
  const accountSelect = () => `<label for="sv-live-accountId">Your savings account</label><select id="sv-live-accountId" name="accountId" required>${accounts().map(a=>`<option value="${a.accountId}">Savings •••• ${esc(String(a.accountNumber).slice(-4))}</option>`).join('')}</select>`;
  const consent = checked => `<label class="sv-checkbox"><input type="checkbox" name="shareProgress" ${checked?'checked':''}> Share my individual progress with circle members</label><small>The circle total remains visible. Your money stays in your own account.</small>`;
  const form = (kind,body,label='Save',id='') => `<form data-sv-form="${kind}" data-id="${esc(id)}">${body}<p class="sv-form-error" role="alert" hidden></p><button type="submit" class="btn btn-primary">${label}</button></form>`;
  function redraw() { const root=document.querySelector('#savings-live'); if(root && context) root.outerHTML=render(); }
  function modal(title,body) {
    let dialog=document.querySelector('#savings-live-dialog');
    if(!dialog) {
      dialog=document.createElement('dialog'); dialog.id='savings-live-dialog'; dialog.className='sv-dialog'; document.body.append(dialog);
      dialog.addEventListener('cancel', e=>{if(busy)e.preventDefault();});
      dialog.addEventListener('close',()=>{if(!dialog.open){draft=null;if(origin?.isConnected)origin.focus();}});
    }
    if(!dialog.open)origin=document.activeElement;
    dialog.setAttribute('aria-labelledby','sv-live-title');
    dialog.innerHTML=`<button class="sv-dialog-close" data-sv="close" aria-label="Close dialog">×</button><h2 id="sv-live-title">${esc(title)}</h2>${body}`;
    if(!dialog.open)dialog.showModal();
  }
  const close = () => {draft=null;document.querySelector('#savings-live-dialog')?.close();};
  function badgeList() {
    const total=Number(data.personalTotal)+Number(data.groupTotal);
    return [
      ['🌱','First ₱1K',total>=1000,'mint','Save your first ₱1,000 across your goals.'],
      ['🌷','Emergency Era',personal().some(g=>g.category==='EMERGENCY' && Number(g.savedAmount)>=100000),'rose','Build a ₱100,000 emergency cushion.'],
      ['👑','Consistency Queen',goals().some(g=>g.streak>=4),'lilac','Complete four scheduled contributions in a row.'],
      ['💎','Million Club',Number(data.personalTotal)>=1000000,'peach','Reach ₱1,000,000 in personal savings.']
    ].map(([emoji,name,earned,color,description])=>`<button class="sv-badge ${earned?'':'sv-locked'}" data-sv="badge" data-name="${esc(name)}" data-description="${esc(description)}"><span class="sv-medal sv-${color}" aria-hidden="true">${emoji}<span>${earned?'✓':'◇'}</span></span><strong>${name}</strong><small>${earned?'Earned':'Still growing'}</small></button>`).join('');
  }
  function overview() {
    const grouped=summary==='groups';
    return `<div class="sv-overview-grid"><section class="sv-total-card" aria-label="Savings total"><div class="sv-total-top"><span class="sv-eyebrow">YOUR SAVINGS</span><span aria-hidden="true">♡</span></div><div class="sv-total-switch" role="group" aria-label="Choose savings total"><button data-sv="summary-personal" aria-pressed="${!grouped}">Personal</button><button data-sv="summary-groups" aria-pressed="${grouped}">My group savings</button></div><p class="sv-total-label">${grouped?'My total group savings':'Total personal savings'}</p><h2 class="sv-total-amount">${money(grouped?data.groupTotal:data.personalTotal)}</h2><p>Across ${grouped?circles.filter(c=>c.membership_status==='ACTIVE').length:personal().length} ${grouped?'PinkCircles':'savings goals'}</p><div class="sv-total-note">${grouped?'Your contributions only. Every member keeps their own savings.':'A little today, a little closer to your goals.'}</div></section><section class="sv-card sv-badge-card"><div class="sv-card-top"><div><div class="sv-eyebrow">SMALL WINS, BIG ENERGY</div><h2>Your little hall of fame</h2></div><span aria-hidden="true">✨</span></div><p>Good habits deserve a cute little celebration.</p><div class="sv-badges">${badgeList()}</div><div class="sv-badge-note">♡ Badges reflect your saved progress and have no cash value.</div></section></div>`;
  }
  function goalActions(g) {
    return `<div class="sv-actions">${btn('add','＋ Add money',g.goal_id,true)}${btn('release','Release',g.goal_id,true)}${!g.circle_id?btn('edit','Edit goal',g.goal_id,true):''}${btn('schedule','Savings plan',g.goal_id,true)}${btn('activity','Activity',g.goal_id)}</div>`;
  }
  function plan(g) {
    const s=g.schedule?.[0];
    return s?.enabled?`${money(s.amount)} / ${esc(s.frequency.toLowerCase())} · Next: ${date(s.next_due)}`:'Manual contributions';
  }
  function personalView() {
    const completed=personal().filter(g=>Number(g.savedAmount)>=Number(g.target_amount)).length;
    return `<section class="sv-summary"><span>Your savings goals</span><h2>${personal().length} savings ${personal().length===1?'goal':'goals'}</h2><p>${personal().length-completed} in progress · ${completed} completed</p></section><div class="sv-goals">${personal().map(g=>`<article class="sv-card"><div class="sv-card-top"><h2>${categories[g.category]?.[0]||'✨'} ${esc(g.name)}</h2><strong>${Math.round(100*Number(g.savedAmount)/Number(g.target_amount))}%</strong></div><p><strong>${money(g.savedAmount)}</strong> / ${money(g.target_amount)}</p>${bar(g.savedAmount,g.target_amount,g.name)}<p class="sv-plan">Target: ${date(g.target_date)} · ${plan(g)}</p>${goalActions(g)}</article>`).join('')||'<div class="sv-card"><h2>Make room for your first goal</h2><p>Choose what you are saving for. You can start at ₱0.</p></div>'}</div><div class="sv-actions">${accounts().length?btn('new','＋ New savings goal','',true):''}${personal().length?btn('split','Smart Split','',true):''}</div><section class="sv-card sv-separate"><h2>Savings Streaks</h2><p>Consecutive successful scheduled attempts. Manual top-ups do not extend streaks.</p>${personal().map(g=>`<p>${esc(g.name)} <strong>${Number(g.streak||0)} contributions</strong></p>`).join('')||'<p>Your streaks will appear as you save.</p>'}<small>A declined scheduled attempt starts a new streak. Your saved amounts remain yours.</small></section><section class="sv-card sv-separate"><h2>Savings Milestones</h2><p>🌱 First ₱1,000 ${Number(data.personalTotal)>=1000?'✓':'◇'} · Completed goals: ${completed}</p><h3>First Million</h3>${bar(data.personalTotal,1000000,'Personal first million')}<p>${money(data.personalTotal)} / ${money(1000000)}</p><p>Badges have no cash value and do not change your account’s interest rate.</p></section>`;
  }
  function circleView() {
    const c=circles.find(c=>c.circle_id===selected), own=c?.members?.find(m=>m.goal_id), g=goals().find(g=>g.goal_id===own?.goal_id);
    const admin=c && String(c.admin_customer_id)===String(data.customerId);
    return `<section class="sv-summary"><span>Your PinkCircles</span><h2>${circles.filter(c=>c.membership_status==='ACTIVE').length} active circles</h2><p>Your personal contributions stay in your account.</p></section><div class="sv-goals">${circles.map(c=>`<button class="sv-card sv-circle-choice" data-sv="select" data-id="${esc(c.circle_id)}" aria-pressed="${selected===c.circle_id}"><div class="sv-card-top"><h2>${esc(c.name)}</h2><span>${c.membership_status==='INVITED'?'Invitation':`${c.members.filter(m=>m.status==='ACTIVE').length} members`}</span></div><p>${c.membership_status==='INVITED'?'You have been invited to save together.':`${money(c.savedAmount)} / ${money(c.target_amount)}`}</p>${c.membership_status==='ACTIVE'?bar(c.savedAmount,c.target_amount,c.name):''}</button>`).join('')||'<div class="sv-card"><p>No circles yet. Create one and invite another PayPink user.</p></div>'}</div>${accounts().length?btn('create','＋ Create PinkCircle','',true):''}${c?`<section class="sv-card sv-separate"><div class="sv-card-top"><h2>${esc(c.name)}</h2><span class="sv-soft-pill">Shared goal</span></div><p>Target: ${money(c.target_amount)} · ${date(c.target_date)}</p>${c.membership_status==='INVITED'?`<p>Join with your own savings account. Joining does not reserve money.</p>${accounts().length?btn('accept','Accept invitation',c.circle_id,true):'<p>An active PHP savings account is needed to join.</p>'}`:`<h3>Member contributions</h3>${c.members.map(m=>`<div class="sv-member"><div class="sv-card-top"><strong>${esc(m.first_name)} ${esc(m.last_name)}${m.goal_id?' (you)':''}</strong><span>${m.status==='INVITED'?'Invited':m.savedAmount===undefined?'Contribution hidden':`${money(m.savedAmount)} / ${money(m.target_amount)}`}</span></div>${m.status==='ACTIVE' && m.savedAmount!==undefined?bar(m.savedAmount,m.target_amount,'Member savings'):''}${admin&&m.status==='ACTIVE'?btn('target','Propose target',m.customer_id,true):''}</div>`).join('')}${g?goalActions(g):''}<div class="sv-actions">${own?btn('visibility','Progress privacy',c.circle_id,true):''}${admin?btn('invite','Invite PayPink user',c.circle_id,true):''}</div>${own?.proposed_target!=null?`<p>Proposed personal target: ${money(own.proposed_target)}</p>${btn('approve','Review target proposal',c.circle_id,true)}`:''}<div class="sv-privacy sv-separate"><div><strong>♧ Your money stays yours</strong><p>Each member controls their own savings and releases. Only progress is shared. Circle totals remain visible when individual amounts are hidden.</p></div></div>`}</section>`:''}`;
  }
  function render() {
    const header=`<div class="sv-heading"><div><h1>PayPink Savings</h1><p>Your goals, your pace.</p></div>${btn('refresh',loading?'Loading…':'Refresh')}</div>`;
    const body=loading&&!data?'<p role="status">Loading your savings…</p>':failure?`<section class="sv-card" role="alert"><h2>Savings is unavailable right now</h2><p>${esc(failure)}</p>${btn('refresh','Try again')}</section>`:data?`${notice?`<p class="notice" role="status">${esc(notice)}</p>`:''}${pending()?`<div class="notice" role="status"><p>A savings request is awaiting confirmation. Check its status before making another change.</p>${btn('check','Check pending request')}</div>`:''}${!accounts().length?'<p class="notice">You need an active PHP savings account to create a goal or join a PinkCircle.</p>':''}${overview()}<div class="sv-circle-tabs" role="group" aria-label="Savings views"><button data-sv="personal" aria-pressed="${tab==='personal'}">My Savings</button><button data-sv="circles" aria-pressed="${tab==='circles'}">PinkCircles</button></div>${tab==='personal'?personalView():circleView()}`:'<p>Loading your accounts…</p>';
    return `<div id="savings-live" class="savings-view" aria-busy="${loading||busy}">${header}${body}</div>`;
  }
  async function load() {
    if(!context)return;
    const stamp=epoch, version=++readVersion;
    loading=true;redraw();clearTimeout(timer);
    try {
      const results=await Promise.allSettled([request(),request('/circles'),request('/activity')]);
      if(!active(stamp)||version!==readVersion)return;
      const failed=results.find(r=>r.status==='rejected');if(failed)throw failed.reason;
      [data,circles,history]=results.map(r=>r.value);failure='';
      if(!circles.some(c=>c.circle_id===selected))selected=circles[0]?.circle_id;
      if(retry) {
        const match=history.find(h=>h.idempotency_key===retry.key || h.operation_id===retry.operationId);
        if(match) {
          if(match.status==='PENDING')retry.operationId=match.operation_id;
          else {notice=match.status==='CONFIRMED'?'Your savings request is confirmed.':'Your savings request was declined. No savings were changed by that request.';retry=null;}
          persist();
        }
      }
    } catch(error) {
      if(active(stamp)&&version===readVersion)failure=error.status===404?'Savings is not available yet. Please try again later.':error.message;
    } finally {
      if(active(stamp)&&version===readVersion){loading=false;redraw();if(pending()&&document.querySelector('#savings-live'))timer=setTimeout(load,10000);}
    }
  }
  function mount(options) {
    if(!context || context.owner!==options.owner){reset();context=options;try{retry=JSON.parse(sessionStorage.getItem(storageKey())||'null');}catch{retry=null;}}
    else context=options;
    document.querySelector('#main').innerHTML=render();
    load();
  }
  function reset() {
    epoch++;readVersion++;clearTimeout(timer);context=null;data=null;circles=[];history=[];draft=null;retry=null;busy=false;loading=false;notice='';failure='';tab='personal';summary='personal';selected=null;close();
    const dialog=document.querySelector('#savings-live-dialog');if(dialog)dialog.innerHTML='';
  }
  function scheduleFields(s={}) {
    return `<label class="sv-checkbox"><input name="enabled" type="checkbox" ${s.enabled?'checked':''}> Enable automatic contributions</label>${amount('amount','Contribution amount (₱)',s.amount||500)}<label for="sv-live-frequency">Frequency</label><select id="sv-live-frequency" name="frequency">${['WEEKLY','MONTHLY','PAYDAY'].map(f=>`<option ${f===s.frequency?'selected':''}>${f}</option>`).join('')}</select>${input('nextDue','Next contribution date',String(s.next_due||s.nextDue||today()).slice(0,10),'date',`min="${today()}" required`)}<small>Payday dates are the 15th and month-end. Contributions need sufficient funds and may be declined.</small>`;
  }
  function wizard(step) {
    draft.step=step;
    let body;
    if(step===1)body=form('goal',`${accountSelect()}${input('name','Goal name',draft.name,'text','maxlength="80" required')}<label for="sv-live-category">Category</label><select id="sv-live-category" name="category">${Object.entries(categories).map(([k,v])=>`<option value="${k}" ${draft.category===k?'selected':''}>${v[1]}</option>`).join('')}</select>${amount('target','Target amount (₱)',draft.target)}${input('targetDate','Target date',draft.targetDate,'date',`min="${today()}" required`)}`,'Continue');
    if(step===2)body=form('plan',`${amount('initial','Initial amount to set aside (₱)',draft.initial,'0')}${scheduleFields(draft.schedule)}${btn('back','Back')}`,'Review goal');
    if(step===3)body=form('confirm',`<section class="sv-card"><h3>${esc(draft.name)}</h3><p>Target: ${money(draft.target)} · ${date(draft.targetDate)}</p><p>Initial reservation: ${money(draft.initial)}</p><p>Auto-save: ${draft.schedule.enabled?`${money(draft.schedule.amount)} / ${esc(draft.schedule.frequency.toLowerCase())} from ${date(draft.schedule.nextDue)}`:'Off'}</p></section><p>Your savings stay in your account. Initial funding and the savings plan are confirmed separately after the goal is created.</p>${btn('back','Back')}`,'Create savings goal');
    modal(['','What are you saving for?','How would you like to save?','Review your savings goal'][step],`<div class="sv-steps" aria-label="Step ${step} of 3">${['Set your goal','Savings plan','Review'].map((s,i)=>`<span class="${i<step?'active':''}">${s}</span>`).join('')}</div>${body}`);
    if(step===1&&draft.accountId)document.querySelector('#sv-live-accountId').value=draft.accountId;
  }
  function positive(value,zero=false) {
    const n=Number(value);if(!Number.isFinite(n)||n<(zero?0:0.01)||Math.abs(n*100-Math.round(n*100))>0.001)throw new Error('Enter an amount in whole centavos.');return n;
  }
  function scheduleBody(f) {
    const s={enabled:f.has('enabled'),amount:positive(f.get('amount')),frequency:f.get('frequency'),nextDue:f.get('nextDue')};
    if(s.frequency==='PAYDAY'){const d=new Date(s.nextDue+'T00:00:00Z');if(d.getUTCDate()!==15 && d.getUTCDate()!==new Date(Date.UTC(d.getUTCFullYear(),d.getUTCMonth()+1,0)).getUTCDate())throw new Error('Choose the 15th or last day of the month for payday savings.');}
    return s;
  }
  async function operation(body) {
    if(pending())throw new Error('Check your pending savings request first.');
    const bytes=crypto.getRandomValues(new Uint8Array(16));
    retry={key:Array.from(bytes,b=>b.toString(16).padStart(2,'0')).join(''),body};persist();
    return sendRetry();
  }
  async function sendRetry() {
    clearTimeout(timer);
    const attempt=retry, stamp=epoch;
    try {
      const result=await request('/operations',{method:'POST',body:attempt.body,headers:{'Idempotency-Key':attempt.key}});
      if(result.status==='PENDING'){retry.operationId=result.operationId;persist();return 'Your request is pending. We will show the result when confirmed.';}
      retry=null;persist();return 'Your savings request is confirmed.';
    } catch(error) {
      if(!active(stamp))throw error;
      if(error.data?.status==='REJECTED' || [400,403,404,409,422].includes(error.status)){retry=null;persist();throw error;}
      // A dropped response can follow a committed reservation. Keep the exact key and body.
      persist();return 'We could not confirm the result yet. Check the pending request before trying another contribution.';
    }
  }
  async function checkPending() {
    if(retry) {
      if(retry.operationId) {
        const result=await request('/operations/'+retry.operationId);
        if(result.status!=='PENDING'){notice=result.status==='CONFIRMED'?'Your savings request is confirmed.':'Your savings request was declined.';retry=null;persist();}
      } else notice=await sendRetry();
    }
    await load();
  }
  async function createGoal() {
    const d={...draft}, stamp=epoch, goal={accountId:Number(d.accountId),name:d.name,category:d.category,target:d.target,targetDate:d.targetDate};
    let created;
    try {created=await request('/goals',{method:'POST',body:goal});}
    catch(error){if(!active(stamp)||error.status<500)throw error;close();notice='The goal could not be confirmed. Check your refreshed goals before creating another. '+error.message;await load();return;}
    const messages=['Goal created.'];
    if(d.initial>0) {
      try {messages.push(await operation({accountId:goal.accountId,type:'ALLOCATE',lines:[{goalId:created.goalId,amount:d.initial}]}));}
      catch(error){if(!active(stamp))throw error;messages.push('Initial funding was not completed: '+error.message);}
    }
    if(d.schedule.enabled) {
      if(pending())messages.push('Set your savings plan from the goal once funding is confirmed.');
      else try {await request('/goals/'+created.goalId+'/schedule',{method:'PUT',body:d.schedule});messages.push('Savings plan saved.');}
      catch(error){if(!active(stamp))throw error;messages.push('Savings plan was not saved. Open Savings plan on this goal to try again.');}
    }
    notice=messages.join(' ');close();await load();
  }
  function activity(id) {
    const rows=history.map(h=>{try{return {...h,command:JSON.parse(h.request_json).command};}catch{return {...h,command:{lines:[]}};}}).filter(h=>h.command.lines.some(l=>l.goalId===id));
    modal('Savings activity',`<p>From your latest 100 savings requests. Times shown in Manila.</p>${rows.map(h=>{const raw=String(h.created_at);const time=new Date(/[zZ]|[+-]\d\d:\d\d$/.test(raw)?raw:raw+'Z').toLocaleString('en-PH',{timeZone:'Asia/Manila'});return `<article class="sv-card"><p>${esc(time)} PHT</p><p>${h.command.type==='RELEASE'?'Release':'Set aside'} ${money(h.command.lines.find(l=>l.goalId===id).amount)} · ${esc(h.status)}</p><small>Reference: ${esc(h.operation_id)}</small></article>`;}).join('')||'<p>No recent activity for this goal.</p>'}`);
  }
  document.addEventListener('click', async event=>{
    const b=event.target.closest('[data-sv]');if(!b||!context||busy)return;
    const action=b.dataset.sv,id=b.dataset.id,g=goals().find(g=>g.goal_id===id),c=circles.find(c=>c.circle_id===id),current=circles.find(c=>c.circle_id===selected);
    if(action==='close')return close();
    if(action==='refresh')return load();
    if(action==='summary-personal'||action==='summary-groups'){summary=action==='summary-groups'?'groups':'personal';return redraw();}
    if(action==='personal'||action==='circles'){tab=action;return redraw();}
    if(action==='select'){selected=id;return redraw();}
    if(action==='badge')return modal(b.dataset.name,`<p>${esc(b.dataset.description)}</p><p>Badges celebrate your progress and have no cash value.</p>`);
    if(action==='activity')return activity(id);
    if(action==='check'){
      const stamp=epoch;busy=true;redraw();try{await checkPending();}catch(error){if(active(stamp)){notice=error.message;await load();}}finally{if(active(stamp)){busy=false;redraw();}}return;
    }
    if(pending())return;
    if(action==='new'){draft={name:'',category:'OTHER',target:10000,targetDate:today(),initial:0,schedule:{enabled:false}};return wizard(1);}
    if(action==='back'){
      if(draft.step===2){const values=new FormData(b.closest('form'));draft.initial=values.get('initial');draft.schedule={enabled:values.has('enabled'),amount:values.get('amount'),frequency:values.get('frequency'),nextDue:values.get('nextDue')};}
      return wizard(draft.step-1);
    }
    if(action==='add'||action==='release')return modal(action==='add'?'Add money':'Release savings',form(action,`<p>${esc(g.name)} · Saved: ${money(g.savedAmount)}</p><p>${action==='add'?'This reserves funds in your own savings account.':'Released funds become available in your account.'}</p>${amount('amount','Amount (₱)')}`,'Confirm',id));
    if(action==='edit')return modal('Edit savings goal',form('edit',`${input('name','Goal name',g.name,'text','maxlength="80" required')}${amount('target','Target amount (₱)',g.target_amount)}${input('targetDate','Target date',String(g.target_date).slice(0,10),'date',`min="${today()}" required`)}`,'Save changes',id));
    if(action==='schedule')return modal('Savings plan',form('schedule',scheduleFields(g.schedule?.[0]),'Save plan',id));
    if(action==='split')return modal('Smart Split',form('split',`${amount('budget','Savings budget (₱)')}<p>Choose goals linked to the same savings account.</p>${personal().map(g=>amount(g.goal_id,`${esc(g.name)} · account •••• ${esc(String(context.profile().accounts.find(a=>a.accountId===g.account_id)?.accountNumber||'').slice(-4))}`,0,'0')).join('')}`,'Confirm split'));
    if(action==='create')return modal('Create PinkCircle',form('create',`${accountSelect()}${input('name','Circle name','','text','maxlength="80" required')}${amount('target','Shared target (₱)')}${amount('myTarget','My agreed target (₱)')}${input('targetDate','Target date',today(),'date',`min="${today()}" required`)}${consent(false)}<p>Invite members after creating your circle. No money is reserved when you create it.</p>`,'Create circle'));
    if(action==='accept')return modal('Join '+c.name,form('accept',`${accountSelect()}${consent(false)}`,'Join circle',id));
    if(action==='invite')return modal('Invite a PayPink user',form('invite',`${input('username','PayPink username','','text','maxlength="100" required')}${amount('target','Proposed contribution target (₱)')}<p>Only an active registered PayPink user can be invited. They choose whether to join.</p>`,'Send invitation',id));
    if(action==='visibility')return modal('Progress privacy',form('visibility',consent(c.members.find(m=>m.goal_id)?.share_progress),'Save privacy',id));
    if(action==='target')return modal('Propose a member target',form('target',`${amount('target','New target (₱)')}<p>The member must approve this change.</p>`,'Propose target',id));
    if(action==='approve')return modal('Review your target',form('approve',`<p>Your new agreed target would be ${money(current.members.find(m=>m.goal_id).proposed_target)}. This does not reserve any money.</p>`,'Accept target',id));
  });
  document.addEventListener('submit',async event=>{
    const f=event.target.closest('[data-sv-form]');if(!f||!context)return;event.preventDefault();if(busy)return;
    const values=new FormData(f),kind=f.dataset.svForm,id=f.dataset.id,stamp=epoch;
    const get=n=>values.get(n), write=(path,body,method='POST')=>request(path,{method,body});
    try {
      f.querySelector('.sv-form-error').hidden=true;
      if(kind==='goal'){Object.assign(draft,{accountId:Number(get('accountId')),name:get('name').trim(),category:get('category'),target:positive(get('target')),targetDate:get('targetDate')});if(!draft.name)throw new Error('Enter a goal name.');return wizard(2);}
      if(kind==='plan'){draft.initial=positive(get('initial'),true);if(draft.initial>draft.target)throw new Error('Initial savings cannot exceed the goal target.');draft.schedule=scheduleBody(values);return wizard(3);}
      busy=true;f.querySelectorAll('button,input,select').forEach(n=>n.disabled=true);redraw();
      if(kind==='confirm'){await createGoal();return;}
      if(kind==='add'||kind==='release'){
        const g=goals().find(g=>g.goal_id===id),n=positive(get('amount'));
        if(n>Number(kind==='release'?g.savedAmount:Number(g.target_amount)-Number(g.savedAmount)))throw new Error(kind==='release'?'The amount exceeds your reserved savings.':'The amount exceeds the remaining goal target.');
        notice=await operation({accountId:Number(g.account_id),type:kind==='release'?'RELEASE':'ALLOCATE',lines:[{goalId:id,amount:n}]});
      } else if(kind==='split'){
        const budget=positive(get('budget')),chosen=personal().map(g=>({g,n:positive(get(g.goal_id),true)})).filter(v=>v.n>0);
        if(!chosen.length)throw new Error('Choose at least one contribution.');
        if(new Set(chosen.map(v=>v.g.account_id)).size!==1)throw new Error('Split only between goals on the same savings account.');
        if(chosen.reduce((n,v)=>n+Math.round(v.n*100),0)>Math.round(budget*100))throw new Error('Your split exceeds the savings budget.');
        if(chosen.some(v=>v.n>Number(v.g.target_amount)-Number(v.g.savedAmount)))throw new Error('A contribution exceeds its remaining goal target.');
        notice=await operation({accountId:Number(chosen[0].g.account_id),type:'ALLOCATE',lines:chosen.map(v=>({goalId:v.g.goal_id,amount:v.n}))});
      } else if(kind==='edit'){await write('/goals/'+id,{name:get('name').trim(),target:positive(get('target')),targetDate:get('targetDate')},'PUT');notice='Goal updated.';}
      else if(kind==='schedule'){await write('/goals/'+id+'/schedule',scheduleBody(values),'PUT');notice='Savings plan saved.';}
      else if(kind==='create'){
        const target=positive(get('target')),myTarget=positive(get('myTarget'));if(myTarget>target)throw new Error('Your target cannot exceed the shared target.');
        try {const result=await write('/circles',{goal:{accountId:Number(get('accountId')),name:get('name').trim(),category:'OTHER',target,targetDate:get('targetDate')},myTarget,shareProgress:values.has('shareProgress')});selected=result.circleId;tab='circles';notice='Circle created. Invite another PayPink user to join.';}
        catch(error){if(!active(stamp)||error.status<500)throw error;close();notice='Circle creation could not be confirmed. Check your refreshed circles before creating another. '+error.message;await load();return;}
      } else if(kind==='accept'){await write('/circles/'+id+'/accept',{accountId:Number(get('accountId')),shareProgress:values.has('shareProgress')});notice='You joined the circle. Add your contribution when you are ready.';}
      else if(kind==='invite'){await write('/circles/'+id+'/invitations',{username:get('username').trim(),target:positive(get('target'))});notice='Invitation sent.';}
      else if(kind==='visibility'){await write('/circles/'+id+'/visibility',{shareProgress:values.has('shareProgress')},'PUT');notice='Progress privacy saved.';}
      else if(kind==='target'){await write('/circles/'+selected+'/members/'+id+'/target',{target:positive(get('target'))});notice='Target proposed. Waiting for the member’s approval.';}
      else if(kind==='approve'){await write('/circles/'+id+'/target/accept');notice='New target accepted.';}
      close();await load();
    } catch(error){if(active(stamp)){const node=f.querySelector('.sv-form-error');node.textContent=error.message;node.hidden=false;if(error.status===409)await load();}}
    finally{if(active(stamp)){busy=false;f.querySelectorAll('button,input,select').forEach(n=>n.disabled=false);redraw();}}
  });
  return {mount,reset};
})();
