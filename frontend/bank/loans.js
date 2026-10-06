'use strict';

// Phase 6 Loans: apply → offer → accept → my loans → schedule → pay. All money moves server-side.
const LOANS_API = '/api/v1/loans';
const loanState = {owner:null, loans:[], eligibility:null, loaded:false, loading:false, error:'', offer:null, applyKey:null, applyFingerprint:'', payKeys:{}, busy:false, formError:'', form:{}};

function resetLoansFor(owner) {
  Object.assign(loanState, {owner, loans:[], eligibility:null, loaded:false, loading:false, error:'', offer:null, applyKey:null, applyFingerprint:'', payKeys:{}, busy:false, formError:'', form:{}});
}

async function loanApi(path, {method = 'GET', body, idempotencyKey} = {}) {
  const headers = {Accept:'application/json', Authorization:`Bearer ${state.session.token}`};
  if (body) headers['Content-Type'] = 'application/json';
  if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey;
  let response;
  try {
    response = await fetch(`${LOANS_API}${path}`, {method, headers, body:body ? JSON.stringify(body) : undefined,
      cache:'no-store', signal:AbortSignal.timeout(15000)});
  } catch {
    const error = new Error('We couldn’t reach the bank. Check your connection and try again.');
    error.retryable = true;
    throw error;
  }
  const data = await response.json().catch(() => ({}));
  if (response.status === 401) { logout('Your session has ended. Please log in again.'); throw new Error('Your session has ended.'); }
  if (!response.ok) {
    const error = new Error(data.detail || data.message || 'We couldn’t complete your request. Please try again.');
    error.status = response.status;
    error.type = String(data.type || '').split('/').pop();
    error.retryable = response.status >= 500;
    throw error;
  }
  return data;
}

const loanPill = status => `<span class="pill ${['APPROVED','ACTIVE','PAID','CLOSED'].includes(status) ? 'pill-green' : ['DECLINED','OVERDUE'].includes(status) ? 'pill-red' : 'pill-gray'}">${escapeHtml(String(status).replace('_',' ').toLowerCase().replace(/^./, c => c.toUpperCase()))}</span>`;
const loanDate = value => value ? new Date(`${value}T00:00:00`).toLocaleDateString('en-PH', {month:'short', day:'numeric', year:'numeric'}) : '—';
const declineText = reason => ({CREDIT_SCORE_TOO_LOW:'Your credit score is below our minimum for a personal loan.', INSUFFICIENT_INCOME:'The monthly installment would be more than 30% of your monthly income.', EXISTING_LOAN_OVERDUE:'You have a loan with an overdue installment. Please settle it first.', CREDIT_LIMIT_REACHED:'You’ve reached your credit limit. Pay down your current loan to borrow more.'}[reason] || 'We can’t offer a loan right now.');

async function loadLoans() {
  if (!state.session || !state.profile || loanState.loading) return;
  if (loanState.owner !== state.profile.username) resetLoansFor(state.profile.username);
  loanState.loading = true;
  const generation = state.generation;
  try {
    const [loans, eligibility] = await Promise.all([loanApi(''), loanApi('/eligibility').catch(() => null)]);
    if (generation !== state.generation) return;
    loanState.loans = loans; loanState.eligibility = eligibility; loanState.loaded = true; loanState.error = '';
  } catch (error) {
    if (generation === state.generation) loanState.error = error.message;
  } finally {
    loanState.loading = false;
    if (generation === state.generation && ['loans','overview'].includes(state.page)) renderPage();
  }
}

function loansPage() {
  if (loanState.owner !== state.profile.username) resetLoansFor(state.profile.username);
  if (!loanState.loaded && !loanState.loading && !loanState.error) queueMicrotask(loadLoans);
  const accounts = state.profile.accounts.filter(a => a.status === 'ACTIVE' && a.currency === 'PHP');
  const terms = [3,6,9,12,18,24,36,48,60];
  return heading('Borrow with confidence.', 'Apply for a personal loan and get a decision instantly.')
    + `<div class="transfer-layout"><section class="transfer-panel" aria-labelledby="loan-apply-title"><h2 id="loan-apply-title">Apply for a loan</h2>
      ${loanState.formError ? `<div class="form-error" role="alert">${escapeHtml(loanState.formError)}</div>` : ''}
      <form id="loan-apply-form"><fieldset ${loanState.busy ? 'disabled' : ''}>
        <div class="form-field"><label for="loan-account">Pay into and repay from</label><select id="loan-account" name="accountNo" required>${accounts.map(a => `<option value="${escapeHtml(a.accountNumber)}" ${a.accountNumber === loanState.form.accountNo ? 'selected' : ''}>${escapeHtml(accountName(a.accountType))} · ${escapeHtml(maskedNumber(a.accountNumber))} · ${balance(a.currentBalance)}</option>`).join('')}</select></div>
        <div class="form-row"><div class="form-field"><label for="loan-amount">Amount (PHP)</label><input id="loan-amount" name="amount" type="number" min="5000" step="0.01" required placeholder="250000.00" value="${escapeHtml(loanState.form.amount ?? '')}"><small>${creditLimitText()}</small></div>
        <div class="form-field"><label for="loan-term">Term</label><select id="loan-term" name="termMonths">${terms.map(t => `<option value="${t}" ${t === Number(loanState.form.termMonths || 12) ? 'selected' : ''}>${t} months</option>`).join('')}</select></div></div>
        <button class="btn btn-primary" type="submit" ${accounts.length ? '' : 'disabled'}>${loanState.busy ? 'Checking…' : `Get my decision ${icon('arrow')}`}</button>
      </fieldset></form>
      ${loanState.offer ? offerCard(loanState.offer) : ''}
    </section>
    <aside class="transfer-guide" aria-labelledby="my-loans-title"><h2 id="my-loans-title">My loans</h2>${myLoansMarkup()}</aside></div>`;
}

// Credit limit = the credit-score band's maximum; what is still owed on open loans is taken off it.
function creditLimitText() {
  const e = loanState.eligibility;
  if (!e) return 'From ₱5,000.00.';
  if (!e.eligible) return escapeHtml(declineText(e.reason));
  return `From ₱5,000.00 up to <strong>${escapeHtml(money(e.available))}</strong>` + (Number(e.outstanding) > 0 ? ` (limit ${escapeHtml(money(e.creditLimit))}, ${escapeHtml(money(e.outstanding))} still owed).` : '.');
}

function offerCard(offer) {
  const o = offer.offer;
  const declined = offer.decision === 'DECLINED';
  const accepted = offer.status === 'ACCEPTED';
  return `<section class="transfer-receipt" aria-labelledby="loan-offer-title" style="margin-top:24px"><h2 id="loan-offer-title">Your decision ${loanPill(offer.decision)}</h2>
    <dl class="detail-list">${detail('Application',escapeHtml(offer.referenceNo))}${detail('Credit score',`${escapeHtml(offer.creditScore)} · ${escapeHtml(offer.band || '—')}`)}
    ${o ? detail('Amount',escapeHtml(money(o.amount))) + detail('Term',`${escapeHtml(o.termMonths)} months`) + detail('Interest rate',`${escapeHtml(o.annualRate)}% a year`) + detail('Monthly installment',`<strong>${escapeHtml(money(o.monthlyInstallment))}</strong>`) + detail('Offer valid until',escapeHtml(new Date(offer.expiresAt).toLocaleString('en-PH'))) : ''}</dl>
    ${declined ? `<p role="status">${escapeHtml(declineText(offer.declineReason))}</p>` : offer.decision === 'COUNTER_OFFER' ? '<p role="status">We can’t lend the full amount or term you asked for, but we can offer this instead.</p>' : ''}
    ${!declined && !accepted ? `<div class="dialog-actions"><button class="btn btn-secondary" type="button" data-loan-action="dismiss-offer">Not now</button><button class="btn btn-primary" type="button" data-loan-action="accept" data-ref="${escapeHtml(offer.referenceNo)}" ${loanState.busy ? 'disabled' : ''}>${loanState.busy ? 'Disbursing…' : `Accept and receive ${escapeHtml(money(o.amount))}`}</button></div>` : ''}</section>`;
}

// Installments are collected automatically by the nightly EOD job on their due date.
// A short balance leaves the loan flagged INSUFFICIENT_FUNDS until the amount is paid (by a later debit or manually).
function missedAutoDebit(loan) {
  const last = loan.lastAutoDebit;
  if (!last || last.status !== 'INSUFFICIENT_FUNDS' || loan.status === 'CLOSED') return false;
  return loan.status === 'OVERDUE' || Number(loan.penaltyDue) > 0 || (loan.nextDue && loan.nextDue.dueDate <= last.date);
}

function autoDebitAlert(loan) {
  const last = loan.lastAutoDebit;
  return `<div class="loan-alert" role="alert">${icon('wallet')}<div><strong>We couldn’t collect your ${escapeHtml(money(last.amount))} payment</strong><p>Your account ${escapeHtml(maskedNumber(loan.accountNo))} didn’t have enough balance on ${escapeHtml(loanDate(last.date))}. Top up and we’ll try again tonight, or pay now to avoid ${loan.status === 'OVERDUE' ? 'more charges' : 'a 2% late fee'}.</p></div></div>`;
}

// Overview banner for loans whose automatic payment failed.
function loanAlertsBanner() {
  if (!loanState.loaded && !loanState.loading && !loanState.error && state.profile) queueMicrotask(loadLoans);
  const missed = loanState.loans.filter(missedAutoDebit);
  if (!missed.length) return '';
  const total = missed.reduce((sum, loan) => sum + Number(loan.lastAutoDebit.amount), 0);
  return `<div class="loan-alert loan-alert-banner" role="alert">${icon('wallet')}<div><strong>Loan payment not collected</strong><p>We couldn’t auto-debit ${escapeHtml(money(total))} for ${missed.length === 1 ? 'your loan' : `${missed.length} loans`} — not enough balance. Top up before tonight’s run or pay now.</p></div><button class="btn btn-primary" type="button" data-action="navigate" data-page="loans">Pay now</button></div>`;
}

function myLoansMarkup() {
  if (loanState.error) return `<p class="notice" role="alert">${escapeHtml(loanState.error)} <button class="btn btn-subtle" type="button" data-loan-action="reload">Try again</button></p>`;
  if (!loanState.loaded) return '<div class="loading-panel" role="status"><div class="skeleton"></div><p>Loading your loans…</p></div>';
  if (!loanState.loans.length) return '<p class="muted">Loans you accept will appear here, with what’s due next.</p>';
  return loanState.loans.map(loan => `<article class="account-card" style="margin-bottom:16px"><div class="account-card-top"><strong>${escapeHtml(loan.referenceNo)}</strong>${loanPill(loan.status)}</div>
    ${missedAutoDebit(loan) ? autoDebitAlert(loan) : ''}
    <dl class="detail-list">${detail('Outstanding principal',balance(loan.outstandingPrincipal))}${Number(loan.penaltyDue) > 0 ? detail('Penalty due',balance(loan.penaltyDue)) : ''}
    ${loan.nextDue ? detail('Next payment',`${balance(loan.nextDue.amount)} · ${escapeHtml(loanDate(loan.nextDue.dueDate))}`) + detail('Auto-debit',`On the due date from ${escapeHtml(maskedNumber(loan.accountNo))}`) : ''}${detail('Rate · term',`${escapeHtml(loan.annualRate)}% · ${escapeHtml(loan.termMonths)} months`)}</dl>
    <div class="dialog-actions"><button class="btn btn-secondary" type="button" data-loan-action="schedule" data-id="${loan.loanId}">View schedule</button>${loan.status !== 'CLOSED' ? `<button class="btn btn-primary" type="button" data-loan-action="pay" data-id="${loan.loanId}">Pay now</button>` : ''}</div></article>`).join('');
}

async function applyForLoan(form) {
  const values = Object.fromEntries(new FormData(form));
  loanState.form = values;
  const amount = Number(values.amount);
  loanState.formError = '';
  if (!Number.isFinite(amount) || amount < 5000 || Math.abs(amount * 100 - Math.round(amount * 100)) > .00001) {
    loanState.formError = 'Enter an amount of at least ₱5,000.00, with at most two decimal places.'; renderPage(); return;
  }
  const body = {accountNo:values.accountNo, amount:Number(amount.toFixed(2)), termMonths:Number(values.termMonths)};
  // Reuse the key while the request is unchanged, so a retry after a network error returns the same application.
  const fingerprint = JSON.stringify(body);
  if (fingerprint !== loanState.applyFingerprint) {
    loanState.applyKey = (typeof generateUUID === 'function' ? generateUUID() : 'idemp-' + Math.random().toString(36).slice(2) + Date.now());
    loanState.applyFingerprint = fingerprint;
  }
  const generation = state.generation;
  loanState.busy = true; renderPage();
  try {
    const offer = await loanApi('/applications', {method:'POST', body, idempotencyKey:loanState.applyKey});
    if (generation !== state.generation) return;
    loanState.offer = offer; loanState.applyKey = null; loanState.applyFingerprint = '';
  } catch (error) {
    if (generation === state.generation) loanState.formError = error.message;
  } finally {
    if (generation === state.generation) { loanState.busy = false; renderPage(); }
  }
}

async function acceptOffer(referenceNo) {
  const generation = state.generation;
  loanState.busy = true; loanState.formError = ''; renderPage();
  try {
    await loanApi(`/applications/${encodeURIComponent(referenceNo)}/accept`, {method:'POST', idempotencyKey:`accept-${referenceNo}`});
    if (generation !== state.generation) return;
    toast('Your loan has been credited to your account.');
    loanState.offer = null; loanState.form = {};
    await Promise.all([loadLoans(), refresh()]);
  } catch (error) {
    if (generation === state.generation) {
      loanState.formError = error.message;
      if (['already-accepted','disbursement-failed','credit-limit-reached'].includes(error.type)) { loanState.offer = null; await loadLoans(); }
      // Still processing server-side: the loan is recorded automatically once the transfer completes.
      if (error.type === 'core-unavailable') { loanState.offer = null; setTimeout(() => { if (generation === state.generation) { loadLoans(); refresh(); } }, 35000); }
    }
  } finally {
    if (generation === state.generation) { loanState.busy = false; renderPage(); }
  }
}

async function showSchedule(loanId) {
  try {
    const data = await loanApi(`/${loanId}/schedule`);
    showDialog(`Repayment schedule · ${data.referenceNo}`, `<div class="table-wrap"><table><thead><tr><th scope="col">#</th><th scope="col">Due</th><th scope="col">Principal</th><th scope="col">Interest</th><th scope="col">Paid</th><th scope="col">Status</th></tr></thead><tbody>${data.installments.map(r => `<tr><td>${r.installmentNo}</td><td>${escapeHtml(loanDate(r.dueDate))}</td><td class="amount">${balance(r.principalDue)}</td><td class="amount">${balance(r.interestDue)}</td><td class="amount">${balance(r.amountPaid)}</td><td>${loanPill(r.status)}</td></tr>`).join('')}</tbody></table></div>`,
      '<button class="btn btn-primary" data-action="close-dialog">Done</button>');
  } catch (error) { toast(error.message, true); }
}

function showPayForm(loanId) {
  const loan = loanState.loans.find(l => l.loanId === loanId);
  if (!loan) return;
  const suggested = (Number(loan.nextDue?.amount || 0) + Number(loan.penaltyDue || 0)).toFixed(2);
  showDialog(`Pay loan ${loan.referenceNo}`, `<form id="loan-pay-form" class="auth-form" data-id="${loanId}"><p class="muted">The payment is taken from the account the loan was paid into. Any penalty is paid first, then your oldest installment.</p>
    <div class="form-field"><label for="loan-pay-amount">Amount (PHP)</label><input id="loan-pay-amount" name="amount" type="number" min="0.01" step="0.01" required value="${escapeHtml(suggested)}"></div>
    <div id="loan-pay-error" class="form-error" role="alert" hidden></div><button class="btn btn-primary" type="submit">Pay now</button></form>`);
}

async function payLoan(form) {
  const loanId = Number(form.dataset.id);
  const amount = Number(new FormData(form).get('amount'));
  const errorBox = form.querySelector('#loan-pay-error'), button = form.querySelector('[type="submit"]');
  errorBox.hidden = true;
  if (!Number.isFinite(amount) || amount <= 0) { errorBox.textContent = 'Enter a positive amount.'; errorBox.hidden = false; return; }
  const pending = loanState.payKeys[loanId];
  const key = pending && pending.amount === amount
    ? pending.key
    : (typeof generateUUID === 'function' ? generateUUID() : 'idemp-' + Math.random().toString(36).slice(2) + Date.now());
  loanState.payKeys[loanId] = {amount, key};
  button.disabled = true; button.textContent = 'Paying…';
  try {
    const receipt = await loanApi(`/${loanId}/repayments`, {method:'POST', body:{amount:Number(amount.toFixed(2))}, idempotencyKey:key});
    delete loanState.payKeys[loanId];
    dialog.close();
    toast(receipt.loanStatus === 'CLOSED' ? 'Payment received. Your loan is fully paid!' : `Payment of ${money(receipt.amount)} received.`);
    await Promise.all([loadLoans(), refresh()]);
  } catch (error) {
    if (!error.retryable) delete loanState.payKeys[loanId]; // keep the key only when a retry is safe and useful
    if (form.isConnected) { errorBox.textContent = error.message; errorBox.hidden = false; }
  } finally {
    if (form.isConnected) { button.disabled = false; button.textContent = 'Pay now'; }
  }
}

document.addEventListener('submit', event => {
  if (event.target.id === 'loan-apply-form') { event.preventDefault(); applyForLoan(event.target); }
  else if (event.target.id === 'loan-pay-form') { event.preventDefault(); payLoan(event.target); }
});

document.addEventListener('click', event => {
  const button = event.target.closest('[data-loan-action]');
  if (!button || !state.session) return;
  switch (button.dataset.loanAction) {
    case 'accept': acceptOffer(button.dataset.ref); break;
    case 'dismiss-offer': loanState.offer = null; renderPage(); break;
    case 'schedule': showSchedule(Number(button.dataset.id)); break;
    case 'pay': showPayForm(Number(button.dataset.id)); break;
    case 'reload': loanState.error = ''; loadLoans(); break;
  }
});
