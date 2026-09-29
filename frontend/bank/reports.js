'use strict';

function openTransactionReport() {
  if (!state.profile) return;
  const today=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Manila',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
  const first=today.slice(0,8)+'01';
  showDialog('Generate transaction report',`<p class="muted">Choose an account and date range to download your transactions as a PDF.</p><form id="report-form" class="auth-form">
    <div class="form-field"><label for="report-account">Account</label><select id="report-account" name="accountId" required>${state.profile.accounts.map(a=>`<option value="${a.accountId}" ${String(a.accountId)===state.accountFilter?'selected':''}>${escapeHtml(accountName(a.accountType))} · ${escapeHtml(maskedNumber(a.accountNumber))} · ${escapeHtml(a.currency)}</option>`).join('')}</select></div>
    <div class="form-row"><div class="form-field"><label for="report-from">From date</label><input id="report-from" name="from" type="date" required max="${today}" value="${first}"></div><div class="form-field"><label for="report-to">To date</label><input id="report-to" name="to" type="date" required max="${today}" value="${today}"></div></div>
    <p class="muted report-note">Both dates are included, using Philippine time. Choose up to 366 days. The report includes all transactions in that period, including pending and failed entries.</p>
    <div id="report-error" class="form-error" role="alert" hidden></div>
    <button class="btn btn-primary" type="submit" ${!state.profile.accounts.length?'disabled':''}>Download PDF</button></form>`);
}
document.addEventListener('click',event=>{if(event.target.closest('[data-generate-report]'))openTransactionReport();});
document.addEventListener('submit',async event=>{
  if(event.target.id!=='report-form')return;
  event.preventDefault();const form=event.target;
  const error=form.querySelector('#report-error'),button=form.querySelector('[type="submit"]');
  const values=Object.fromEntries(new FormData(form));error.hidden=true;
  const days=(new Date(values.to+'T00:00:00Z')-new Date(values.from+'T00:00:00Z'))/86400000;
  if(!Number.isFinite(days)||days<0||days>365){error.textContent='Choose a From date on or before the To date, with no more than 366 days.';error.hidden=false;return;}
  const generation=state.generation;button.disabled=true;button.textContent='Generating PDF…';
  try {
    const response=await fetch(`${API}/reports/transactions.pdf?${new URLSearchParams(values)}`,{headers:{Authorization:`Bearer ${state.session.token}`,Accept:'application/pdf'},cache:'no-store',signal:AbortSignal.timeout(60000)});
    if(generation!==state.generation)return;
    if(response.status===401){logout('Your session has ended. Please log in again.');return;}
    if(!response.ok){const data=await response.json().catch(()=>({}));throw new Error(data.message||'The report could not be generated. Please try again.');}
    if(!response.headers.get('content-type')?.includes('application/pdf'))throw new Error('The report could not be downloaded. Please try again.');
    const blob=await response.blob();if(generation!==state.generation)return;
    const url=URL.createObjectURL(blob),link=document.createElement('a');link.href=url;link.download=`PayPink-Transactions-${values.from}-to-${values.to}.pdf`;
    document.body.append(link);link.click();link.remove();setTimeout(()=>URL.revokeObjectURL(url),10000);
    toast('Your transaction report has been downloaded.');
  } catch(ex){if(generation===state.generation&&form.isConnected){error.textContent=ex.name==='TimeoutError'?'The report is taking too long. Try a shorter date range.':ex.message;error.hidden=false;}}
  finally{if(form.isConnected){button.disabled=false;button.textContent='Download PDF';}}
});
