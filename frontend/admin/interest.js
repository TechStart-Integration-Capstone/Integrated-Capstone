/* Admin interest recovery. Amounts and IDs stay strings until server-side decimal calculation. */
(() => {
    const root = document.getElementById('tab-interest');
    if (!root) return;
    const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
    const date = value => value ? value.split('-').reverse().join('/') : '—';
    const timestamp = value => value ? new Date(value).toLocaleString('en-GB', { timeZone: 'Asia/Manila' }) + ' PHT' : '—';
    const peso = value => {
        const [whole, fraction] = String(value).split('.');
        return '₱' + whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',') + (fraction === undefined ? '.00' : '.' + fraction);
    };
    const statusLabel = value => ({PENDING_APPROVAL:'Pending review', APPROVED:'Approved', SUPERSEDED:'Superseded',
        POSTED:'Posted', BLOCKED:'Blocked', READY:'Ready to post', ACCRUING:'Accruing'})[value] || value;
    const el = id => document.getElementById('interest-' + id);
    let overview = null, review = null, loading = false, busy = false, generation = 0;
    root.innerHTML = `
        <div class="interest-toolbar"><label>Interest month<input id="interest-month" type="month"></label>
            <div class="interest-actions"><button class="btn btn-secondary" id="interest-refresh">Refresh</button>
            <button class="btn btn-primary" id="interest-post" disabled>Post monthly interest</button></div></div>
        <p class="interest-note"><strong>Single-admin simulation.</strong> File a historical backfill first, then open it for review and approve it separately. Both steps record your identity and time. Missing days cannot be waived.</p>
        <div id="interest-message" class="interest-message" role="status" hidden></div>
        <div id="interest-summary" class="interest-summary"></div>
        <div class="interest-grid"><section class="section-card"><h2 class="card-title">Missing days</h2>
            <p id="interest-period" class="interest-subtitle">Choose a month to check daily processing.</p>
            <div id="interest-missing" class="interest-days"></div></section>
        <section class="section-card"><h2 class="card-title">Filed backfills</h2>
            <p class="interest-subtitle">Open a submission to review its historical balances and approval history.</p>
            <div class="interest-scroll"><table><thead><tr><th>Date</th><th>Filed by</th><th>Status</th><th>Action</th></tr></thead>
            <tbody id="interest-proposals"></tbody></table></div></section></div>
        <section class="section-card" id="interest-prepare" hidden><h2 class="card-title">1. File historical balances</h2>
            <p class="interest-subtitle">Include every eligible account for this date, including accounts since closed. Use a verified historical source; current balances cannot replace missing history. Savings rates are calculated automatically.</p>
            <form id="interest-file-form"><div class="interest-form-grid">
                <label>Missing business date<input id="interest-date" type="date" required readonly></label>
                <label>Source reference<input id="interest-source" maxlength="255" required placeholder="Historical EOD export or incident reference"></label>
            </div><label>Reason for backfill<textarea id="interest-reason" maxlength="1000" required></textarea></label>
            <div class="interest-scroll"><table><thead><tr><th>Account ID</th><th>Account type</th><th>Historical EOD balance (PHP)</th><th>Loan annual rate (fraction)</th><th></th></tr></thead><tbody id="interest-accounts"></tbody></table></div>
            <div class="interest-actions"><button type="button" class="btn btn-secondary" id="interest-add">Add account</button></div>
            <label class="interest-check"><input type="checkbox" id="interest-empty">The source confirms there were no eligible accounts on this date.</label>
            <label class="interest-check"><input type="checkbox" id="interest-file-confirm" required>I checked the source and included the complete historical account list.</label>
            <button class="btn btn-primary" id="interest-file-submit" type="submit">File for review</button>
            <button class="btn btn-secondary" id="interest-cancel-file" type="button">Cancel</button></form></section>
        <section class="section-card" id="interest-review" hidden><h2 class="card-title">2. Review historical backfill</h2>
            <div id="interest-review-details"></div>
            <form id="interest-approve-form"><label>Approval reason<textarea id="interest-approval-reason" maxlength="1000" required></textarea></label>
            <label class="interest-check"><input type="checkbox" id="interest-approve-confirm" required>I reviewed the stored balances, source and complete account list, and approve this backfill.</label>
            <button class="btn btn-primary" id="interest-approve-submit" type="submit">Approve reviewed backfill</button></form>
            <button class="btn btn-secondary" id="interest-close-review" type="button">Close review</button></section>`;

    function message(text, error = false) {
        el('message').textContent = text;
        el('message').className = 'interest-message' + (error ? ' error' : '');
        el('message').hidden = !text;
    }
    async function api(path, body) {
        const token = sessionStorage.getItem('paypink_admin_jwt');
        if (!token) throw new Error('Sign in as an administrator to manage interest.');
        const response = await fetch(`${API_BASE}/interest/eod${path}`, {
            method: body === undefined ? 'GET' : 'POST', cache: 'no-store', signal: AbortSignal.timeout(30000),
            headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
            ...(body === undefined ? {} : { body: JSON.stringify(body) })
        });
        const data = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(response.status === 404 ? 'Interest operations are unavailable. Check service activation and deployment.'
            : response.status === 401 ? 'Your session expired. Sign in again.' : data.detail || data.message || data.error || `Interest request failed (${response.status}).`);
        return data;
    }
    function periodEnd(month) {
        if (!/^\d{4}-\d{2}$/.test(month)) throw new Error('Select an interest month.');
        const [year, part] = month.split('-').map(Number);
        return `${month}-${new Date(Date.UTC(year, part, 0)).getUTCDate()}`;
    }
    function render() {
        if (!overview) return;
        el('month').value = overview.periodEnd.slice(0, 7);
        el('month').min = overview.startDate.slice(0, 7);
        el('month').max = overview.businessDate.slice(0, 7);
        el('summary').innerHTML = `<div><span>Monthly posting</span><strong>${esc(statusLabel(overview.postingStatus))}</strong></div>
            <div><span>Missing elapsed days</span><strong>${overview.missingDays.length}</strong></div>
            <div><span>Pending reviews</span><strong>${overview.proposals.filter(p => p.status === 'PENDING_APPROVAL').length}</strong></div>`;
        el('period').textContent = `${date(overview.periodStart)} – ${date(overview.periodEnd)}. Today and future dates are not counted as missing.`;
        el('missing').innerHTML = overview.missingDays.length ? overview.missingDays.map(day =>
            `<button class="interest-day" data-file-date="${esc(day)}">${date(day)} · File balances</button>`).join('')
            : '<p class="interest-subtitle">No missing elapsed days in this period.</p>';
        el('proposals').innerHTML = overview.proposals.length ? overview.proposals.map(p => `<tr><td>${date(p.businessDate)}</td>
            <td>${esc(p.preparedBy)}</td><td><span class="interest-tag">${esc(statusLabel(p.status))}</span></td>
            <td><button class="btn btn-secondary" data-review-id="${esc(p.proposalId)}">${p.status === 'PENDING_APPROVAL' ? 'Review' : 'View'}</button></td></tr>`).join('')
            : '<tr><td colspan="4">No backfills filed for this month.</td></tr>';
        el('post').disabled = busy || overview.postingStatus !== 'READY';
    }
    async function load() {
        if (loading || busy) return;
        const version = generation;
        loading = true;
        el('refresh').disabled = true;
        el('post').disabled = true;
        try {
            const month = el('month').value;
            const result = await api('/overview' + (month ? `?periodEnd=${periodEnd(month)}` : ''));
            if (version !== generation) return;
            overview = result;
            render();
        } catch (error) {
            if (version !== generation) return;
            overview = null;
            el('summary').innerHTML = '';
            el('missing').innerHTML = '';
            el('proposals').innerHTML = '';
            el('prepare').hidden = true;
            el('review').hidden = true;
            message(error.message, true);
        } finally { loading = false; el('refresh').disabled = false; }
    }
    function addAccount() {
        const row = document.createElement('tr');
        row.innerHTML = `<td><input aria-label="Account ID" data-field="accountId" inputmode="numeric" pattern="[1-9][0-9]*" required></td>
            <td><select aria-label="Account type" data-field="accountType"><option>SAVINGS</option><option>SAVINGS_ACCOUNT</option><option>LOAN</option></select></td>
            <td><input aria-label="Historical EOD balance in PHP" data-field="eodBalance" inputmode="decimal" pattern="[0-9]+(\\.[0-9]{1,4})?" required></td>
            <td><input aria-label="Loan annual rate as fraction" data-field="annualRate" inputmode="decimal" placeholder="Loan only, e.g. 0.1800" pattern="[0-9]+(\\.[0-9]{1,4})?"></td>
            <td><button class="btn btn-secondary" type="button" data-remove-row>Remove</button></td>`;
        el('accounts').append(row);
    }
    function file(day) {
        if (busy || !overview?.missingDays.includes(day)) return;
        review = null;
        el('review').hidden = true;
        el('file-form').reset();
        el('accounts').innerHTML = '';
        el('add').disabled = false;
        addAccount();
        el('date').value = day;
        el('prepare').hidden = false;
        el('prepare').scrollIntoView({ behavior: 'smooth', block: 'start' });
        el('source').focus();
    }
    async function openReview(id) {
        if (busy) return;
        const version = generation;
        busy = true;
        el('month').disabled = true;
        el('review').hidden = true;
        review = null;
        try {
            const result = await api('/backfills/' + encodeURIComponent(id));
            if (version !== generation) return;
            review = result;
            const p = review;
            el('prepare').hidden = true;
            el('approve-form').reset();
            const canApprove = !p.approvedBy && overview?.missingDays.includes(p.businessDate);
            el('approve-form').hidden = !canApprove;
            el('review-details').innerHTML = `<dl class="interest-meta"><dt>Business date</dt><dd>${date(p.businessDate)}</dd>
                <dt>Filed by</dt><dd>${esc(p.preparedBy)} · ${esc(timestamp(p.preparedAt))}</dd>
                <dt>Source reference</dt><dd>${esc(p.request.sourceReference)}</dd><dt>Reason</dt><dd>${esc(p.request.reason)}</dd>
                <dt>Status</dt><dd>${p.approvedBy ? 'Approved' : canApprove ? 'Pending review' : 'Date already sealed; this proposal cannot be approved'}</dd>
                ${p.approvedBy ? `<dt>Approved by</dt><dd>${esc(p.approvedBy)} · ${esc(timestamp(p.approvedAt))}</dd><dt>Approval reason</dt><dd>${esc(p.approvalReason)}</dd>` : ''}</dl>
                <div class="interest-scroll"><table><thead><tr><th>Account ID</th><th>Type</th><th>Historical balance (PHP)</th><th>Annual rate (fraction)</th></tr></thead><tbody>
                ${p.request.accounts.map(a => `<tr><td>${esc(a.accountId)}</td><td>${esc(a.accountType)}</td><td>${esc(peso(a.eodBalance))}</td><td>${a.accountType === 'LOAN' ? esc(a.annualRate) : 'Calculated from savings tier'}</td></tr>`).join('') || '<tr><td colspan="4">Source attests there were no eligible accounts.</td></tr>'}
                </tbody></table></div>`;
            el('review').hidden = false;
            el('review').scrollIntoView({ behavior: 'smooth', block: 'start' });
        } catch (error) { if (version === generation) message(error.message, true); }
        finally { busy = false; el('month').disabled = false; }
    }
    async function mutate(action) {
        if (busy) return;
        busy = true;
        el('month').disabled = true;
        const version = generation;
        root.querySelectorAll('button').forEach(button => button.disabled = true);
        try { await action(version); }
        catch (error) { if (version === generation) message(error.message + ' Refresh before retrying if the outcome is uncertain.', true); }
        finally {
            busy = false;
            el('month').disabled = false;
            root.querySelectorAll('button').forEach(button => button.disabled = false);
            el('add').disabled = el('empty').checked;
            if (version === generation) await load();
        }
    }
    el('file-form').addEventListener('submit', event => {
        event.preventDefault();
        if (!el('file-form').reportValidity()) return;
        const accounts = [...el('accounts').querySelectorAll('tr')].map(row => {
            const account = Object.fromEntries([...row.querySelectorAll('[data-field]')].map(input => [input.dataset.field, input.value.trim()]));
            if (!account.annualRate) delete account.annualRate;
            return account;
        });
        if (!accounts.length && !el('empty').checked) return message('Add all eligible accounts, or explicitly confirm the source has no eligible accounts.', true);
        if (accounts.some(a => a.accountType === 'LOAN' && !a.annualRate)) return message('Every loan account needs its historical annual contract rate as a fraction.', true);
        mutate(async version => {
            const proposal = await api(`/resolve?businessDate=${el('date').value}`, { mode:'BACKFILL', reason:el('reason').value.trim(),
                sourceReference:el('source').value.trim(), confirmed:el('file-confirm').checked, accounts });
            if (version !== generation) return;
            el('prepare').hidden = true;
            message(`Backfill filed for ${date(proposal.businessDate)}. Open it in Filed backfills to review and approve. Interest is still blocked for this day.`);
        });
    });
    el('approve-form').addEventListener('submit', event => {
        event.preventDefault();
        if (!review || !el('approve-form').reportValidity()) return;
        const id = review.proposalId;
        mutate(async version => {
            await api(`/backfills/${id}/approve`, { reason:el('approval-reason').value.trim(), confirmed:el('approve-confirm').checked });
            if (version !== generation) return;
            review = null;
            el('review').hidden = true;
            message('Backfill approved and the daily accrual recorded. The monthly status will refresh; closed complete months are also posted by scheduled recovery.');
        });
    });
    el('post').addEventListener('click', () => {
        if (!overview || overview.postingStatus !== 'READY' || !window.confirm(`Post earned savings interest for the period ending ${date(overview.periodEnd)}? This credits account balances.`)) return;
        const end = overview.periodEnd;
        mutate(async version => {
            const result = await api(`/post?businessDate=${end}`, {});
            if (version === generation) message(result.replayed ? 'This period was already processed; no duplicate credits were made.' : `Monthly interest posted to ${result.accounts} accounts.`);
        });
    });
    root.addEventListener('click', event => {
        const target = event.target.closest('button');
        if (!target || busy) return;
        if (target.dataset.fileDate) file(target.dataset.fileDate);
        if (target.dataset.reviewId) openReview(target.dataset.reviewId);
        if (target.hasAttribute('data-remove-row')) target.closest('tr').remove();
    });
    el('empty').addEventListener('change', () => {
        if (el('empty').checked) el('accounts').innerHTML = '';
        else addAccount();
        el('add').disabled = el('empty').checked;
    });
    el('add').addEventListener('click', addAccount);
    el('cancel-file').addEventListener('click', () => { el('prepare').hidden = true; });
    el('close-review').addEventListener('click', () => { review = null; el('review').hidden = true; });
    el('refresh').addEventListener('click', () => { message(''); load(); });
    el('month').addEventListener('change', () => {
        generation++; loading = false; review = null;
        el('prepare').hidden = true; el('review').hidden = true;
        message(''); load();
    });
    window.InterestAdmin = { load, reset() {
        generation++; overview = null; review = null;
        el('summary').innerHTML = ''; el('missing').innerHTML = ''; el('proposals').innerHTML = '';
        el('prepare').hidden = true; el('review').hidden = true; el('post').disabled = true;
        message('Sign in as an administrator to manage interest.');
    } };
    setInterval(() => { if (root.classList.contains('active') && !document.hidden && sessionStorage.getItem('paypink_admin_jwt')) load(); }, 60000);
})();
