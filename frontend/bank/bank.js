'use strict';

const API = '/api/v1/auth/banking';
const SESSION_KEY = 'paypink.customer.session';
const app = document.querySelector('#app');
const dialog = document.querySelector('#details');
const state = {
  session: null, profile: null, activity: [], page: 'overview', hideBalances: false,
  visibleAccounts: new Set(), query: '', accountFilter: '', statusFilter: '',
  updated: null, error: '', busy: false, generation: 0, authMode: 'login', transfer: null,
  recipients: {favorites:[],recent:[]}, dialogReceipt: null
};
let toastTimer;
let expiryTimer;
let recipientTimer;
const maskedNumber = number => `•••• ${String(number || '').slice(-4)}`;
const icon = name => `<svg class="icon" aria-hidden="true"><use href="#i-${name}"></use></svg>`;
const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const brand = () => '<span class="brand"><span class="brand-mark">p</span>PayPink<span class="brand-dot">®</span></span>';
const money = (amount, currency = 'PHP') => new Intl.NumberFormat('en-PH', {style:'currency', currency}).format(Number(amount || 0));
const balance = (amount, currency) => state.hideBalances ? '••••••' : escapeHtml(money(amount, currency));
const accountName = type => ({SAVINGS_ACCOUNT:'Savings account', EVERYDAY_ACCOUNT:'Everyday account', CHECKING_ACCOUNT:'Checking account', TIME_DEPOSIT:'Time deposit', STRESS_TEST_ACCOUNT:'Everyday account', SAVINGS:'Savings account'}[type] || 'Bank account');
const formattedAccountNumber = number => /^\d{12}$/.test(number) ? number.replace(/^(\d{3})(\d)(\d{7})(\d)$/,'$1 $2 $3 $4') : number;
const accountNumber = account => state.visibleAccounts.has(account.accountId) ? formattedAccountNumber(account.accountNumber) : `•••• •••• ${account.accountNumber.slice(-4)}`;
const isSuccess = tx => ['SUCCESS', 'COMPLETED'].includes(tx.status);
const friendlyType = tx => tx.type?.startsWith('EXT_') ? (tx.type.includes('PESONET') ? 'PESONet transfer' : 'InstaPay transfer') : ({CREDIT:'Money received', DEBIT:'Payment', WELCOME_GIFT:'Welcome gift', TRANSFER_OUT:'Transfer sent', TRANSFER_IN:'Transfer received', TRANSFER:'Account transfer', INSTAPAY:'InstaPay transfer', PESONET:'PESONet transfer', WITHDRAWAL:'Withdrawal', DEPOSIT:'Deposit'}[tx.type] || String(tx.type || 'Transaction').replaceAll('_',' ').toLowerCase().replace(/^./, c => c.toUpperCase()));
const txDate = tx => new Date(tx.date.endsWith('Z') || /[+-]\d\d:\d\d$/.test(tx.date) ? tx.date : `${tx.date}Z`);
const shortDate = tx => txDate(tx).toLocaleDateString('en-PH', {month:'short', day:'numeric', year:'numeric'});
const statusPill = status => `<span class="pill ${['ACTIVE','SUCCESS','COMPLETED'].includes(status) ? 'pill-green' : status === 'FAILED' ? 'pill-red' : 'pill-gray'}">${escapeHtml(status === 'SUCCESS' ? 'Completed' : String(status).toLowerCase().replace(/^./, c => c.toUpperCase()))}</span>`;

function toast(message, error = false) {
  const node = document.querySelector('#toast');
  clearTimeout(toastTimer);
  node.textContent = message;
  node.className = `toast${error ? ' error' : ''}`;
  node.hidden = false;
  toastTimer = setTimeout(() => { node.hidden = true; }, 4500);
}

async function api(path, {method = 'GET', body, authenticated = true} = {}) {
  const headers = {Accept:'application/json'};
  if (body) headers['Content-Type'] = 'application/json';
  if (authenticated && state.session) headers.Authorization = `Bearer ${state.session.token}`;
  let response;
  try {
    response = await fetch(`${API}${path}`, {method, headers, body:body ? JSON.stringify(body) : undefined,
      cache:'no-store', signal:AbortSignal.timeout(15000)});
  } catch {
    throw new Error('We couldn’t reach the bank. Check your connection and try again.');
  }
  const data = await response.json().catch(() => ({}));
  if (response.status === 401 && authenticated) {
    logout('Your session has ended. Please log in again.');
    throw new Error('Your session has ended. Please log in again.');
  }
  if (!response.ok) {
    const error = new Error(data.message || (response.status === 429
      ? 'Too many requests. Please wait a moment and try again.' : 'We couldn’t complete your request. Please try again.'));
    error.status = response.status;
    throw error;
  }
  return data;
}

function renderAuth(mode = 'login', message = '') {
  state.authMode = mode;
  document.title = `${mode === 'register' ? 'Open an account' : 'Log in'} — PayPink`;
  const register = mode === 'register';
  app.innerHTML = `<div class="auth-layout">
    <aside class="auth-story" aria-label="Welcome to PayPink">
      ${brand()}
      <div class="story-copy"><div class="eyebrow">A little more life. A little less banking.</div>
        <h1>Your money.<br>Your everyday.<br><em>Your PayPink.</em></h1>
        <p>Make room for what matters. Your accounts, balances, and everyday banking — all in one place.</p></div>
      <div class="card-scene" aria-hidden="true"><div class="illustrated-card"><span class="brand">PayPink<span class="brand-dot">®</span></span><div class="card-chip"></div><small>YOUR EVERYDAY, SIMPLIFIED</small></div>
        <div class="floating-note"><span class="circle-icon">${icon('check')}</span><div><strong>A fresh start.</strong><small>Good things begin with you.</small></div></div></div>
      <div class="story-footer">${icon('lock')} A space for you and your money.</div>
    </aside>
    <main id="main" class="auth-panel ${register ? 'register' : ''}">${brand()}
      <div class="auth-form-wrap"><div class="auth-kicker">${icon('shield')} PERSONAL BANKING</div>
        <h2>${register ? 'Hello, new beginnings.' : 'Welcome back.'}</h2>
        <p>${register ? 'Savings for your plans. Everyday for your daily life. Get both when you join.' : 'A little check-in. A clearer picture of your money.'}</p>
        <form id="auth-form" class="auth-form" data-mode="${mode}">
          <div id="form-error" class="form-error" role="alert" ${message ? '' : 'hidden'}>${escapeHtml(message)}</div>
          ${register ? `<div class="form-row"><div class="form-field"><label for="firstName">First name</label><input id="firstName" name="firstName" autocomplete="given-name" maxlength="100" required placeholder="First name"></div><div class="form-field"><label for="lastName">Last name</label><input id="lastName" name="lastName" autocomplete="family-name" maxlength="100" required placeholder="Last name"></div></div>
            <div class="form-field"><label for="email">Email address</label><input id="email" name="email" type="email" autocomplete="email" maxlength="150" required placeholder="you@example.com"></div>
            <div class="form-field"><label for="phone">Mobile number</label><input id="phone" name="phone" type="tel" autocomplete="tel" pattern="[+0-9\\(\\) \\-]{7,30}" maxlength="30" required placeholder="+63 917 123 4567"></div>` : ''}
          <div class="form-field"><label for="username">Username</label><input id="username" name="username" autocomplete="username" autocapitalize="none" spellcheck="false" maxlength="50" ${register ? 'pattern="[a-zA-Z0-9_]{3,50}" minlength="3"' : ''} required placeholder="${register ? 'Choose a username' : 'Enter your username'}">${register ? '<small>3–50 letters, numbers, or underscores.</small>' : ''}</div>
          <div class="form-field"><label for="password">Password</label><div class="password-wrap"><input id="password" name="password" type="password" autocomplete="${register ? 'new-password' : 'current-password'}" ${register ? 'minlength="8" maxlength="64"' : ''} required placeholder="${register ? 'Create a password' : 'Enter your password'}"><button type="button" class="icon-btn" data-action="password" aria-label="Show password" aria-pressed="false">${icon('eye')}</button></div>${register ? '<small>Use 8–64 characters. Choose something only you know.</small>' : ''}</div>
          ${register ? '<div class="form-field"><label for="confirmPassword">Confirm password</label><input id="confirmPassword" name="confirmPassword" type="password" autocomplete="new-password" required placeholder="Enter your password again"></div>' : ''}
          <button class="btn btn-primary" type="submit">${register ? 'Create my account' : 'Log in'} ${icon('arrow')}</button>
        </form>
        <div class="auth-switch">${register ? 'Already part of PayPink?' : 'New around here?'} <button class="text-link" data-action="auth-mode" data-mode="${register ? 'login' : 'register'}">${register ? 'Log in' : 'Open an account'}</button></div>
        <p class="auth-note">${register ? 'Your Savings account starts at ₱0.00.<br>Enjoy a ₱50 welcome gift in your new Everyday account.' : 'Keep your password to yourself.<br>Always log out when using a shared device.'}</p>
      </div>
    </main></div>`;
}

function navLink(page, title, symbol) {
  return `<button class="nav-link ${state.page === page ? 'active' : ''}" data-action="navigate" data-page="${page}" ${state.page === page ? 'aria-current="page"' : ''} aria-label="${title}">${icon(symbol)}<span>${title}</span></button>`;
}

function renderShell() {
  const name = state.profile?.fullName || state.session?.fullName || 'Your account';
  const initials = name.split(/\s+/).slice(0,2).map(part => part[0]).join('').toUpperCase();
  app.innerHTML = `<div class="bank-layout">
    <aside class="sidebar">${brand()}<div class="eyebrow">YOUR BANKING</div>
      <nav aria-label="Main navigation">${navLink('overview','Overview','home')}${navLink('accounts','My accounts','wallet')}${navLink('transfer','Transfers','arrow')}${navLink('activity','Transactions','activity')}</nav>
      <div class="sidebar-bottom"><div class="privacy-note">${icon('shield')}<strong>A little privacy goes a long way.</strong><p>Keep your account details and password just for you.</p></div><button class="logout" data-action="logout">${icon('logout')}<span>Log out</span></button></div>
    </aside>
    <div class="bank-content"><header class="topbar"><div class="breadcrumb"><span>PayPink</span><span>/</span><strong id="breadcrumb-page">Personal banking</strong></div>
      <div class="topbar-right"><button type="button" id="notification-button" class="notification-button" aria-label="Notifications"><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"/></svg><span class="notification-count" hidden></span></button><span class="today">${new Date().toLocaleDateString('en-PH',{weekday:'short',month:'long',day:'numeric',year:'numeric'})}</span><button class="profile-button" data-action="profile" aria-label="View your profile"><span class="avatar">${escapeHtml(initials)}</span><span class="profile-label">${escapeHtml(name)}<small>Personal account</small></span></button></div></header>
      <main class="page" id="main" tabindex="-1"></main></div></div>`;
  renderPage();
}

function heading(title, subtitle) {
  return `<div class="page-heading"><div><h1>${title}</h1><p>${subtitle}</p></div><div class="page-heading-actions">${state.profile && ['overview','accounts','activity'].includes(state.page) ? '<button class="btn btn-secondary report-trigger" type="button" data-generate-report>Generate transaction report</button>' : ''}${state.page !== 'transfer' && state.profile ? `<button class="btn btn-primary" data-action="navigate" data-page="transfer" aria-label="Transfer money">${icon('arrow')} Transfer money</button>` : ''}<button class="btn btn-secondary" data-action="refresh" aria-label="Refresh balances and transactions" ${state.busy ? 'disabled' : ''}>${icon('refresh')} Refresh</button></div></div>`;
}

function renderPage() {
  const main = document.querySelector('#main');
  if (!state.session || !main) return;
  const titles = {overview:'Overview',accounts:'My accounts',activity:'Transactions',transfer:'Transfers'};
  document.title = `${titles[state.page]} — PayPink`;
  document.querySelector('#breadcrumb-page').textContent = titles[state.page];
  document.querySelectorAll('.nav-link').forEach(button => {
    const active = button.dataset.page === state.page;
    button.classList.toggle('active',active);
    if (active) button.setAttribute('aria-current','page'); else button.removeAttribute('aria-current');
  });
  if (!state.profile) {
    main.innerHTML = heading('Your banking, in a moment.', 'We’re getting your accounts ready.') + (state.error
      ? `<div class="notice" role="alert">${escapeHtml(state.error)} Use Refresh to try again.</div>`
      : '<div class="loading-panel" role="status"><div class="skeleton wide"></div><div class="skeleton"></div><p>Loading your accounts…</p></div>');
    return;
  }
  main.innerHTML = (state.error ? `<div class="notice" role="alert">${escapeHtml(state.error)} Showing your last loaded information.</div>` : '')
    + (state.page === 'overview' ? overview() : state.page === 'accounts' ? accountsPage() : state.page === 'transfer' ? transferPage() : activityPage())
    + `<footer class="page-footer"><span>© ${new Date().getFullYear()} PayPink. A little more everyday.</span><span>${icon('lock')} ${state.updated ? `Updated ${state.updated.toLocaleTimeString('en-PH',{hour:'2-digit',minute:'2-digit'})}` : 'Personal banking'} · Philippine peso accounts</span></footer>`;
}

function overview() {
  const accounts = state.profile.accounts;
  const total = accounts.reduce((sum,a) => sum + Number(a.currentBalance),0);
  const now = new Date();
  const monthly = state.activity.filter(tx => isSuccess(tx) && txDate(tx).getMonth() === now.getMonth() && txDate(tx).getFullYear() === now.getFullYear());
  const incoming = monthly.filter(tx => tx.operation === 'CREDIT').reduce((sum,tx) => sum + Number(tx.amount),0);
  const outgoing = monthly.filter(tx => tx.operation === 'DEBIT').reduce((sum,tx) => sum + Number(tx.amount),0);
  return heading(`Hello, ${escapeHtml(state.profile.firstName)}<span class="muted">.</span>`, 'Your everyday, at a glance. It’s good to have you here.')
    + `<div class="overview-grid"><section class="balance-card" aria-label="Total balance"><div class="balance-top"><span>Total available balance <button class="icon-btn" data-action="balance-visibility" aria-label="${state.hideBalances ? 'Show' : 'Hide'} balances" aria-pressed="${state.hideBalances}">${icon(state.hideBalances ? 'eye-off' : 'eye')}</button></span><span>PHP</span></div>
      <div class="big-balance">${balance(total)}</div><div class="balance-subtitle">Across ${accounts.length} ${accounts.length === 1 ? 'account' : 'accounts'}. All yours.</div>
      <div class="balance-bottom"><span>${icon('shield')} Your money, in view.</span><button data-action="navigate" data-page="accounts">View accounts ${icon('arrow')}</button></div></section>
      <section class="summary-card" aria-label="This month’s activity"><div class="summary-head"><h3>This month, so far</h3><span>${now.toLocaleDateString('en-PH',{month:'short',year:'numeric'})}</span></div>
        <div class="flow-row"><span class="circle-icon">${icon('down')}</span><div><small>Money in</small><strong>${balance(incoming)}</strong></div></div>
        <div class="flow-row"><span class="circle-icon">${icon('up')}</span><div><small>Money out</small><strong>${balance(outgoing)}</strong></div></div><p>Based on your latest 200 transactions.</p></section></div>
      <section aria-labelledby="accounts-title"><div class="section-heading"><h2 id="accounts-title">Your accounts <small>${accounts.length} in one place</small></h2><button class="btn btn-subtle" data-action="navigate" data-page="accounts">Manage your view ${icon('arrow')}</button></div><div class="accounts-grid">${accounts.slice(0,3).map(accountCard).join('') || empty('Your accounts will appear here.','No accounts are linked to this profile yet.','wallet')}</div></section>
      <section class="activity-panel" aria-labelledby="recent-title"><div class="activity-toolbar"><h2 id="recent-title">Recent activity</h2><button class="btn btn-subtle" data-action="navigate" data-page="activity">View all transactions ${icon('arrow')}</button></div>${transactionTable(state.activity.slice(0,5))}</section>`;
}

function accountCard(account) {
  return `<article class="account-card"><div class="account-card-top"><span class="account-symbol">${icon('wallet')}</span>${statusPill(account.status)}</div><h3>${escapeHtml(accountName(account.accountType))}</h3>
    <div class="account-number"><span>${escapeHtml(accountNumber(account))}</span><button class="icon-btn" data-action="account-visibility" data-id="${account.accountId}" aria-label="${state.visibleAccounts.has(account.accountId) ? 'Hide' : 'Show'} account number ending ${escapeHtml(account.accountNumber.slice(-4))}" aria-pressed="${state.visibleAccounts.has(account.accountId)}">${icon(state.visibleAccounts.has(account.accountId) ? 'eye-off' : 'eye')}</button></div>
    <div class="account-balance">${balance(account.currentBalance,account.currency)}</div><div class="account-card-footer"><span>Available balance</span><button data-action="account-details" data-id="${account.accountId}">Account details ${icon('arrow')}</button></div></article>`;
}

function accountsPage() {
  return heading('A home for your money.', 'Your accounts, together. Select an account to see its details.')
    + `<div class="section-heading"><h2>My accounts <small>${state.profile.accounts.length} linked</small></h2><button class="btn btn-subtle" data-action="balance-visibility" aria-pressed="${state.hideBalances}">${icon(state.hideBalances ? 'eye-off' : 'eye')} ${state.hideBalances ? 'Show' : 'Hide'} balances</button></div>
      <div class="accounts-grid">${state.profile.accounts.map(accountCard).join('') || empty('No accounts yet.','No accounts are linked to this profile yet.','wallet')}</div>
      <div class="account-explainer"><span class="circle-icon">${icon('shield')}</span><div><h3>A little discretion, built in.</h3><p>Your account numbers are masked by default. Use the eye icon to reveal them, or open account details to copy a number.</p></div></div>`;
}

function empty(title = 'A fresh page for your money.', text = 'Your transactions will appear here as you use your account.', symbol = 'activity') {
  return `<div class="empty-state"><span class="circle-icon">${icon(symbol)}</span><h3>${escapeHtml(title)}</h3><p>${escapeHtml(text)}</p></div>`;
}

function transactionTable(items, filtered = false) {
  if (!items.length) return filtered ? empty('No matching transactions.','Try a different search or change your filters.','search') : empty();
  return `<div class="table-wrap"><table><thead><tr><th scope="col">Transaction</th><th class="table-date" scope="col">Date</th><th scope="col">Status</th><th scope="col">Amount</th></tr></thead><tbody>${items.map(tx => {
    const credit = tx.operation === 'CREDIT';
    const sign = isSuccess(tx) ? credit ? '+' : tx.operation === 'DEBIT' ? '−' : '' : '';
    return `<tr><td><button class="transaction-name transaction-link" data-action="transaction-details" data-id="${tx.transactionId}"><span class="circle-icon ${credit ? 'incoming' : ''}">${icon(credit ? 'down' : tx.operation === 'DEBIT' ? 'up' : 'activity')}</span><span><strong>${escapeHtml(friendlyType(tx))}</strong><small>Account •••• ${escapeHtml(tx.accountNumber.slice(-4))}</small></span></button></td><td class="table-date muted">${escapeHtml(shortDate(tx))}</td><td>${statusPill(tx.status)}</td><td class="amount ${credit && isSuccess(tx) ? 'credit' : ''}">${state.hideBalances ? '••••••' : sign + escapeHtml(money(tx.amount,tx.currency))}</td></tr>`;
  }).join('')}</tbody></table></div>`;
}

function filteredActivity() {
  const query = state.query.trim().toLowerCase();
  return state.activity.filter(tx => (!state.accountFilter || String(tx.accountId) === state.accountFilter)
    && (!state.statusFilter || (state.statusFilter === 'SUCCESS' ? isSuccess(tx) : tx.status === state.statusFilter))
    && (!query || `${friendlyType(tx)} ${tx.reference} ${tx.accountNumber.slice(-4)}`.toLowerCase().includes(query)));
}

function activityPage() {
  return heading('Every little detail.', 'Your latest 200 transactions, with a clearer view of where your money goes.')
    + `<div class="filter-bar"><label class="search-box">${icon('search')}<input id="transaction-search" type="search" placeholder="Search transactions or reference…" aria-label="Search transactions" value="${escapeHtml(state.query)}"></label>
      <select id="account-filter" aria-label="Filter by account"><option value="">All accounts</option>${state.profile.accounts.map(a => `<option value="${a.accountId}" ${state.accountFilter === String(a.accountId) ? 'selected' : ''}>${escapeHtml(accountName(a.accountType))} · ${escapeHtml(a.accountNumber.slice(-4))}</option>`).join('')}</select>
      <select id="status-filter" aria-label="Filter by status"><option value="">All statuses</option>${[['SUCCESS','Completed'],['PENDING','Pending'],['FAILED','Failed']].map(([value,label]) => `<option value="${value}" ${state.statusFilter === value ? 'selected' : ''}>${label}</option>`).join('')}</select></div>
      <section class="activity-panel" aria-label="Transaction history"><div id="activity-results">${activityResults()}</div></section>`;
}

function activityResults() {
  const items = filteredActivity();
  return transactionTable(items,Boolean(state.query || state.accountFilter || state.statusFilter))
    + `<div class="result-count" role="status">${items.length} ${items.length === 1 ? 'transaction' : 'transactions'}${state.query || state.accountFilter || state.statusFilter ? ' match your filters' : ' shown'}</div>`;
}

async function refresh(manual = false) {
  if (!state.session || state.busy) return;
  const generation = state.generation;
  state.busy = true;
  const button = document.querySelector('[data-action="refresh"]');
  if (button) button.disabled = true;
  try {
    const [profile,activity,recipients] = await Promise.all([api('/me'),api('/transactions'),api('/recipients').catch(error => ({favorites:[],recent:[],error:error.message}))]);
    if (generation !== state.generation || !state.session) return;
    state.profile = profile; state.activity = activity; state.updated = new Date(); state.error = '';
    state.recipients = recipients;
    updateTransferNotifications(activity);
    if (manual) toast('Your accounts are up to date.');
  } catch (error) {
    if (generation === state.generation && state.session) state.error = error.message;
  } finally {
    if (generation === state.generation && state.session) {
      state.busy = false;
      // Preserve keyboard focus and cursor while polling the searchable transaction page.
      const focus = document.activeElement;
      const id = focus?.id;
      const selection = focus?.selectionStart;
      renderPage();
      if (id) {
        const replacement = document.getElementById(id);
        replacement?.focus();
        if (replacement?.setSelectionRange && selection != null) replacement.setSelectionRange(selection,selection);
      }
    }
  }
}

function startSession(response) {
  state.session = {token:response.token,fullName:response.fullName,expiresAt:Date.now() + response.expiresInMs};
  state.generation++; state.busy = false;
  saveSession();
  scheduleExpiry(); renderShell(); refresh();
}

function scheduleExpiry() {
  clearTimeout(expiryTimer);
  expiryTimer = setTimeout(() => logout('Your session has ended. Please log in again.'),Math.max(0,state.session.expiresAt - Date.now()));
}

function logout(message = 'You’ve been logged out. See you again soon.') {
  dismissTransferPopups();
  clearTimeout(expiryTimer);
  try { sessionStorage.removeItem(SESSION_KEY); } catch { /* No persisted session. */ }
  state.generation++; state.session = null; state.profile = null; state.activity = [];
  state.page = 'overview'; state.visibleAccounts.clear(); state.hideBalances = false; state.transfer = null;
  state.recipients = {favorites:[],recent:[]}; state.dialogReceipt = null; clearTimeout(recipientTimer);
  state.query = ''; state.accountFilter = ''; state.statusFilter = ''; state.error = ''; state.updated = null; state.busy = false;
  if (dialog.open) dialog.close();
  dialog.innerHTML = '';
  renderAuth(); toast(message); document.querySelector('#username')?.focus();
}

function showDialog(title, content, actions = '') {
  dialog.innerHTML = `<div class="dialog-top"><h2 id="dialog-title">${escapeHtml(title)}</h2><button class="icon-btn" data-action="close-dialog" aria-label="Close dialog">${icon('close')}</button></div>${content}${actions ? `<div class="dialog-actions">${actions}</div>` : ''}`;
  if (!dialog.open) dialog.showModal();
}
const detail = (label,value) => `<div><dt>${escapeHtml(label)}</dt><dd>${value}</dd></div>`;

function accountDialog(id) {
  const account = state.profile.accounts.find(a => a.accountId === id);
  if (!account) return;
  showDialog(accountName(account.accountType), `<dl class="detail-list">${detail('Account holder',escapeHtml(state.profile.fullName))}
    ${detail('Account number',`${escapeHtml(accountNumber(account))}<button class="icon-btn" data-action="dialog-account-visibility" data-id="${id}" aria-label="${state.visibleAccounts.has(id) ? 'Hide' : 'Show'} account number">${icon(state.visibleAccounts.has(id) ? 'eye-off' : 'eye')}</button><button class="icon-btn" data-action="copy-account" data-id="${id}" aria-label="Copy account number">${icon('copy')}</button>`)}
    ${detail('Available balance',balance(account.currentBalance,account.currency))}${detail('Status',statusPill(account.status))}</dl>`,
    `<button class="btn btn-primary" data-action="account-activity" data-id="${id}">View transactions ${icon('arrow')}</button>`);
}

app.addEventListener('submit', async event => {
  if (event.target.id === 'transfer-form') { event.preventDefault(); reviewTransfer(); return; }
  if (event.target.id !== 'auth-form') return;
  event.preventDefault();
  const form = event.target;
  const data = Object.fromEntries(new FormData(form));
  const register = form.dataset.mode === 'register';
  const errorBox = form.querySelector('#form-error');
  errorBox.hidden = true;
  if (register && data.password !== data.confirmPassword) {
    errorBox.textContent = 'Your passwords don’t match. Please enter them again.'; errorBox.hidden = false;
    document.querySelector('#confirmPassword').focus(); return;
  }
  data.username = data.username.trim().toLowerCase();
  delete data.confirmPassword;
  const submit = form.querySelector('[type="submit"]');
  submit.disabled = true; submit.textContent = register ? 'Opening your account…' : 'Logging in…';
  try {
    const response = await api(register ? '/register' : '/login',{method:'POST',body:data,authenticated:false});
    // Ignore a response if the user switched between login and registration during the request.
    if (!form.isConnected) return;
    startSession(response);
    if (register) toast('Your Savings and Everyday accounts are ready, with a ₱50 welcome gift.');
  } catch (error) {
    if (form.isConnected) { errorBox.textContent = error.message; errorBox.hidden = false; }
  } finally {
    if (form.isConnected) { submit.disabled = false; submit.innerHTML = `${register ? 'Create my account' : 'Log in'} ${icon('arrow')}`; }
  }
});

document.addEventListener('click', async event => {
  const button = event.target.closest('[data-action]');
  if (!button) return;
  const id = Number(button.dataset.id);
  switch (button.dataset.action) {
    case 'auth-mode': renderAuth(button.dataset.mode); document.querySelector('#main h2')?.scrollIntoView({block:'center'}); break;
    case 'password': {
      const input = document.querySelector('#password');
      const visible = input.type === 'password'; input.type = visible ? 'text' : 'password';
      button.innerHTML = icon(visible ? 'eye-off' : 'eye'); button.setAttribute('aria-label',visible ? 'Hide password' : 'Show password'); button.setAttribute('aria-pressed',String(visible)); break;
    }
    case 'navigate': state.page = button.dataset.page; renderPage(); document.querySelector('#main').focus({preventScroll:true}); window.scrollTo(0,0); break;
    case 'refresh': await refresh(true); break;
    case 'transfer-mode': state.transfer.mode = button.dataset.mode; state.transfer.error = ''; renderPage(); if (state.transfer.mode === 'other' && state.transfer.number) await lookupRecipient(); break;
    case 'confirm-transfer': await sendTransfer(); break;
    case 'retry-transfer': await sendTransfer(); break;
    case 'new-transfer': state.transfer = null; state.page = 'transfer'; renderPage(); break;
    case 'choose-recipient': state.transfer.number = button.dataset.number; state.transfer.recipient = null; renderPage(); await lookupRecipient(); break;
    case 'favorite-recipient': await toggleFavorite(button.dataset.number,button.dataset.saved === 'true',button); break;
    case 'save-receipt': saveReceipt(state.transfer?.receipt); break;
    case 'save-history-receipt': saveReceipt(state.dialogReceipt); break;
    case 'balance-visibility': state.hideBalances = !state.hideBalances; renderPage(); document.querySelector('[data-action="balance-visibility"]')?.focus(); break;
    case 'account-visibility':
    case 'dialog-account-visibility':
      if (state.visibleAccounts.has(id)) state.visibleAccounts.delete(id); else state.visibleAccounts.add(id);
      renderPage();
      if (button.dataset.action === 'dialog-account-visibility') accountDialog(id);
      else document.querySelector(`[data-action="account-visibility"][data-id="${id}"]`)?.focus();
      break;
    case 'account-details': accountDialog(id); break;
    case 'account-activity': dialog.close(); state.page = 'activity'; state.accountFilter = String(id); state.query = ''; state.statusFilter = ''; renderPage(); document.querySelector('#main').focus({preventScroll:true}); break;
    case 'copy-account': {
      const account = state.profile.accounts.find(a => a.accountId === id);
      try { await navigator.clipboard.writeText(account.accountNumber); toast('Account number copied.'); }
      catch { toast('Copy is unavailable. Reveal the number to select and copy it.',true); }
      break;
    }
    case 'transaction-details': {
      const tx = state.activity.find(item => item.transactionId === id);
      if (!tx) break;
      const external = tx.type?.startsWith('EXT_');
      const transfer = external || ['TRANSFER_IN','TRANSFER_OUT'].includes(tx.type);
      state.dialogReceipt = transfer ? {amount:tx.amount,currency:tx.currency,status:tx.status,date:tx.date,reference:tx.reference,
        recipientName:(external || tx.type === 'TRANSFER_OUT') ? tx.counterpartyName : state.profile.fullName,
        destinationAccountNumber:(external || tx.type === 'TRANSFER_OUT') ? tx.counterpartyAccountNumber : tx.accountNumber} : null;
      showDialog(friendlyType(tx), `<dl class="detail-list">${detail('Amount',balance(tx.amount,tx.currency))}${detail('Status',statusPill(tx.status))}${transfer ? detail((external || tx.type === 'TRANSFER_OUT') ? 'Recipient' : 'Sender',escapeHtml(tx.counterpartyName || 'PayPink customer')) + detail((external || tx.type === 'TRANSFER_OUT') ? 'Recipient account' : 'Sender account',escapeHtml(maskedNumber(tx.counterpartyAccountNumber))) : ''}${detail('Your account',escapeHtml(maskedNumber(tx.accountNumber)))}${detail('Date & time',escapeHtml(txDate(tx).toLocaleString('en-PH')))}${detail('Reference number',escapeHtml(tx.reference))}${detail('Movement',escapeHtml(tx.operation === 'CREDIT' ? 'Money in' : tx.operation === 'DEBIT' ? 'Money out' : 'See transaction type'))}</dl>`, transfer ? '<button class="btn btn-secondary" data-action="save-history-receipt">Save receipt</button><button class="btn btn-primary" data-action="close-dialog">Done</button>' : ''); break;
    }
    case 'profile':
      if (!state.profile) break;
      showDialog('Your profile',`<dl class="detail-list">${detail('Full name',escapeHtml(state.profile.fullName))}${detail('Username',escapeHtml(state.profile.username))}${detail('Email address',escapeHtml(state.profile.email))}</dl><p class="dialog-copy">It’s good to have you here.</p>`); break;
    case 'logout': showDialog('Log out of PayPink?', '<p class="muted">You’ll need your username and password to log back in.</p>', '<button class="btn btn-secondary" data-action="close-dialog">Stay here</button><button class="btn btn-primary" data-action="confirm-logout">Log out</button>'); break;
    case 'confirm-logout': logout(); break;
    case 'close-dialog': dialog.close(); break;
  }
});
app.addEventListener('input', event => {
  if (event.target.id === 'transfer-amount') state.transfer.amount = event.target.value;
  if (event.target.id === 'recipient-number') {
    event.target.value = event.target.value.replace(/\s/g,''); state.transfer.number = event.target.value; state.transfer.recipient = null; state.transfer.lookupError = ''; state.transfer.lookupPending = false;
    clearTimeout(recipientTimer); renderRecipientStatus();
    if (event.target.value.trim()) recipientTimer = setTimeout(lookupRecipient,450);
  }
  if (event.target.id === 'transaction-search') { state.query = event.target.value; document.querySelector('#activity-results').innerHTML = activityResults(); }
});
app.addEventListener('change', event => {
  if (event.target.id === 'transfer-source') { state.transfer.source = event.target.value; state.transfer.error = ''; renderPage(); return; }
  if (event.target.id === 'transfer-destination') { state.transfer.destination = event.target.value; return; }
  if (event.target.id === 'account-filter') state.accountFilter = event.target.value;
  else if (event.target.id === 'status-filter') state.statusFilter = event.target.value;
  else return;
  document.querySelector('#activity-results').innerHTML = activityResults();
});
dialog.addEventListener('click', event => {
  if (event.target === dialog) {
    const rect = dialog.getBoundingClientRect();
    if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) dialog.close();
  }
});
setInterval(() => { if (state.session && !document.hidden && !dialog.open) refresh(); },30000);
document.addEventListener('visibilitychange', () => {
  if (!document.hidden && state.session) {
    if (Date.now() >= state.session.expiresAt) logout('Your session has ended. Please log in again.'); else refresh();
  }
});
try {
  const saved = JSON.parse(sessionStorage.getItem(SESSION_KEY));
  if (saved?.token && typeof saved.expiresAt === 'number' && saved.expiresAt > Date.now()) state.session = saved;
  else sessionStorage.removeItem(SESSION_KEY);
} catch { /* A missing or invalid session always starts at login. */ }
if (state.session) { if (state.session.pendingTransfer || state.session.pendingExternal) state.page = 'transfer'; scheduleExpiry(); renderShell(); refresh(); } else renderAuth();

function saveSession() {
  try { sessionStorage.setItem(SESSION_KEY,JSON.stringify(state.session)); } catch { /* Session remains usable in this tab. */ }
}

function transferPage() {
  const accounts = state.profile.accounts.filter(a => a.status === 'ACTIVE' && a.currency === 'PHP');
  if (!state.transfer) {
    const preferred = accounts.find(a => a.accountType === 'EVERYDAY_ACCOUNT')
      || accounts.find(a => a.accountType === 'STRESS_TEST_ACCOUNT') || accounts[0];
    state.transfer = {source:String(preferred?.accountId || ''),mode:state.session.pendingExternal ? 'external' : 'own',destination:'',number:'',amount:'',error:'',sending:false,receipt:null,recipient:null,lookupError:'',lookupPending:false};
  }
  const form = state.transfer;
  if (form.receipt) return form.receipt.mock ? mockReceipt(form.receipt) : transferReceipt(form.receipt);
  if (form.mode === 'external') return externalTransferPage(accounts);
  const source = accounts.find(a => String(a.accountId) === form.source);
  const destinations = accounts.filter(a => a.accountId !== source?.accountId);
  if (!destinations.some(a => String(a.accountId) === form.destination)) form.destination = String(destinations[0]?.accountId || '');
  const pending = state.session.pendingTransfer;
  const label = a => `${accountName(a.accountType)} · ${a.accountNumber.slice(-4)} · ${state.hideBalances ? '••••••' : money(a.currentBalance)}`;
  return heading('Move money. Make things happen.', 'Transfer between your accounts or send to another PayPink account.')
    + `<div class="transfer-layout"><section class="transfer-panel"><div class="section-heading"><h2>Fund transfer</h2><span class="pill pill-green">No transfer fee</span></div>
      ${pending ? `<div class="notice" role="status">${form.sending ? 'Your transfer is being processed. Please wait.' : 'Your last transfer is awaiting confirmation. Check its status safely before making another transfer.'}<dl class="pending-details"><dt>Amount</dt><dd>${escapeHtml(money(pending.amount))}</dd><dt>To account</dt><dd>${escapeHtml(pending.destinationAccountNumber)}</dd></dl>${!form.sending ? '<button class="btn btn-primary" data-action="retry-transfer">Check transfer status</button>' : ''}</div>` : ''}
      ${form.error ? `<div class="form-error" role="alert">${escapeHtml(form.error)}</div>` : ''}
      <form id="transfer-form"><fieldset ${pending || form.sending ? 'disabled' : ''}><div class="form-field"><label for="transfer-source">Transfer from</label><select id="transfer-source" required>${accounts.map(a => `<option value="${a.accountId}" ${String(a.accountId) === form.source ? 'selected' : ''}>${escapeHtml(label(a))}</option>`).join('')}</select><small>${source ? `Available balance: ${balance(source.currentBalance)}.${['EVERYDAY_ACCOUNT','STRESS_TEST_ACCOUNT'].includes(source.accountType) ? ' Your default Everyday account.' : ''}` : 'No active PHP accounts are available.'}</small></div>
      <div class="transfer-tabs" role="group" aria-label="Recipient type"><button type="button" data-action="transfer-mode" data-mode="own" class="${form.mode === 'own' ? 'selected' : ''}" aria-pressed="${form.mode === 'own'}">My own account</button><button type="button" data-action="transfer-mode" data-mode="other" class="${form.mode === 'other' ? 'selected' : ''}" aria-pressed="${form.mode === 'other'}">Another PayPink account</button><button type="button" data-action="transfer-mode" data-mode="external">Outside PayPink</button></div>
      ${form.mode === 'own' ? `<div class="form-field"><label for="transfer-destination">Transfer to</label><select id="transfer-destination" required ${!destinations.length ? 'disabled' : ''}>${destinations.length ? destinations.map(a => `<option value="${a.accountId}" ${String(a.accountId) === form.destination ? 'selected' : ''}>${escapeHtml(label(a))}</option>`).join('') : '<option value="">No other accounts available</option>'}</select></div>` : `${recipientPicker()}<div class="form-field"><label for="recipient-number">Recipient account number</label><input id="recipient-number" value="${escapeHtml(form.number)}" maxlength="30" pattern="[A-Za-z0-9\\-]{3,30}" autocomplete="off" spellcheck="false" required placeholder="Enter the full PayPink account number"><small>Enter the full number to look up the account holder.</small></div><div id="recipient-status" aria-live="polite">${recipientStatus()}</div>`}
      <div class="form-field"><label for="transfer-amount">Amount</label><div class="amount-input"><span>PHP</span><input id="transfer-amount" type="number" inputmode="decimal" min="0.01" step="0.01" max="99999999999999.99" value="${escapeHtml(form.amount)}" required placeholder="0.00"></div></div>
      <div class="transfer-fee"><span>Transfer fee</span><strong>₱0.00</strong></div><button class="btn btn-primary transfer-submit" type="submit" ${!source || (form.mode === 'own' && !destinations.length) ? 'disabled' : ''}>Review transfer ${icon('arrow')}</button></fieldset></form></section>
      <aside class="transfer-guide"><span class="circle-icon">${icon('activity')}</span><h2>Your money, on the move.</h2><p>Move money from Everyday to Savings, or send to someone else with PayPink.</p><ol><li>Choose the account to pay from.</li><li>Select your receiving account or enter a PayPink account number.</li><li>Review the details and confirm.</li></ol><div class="transfer-guide-note">${icon('shield')} Double-check the receiving account number before sending.</div></aside></div>`;
}

async function reviewTransfer() {
  if (state.transfer?.mode === 'external') return reviewExternalTransfer();
  const form = state.transfer;
  if (form.sending || state.session.pendingTransfer) return;
  const source = state.profile.accounts.find(a => String(a.accountId) === form.source);
  const target = state.profile.accounts.find(a => String(a.accountId) === form.destination);
  let destination = form.mode === 'own' ? target?.accountNumber : form.number.trim().toUpperCase();
  const amount = Number(form.amount);
  form.error = '';
  if (!source || !destination) form.error = 'Choose both the sending and receiving accounts.';
  else if (destination === source.accountNumber) form.error = 'Choose a different receiving account.';
  else if (!Number.isFinite(amount) || amount <= 0) form.error = 'Enter an amount greater than zero.';
  else if (amount > Number(source.currentBalance)) form.error = 'Not enough money in this account. Choose another account or a smaller amount.';
  if (form.error) { renderPage(); return; }
  if (form.mode === 'other') {
    if ((form.recipient?.lookupNumber || form.recipient?.accountNumber) !== destination) await lookupRecipient();
    if (state.transfer !== form || !state.session || form.mode !== 'other' || form.number.trim().toUpperCase() !== destination) return;
    if (String(source.accountId) !== form.source || Number(form.amount) !== amount) return;
    if ((form.recipient?.lookupNumber || form.recipient?.accountNumber) !== destination) { form.error = 'Confirm a valid recipient account before continuing.'; renderPage(); return; }
    destination = form.recipient.accountNumber;
    if (destination === source.accountNumber) { form.error = 'Choose a different receiving account.'; renderPage(); return; }
  }
  form.review = {sourceAccountId:source.accountId,destinationAccountNumber:destination,amount:form.amount,idempotencyKey:crypto.randomUUID()};
  showDialog('Review your transfer', `<p class="muted">Please check these details before sending.</p><dl class="detail-list">${detail('From',`${escapeHtml(accountName(source.accountType))} · ${escapeHtml(source.accountNumber.slice(-4))}`)}${detail('Recipient',escapeHtml(form.mode === 'own' ? state.profile.fullName : form.recipient.fullName))}${detail('Recipient account',escapeHtml(maskedNumber(destination)))}${detail('Amount',escapeHtml(money(amount)))}${detail('Transfer fee','₱0.00')}${detail('Total to deduct',`<strong>${escapeHtml(money(amount))}</strong>`)}</dl>`, '<button class="btn btn-secondary" data-action="close-dialog">Go back</button><button class="btn btn-primary" data-action="confirm-transfer">Confirm transfer</button>');
}

async function sendTransfer() {
  if (state.transfer?.mode === 'external') return sendExternalTransfer();
  const form = state.transfer;
  if (!form || form.sending) return;
  const request = state.session.pendingTransfer || form.review;
  if (!request) return;
  const generation = state.generation;
  state.session.pendingTransfer = request;
  saveSession();
  form.sending = true; form.error = '';
  dialog.close(); renderPage();
  try {
    const receipt = await api('/transfers',{method:'POST',body:request});
    if (generation !== state.generation || !state.session) return;
    delete state.session.pendingTransfer; saveSession();
    form.receipt = receipt; form.review = null;
    await refresh();
    toast('Transfer complete. The receiving account has been credited.');

    // Real-time synchronization broadcast across banking and admin tabs
    try {
      const sourceAcc = (state.profile?.accounts || []).find(a => String(a.accountId) === String(request.sourceAccountId));
      const syncEvent = {
        type: 'CUSTOMER_TRANSFER',
        reference: receipt.reference,
        sourceAccountId: request.sourceAccountId,
        sourceAccountNumber: sourceAcc ? sourceAcc.accountNumber : '001181233469',
        destinationAccountNumber: request.destinationAccountNumber,
        amount: request.amount,
        currency: receipt.currency || 'PHP',
        status: receipt.status || 'SUCCESS',
        date: receipt.date || new Date().toISOString(),
        recipientName: receipt.recipientName || 'PayPink customer',
        timestamp: Date.now()
      };
      if (window.BroadcastChannel) {
        new BroadcastChannel('paypink_ledger_channel').postMessage(syncEvent);
      }
      localStorage.setItem('paypink_last_transfer_event', JSON.stringify(syncEvent));
      localStorage.setItem('paypink_sync_timestamp', String(Date.now()));
    } catch (broadcastErr) {
      console.warn('Real-time sync broadcast notice:', broadcastErr);
    }
  } catch (error) {
    if (generation !== state.generation || !state.session) return;
    form.error = error.message;
    // Preserve the exact request and key after timeouts/server failures: a retry checks the same transfer.
    if ([400,403,404,409,422,429].includes(error.status)) { delete state.session.pendingTransfer; saveSession(); form.review = null; }
  } finally {
    if (generation === state.generation && state.session) { form.sending = false; renderPage(); }
  }
}

function transferReceipt(receipt) {
  return heading('All sent.', 'Your transfer is complete. Both account balances have been updated.')
    + `<section class="transfer-receipt"><span class="receipt-check">${icon('check')}</span><h2>Transfer successful</h2><div class="receipt-amount">${escapeHtml(money(receipt.amount,receipt.currency))}</div><p class="receipt-recipient">${escapeHtml(receipt.recipientName || 'PayPink customer')}</p><p class="muted">${escapeHtml(maskedNumber(receipt.destinationAccountNumber))}</p><dl class="detail-list">${detail('Reference number',escapeHtml(receipt.reference))}${detail('Status',statusPill(receipt.status))}${detail('Transfer fee','₱0.00')}${detail('Date & time',escapeHtml(txDate({date:receipt.date}).toLocaleString('en-PH')))}</dl><div class="dialog-actions"><button class="btn btn-secondary" data-action="save-receipt">Save receipt</button><button class="btn btn-primary" data-action="new-transfer">Done</button></div></section>`;
}

function recipientPicker() {
  const directory = state.recipients;
  const list = (items, title) => `<div class="recipient-list"><h3>${title}</h3>${items.length ? items.map(recipient => `<div class="recipient-option"><button type="button" data-action="choose-recipient" data-number="${escapeHtml(recipient.accountNumber)}"><span class="avatar">${escapeHtml(recipient.fullName.slice(0,1))}</span><span><strong>${escapeHtml(recipient.fullName)}</strong><small>${escapeHtml(maskedNumber(recipient.accountNumber))}</small></span></button><button type="button" class="favorite-toggle ${recipient.favorite ? 'saved' : ''}" data-action="favorite-recipient" data-number="${escapeHtml(recipient.accountNumber)}" data-saved="${recipient.favorite}" aria-label="${recipient.favorite ? 'Remove favorite' : 'Save favorite'} ${escapeHtml(recipient.fullName)} ${escapeHtml(recipient.accountNumber.slice(-4))}" aria-pressed="${recipient.favorite}">${icon(recipient.favorite ? 'star-filled' : 'star')}</button></div>`).join('') : `<p class="recipient-empty">${title === 'Favorites' ? 'Save an account with the star to find it here.' : 'Accounts you transfer with will appear here.'}</p>`}</div>`;
  return `<details class="recipient-picker"><summary>Choose from favorites or recent recipients</summary>${directory.error ? `<p class="form-error">${escapeHtml(directory.error)} Use Refresh to try again.</p>` : list(directory.favorites,'Favorites') + list(directory.recent,'Recent recipients')}</details>`;
}

function recipientStatus() {
  const form = state.transfer;
  if (!form) return '';
  if (form.lookupPending) return '<p class="recipient-feedback">Looking up the account holder…</p>';
  if (form.lookupError) return `<p class="form-error" role="alert">${escapeHtml(form.lookupError)}</p>`;
  const recipient = form.recipient;
  if (!recipient) return '';
  return `<div class="verified-recipient"><div><small>Recipient name</small><strong>${escapeHtml(recipient.fullName)}</strong><span>${icon('check')} Account found · ${escapeHtml(maskedNumber(recipient.accountNumber))}</span></div><button type="button" class="favorite-toggle ${recipient.favorite ? 'saved' : ''}" data-action="favorite-recipient" data-number="${escapeHtml(recipient.accountNumber)}" data-saved="${recipient.favorite}" aria-label="${recipient.favorite ? 'Remove favorite' : 'Save favorite'}" aria-pressed="${recipient.favorite}">${icon(recipient.favorite ? 'star-filled' : 'star')}</button></div>`;
}
function renderRecipientStatus() {
  const node = document.querySelector('#recipient-status');
  if (node) node.innerHTML = recipientStatus();
}

async function lookupRecipient() {
  clearTimeout(recipientTimer);
  const form = state.transfer;
  if (!form || form.mode !== 'other' || !state.session) return;
  const number = form.number.trim().toUpperCase();
  const generation = state.generation;
  const sequence = form.lookupSequence = (form.lookupSequence || 0) + 1;
  form.recipient = null; form.lookupError = '';
  if (!/^[A-Z0-9-]{3,30}$/.test(number)) { form.lookupError = 'Enter the full PayPink account number.'; renderRecipientStatus(); return; }
  form.lookupPending = true; renderRecipientStatus();
  const current = () => state.generation === generation && state.transfer === form && form.mode === 'other'
    && form.number.trim().toUpperCase() === number && form.lookupSequence === sequence;
  try {
    const recipient = await api(`/recipients/lookup?accountNumber=${encodeURIComponent(number)}`);
    if (current()) form.recipient = {...recipient,lookupNumber:number};
  } catch (error) { if (current()) form.lookupError = error.message; }
  finally { if (current()) { form.lookupPending = false; renderRecipientStatus(); } }
}

async function toggleFavorite(number, saved, button) {
  if (!state.session) return;
  button.disabled = true;
  const generation = state.generation;
  try {
    const recipient = await api(saved ? `/favorites/${encodeURIComponent(number)}` : '/favorites',
      {method:saved ? 'DELETE' : 'POST',body:saved ? undefined : {accountNumber:number}});
    if (generation !== state.generation || !state.session) return;
    if (state.transfer?.recipient?.accountNumber === number) state.transfer.recipient.favorite = !saved;
    const directory = await api('/recipients');
    if (generation !== state.generation || !state.session) return;
    state.recipients = directory;
    const pickerOpen = document.querySelector('.recipient-picker')?.open;
    renderPage();
    if (pickerOpen && document.querySelector('.recipient-picker')) document.querySelector('.recipient-picker').open = true;
    toast(saved ? 'Recipient removed from favorites.' : `${recipient.fullName} saved to favorites.`);
  } catch (error) { if (generation === state.generation) toast(error.message,true); }
  finally { if (button.isConnected) button.disabled = false; }
}

function saveReceipt(receipt) {
  if (!receipt) return;
  const canvas = document.createElement('canvas'); canvas.width = 1000; canvas.height = 1250;
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#faf9f6'; ctx.fillRect(0,0,1000,1250);
  ctx.fillStyle = '#651c3e'; ctx.fillRect(0,0,1000,135);
  ctx.fillStyle = '#fff'; ctx.font = 'bold 44px Arial'; ctx.fillText('PayPink',65,85);
  ctx.fillStyle = '#29242a'; ctx.font = 'bold 32px Arial'; ctx.fillText('Transfer receipt',65,210);
  ctx.font = 'bold 54px Arial'; ctx.fillStyle = '#651c3e'; ctx.fillText(money(receipt.amount,receipt.currency),65,290);
  const fields = [['Recipient',receipt.recipientName || 'PayPink customer'],['Recipient account',maskedNumber(receipt.destinationAccountNumber)],
    ['Status',receipt.status === 'SUCCESS' ? 'Completed' : receipt.status],['Transfer fee','PHP 0.00'],
    ['Date & time',txDate({date:receipt.date}).toLocaleString('en-PH')],['Reference number',receipt.reference]];
  let y = 365;
  for (const [label,value] of fields) {
    ctx.fillStyle = '#807a80'; ctx.font = '22px Arial'; ctx.fillText(label,65,y); y += 38;
    ctx.fillStyle = '#29242a'; ctx.font = '26px Arial';
    let line = '';
    for (const char of String(value)) {
      if (ctx.measureText(line + char).width > 860) { ctx.fillText(line,65,y); y += 34; line = ''; }
      line += char;
    }
    ctx.fillText(line,65,y); y += 24;
    ctx.strokeStyle = '#e5dde1'; ctx.beginPath(); ctx.moveTo(65,y); ctx.lineTo(935,y); ctx.stroke(); y += 42;
  }
  canvas.toBlob(blob => {
    if (!blob) { toast('Could not save the receipt. Please try again.',true); return; }
    const url = URL.createObjectURL(blob); const link = document.createElement('a');
    link.href = url; link.download = `PayPink-${receipt.reference.replace(/[^A-Za-z0-9_-]/g,'')}.png`;
    document.body.append(link); link.click(); link.remove(); setTimeout(() => URL.revokeObjectURL(url),10000);
    toast('Receipt saved.');
  },'image/png');
}
