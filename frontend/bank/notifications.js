'use strict';

// Uses customer-scoped history, including unposted sender requests.
// No browser-supplied customer ID: /transactions authenticates ownership on the server.
const transferNotifications = {owner:null, generation:-1, items:[], known:new Set(), read:new Set(), loaded:false, busy:false, error:''};
const notificationKey = () => `paypink.notifications.read.${state.profile?.username}`;
function resetNotificationOwner() {
  if (transferNotifications.owner === state.profile?.username && transferNotifications.generation === state.generation) return;
  dismissTransferPopups();
  Object.assign(transferNotifications,{owner:state.profile?.username,generation:state.generation,items:[],known:new Set(),read:new Set(),loaded:false,error:''});
  try { transferNotifications.read = new Set(JSON.parse(localStorage.getItem(notificationKey()) || '[]')); } catch { /* Reading remains available without storage. */ }
}
function notificationFromLoan(tx) {
  const amount = money(tx.amount,tx.currency);
  const account = maskedNumber(tx.accountNumber);
  return tx.type === 'LOAN_DISBURSEMENT'
    ? {id:`${tx.transactionId}:${tx.status}`,title:'Loan approved and received',message:`Your loan was approved. ${amount} was credited to your account ${account}.`,tx}
    : {id:`${tx.transactionId}:${tx.status}`,title:'Loan payment received',message:`${amount} was paid toward your loan from your account ${account}.`,tx};
}
function notificationFromTransfer(tx) {
  if (tx.type?.startsWith('LOAN_')) return notificationFromLoan(tx);
  const incoming = tx.type === 'TRANSFER_IN';
  const status = String(tx.status).toUpperCase();
  const pending = ['PENDING','RESERVED','PROCESSING'].includes(status);
  const cancelled = status === 'CANCELLED';
  const failed = status === 'FAILED';
  const title = pending ? 'Transfer pending' : cancelled ? 'Transfer cancelled' : failed ? 'Transfer failed' : incoming ? 'Money received' : 'Money sent';
  const amount = money(tx.amount,tx.currency);
  const person = tx.counterpartyName?.trim() || 'another account';
  const account = maskedNumber(tx.accountNumber);
  const message = pending ? `Your ${amount} transfer ${incoming ? 'from' : 'to'} ${person} is awaiting confirmation. Check its status before sending again.`
    : cancelled ? `Your ${amount} transfer ${incoming ? 'from' : 'to'} ${person} was cancelled.`
    : failed ? `Your ${amount} transfer ${incoming ? 'from' : 'to'} ${person} could not be completed.`
    : incoming ? `${amount} from ${person} was credited to your account ${account}.`
    : `${amount} was sent to ${person} from your account ${account}.`;
  return {id:`${tx.transactionId || tx.reference}:${tx.status}`,title,message,tx};
}
function updateTransferNotifications(activity, announce = true) {
  if (!state.session || !state.profile) return;
  resetNotificationOwner();
  const items = activity.filter(tx => (['P2P_REMITTANCE','TRANSFER_IN','TRANSFER_OUT','LOAN_DISBURSEMENT','LOAN_REPAYMENT'].includes(tx.type) || tx.type?.startsWith('EXT_'))
    && ['PENDING','FAILED','SUCCESS','COMPLETED','POSTED','CANCELLED','RESERVED','PROCESSING'].includes(String(tx.status).toUpperCase())).map(notificationFromTransfer);
  const fresh = items.filter(item => !transferNotifications.known.has(item.id));
  if (announce && transferNotifications.loaded && fresh.length) {
    fresh.slice(0,3).reverse().forEach(showTransferPopup);
  }
  transferNotifications.items = items;
  items.forEach(item => transferNotifications.known.add(item.id));
  transferNotifications.loaded = true; transferNotifications.error = '';
  renderNotificationBadge();
  if (dialog.open && dialog.dataset.notifications === 'true') renderNotificationInbox();
}
function renderNotificationBadge() {
  const button = document.querySelector('#notification-button');
  if (!button) return;
  const count = transferNotifications.items.filter(item => !transferNotifications.read.has(item.id)).length;
  button.setAttribute('aria-label',`Notifications${count ? `, ${count} unread` : ''}`);
  const badge = button.querySelector('.notification-count');
  badge.hidden = count === 0; badge.textContent = count > 99 ? '99+' : String(count);
}
function notificationList() {
  if (transferNotifications.error) return `<p role="status" class="notice">${escapeHtml(transferNotifications.error)}</p>`;
  if (!transferNotifications.loaded) return '<p role="status" class="muted">Loading notifications…</p>';
  if (!transferNotifications.items.length) return '<div class="notification-empty"><h3>You’re all caught up.</h3><p>Updates about money you send and receive will appear here.</p></div>';
  return `<div class="notification-list">${transferNotifications.items.map(item => `<button type="button" class="notification-item ${transferNotifications.read.has(item.id) ? '' : 'unread'}" data-notification-id="${escapeHtml(item.id)}"><span class="notification-symbol" aria-hidden="true">${['TRANSFER_IN','LOAN_DISBURSEMENT'].includes(item.tx.type) ? '↓' : '↑'}</span><span><strong>${escapeHtml(item.title)}</strong><span class="notification-message">${escapeHtml(item.message)}</span><small>${escapeHtml(txDate(item.tx).toLocaleString('en-PH'))}</small>${!transferNotifications.read.has(item.id) ? '<span class="notification-unread-label">Unread</span>' : ''}</span></button>`).join('')}</div>`;
}
function renderNotificationInbox() {
  const body = dialog.querySelector('#notification-inbox');
  if (body) body.innerHTML = notificationList();
}
function openNotifications() {
  if (!transferNotifications.loaded || transferNotifications.generation !== state.generation) updateTransferNotifications(state.activity,false);
  showDialog('Notifications','<div id="notification-inbox">'+notificationList()+'</div>', '<button class="btn btn-secondary" type="button" data-notifications-read-all>Mark all as read</button><button class="btn btn-primary" data-action="close-dialog">Done</button>');
  dialog.dataset.notifications = 'true';
  pollTransferNotifications();
}
function persistNotificationReads() {
  // Bound browser storage to the notifications still present in the inbox.
  const ids = transferNotifications.items.filter(item => transferNotifications.read.has(item.id)).map(item => item.id);
  try { localStorage.setItem(notificationKey(),JSON.stringify(ids)); } catch { /* Read state stays in memory. */ }
  renderNotificationBadge();
}
async function pollTransferNotifications() {
  if (!state.session || !state.profile || document.hidden || transferNotifications.busy) return;
  const generation = state.generation;
  transferNotifications.busy = true;
  try {
    const activity = await api('/transactions');
    if (generation !== state.generation || !state.session) return;
    updateTransferNotifications(activity);
  } catch {
    if (generation !== state.generation || !state.session) return;
    transferNotifications.error = 'Notifications are temporarily unavailable. We’ll retry shortly.';
    if (dialog.open && dialog.dataset.notifications === 'true') renderNotificationInbox();
  } finally { transferNotifications.busy = false; }
}
document.addEventListener('click', event => {
  const closePopup = event.target.closest('[data-dismiss-transfer-popup]');
  if (closePopup) { closePopup.closest('.transfer-popup').remove(); return; }
  if (event.target.closest('#notification-button')) { openNotifications(); return; }
  if (event.target.closest('[data-notifications-read-all]')) {
    transferNotifications.items.forEach(item => transferNotifications.read.add(item.id));
    persistNotificationReads(); renderNotificationInbox(); return;
  }
  const button = event.target.closest('[data-notification-id]');
  if (!button) return;
  const item = transferNotifications.items.find(item => item.id === button.dataset.notificationId);
  if (!item) return;
  button.closest('.transfer-popup')?.remove();
  transferNotifications.read.add(item.id); persistNotificationReads();
  dialog.dataset.notifications = 'false';
  showDialog(item.title,`<p>${escapeHtml(item.message)}</p><dl class="detail-list">${detail('Amount',escapeHtml(money(item.tx.amount,item.tx.currency)))}${detail('Status',statusPill(item.tx.status))}${detail(['TRANSFER_IN','LOAN_DISBURSEMENT'].includes(item.tx.type) ? 'Sender' : 'Recipient',escapeHtml(item.tx.type?.startsWith('LOAN_') ? 'PayPink Loans' : item.tx.counterpartyName || 'PayPink customer'))}${detail('Account',escapeHtml(maskedNumber(item.tx.counterpartyAccountNumber)))}${detail('Reference',escapeHtml(item.tx.reference))}${detail('Date & time',escapeHtml(txDate(item.tx).toLocaleString('en-PH')))}</dl>`, '<button class="btn btn-secondary" type="button" id="notification-button-back">All notifications</button><button class="btn btn-primary" data-action="close-dialog">Done</button>');
});
document.addEventListener('click', event => { if (event.target.closest('#notification-button-back')) openNotifications(); });
dialog.addEventListener('close',()=>{ dialog.dataset.notifications='false'; });
document.addEventListener('visibilitychange',()=>{ if (!document.hidden) pollTransferNotifications(); });
setInterval(pollTransferNotifications,8000);

function dismissTransferPopups() {
  document.querySelector('#transfer-popups')?.remove();
}
function showTransferPopup(item) {
  let host = document.querySelector('#transfer-popups');
  if (!host) {
    host = document.createElement('div');
    host.id = 'transfer-popups'; host.setAttribute('aria-label','New transfer notifications');
    document.body.append(host);
  }
  const popup = document.createElement('div');
  popup.className = 'transfer-popup';
  popup.innerHTML = `<button type="button" class="transfer-popup-open" data-notification-id="${escapeHtml(item.id)}"><span class="notification-symbol" aria-hidden="true">${['TRANSFER_IN','LOAN_DISBURSEMENT'].includes(item.tx.type) ? '↓' : '↑'}</span><span><span role="status"><strong>${escapeHtml(item.title)}</strong><span class="notification-message">${escapeHtml(item.message)}</span></span><small>View transfer details →</small></span></button><button type="button" class="transfer-popup-close" data-dismiss-transfer-popup aria-label="Dismiss notification">×</button>`;
  host.prepend(popup);
  while (host.children.length > 3) host.lastElementChild.remove();
  let timer;
  const schedule = () => { clearTimeout(timer); timer = setTimeout(()=>popup.remove(),6000); };
  popup.addEventListener('mouseenter',()=>clearTimeout(timer));
  popup.addEventListener('mouseleave',()=>{ if (!popup.contains(document.activeElement)) schedule(); });
  popup.addEventListener('focusin',()=>clearTimeout(timer));
  popup.addEventListener('focusout',()=>{ if (!popup.matches(':hover')) schedule(); });
  schedule();
}
