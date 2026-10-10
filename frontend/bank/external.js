'use strict';

// Mock recipients are never inserted into the database. PayPink debits and transfer statuses are persisted by the server.
const externalRecipients = [
  {bank:'BDO', name:'Alex Reyes', number:'001234567890'},
  {bank:'BPI', name:'Jamie Santos', number:'009876543210'},
  {bank:'Metrobank', name:'Sam Rivera', number:'003456789012'}
];
let externalRows = [];
let externalOwner = null;
let externalLoading = false;
function settleExternal() { return externalOwner === state.profile?.username ? externalRows : []; }
async function loadExternalHistory() {
  if (!state.session || !state.profile || externalLoading) return;
  externalLoading = true;
  const generation = state.generation;
  try {
    const rows = await api('/external/transfers');
    if (generation !== state.generation) return;
    externalOwner = state.profile.username; externalRows = rows;
    if (state.page === 'transfer' && state.transfer?.receipt?.mock) {
      const current = rows.find(r => r.reference === state.transfer.receipt.reference);
      if (current && current.status !== state.transfer.receipt.status) { state.transfer.receipt = current; renderPage(); await refresh(); }
    }

    // Broadcast status updates across tabs so admin monitor immediately receives batch completion
    if (Array.isArray(rows)) {
      rows.forEach(r => {
        const normStatus = (r.status === 'SUCCESS' || r.status === 'COMPLETED') ? 'COMPLETED' : r.status;
        const sourceAcc = (state.profile?.accounts || []).find(a => String(a.accountId) === String(r.sourceAccountId));
        const syncEvent = {
          type: 'CUSTOMER_TRANSFER',
          rail: r.rail || 'PESONET',
          reference: r.reference,
          sourceAccountId: r.sourceAccountId,
          sourceAccountNumber: sourceAcc ? sourceAcc.accountNumber : '001181233469',
          destinationAccountNumber: `${r.bank || 'External'} · ${r.destinationAccountNumber}`,
          amount: r.amount,
          currency: r.currency || 'PHP',
          status: normStatus,
          date: r.date || new Date().toISOString(),
          recipientName: r.recipientName || 'External Customer',
          bank: r.bank || 'External Bank',
          timestamp: Date.now()
        };
        try {
          if (window.BroadcastChannel) {
            new BroadcastChannel('paypink_ledger_channel').postMessage(syncEvent);
          }
          localStorage.setItem('paypink_last_transfer_event', JSON.stringify(syncEvent));
          localStorage.setItem('paypink_sync_timestamp', String(Date.now()));
        } catch (e) {}
      });
    }

    const history = document.querySelector('#external-history');
    if (history) history.innerHTML = externalHistoryMarkup(rows);
  } catch (error) {
    const history = document.querySelector('#external-history');
    if (history) history.textContent = 'Transfer status is temporarily unavailable. It will retry automatically.';
  } finally { externalLoading = false; }
}
function externalTransferPage(accounts) {
  const f = state.transfer;
  f.rail ||= 'INSTAPAY'; f.externalNumber ??= '';
  const rows = settleExternal();
  queueMicrotask(loadExternalHistory);
  const recipient = externalRecipients.find(r => r.number === f.externalNumber);
  return heading('Send outside PayPink.', 'Choose how you want to transfer.') + `<div class="transfer-layout"><section class="transfer-panel">
    <div class="transfer-tabs"><button type="button" data-action="transfer-mode" data-mode="own">My own account</button><button type="button" data-action="transfer-mode" data-mode="other">Another PayPink account</button><button type="button" class="selected" data-action="transfer-mode" data-mode="external">Outside PayPink</button></div>

    ${state.session.pendingExternal ? `<div class="notice">${f.sending ? 'Submitting transfer…' : 'Your transfer needs confirmation. Retry safely using the same request.'}<div style="display:flex;gap:8px;margin-top:10px;flex-wrap:wrap;"><button type="button" class="btn btn-secondary" data-action="confirm-transfer" ${f.sending ? 'disabled' : ''}>Check transfer status</button></div></div>` : ''}${f.error ? `<div class="form-error" role="alert">${escapeHtml(f.error)}</div>` : ''}
    <form id="transfer-form"><fieldset ${state.session.pendingTransfer || state.session.pendingExternal || f.sending ? 'disabled' : ''}>
    <div class="form-field"><label for="transfer-source">Transfer from</label><select id="transfer-source" required>${accounts.map(a => `<option value="${a.accountId}" ${String(a.accountId) === f.source ? 'selected' : ''}>${escapeHtml(accountName(a.accountType))} · ${escapeHtml(a.accountNumber.slice(-4))} · ${balance(a.currentBalance)}</option>`).join('')}</select><small>${f.rail === 'PESONET' ? 'The amount will be deducted when your transfer is processed, after approximately 90 seconds.' : 'The amount will be deducted when you confirm.'}</small></div>
    <div class="form-field"><label for="external-rail">Choose how you want to transfer</label><select id="external-rail"><option value="INSTAPAY" ${f.rail === 'INSTAPAY' ? 'selected' : ''}>InstaPay — instant</option><option value="PESONET" ${f.rail === 'PESONET' ? 'selected' : ''}>PESONet — batch processing</option></select><small>${f.rail === 'PESONET' ? 'Processed in batches. Your transfer will remain pending for approximately 90 seconds.' : 'Completed immediately on confirmation. Up to PHP 50,000 per transfer.'}</small></div>
    <div class="form-field"><label for="external-recipient">Recipient account number</label><input id="external-recipient" inputmode="numeric" autocomplete="off" maxlength="12" pattern="[0-9]{12}" required value="${escapeHtml(f.externalNumber)}" placeholder="Enter the recipient account number"><small id="external-lookup-status" aria-live="polite">${recipient ? 'Account found.' : f.externalNumber.length === 12 ? 'Account not found. Check the account number.' : 'Enter the full account number to find the recipient.'}</small></div><div class="form-field"><label for="external-bank">Receiving bank</label><input id="external-bank" readonly value="${escapeHtml(recipient?.bank || '')}" placeholder="Bank name"></div>
    <div class="form-field"><label for="external-name">Recipient name</label><input id="external-name" readonly value="${escapeHtml(recipient?.name || '')}" placeholder="Recipient name"></div>
    <div class="form-field"><label for="transfer-amount">Amount (PHP)</label><input id="transfer-amount" type="number" min="0.01" step="0.01" required value="${escapeHtml(f.amount)}" placeholder="0.00"></div>
    <div class="transfer-fee"><span>Transfer fee</span><strong>₱0.00</strong></div><button class="btn btn-primary" type="submit">Review transfer ${icon('arrow')}</button></fieldset></form>
    ${state.session.pendingTransfer ? '<p class="notice">Resolve your pending PayPink transfer before starting another transfer.</p>' : ''}
    </section><aside class="transfer-guide"><h2>Recent transfers</h2><p>PESONet transfers keep processing when you leave this screen. Reopen a receipt to check its status.</p><div id="external-history">${externalHistoryMarkup(rows)}</div></aside></div>`;
}
function externalHistoryMarkup(rows) {
  return rows.length ? rows.map(r => `<button class="btn btn-secondary" type="button" data-external-receipt="${escapeHtml(r.reference)}">${escapeHtml(r.recipientName)} · ${escapeHtml(money(r.amount))} · ${r.rail}<br>${r.status === 'PENDING' ? 'Pending — batch processing' : r.status === 'FAILED' ? 'Failed' : 'Completed'}</button>`).join('') : '<p class="muted">Your transfers will appear here.</p>';
}
function reviewExternalTransfer() {
  const f = state.transfer;
  if (state.session.pendingTransfer || state.session.pendingExternal || f.sending) return;
  const source = state.profile.accounts.find(a => String(a.accountId) === f.source && a.status === 'ACTIVE' && a.currency === 'PHP');
  const recipient = externalRecipients.find(r => r.number === f.externalNumber);
  const amount = Number(f.amount);
  f.error = '';
  if (!source || !recipient) f.error = 'Choose valid sending and receiving accounts.';
  else if (!Number.isFinite(amount) || amount <= 0 || Math.abs(amount * 100 - Math.round(amount * 100)) > .00001) f.error = 'Enter a positive amount with at most two decimal places.';
  else if (amount > Number(source.currentBalance)) f.error = 'Not enough available balance for this amount.';
  else if (f.rail === 'INSTAPAY' && amount > 50000) f.error = 'InstaPay allows up to PHP 50,000 per transfer. Choose PESONet for a larger amount.';
  const refKey = typeof generateUUID === 'function' ? generateUUID() : ('PAY-' + Math.random().toString(36).slice(2) + Date.now());
  f.externalReview = {mock:true,reference:`PAY-${refKey}`,recipientName:recipient.name,destinationAccountNumber:recipient.number,bank:recipient.bank,sourceAccountId:source.accountId,amount,currency:'PHP',rail:f.rail};
  showDialog('Review your transfer', `<p class="notice">Please check the recipient details before confirming.</p><dl class="detail-list">${detail('From',escapeHtml(maskedNumber(source.accountNumber)))}${detail('Recipient',escapeHtml(recipient.name))}${detail('Bank',escapeHtml(recipient.bank))}${detail('Account',escapeHtml(maskedNumber(recipient.number)))}${detail('Method',f.rail)}${detail('Amount',escapeHtml(money(amount)))}${detail('Processing',f.rail === 'PESONET' ? 'Pending for about 90 seconds; money deducted on completion' : 'Immediate')}</dl>`, '<button class="btn btn-secondary" data-action="close-dialog">Go back</button><button class="btn btn-primary" data-action="confirm-transfer">Confirm transfer</button>');
}
async function sendExternalTransfer() {
  const f = state.transfer;
  if (f.sending || state.session.pendingTransfer) return;
  const request = state.session.pendingExternal || (f.externalReview && {
    sourceAccountId:f.externalReview.sourceAccountId,destinationAccountNumber:f.externalReview.destinationAccountNumber,
    amount:f.externalReview.amount,rail:f.externalReview.rail,idempotencyKey:f.externalReview.reference
  });
  if (!request) return;
  state.session.pendingExternal = request; saveSession();
  const generation = state.generation;
  f.sending = true; dialog.close(); renderPage();
  try {
    const receipt = await api('/external/transfers',{method:'POST',body:request});
    if (generation !== state.generation) return;
    delete state.session.pendingExternal;
    saveSession();
    f.receipt = receipt;
    f.externalReview = null;
    await refresh();
    await loadExternalHistory();
    renderPage();
    toast('Transfer complete. The external recipient has been credited.');

    // Broadcast external transfer event for instant real-time admin sync
    try {
      const sourceAcc = (state.profile?.accounts || []).find(a => String(a.accountId) === String(request.sourceAccountId));
      const afterBal = sourceAcc ? parseFloat(sourceAcc.currentBalance || 0) : null;
      const beforeBal = afterBal !== null ? (afterBal + parseFloat(request.amount)) : null;
      const syncEvent = {
        type: 'CUSTOMER_TRANSFER',
        rail: receipt.rail || request.rail || 'INSTAPAY',
        reference: receipt.reference,
        sourceAccountId: request.sourceAccountId,
        sourceAccountNumber: sourceAcc ? sourceAcc.accountNumber : '001181233469',
        sourceBeforeBalance: beforeBal,
        sourceAfterBalance: afterBal,
        destinationAccountNumber: `${receipt.bank || 'External'} · ${receipt.destinationAccountNumber}`,
        amount: receipt.amount,
        currency: receipt.currency || 'PHP',
        status: receipt.status || 'COMPLETED',
        date: receipt.date || new Date().toISOString(),
        recipientName: receipt.recipientName || 'External Customer',
        bank: receipt.bank || 'External Bank',
        timestamp: Date.now()
      };
      if (window.BroadcastChannel) {
        new BroadcastChannel('paypink_ledger_channel').postMessage(syncEvent);
      }
      localStorage.setItem('paypink_last_transfer_event', JSON.stringify(syncEvent));
      localStorage.setItem('paypink_sync_timestamp', String(Date.now()));
    } catch (broadcastErr) {
      console.warn('Real-time external broadcast note:', broadcastErr);
    }
  } catch (error) {
    if (generation !== state.generation) return;
    f.error = error.message;
    // A conflict does not prove that the original transfer failed; retry the saved instruction.
    if ([400,403,404,422,429].includes(error.status)) { delete state.session.pendingExternal; saveSession(); }
  } finally { if (generation === state.generation) { f.sending = false; renderPage(); } }
}
function mockReceipt(receipt) {
  const current = settleExternal().find(r => r.reference === receipt.reference) || receipt;
  state.transfer.receipt = current;
  return heading(current.status === 'PENDING' ? 'Transfer pending.' : current.status === 'FAILED' ? 'Transfer failed.' : 'Transfer complete.', 'Track your transfer and save your receipt.') + `<section class="transfer-receipt"><h2>${current.status === 'PENDING' ? 'Waiting for the PESONet batch' : current.status === 'FAILED' ? 'Transfer unsuccessful' : 'Transfer completed'}</h2><div class="receipt-amount">${escapeHtml(money(current.amount))}</div><dl class="detail-list">${detail('Recipient',escapeHtml(current.recipientName))}${detail('Bank',escapeHtml(current.bank))}${detail('Account',escapeHtml(maskedNumber(current.destinationAccountNumber)))}${detail('Method',current.rail)}${detail('Status',statusPill(current.status))}${detail('Reference',escapeHtml(current.reference))}${detail('Submitted',escapeHtml(txDate({date:current.date}).toLocaleString('en-PH')))}</dl>${current.status === 'PENDING' ? '<p role="status">Your transfer is waiting to be processed. No money has been deducted yet. Keep sufficient funds in your account.</p>' : ''}${current.status === 'FAILED' ? '<p role="status">The account was unavailable or had insufficient funds when processing began. No money was deducted.</p>' : ''}<div class="dialog-actions"><button class="btn btn-secondary" data-action="save-receipt">Save receipt</button><button class="btn btn-primary" data-action="new-transfer">Done</button></div></section>`;
}
document.addEventListener('change', event => {
  if (!state.transfer) return;
  if (event.target.id === 'external-rail') { state.transfer.rail = event.target.value; renderPage(); }
  
});
document.addEventListener('click', event => {
  const button = event.target.closest('[data-external-receipt]');
  if (button) {
    const receipt = settleExternal().find(r => r.reference === button.dataset.externalReceipt);
    if (receipt && state.transfer) { state.transfer.receipt = receipt; renderPage(); }
    return;
  }
});
setInterval(() => { if (state.session && state.page === 'transfer') loadExternalHistory(); }, 2000);

document.addEventListener('input', event => {
  if (event.target.id !== 'external-recipient' || !state.transfer) return;
  const number = event.target.value.replace(/\s/g,''); event.target.value = number;
  state.transfer.externalNumber = number;
  state.transfer.externalReview = null;
  const recipient = externalRecipients.find(r => r.number === number);
  document.querySelector('#external-name').value = recipient?.name || '';
  document.querySelector('#external-bank').value = recipient?.bank || '';
  document.querySelector('#external-lookup-status').textContent = recipient ? 'Account found.' : number.length >= 12 ? 'Account not found. Check the account number.' : 'Enter the full account number to find the recipient.';
  event.target.setCustomValidity(number.length >= 12 && !recipient ? 'Account not found. Check the account number.' : '');
});
setInterval(() => {
  if (state.session && !document.hidden && !dialog.open && state.page === 'activity'
      && state.activity.some(tx => tx.type?.startsWith('EXT_') && tx.status === 'PENDING')) refresh();
}, 2000);
