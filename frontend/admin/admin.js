/* Presentation for the operations workspace. Ledger actions remain in app.js. */
(() => {
    const pages = {
        dashboard: ['Overview', 'Banking operations, in view.', 'Manage the demo portfolio and follow every ledger movement.', 'M3 10l9-7 9 7v11H3z M9 21v-8h6v8'],
        customers: ['Manage Users', 'Customers, Roles & Transaction Limits.', 'Manage users, assign roles, configure transfer limits, and freeze/unfreeze accounts.', 'M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2 M9 7a4 4 0 1 0 0-8 4 4 0 0 0 0 8 M23 21v-2a4 4 0 0 0-3-3.87 M16 3.13a4 4 0 0 1 0 7.75'],
        transactions: ['Monitor Transactions', 'Real-Time Ledger Transaction Feed.', 'Monitor all live mutations, channel settlements, and authorization events.', 'M4 7h16m-4-4 4 4-4 4 M20 17H4m4-4-4 4 4 4'],
        audit: ['View Audit Logs', 'Immutable Audit & Non-Repudiation.', 'Inspect synchronous Azure SQL audit logs and PostgreSQL double-entry audit streams.', 'M12 3l8 4v6c0 5-8 9-8 9s-8-4-8-9V7z M8 12l3 3 5-6'],
        reports: ['Generate Reports', 'Compliance & Reconciliation Reports.', 'Generate and export cross-database reconciliation, settlement, and regulatory audit reports.', 'M9 17v-2m3 2v-4m3 4v-6m2 10H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z M9 9h1m-1 4h4'],
        reconciliation: ['Reconciliation', 'Keep the books aligned.', 'Compare account balances with the recorded ledger and review discrepancies.', 'M4 5h16v16H4z M8 9h8 M8 13h8 M8 17h4'],
        telemetry: ['Observability', 'A closer look at performance.', 'Explore the simulation’s throughput, latency, and connection metrics.', 'M4 20V10 M10 20V4 M16 20v-8 M22 20H2']
    };
    const sidebar = document.createElement('aside');
    sidebar.className = 'admin-sidebar';
    sidebar.innerHTML = '<a class="admin-brand" href="/" aria-label="PayPink admin home"><span class="admin-mark">p</span>PayPink<sup>®</sup></a><div class="admin-eyebrow">ADMIN WORKSPACE</div>';
    const nav = document.getElementById('app-tabs');
    nav.setAttribute('aria-label', 'Administration');
    sidebar.append(nav);
    const footer = document.createElement('div');
    footer.className = 'admin-sidebar-footer';
    footer.innerHTML = '<div class="admin-workspace-note"><strong>A clear view of your bank.</strong><p>Ledger tools and simulation controls, together in one workspace.</p><span>Simulation environment</span></div><a href="/bank/" class="admin-bank-link">Open customer banking <span aria-hidden="true">↗</span></a>';
    sidebar.append(footer);
    document.getElementById('app-container').prepend(sidebar);
    document.querySelector('.nav-brand').innerHTML = '<div class="admin-breadcrumb">PayPink <span>/</span> Admin <span>/</span> <strong id="admin-current-page">Overview</strong></div>';
    document.querySelector('.badge-text').textContent = 'Session details';
    document.querySelector('.badge-icon').textContent = '↗';
    document.querySelector('.avatar-circle').textContent = 'PP';
    document.querySelector('.user-name').textContent = 'Operations';
    document.querySelector('.user-branch').textContent = 'Simulation workspace';
    const main = document.getElementById('main-content');
    const intro = document.createElement('div');
    intro.className = 'admin-page-heading';
    intro.innerHTML = '<div><div class="admin-eyebrow">PAYPINK OPERATIONS</div><h1 id="admin-page-title"></h1><p id="admin-page-description"></p></div><a class="admin-customer-button" href="/bank/">Customer banking <span aria-hidden="true">↗</span></a>';
    main.prepend(intro);
    const architecture = document.querySelector('.system-pills');
    architecture.classList.add('admin-architecture');
    architecture.setAttribute('aria-label', 'Configured simulation architecture');
    architecture.insertAdjacentHTML('afterbegin', '<span class="admin-architecture-label">Architecture</span>');
    intro.after(architecture);
    const titles = document.querySelectorAll('#tab-dashboard .card-title');
    titles[0].textContent = 'Demo customer accounts';
    titles[1].textContent = 'Run a ledger mutation';
    const banner = document.createElement('div');
    banner.className = 'admin-overview-banner';
    banner.innerHTML = '<div><span class="admin-banner-label">THE OPERATIONS DESK</span><h2>One workspace. Every movement.</h2><p>Review accounts, inspect transactions, and put the ledger through its paces.</p></div><div class="admin-banner-tag">Core retail ledger <span>Simulation & audit tools</span></div>';
    document.getElementById('tab-dashboard').prepend(banner);
    Object.entries(pages).forEach(([id, page]) => {
        const button = document.getElementById(`tab-btn-${id}`);
        if (button) {
            button.innerHTML = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="${page[3]}"/></svg><span>${page[0]}</span><i aria-hidden="true"></i>`;
            button.setAttribute('aria-controls', `tab-${id}`);
            button.addEventListener('click', (e) => {
                e.preventDefault();
                window.switchTab(id);
            });
        }
    });
    const originalSwitchTab = window.switchTab;
    window.switchTab = function (id) {
        if (!pages[id]) return;
        if (typeof originalSwitchTab === 'function') {
            originalSwitchTab(id);
        }
        const curPage = document.getElementById('admin-current-page');
        const pageTitle = document.getElementById('admin-page-title');
        const pageDesc = document.getElementById('admin-page-description');
        if (curPage) curPage.textContent = pages[id][0];
        if (pageTitle) pageTitle.textContent = pages[id][1];
        if (pageDesc) pageDesc.textContent = pages[id][2];
        Object.keys(pages).forEach(key => {
            const button = document.getElementById(`tab-btn-${key}`);
            if (button) {
                if (key === id) button.setAttribute('aria-current', 'page');
                else button.removeAttribute('aria-current');
            }
        });
    };
    window.switchTab('dashboard');
})();
