// Evaluated by savings-preview.cjs in the real bank shell. HTTP is stubbed locally.
(async () => {
  const passed = [], calls = [];
  const check = (value, label) => { if (!value) throw new Error(label); passed.push(label); };
  const tick = () => new Promise(resolve => setTimeout(resolve, 0));
  const wait = async condition => { for(let i=0;i<200;i++){if(condition())return;await new Promise(r=>setTimeout(r,5));}throw new Error('UI did not settle'); };
  const click = (action,id) => {const b=document.querySelector(`[data-sv="${action}"]${id?`[data-id="${id}"]`:''}`);if(!b||b.disabled)throw new Error('Unavailable action: '+action);b.click();};
  const fill = (name,value) => {document.querySelector(`#savings-live-dialog [name="${name}"]`).value=value;};
  const openAdd = async id => {click('add',id);await wait(()=>document.querySelector('#savings-live-dialog [data-sv-form="add"]'));};
  const submit = async () => {document.querySelector('#savings-live-dialog form').requestSubmit();await tick();await wait(()=>document.querySelector('#savings-live')?.getAttribute('aria-busy')==='false');};
  const close = async () => {document.querySelector('#savings-live-dialog').close();await tick();};
  const text = () => document.querySelector('#savings-live').textContent;
  const gid='00000000-0000-0000-0000-000000000001', cg='00000000-0000-0000-0000-000000000002';
  let serverGoals=[{goal_id:gid,account_id:11,name:'Real emergency goal',category:'EMERGENCY',circle_id:null,target_amount:10000,target_date:'2099-12-15',savedAmount:1200,streak:4,schedule:[]}];
  let serverCircles=[], serverHistory=[], mode='', sequence=10, deferredRead, available=9000;
  const reply=(body,status=200)=>new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json'}});
  window.fetch=async (url,options={})=>{
    if(!String(url).startsWith('/api/v1/accounts/savings'))return reply([]);
    const route=String(url).slice('/api/v1/accounts/savings'.length),method=options.method||'GET',body=options.body?JSON.parse(options.body):undefined;
    calls.push({route,method,body,key:options.headers?.['Idempotency-Key'],auth:options.headers?.Authorization});
    if(mode==='unavailable')return reply({message:'Disabled'},404);
    if(mode==='defer' && method==='GET' && route==='')return new Promise(resolve=>{deferredRead=()=>resolve(reply({customerId:1,goals:[],personalTotal:999999,groupTotal:0}));});
    if(method==='GET'){
      if(route.endsWith('/breakdown'))return reply({accountBalance:20000,personalReserved:6000,circleReserved:5000,otherHolds:0,unlistedReservations:0,availableBalance:9000,allocations:[{name:'Emergency fund',kind:'PERSONAL',amount:6000},{name:'Our trip',kind:'PINK_CIRCLE',amount:5000}]});
      if(route.endsWith('/funding')){
        if(mode==='funding-fail'){mode='';return reply({message:'Unavailable'},503);}
        const g=serverGoals.find(g=>route.includes(g.goal_id)),remaining=g.target_amount-g.savedAmount;
        return reply({accountId:g.account_id,accountBalance:20000,reservedSavings:10000,otherHolds:1000,availableBalance:available,goalSavedAmount:g.savedAmount,remainingTarget:remaining,maximumContribution:Math.min(available,remaining)});
      }
      if(route==='')return reply({customerId:1,goals:serverGoals,personalTotal:serverGoals.filter(g=>!g.circle_id).reduce((n,g)=>n+g.savedAmount,0),groupTotal:serverGoals.filter(g=>g.circle_id).reduce((n,g)=>n+g.savedAmount,0)});
      if(route==='/circles')return reply(serverCircles);
      if(route==='/activity')return reply(serverHistory);
      if(route.startsWith('/operations/'))return reply({operationId:route.split('/').pop(),status:'CONFIRMED'});
    }
    if(route==='/operations'){
      if(mode==='timeout'){mode='';throw new TypeError('Connection dropped');}
      if(mode==='reject'){mode='';return reply({status:'REJECTED',message:'Insufficient available funds'},422);}
      const operationId='op-'+sequence++,status=mode==='pending'?'PENDING':'CONFIRMED';if(mode==='pending')mode='';
      if(status==='CONFIRMED')for(const line of body.lines)serverGoals.find(g=>g.goal_id===line.goalId).savedAmount+=(body.type==='RELEASE'?-1:1)*line.amount;
      serverHistory.unshift({operation_id:operationId,status,idempotency_key:options.headers['Idempotency-Key'],request_json:JSON.stringify({command:body}),created_at:'2026-10-08T01:00:00'});
      return reply({operationId,status},status==='PENDING'?202:200);
    }
    if(route==='/goals'){
      const goalId='goal-'+sequence++;
      serverGoals.push({goal_id:goalId,account_id:body.accountId,name:body.name,category:body.category,circle_id:null,target_amount:body.target,target_date:body.targetDate,savedAmount:0,schedule:[]});
      if(mode==='create-timeout'){mode='';throw new TypeError('Response lost after commit');}
      return reply({goalId});
    }
    if(route.endsWith('/schedule')){
      if(mode==='schedule-fail'){mode='';return reply({message:'Unavailable'},503);}
      serverGoals.find(g=>route.includes(g.goal_id)).schedule=[{...body,next_due:body.nextDue}];return reply({});
    }
    if(route.startsWith('/goals/')){Object.assign(serverGoals.find(g=>route.includes(g.goal_id)),{name:body.name,target_amount:body.target,target_date:body.targetDate});return reply({});}
    if(route==='/circles'){
      serverGoals.push({goal_id:cg,account_id:11,name:body.goal.name,circle_id:'circle-1',category:'OTHER',target_amount:body.myTarget,target_date:body.goal.targetDate,savedAmount:0,schedule:[]});
      serverCircles.push({circle_id:'circle-1',admin_customer_id:1,name:body.goal.name,target_amount:body.goal.target,target_date:body.goal.targetDate,membership_status:'ACTIVE',savedAmount:0,members:[{customer_id:1,goal_id:cg,status:'ACTIVE',first_name:'Levi',last_name:'Dela Cruz',share_progress:false,savedAmount:0,target_amount:body.myTarget}]});return reply({circleId:'circle-1',goalId:cg});
    }
    if(route.endsWith('/invitations')){if(body.username==='outsider')return reply({message:'Active PayPink user not found'},404);serverCircles[0].members.push({customer_id:2,status:'INVITED',first_name:'Maria',last_name:'Santos',target_amount:body.target});return reply({});}
    if(route.endsWith('/visibility')){serverCircles[0].members[0].share_progress=body.shareProgress;return reply({});}
    if(route.endsWith('/target/accept')){serverCircles[0].members[0].proposed_target=null;return reply({});}
    if(route.includes('/members/')){serverCircles[0].members[0].proposed_target=body.target;return reply({});}
    if(route.endsWith('/accept')){serverCircles.find(c=>route.includes(c.circle_id)).membership_status='ACTIVE';serverCircles.find(c=>route.includes(c.circle_id)).members=[];return reply({goalId:'joined'});}
    throw new Error('Unexpected endpoint '+method+' '+route);
  };
  const login=()=>{
    state.session={token:'local-fixture-token',fullName:'Levi Dela Cruz',expiresAt:Date.now()+60000};
    state.profile={username:'levi-fixture',fullName:'Levi Dela Cruz',accounts:[{accountId:11,accountNumber:'123456789012',accountType:'SAVINGS_ACCOUNT',currency:'PHP',status:'ACTIVE',currentBalance:20000}]};
    state.page='overview';renderShell();document.querySelector('[data-page="savings"]').click();
  };
  login();await wait(()=>document.querySelector('.sv-total-amount'));
  check(text().includes('1,200.00')&&!text().includes('23,500'),'Bank uses server totals without demo fallback');
  check(calls.every(c=>c.auth==='Bearer local-fixture-token'),'Savings requests use bank authentication');
  check(text().includes('1 savings goal')&&document.querySelectorAll('#savings-live .sv-badge').length===4,'Live goal counts and four badges');
  check(document.querySelector('.sv-overview-grid').compareDocumentPosition(document.querySelector('.sv-circle-tabs'))&Node.DOCUMENT_POSITION_FOLLOWING,'Tabs remain below compact overview');
  await openAdd();click('maximum');check(Number(document.querySelector('[name="amount"]').value)===8800,'Add maximum caps available funds at remaining goal target');
  fill('amount',9000);document.querySelector('[name="amount"]').dispatchEvent(new Event('input',{bubbles:true}));check(document.querySelector('#savings-live-dialog [type="submit"]').disabled&&!document.querySelector('.sv-form-error').hidden,'Over-maximum amount immediately disables submission');
  fill('amount',100);document.querySelector('[name="amount"]').dispatchEvent(new Event('input',{bubbles:true}));await submit();check(text().includes('1,300.00'),'Confirmed reservation refreshes server totals');
  check(calls.filter(c=>c.route==='/operations')[0].key,'Reservation has an idempotency key');
  click('release');fill('amount',100);await submit();check(text().includes('1,200.00'),'Release uses the core operation endpoint');
  available=300;await openAdd();click('maximum');check(Number(document.querySelector('[name="amount"]').value)===300,'Add maximum caps remaining target at available account funds');await close();available=9000;
  mode='funding-fail';click('add');await wait(()=>document.querySelector('[data-sv="funding-retry"]'));check(!document.querySelector('#savings-live-dialog form'),'Unavailable live funds block contribution without cached fallback');await close();
  available=0;await openAdd();check(document.querySelector('#savings-live-dialog [type="submit"]').disabled&&!document.querySelector('[name="amount"]'),'Zero available funds block submission');await close();available=9000;
  mode='timeout';await openAdd();fill('amount',50);await submit();
  check(!!document.querySelector('[data-sv="check"]')&&document.querySelector('[data-sv="add"]').disabled,'Unknown result blocks competing contributions');
  const original=calls.filter(c=>c.route==='/operations').at(-1);
  // Remount simulates revisiting the page; retry survives and reuses the exact command.
  window.PayPinkSavingsLive.reset();renderPage();await wait(()=>document.querySelector('[data-sv="check"]'));
  click('check');await wait(()=>!document.querySelector('[data-sv="check"]'));
  const retried=calls.filter(c=>c.route==='/operations').at(-1);
  check(original.key===retried.key&&JSON.stringify(original.body)===JSON.stringify(retried.body),'Timeout recovery retains exact key and body across remount');
  mode='pending';await openAdd();fill('amount',20);await submit();
  check(text().includes('awaiting confirmation'),'202 remains pending rather than reporting success');
  serverHistory[0].status='CONFIRMED';serverGoals[0].savedAmount+=20;click('refresh');await wait(()=>!document.querySelector('[data-sv="check"]'));
  mode='reject';await openAdd();fill('amount',100);await submit();check(document.querySelector('.sv-form-error').textContent.includes('Insufficient'),'Definitive rejection is shown without pretending funds changed');await close();
  click('new');fill('name','<img src=x onerror=alert(1)>');fill('targetDate','2099-12-15');await submit();fill('initial',10);document.querySelector('[name="enabled"]').checked=true;fill('nextDue','2099-12-15');await submit();mode='schedule-fail';await submit();
  check(text().includes('Goal created.')&&text().includes('Savings plan was not saved')&&!document.querySelector('#savings-live img'),'Creation reports partial success and escapes goal names');
  const creates=calls.filter(c=>c.route==='/goals'&&c.method==='POST').length;
  click('new');fill('name','After timeout');fill('targetDate','2099-12-15');await submit();await submit();mode='create-timeout';await submit();
  check(text().includes('After timeout')&&calls.filter(c=>c.route==='/goals'&&c.method==='POST').length===creates+1,'Unknown create response refreshes committed goal without automatic duplicate');
  click('edit',gid);fill('name','Updated goal');await submit();check(text().includes('Updated goal'),'Goal edits use API');
  click('schedule',gid);document.querySelector('[name="enabled"]').checked=true;fill('frequency','PAYDAY');fill('nextDue','2099-12-14');await submit();check(document.querySelector('.sv-form-error').textContent.includes('15th'),'Invalid payday schedule is rejected locally');fill('nextDue','2099-12-15');await submit();check(text().includes('payday'),'Valid savings plan persists');
  click('split');fill('budget',1);fill(gid,5);await submit();check(document.querySelector('.sv-form-error').textContent.includes('budget'),'Smart Split enforces the entered budget');fill('budget',5);await submit();
  click('activity',gid);check(document.querySelector('#savings-live-dialog').textContent.includes('CONFIRMED'),'Activity comes from persisted operations');await close();
  click('circles');click('create');fill('name','Our trip');fill('target',10000);fill('myTarget',4000);fill('targetDate','2099-12-15');await submit();check(text().includes('1 active circles'),'Circle creation creates a real own goal');
  await openAdd(cg);click('maximum');check(Number(document.querySelector('[name="amount"]').value)===4000,'Circle maximum uses own agreed target, not shared circle target');check(document.querySelector('.sv-funding').textContent.includes('10,000.00'),'Dialog includes funds already allocated across goals and circles');await close();
  click('invite');fill('username','outsider');fill('target',6000);await submit();check(document.querySelector('.sv-form-error').textContent.includes('PayPink user not found'),'Inviting an unknown username displays backend rejection');fill('username','maria');await submit();check(text().includes('Maria Santos')&&text().includes('Invited'),'Registered member invitation is displayed');
  serverCircles[0].members[1].status='ACTIVE';serverCircles[0].savedAmount=500;click('refresh');await wait(()=>text().includes('Contribution hidden'));
  check(!text().includes('undefined')&&text().includes('Contribution hidden'),'Hidden member amounts are not reconstructed or displayed');
  click('visibility');document.querySelector('[name="shareProgress"]').checked=true;await submit();check(serverCircles[0].members[0].share_progress,'Member consent is saved through API');
  click('target','1');fill('target',3000);await submit();click('approve');await submit();check(serverCircles[0].members[0].proposed_target===null,'Target changes require explicit member acceptance');
  click('summary-groups');check(document.querySelector('.sv-total-amount').textContent.includes('0.00'),'Group total shows own contributions, excluding other members');
  serverCircles[0].admin_customer_id=2;click('refresh');await wait(()=>!document.querySelector('[data-sv="invite"]'));check(!document.querySelector('[data-sv="target"]'),'Non-admin has no invitation or target proposal controls');
  serverCircles.push({circle_id:'invite-2',admin_customer_id:2,name:'Birthday',target_amount:5000,target_date:'2099-12-15',membership_status:'INVITED'});click('refresh');await wait(()=>document.querySelector('[data-id="invite-2"]'));click('select','invite-2');click('accept');await submit();check(serverCircles[1].membership_status==='ACTIVE','Invitation acceptance links selected savings account');
  mode='unavailable';click('refresh');await wait(()=>text().includes('unavailable'));check(!document.querySelector('.sv-total-amount'),'Disabled or failed API never displays sample totals');
  mode='';serverGoals=[];serverCircles=[];serverHistory=[];click('refresh');await wait(()=>document.querySelector('[data-sv="personal"]'));click('personal');await wait(()=>text().includes('0 savings goals'));check(text().includes('first goal'),'New customer receives a real empty state');
  state.profile.accounts=[];renderPage();await wait(()=>text().includes('active PHP savings account'));check(!document.querySelector('[data-sv="new"]'),'Customers without an eligible account cannot create goals');
  // A response arriving after logout must not restore previous customer data.
  mode='defer';click('refresh');await wait(()=>deferredRead);logout();deferredRead();await tick();check(!document.querySelector('#savings-live'),'Late API response cannot restore savings after logout');
  mode='';login();await wait(()=>document.querySelector('.sv-total-amount'));
  state.page='accounts';renderPage();await wait(()=>document.querySelector('.account-allocations'));
  const breakdown=document.querySelector('#account-savings-breakdown');
  check(breakdown.textContent.includes('20,000.00')&&breakdown.textContent.includes('9,000.00'),'Account breakdown separates total and available balances');
  check(!breakdown.textContent.includes('Other holds'),'Breakdown omits other holds');
  breakdown.querySelector('summary').click();
  check(breakdown.querySelector('details').open&&breakdown.textContent.includes('Our trip · PinkCircle · your contribution'),'View allocations expands own personal and circle savings');
  state.hideBalances=true;renderPage();await wait(()=>document.querySelector('.account-allocations'));
  check(!document.querySelector('#account-savings-breakdown').textContent.includes('9,000.00'),'Hide balances masks allocation breakdown');
  state.hideBalances=false;state.page='savings';renderPage();await wait(()=>document.querySelector('.sv-total-amount'));
  return passed;
})()
