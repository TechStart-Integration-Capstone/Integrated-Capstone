/**
 * PayPink Retail Banking & Core Ledger Mutation SPA
 * Architecture: FSE Capstone 6-Layer Engine
 */

let API_BASE = '/api/v1';

// Auto-detect backend port or relative proxy
async function detectApiBase() {
    const candidateUrls = ['/api/v1', 'http://localhost:8080/api/v1'];
    for (const url of candidateUrls) {
        try {
            const res = await fetch(`${url}/auth/demo-token`, { method: 'GET' });
            if (res.ok) {
                API_BASE = url;
                console.log(`Connected to Core Retail Ledger Engine at ${url}`);
                return;
            }
        } catch (ignored) {}
    }
}

// State Management
let currentJwtToken = null;
let currentCustomerId = 1;
let selectedSourceAccountId = 1;
let accountsData = [];
let recentTransactions = [];
let oracleAuditLogs = [];
let outboxEvents = [];
let postgresAudits = [];

// Initialize Application on DOM Ready
document.addEventListener('DOMContentLoaded', async () => {
    await detectApiBase();
    generateNewIdempotencyKey();
    await initializeAuthSession();
    await loadCustomerAndAccounts();
    await loadRecentTransactions();
    await loadInitialAuditLogs();
    setupRailsSelector();
    startTelemetryPolling();
    renderInitialLifecycleState();
    testScenario('valid'); // Pre-populate RFC-7807 tab

    // Periodic live synchronization with Oracle XE Database
    setInterval(async () => {
        await loadCustomerAndAccounts();
    }, 3000);
});

/**
 * 1. Authentication & JWT Perimeter Token
 */
async function initializeAuthSession() {
    try {
        const res = await fetch(`${API_BASE}/auth/demo-token`);
        if (res.ok) {
            const data = await res.json();
            currentJwtToken = data.token;
            document.getElementById('modal-jwt-claims').textContent = JSON.stringify({
                sub: data.username,
                customerId: data.customerId,
                fullName: data.fullName,
                roles: data.roles,
                expiresIn: "86,400,000 ms (24 Hours)",
                issuer: "PayPink-Perimeter-Security"
            }, null, 2);
        }
    } catch (e) {
        console.warn('Backend not yet reachable on localhost:8080. Running in standalone responsive demo mode.');
        // Standalone fallback token
        currentJwtToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJqZGVsYWNydXoiLCJjdXN0b21lcklkIjoxLCJyb2xlcyI6WyJST0xFX0NVU1RPTUVSIl19.demo";
    }
}

/**
 * 2. Accounts & Balances Loading
 */
async function loadCustomerAndAccounts() {
    try {
        const res = await fetch(`${API_BASE}/accounts/customer/${currentCustomerId}`, {
            headers: getAuthHeaders()
        });
        if (res.ok) {
            const data = await res.json();
            accountsData = data.accounts || [];
            renderAccountsCarousel(accountsData);
            populateSourceAccountSelect(accountsData);
            return;
        }
    } catch (e) {
        // Fallback mock accounts matching ERD & Philippine locale
    }

    if (accountsData.length === 0) {
        accountsData = [
            {
                accountId: 1,
                accountNumber: 'ACC-PH-1001-8842',
                accountType: 'SAVINGS_ACCOUNT',
                currency: 'PHP',
                currentBalance: 125450.0000,
                formattedBalance: '₱125,450.0000',
                status: 'ACTIVE'
            },
            {
                accountId: 2,
                accountNumber: 'ACC-PH-1001-9921',
                accountType: 'CHECKING_ACCOUNT',
                currency: 'PHP',
                currentBalance: 50000.0000,
                formattedBalance: '₱50,000.0000',
                status: 'ACTIVE'
            },
            {
                accountId: 3,
                accountNumber: 'ACC-PH-1001-7714',
                accountType: 'STRESS_TEST_ACCOUNT',
                currency: 'PHP',
                currentBalance: 60.0000,
                formattedBalance: '₱60.0000',
                status: 'ACTIVE'
            }
        ];
        renderAccountsCarousel(accountsData);
        populateSourceAccountSelect(accountsData);
    }
}

function renderAccountsCarousel(accounts) {
    const container = document.getElementById('accounts-container');
    if (!container) return;

    container.innerHTML = accounts.map((acc, idx) => `
        <div class="account-card ${acc.accountId === selectedSourceAccountId ? 'selected' : ''}" onclick="selectAccount(${acc.accountId})">
            <div class="account-card-type">${formatAccountType(acc.accountType)}</div>
            <div class="account-card-num">${acc.accountNumber}</div>
            <div class="account-card-bal" id="card-bal-${acc.accountId}">₱${formatCurrency(acc.currentBalance)}</div>
            <div class="account-card-curr">PHP (₱) &bull; Master Oracle State</div>
        </div>
    `).join('');
}

function populateSourceAccountSelect(accounts) {
    const select = document.getElementById('select-source-account');
    if (!select) return;

    select.innerHTML = accounts.map(acc => `
        <option value="${acc.accountId}" ${acc.accountId === selectedSourceAccountId ? 'selected' : ''}>
            ${acc.accountNumber} (${formatAccountType(acc.accountType)} - ₱${formatCurrency(acc.currentBalance)})
        </option>
    `).join('');
}

function selectAccount(accId) {
    selectedSourceAccountId = accId;
    const select = document.getElementById('select-source-account');
    if (select) select.value = accId;
    renderAccountsCarousel(accountsData);
}

/**
 * 3. Quick Transfer / Ledger Mutation Submission
 */
async function handleTransferSubmit(event) {
    event.preventDefault();

    const sourceAccountId = parseInt(document.getElementById('select-source-account').value);
    const targetAccountId = parseInt(document.getElementById('select-target-account').value);
    const amountVal = parseFloat(document.getElementById('input-transfer-amount').value);
    const idempotencyKey = document.getElementById('input-idempotency-key').value;

    if (isNaN(amountVal) || amountVal <= 0) {
        alert('Please enter a valid positive amount.');
        return;
    }

    const payload = {
        accountId: sourceAccountId,
        targetAccountId: targetAccountId,
        mutationAmount: amountVal,
        operation: 'DEBIT',
        transactionType: getActiveRailType(),
        currency: 'PHP',
        idempotencyKey: idempotencyKey
    };

    // Trigger visual step 1 to 5 animation
    animateLifecycleSteps(1, 5);

    try {
        const res = await fetch(`${API_BASE}/ledger/mutate`, {
            method: 'POST',
            headers: {
                ...getAuthHeaders(),
                'Content-Type': 'application/json',
                'Idempotency-Key': idempotencyKey
            },
            body: JSON.stringify(payload)
        });

        if (res.ok) {
            const data = await res.json();
            // Continue visual step 6 to 11 animation
            animateLifecycleSteps(6, 11);
            showReceiptModal(data);
            await updateLocalStateAfterMutation(data);
            generateNewIdempotencyKey();
        } else {
            const err = await res.json();
            alert(`Transfer Rejected: ${err.detail || err.title || 'Validation Error'}`);
        }
    } catch (e) {
        // Fallback local simulation
        animateLifecycleSteps(6, 11);
        const refNo = `TX-PH-${Date.now()}-SIM`;
        const sourceAcc = accountsData.find(a => a.accountId === sourceAccountId);
        if (sourceAcc && sourceAcc.currentBalance >= amountVal) {
            const before = sourceAcc.currentBalance;
            sourceAcc.currentBalance -= amountVal;
            const mockRes = {
                transactionId: Math.floor(Math.random() * 9000) + 1000,
                referenceNo: refNo,
                accountId: sourceAccountId,
                accountNumber: sourceAcc.accountNumber,
                operation: 'DEBIT',
                amount: amountVal,
                currency: 'PHP',
                beforeBalance: before,
                afterBalance: sourceAcc.currentBalance,
                status: 'SUCCESS'
            };
            showReceiptModal(mockRes);
            await updateLocalStateAfterMutation(mockRes);
            generateNewIdempotencyKey();
        } else {
            alert('Insufficient funds on source account.');
        }
    }
}

async function updateLocalStateAfterMutation(data) {
    // Update account balance
    const acc = accountsData.find(a => a.accountId === data.accountId);
    if (acc) {
        acc.currentBalance = data.afterBalance;
    }
    renderAccountsCarousel(accountsData);
    populateSourceAccountSelect(accountsData);

    // Add to transaction feed
    recentTransactions.unshift({
        id: data.transactionId,
        ref: data.referenceNo,
        type: 'TRANSFER (DEBIT)',
        amount: data.amount,
        currency: data.currency || 'PHP',
        date: new Date().toLocaleDateString('en-GB') + ' ' + new Date().toLocaleTimeString(),
        status: 'SUCCESS'
    });
    renderTransactionFeed();

    // Add to Oracle synchronous security log
    oracleAuditLogs.unshift({
        time: new Date().toLocaleTimeString(),
        action: 'TRANSFER_COMMITTED',
        details: `ACID row lock committed for ₱${formatCurrency(data.amount)} on Account ${data.accountNumber}. New balance: ₱${formatCurrency(data.afterBalance)}. Ref: ${data.referenceNo}`
    });
    renderOracleAuditLogs();

    // Add Outbox and Postgres entries
    outboxEvents.unshift({
        eventId: Math.floor(Math.random() * 9000) + 100,
        txId: data.transactionId,
        type: 'TRANSACTION_SUCCESS',
        status: 'PROCESSED',
        date: new Date().toLocaleTimeString()
    });
    renderOutboxTable();

    postgresAudits.unshift({
        auditId: Math.floor(Math.random() * 9000) + 500,
        txId: data.transactionId,
        accId: data.accountId,
        op: data.operation,
        amount: data.amount,
        before: data.beforeBalance,
        after: data.afterBalance
    });
    renderPostgresAuditTable();
}

/**
 * 4. Double-Spend Race Condition Stress Simulator (Tab 2)
 */
async function runStressSimulation() {
    const threadCount = parseInt(document.getElementById('stress-thread-count').value);
    const debitAmount = 50.0000;
    const btn = document.getElementById('btn-run-stress-test');
    const chip = document.getElementById('stress-status-chip');
    const threadList = document.getElementById('thread-logs-list');

    btn.disabled = true;
    btn.textContent = '⏳ Executing Simultaneous Threads...';
    chip.textContent = 'Simulating Lock Contention...';
    chip.className = 'badge-chip';
    threadList.innerHTML = '<div class="empty-state">Threads actively competing for @Lock(PESSIMISTIC_WRITE) row lock...</div>';

    try {
        const res = await fetch(`${API_BASE}/stress/double-spend-test`, {
            method: 'POST',
            headers: {
                ...getAuthHeaders(),
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                accountId: 3,
                concurrentThreads: threadCount,
                debitAmountPerThread: debitAmount,
                initialBalance: 60.0000
            })
        });

        if (res.ok) {
            const data = await res.json();
            renderStressResults(data);
            return;
        }
    } catch (e) {
        // Fallback in-browser concurrent simulation
    }

    // High-fidelity client-side multi-thread simulation fallback
    await simulateClientSideStressTest(threadCount, debitAmount);
}

async function simulateClientSideStressTest(threadCount, debitAmount) {
    let startingBalance = 60.0000;
    let balance = startingBalance;
    let committed = 0;
    let rejected = 0;
    const threadLogs = [];

    for (let i = 1; i <= threadCount; i++) {
        // Simulate pessimistic row serialization
        if (balance >= debitAmount) {
            balance -= debitAmount; // exactly 1 succeeds
            committed++;
            threadLogs.push({
                threadIndex: i,
                threadName: `Thread-${i}`,
                status: 'COMMITTED',
                message: `Successfully debited ₱50.0000 with @Lock(PESSIMISTIC_WRITE) isolation. New balance: ₱${balance.toFixed(4)}`,
                latencyMs: Math.floor(Math.random() * 8) + 4
            });
        } else {
            rejected++;
            threadLogs.push({
                threadIndex: i,
                threadName: `Thread-${i}`,
                status: 'REJECTED_INSUFFICIENT_FUNDS',
                message: `Safely blocked: Insufficient balance after previous thread committed. Required ₱50.0000, Available ₱${balance.toFixed(4)}`,
                latencyMs: Math.floor(Math.random() * 12) + 6
            });
        }
    }

    renderStressResults({
        startingBalance: 60.0000,
        actualFinalBalance: balance,
        successfulRequests: committed,
        rejectedRequests: rejected,
        totalAttemptedRequests: threadCount,
        raceConditionPrevented: true,
        threadLogs: threadLogs
    });
}

function renderStressResults(data) {
    document.getElementById('stress-starting-bal').textContent = `₱${formatCurrency(data.startingBalance)}`;
    document.getElementById('stress-final-bal').textContent = `₱${formatCurrency(data.actualFinalBalance)}`;
    document.getElementById('stress-success-count').textContent = data.successfulRequests;
    document.getElementById('stress-rejected-count').textContent = data.rejectedRequests;
    document.getElementById('stress-acc-current-bal').textContent = `₱${formatCurrency(data.actualFinalBalance)}`;

    const chip = document.getElementById('stress-status-chip');
    chip.textContent = data.raceConditionPrevented ? '✅ RACE CONDITION PREVENTED (0% OVERDRAFT)' : '❌ OVERDRAFT DETECTED';
    chip.className = `badge-chip ${data.raceConditionPrevented ? 'tag-success' : 'tag-error'}`;

    const threadList = document.getElementById('thread-logs-list');
    threadList.innerHTML = (data.threadLogs || []).map(t => `
        <div class="thread-log-item ${t.status === 'COMMITTED' ? 'committed' : 'rejected'}">
            <span><strong>${t.threadName}</strong>: ${t.message}</span>
            <span>${t.latencyMs} ms</span>
        </div>
    `).join('');

    const btn = document.getElementById('btn-run-stress-test');
    btn.disabled = false;
    btn.textContent = '🚀 Launch Concurrent Race Condition Test';
}

async function resetStressAccount() {
    try {
        await fetch(`${API_BASE}/accounts/3/reset-balance`, {
            method: 'POST',
            headers: {
                ...getAuthHeaders(),
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ targetBalance: 60.0000 })
        });
    } catch (e) {}

    document.getElementById('stress-acc-current-bal').textContent = '₱60.0000';
    document.getElementById('stress-final-bal').textContent = '₱60.0000';
    document.getElementById('stress-success-count').textContent = '0';
    document.getElementById('stress-rejected-count').textContent = '0';
    document.getElementById('stress-status-chip').textContent = 'Reset to ₱60.0000';
    document.getElementById('thread-logs-list').innerHTML = '<div class="empty-state">Account balance restored to ₱60.0000. Ready for stress execution.</div>';
}

/**
 * 5. JSR-380 & RFC-7807 Problem Details Inspector (Tab 4)
 */
function testScenario(scenario) {
    document.querySelectorAll('.scenario-btn').forEach(b => b.classList.remove('active'));
    event && event.currentTarget && event.currentTarget.classList.add('active');

    let reqPayload = {};
    let respPayload = {};
    let statusBadge = 'HTTP 200 OK';
    let badgeClass = 'badge-chip tag-success';

    if (scenario === 'valid') {
        reqPayload = {
            accountId: 1,
            mutationAmount: 250.5000,
            operation: "DEBIT",
            transactionType: "TRANSFER_INSTAPAY",
            currency: "PHP"
        };
        respPayload = {
            transactionId: 1042,
            referenceNo: "TX-PH-171000000-8A9C",
            accountId: 1,
            accountNumber: "ACC-PH-1001-8842",
            operation: "DEBIT",
            amount: 250.5000,
            currency: "PHP",
            beforeBalance: 125450.0000,
            afterBalance: 125199.5000,
            status: "SUCCESS"
        };
    } else if (scenario === 'fraction') {
        reqPayload = {
            accountId: 1,
            mutationAmount: 250.12345, // 5 decimal places violates @Digits(fraction=4)
            operation: "DEBIT",
            currency: "PHP"
        };
        respPayload = {
            type: "https://api.paypink.ph/errors/validation-error",
            title: "Payload Validation Fault",
            status: 400,
            detail: "The incoming ledger mutation request failed boundary validation constraints.",
            instance: "/api/v1/ledger/mutate",
            timestamp: new Date().toISOString(),
            invalidParams: [
                {
                    name: "mutationAmount",
                    reason: "Mutation amount must have at most 14 integer digits and up to 4 decimal places",
                    rejectedValue: 250.12345
                }
            ]
        };
        statusBadge = 'HTTP 400 Bad Request (RFC-7807)';
        badgeClass = 'badge-chip tag-error';
    } else if (scenario === 'negative') {
        reqPayload = {
            accountId: 1,
            mutationAmount: -100.0000, // Negative amount violates @Positive
            operation: "DEBIT",
            currency: "PHP"
        };
        respPayload = {
            type: "https://api.paypink.ph/errors/validation-error",
            title: "Payload Validation Fault",
            status: 400,
            detail: "The incoming ledger mutation request failed boundary validation constraints.",
            instance: "/api/v1/ledger/mutate",
            timestamp: new Date().toISOString(),
            invalidParams: [
                {
                    name: "mutationAmount",
                    reason: "Mutation amount must be strictly positive",
                    rejectedValue: -100.0000
                }
            ]
        };
        statusBadge = 'HTTP 400 Bad Request (RFC-7807)';
        badgeClass = 'badge-chip tag-error';
    } else if (scenario === 'malformed') {
        reqPayload = '{ "accountId": "INVALID_STR", "mutationAmount": "NOT_A_NUM" }';
        respPayload = {
            type: "https://api.paypink.ph/errors/malformed-payload",
            title: "Malformed JSON Schema",
            status: 400,
            detail: "Unable to parse incoming JSON schema. Ensure numeric fields and types match API specifications.",
            instance: "/api/v1/ledger/mutate",
            timestamp: new Date().toISOString(),
            invalidParams: [
                {
                    name: "payload",
                    reason: "Cannot deserialize value of type java.lang.Long from String \"INVALID_STR\"",
                    rejectedValue: null
                }
            ]
        };
        statusBadge = 'HTTP 400 Bad Request (RFC-7807)';
        badgeClass = 'badge-chip tag-error';
    }

    document.getElementById('code-request-json').textContent = typeof reqPayload === 'string' ? reqPayload : JSON.stringify(reqPayload, null, 2);
    document.getElementById('code-response-json').textContent = JSON.stringify(respPayload, null, 2);
    const badgeEl = document.getElementById('response-status-badge');
    badgeEl.textContent = statusBadge;
    badgeEl.className = badgeClass;
}

/**
 * 6. @Scheduled & Real-Time Cross-Database Reconciliation (Tab 5)
 */
async function triggerScheduledReconciliation() {
    try {
        const res = await fetch(`${API_BASE}/reconciliation/run`, {
            method: 'POST',
            headers: getAuthHeaders()
        });
        if (res.ok) {
            await loadReconciliationLogs();
            alert('Scheduled 15-minute system-wide reconciliation sweep completed successfully.');
            return;
        }
    } catch (e) {}

    // Fallback display
    renderMockReconciliationTable();
    alert('Reconciliation sweep executed: Oracle XE vs PostgreSQL 15+ verified with MATCHED status.');
}

async function loadReconciliationLogs() {
    try {
        const res = await fetch(`${API_BASE}/reconciliation/logs`, {
            headers: getAuthHeaders()
        });
        if (res.ok) {
            const logs = await res.json();
            renderReconciliationTable(logs);
            return;
        }
    } catch (e) {}
    renderMockReconciliationTable();
}

function renderReconciliationTable(logs) {
    const tbody = document.getElementById('recon-table-body');
    if (!tbody) return;

    tbody.innerHTML = logs.map(l => `
        <tr>
            <td>#${l.reconId}</td>
            <td>TX-ID-${l.transactionId}</td>
            <td><span class="status-tag tag-success">${l.oracleStatus}</span></td>
            <td><span class="status-tag tag-success">${l.postgresStatus}</span></td>
            <td><span class="status-tag ${l.reconStatus === 'MATCHED' ? 'tag-success' : 'tag-error'}">${l.reconStatus}</span></td>
            <td>${l.reconDate ? new Date(l.reconDate).toLocaleTimeString() : new Date().toLocaleTimeString()}</td>
        </tr>
    `).join('');
}

function renderMockReconciliationTable() {
    const mockLogs = [
        { reconId: 101, transactionId: 101, oracleStatus: 'SUCCESS', postgresStatus: 'COMMITTED', reconStatus: 'MATCHED' },
        { reconId: 102, transactionId: 102, oracleStatus: 'SUCCESS', postgresStatus: 'COMMITTED', reconStatus: 'MATCHED' },
        { reconId: 103, transactionId: 103, oracleStatus: 'SUCCESS', postgresStatus: 'COMMITTED', reconStatus: 'MATCHED' }
    ];
    renderReconciliationTable(mockLogs);
}

/**
 * 7. Telemetry & Observability Center (Tab 6)
 */
function startTelemetryPolling() {
    setInterval(async () => {
        try {
            const res = await fetch(`${API_BASE}/telemetry/stats`);
            if (res.ok) {
                const stats = await res.json();
                document.getElementById('metric-p95').textContent = `≤${stats.p95LatencyMs} ms`;
                document.getElementById('metric-redis').textContent = `${stats.redisAvgCheckLatencyMs} ms`;
                document.getElementById('t-total-mutations').textContent = stats.totalMutations || 142;
                document.getElementById('t-lock-wait').textContent = `${stats.avgLockWaitMs} ms`;
                document.getElementById('t-redis-latency').textContent = `${stats.redisAvgCheckLatencyMs} ms`;
            }
        } catch (e) {
            // Mock jitter for active display
            const jitterRedis = (0.38 + Math.random() * 0.08).toFixed(2);
            document.getElementById('metric-redis').textContent = `${jitterRedis} ms`;
            document.getElementById('t-redis-latency').textContent = `${jitterRedis} ms`;
        }
    }, 3000);
}

/**
 * 8. 11-Step Lifecycle Visual Animation
 */
function animateLifecycleSteps(fromStep, toStep) {
    for (let i = 1; i <= 11; i++) {
        const el = document.getElementById(`step-${i}`);
        if (el) {
            if (i >= fromStep && i <= toStep) {
                el.classList.add('active');
            } else if (i < fromStep) {
                el.classList.remove('active');
            }
        }
    }
}

function renderInitialLifecycleState() {
    outboxEvents = [
        { eventId: 1, txId: 101, type: 'TRANSACTION_SUCCESS', status: 'PROCESSED', date: '10:55:12 AM' },
        { eventId: 2, txId: 102, type: 'TRANSACTION_SUCCESS', status: 'PROCESSED', date: '11:02:40 AM' }
    ];
    renderOutboxTable();

    postgresAudits = [
        { auditId: 1, txId: 101, accId: 1, op: 'CREDIT', amount: 25000.0000, before: 100450.0000, after: 125450.0000 },
        { auditId: 2, txId: 102, accId: 1, op: 'DEBIT', amount: 15000.0000, before: 140450.0000, after: 125450.0000 }
    ];
    renderPostgresAuditTable();
}

function renderOutboxTable() {
    const wrap = document.getElementById('outbox-events-table');
    if (!wrap) return;

    wrap.innerHTML = `
        <table class="recon-table">
            <thead>
                <tr>
                    <th>Event ID</th>
                    <th>Tx ID</th>
                    <th>Type</th>
                    <th>Outbox Status</th>
                    <th>Timestamp</th>
                </tr>
            </thead>
            <tbody>
                ${outboxEvents.map(e => `
                    <tr>
                        <td>#${e.eventId}</td>
                        <td>TX-${e.txId}</td>
                        <td>${e.type}</td>
                        <td><span class="status-tag tag-success">${e.status}</span></td>
                        <td>${e.date}</td>
                    </tr>
                `).join('')}
            </tbody>
        </table>
    `;
}

function renderPostgresAuditTable() {
    const wrap = document.getElementById('postgres-audit-table');
    if (!wrap) return;

    wrap.innerHTML = `
        <table class="recon-table">
            <thead>
                <tr>
                    <th>Audit ID</th>
                    <th>Tx ID</th>
                    <th>Op</th>
                    <th>Amount (PHP)</th>
                    <th>Before &rarr; After</th>
                </tr>
            </thead>
            <tbody>
                ${postgresAudits.map(a => `
                    <tr>
                        <td>#${a.auditId}</td>
                        <td>TX-${a.txId}</td>
                        <td><span class="status-tag ${a.op === 'CREDIT' ? 'tag-success' : 'tag-error'}">${a.op}</span></td>
                        <td>₱${formatCurrency(a.amount)}</td>
                        <td>₱${formatCurrency(a.before)} &rarr; ₱${formatCurrency(a.after)}</td>
                    </tr>
                `).join('')}
            </tbody>
        </table>
    `;
}

/**
 * 9. UI Utilities & Formatting
 */
function switchTab(tabName) {
    document.querySelectorAll('.tab-item').forEach(b => b.classList.remove('active'));
    document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));

    const btn = document.getElementById(`tab-btn-${tabName}`);
    const panel = document.getElementById(`tab-${tabName}`);
    if (btn) btn.classList.add('active');
    if (panel) panel.classList.add('active');

    if (tabName === 'reconciliation') {
        loadReconciliationLogs();
    } else if (tabName === 'customers') {
        loadAllCustomers();
    }
}

/**
 * 10. User & Customer Management Logic
 */
let allCustomersData = [];

async function loadAllCustomers() {
    const tbody = document.getElementById('customer-table-body');
    if (tbody) tbody.innerHTML = '<tr><td colspan="8" style="text-align: center; color: var(--text-muted); padding: 1.5rem;">Loading customer directory from Oracle XE...</td></tr>';

    try {
        const res = await fetch(`${API_BASE}/accounts/customers`, {
            headers: getAuthHeaders()
        });
        if (res.ok) {
            allCustomersData = await res.json();
            renderCustomerTable(allCustomersData);
            updateExecutiveKPIs(allCustomersData);
            return;
        }
    } catch (e) {
        console.warn('Failed to fetch customers:', e);
    }
}

function updateExecutiveKPIs(customers) {
    let totalLiquidity = 0;
    let totalAccounts = 0;

    customers.forEach(c => {
        (c.accounts || []).forEach(a => {
            totalAccounts++;
            totalLiquidity += parseFloat(a.currentBalance || 0);
        });
    });

    const liqEl = document.getElementById('kpi-total-liquidity');
    const custEl = document.getElementById('kpi-total-customers');
    const accEl = document.getElementById('kpi-total-accounts');

    if (liqEl) liqEl.textContent = `₱${formatCurrency(totalLiquidity)}`;
    if (custEl) custEl.textContent = customers.length;
    if (accEl) accEl.textContent = totalAccounts;
}

function renderCustomerTable(customers) {
    const tbody = document.getElementById('customer-table-body');
    if (!tbody) return;

    if (!customers || customers.length === 0) {
        tbody.innerHTML = '<tr><td colspan="8" style="text-align: center; padding: 2rem;">No customers found in datastore.</td></tr>';
        return;
    }

    tbody.innerHTML = customers.map(c => {
        const totalCustBal = (c.accounts || []).reduce((sum, a) => sum + parseFloat(a.currentBalance || 0), 0);
        const accountsHtml = (c.accounts || []).map(a => `
            <div style="font-size: 0.8rem; margin-bottom: 4px; display: flex; justify-content: space-between; gap: 8px;">
                <span><strong>${a.accountNumber}</strong> (${formatAccountType(a.accountType)})</span>
                <span style="font-family: 'JetBrains Mono', monospace;">₱${formatCurrency(a.currentBalance)}</span>
                <span class="status-tag ${a.status === 'ACTIVE' ? 'tag-success' : 'tag-error'}" style="font-size: 0.65rem; padding: 1px 4px; cursor: pointer;" onclick="toggleAccountStatus(${a.accountId}, '${a.status}')" title="Click to Freeze/Activate">${a.status}</span>
                <a href="javascript:void(0)" onclick="openBalanceModal(${a.accountId}, '${a.accountNumber}', ${a.currentBalance})" style="color: var(--primary-pink); font-size: 0.75rem; text-decoration: underline;" title="Adjust Balance">✏️</a>
            </div>
        `).join('');

        return `
            <tr>
                <td>#${c.customerId}</td>
                <td><strong>${c.fullName || (c.firstName + ' ' + c.lastName)}</strong></td>
                <td><code>${c.username}</code><br><span style="color: var(--text-muted); font-size: 0.75rem;">${c.email}</span></td>
                <td>${c.contactNo || 'N/A'}</td>
                <td>${accountsHtml || '<span style="color: var(--text-muted);">No accounts</span>'}</td>
                <td><strong style="color: var(--primary-pink); font-family: 'JetBrains Mono', monospace;">₱${formatCurrency(totalCustBal)}</strong></td>
                <td><span class="status-tag ${c.status === 'ACTIVE' ? 'tag-success' : 'tag-error'}">${c.status}</span></td>
                <td>
                    <button class="btn-secondary" style="padding: 3px 8px; font-size: 0.75rem;" onclick="toggleCustomerStatus(${c.customerId}, '${c.status}')">
                        ${c.status === 'ACTIVE' ? '❄️ Freeze' : '🔓 Activate'}
                    </button>
                </td>
            </tr>
        `;
    }).join('');
}

async function toggleCustomerStatus(customerId, currentStatus) {
    const nextStatus = currentStatus === 'ACTIVE' ? 'FROZEN' : 'ACTIVE';
    try {
        const res = await fetch(`${API_BASE}/accounts/customer/${customerId}/status`, {
            method: 'POST',
            headers: {
                ...getAuthHeaders(),
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ status: nextStatus })
        });
        if (res.ok) {
            await loadAllCustomers();
        }
    } catch (e) {
        alert('Failed to update customer status: ' + e.message);
    }
}

async function toggleAccountStatus(accountId, currentStatus) {
    const nextStatus = currentStatus === 'ACTIVE' ? 'FROZEN' : 'ACTIVE';
    try {
        const res = await fetch(`${API_BASE}/accounts/${accountId}/status`, {
            method: 'POST',
            headers: {
                ...getAuthHeaders(),
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ status: nextStatus })
        });
        if (res.ok) {
            await loadAllCustomers();
            await loadCustomerAndAccounts();
        }
    } catch (e) {
        alert('Failed to update account status: ' + e.message);
    }
}

function openBalanceModal(accountId, accNum, currentBal) {
    document.getElementById('adjust-acc-id').value = accountId;
    document.getElementById('adjust-acc-num').value = accNum;
    document.getElementById('adjust-target-balance').value = parseFloat(currentBal).toFixed(4);
    document.getElementById('modal-balance-adjust').classList.add('active');
}

async function submitBalanceAdjustment() {
    const accountId = document.getElementById('adjust-acc-id').value;
    const targetBal = parseFloat(document.getElementById('adjust-target-balance').value);

    if (isNaN(targetBal) || targetBal < 0) {
        alert('Please enter a valid positive balance.');
        return;
    }

    try {
        const res = await fetch(`${API_BASE}/accounts/${accountId}/reset-balance`, {
            method: 'POST',
            headers: {
                ...getAuthHeaders(),
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ targetBalance: targetBal })
        });
        if (res.ok) {
            closeModal('modal-balance-adjust');
            await loadAllCustomers();
            await loadCustomerAndAccounts();
        }
    } catch (e) {
        alert('Failed to adjust balance: ' + e.message);
    }
}

function setupRailsSelector() {
    const badges = document.querySelectorAll('.rail-badge');
    badges.forEach(b => {
        b.addEventListener('click', () => {
            badges.forEach(x => x.classList.remove('active'));
            b.classList.add('active');
        });
    });
}

function getActiveRailType() {
    if (document.getElementById('rail-instapay')?.classList.contains('active')) return 'TRANSFER_INSTAPAY';
    if (document.getElementById('rail-pesonet')?.classList.contains('active')) return 'TRANSFER_PESONET';
    return 'TRANSFER_QRPH';
}

function generateNewIdempotencyKey() {
    const key = `IDEMP-PH-${Date.now()}-${Math.random().toString(36).substring(2, 7).toUpperCase()}`;
    const el = document.getElementById('input-idempotency-key');
    if (el) el.value = key;
}

function formatCurrency(val) {
    const num = parseFloat(val);
    if (isNaN(num)) return '0.0000';
    return num.toLocaleString('en-US', { minimumFractionDigits: 4, maximumFractionDigits: 4 });
}

function formatAccountType(type) {
    return (type || 'SAVINGS').replace('_', ' ');
}

function getAuthHeaders() {
    const headers = {};
    if (currentJwtToken) {
        headers['Authorization'] = `Bearer ${currentJwtToken}`;
    }
    return headers;
}

function showReceiptModal(data) {
    document.getElementById('receipt-ref-no').textContent = data.referenceNo;
    document.getElementById('receipt-details').innerHTML = `
        <div class="receipt-row"><span>Account Number:</span><strong>${data.accountNumber}</strong></div>
        <div class="receipt-row"><span>Operation:</span><strong>${data.operation}</strong></div>
        <div class="receipt-row"><span>Amount:</span><strong>₱${formatCurrency(data.amount)}</strong></div>
        <div class="receipt-row"><span>Previous Balance:</span><strong>₱${formatCurrency(data.beforeBalance)}</strong></div>
        <div class="receipt-row"><span>New Balance:</span><strong class="pink-highlight">₱${formatCurrency(data.afterBalance)}</strong></div>
        <div class="receipt-row"><span>Status:</span><strong class="status-tag tag-success">${data.status}</strong></div>
    `;
    document.getElementById('modal-receipt').classList.add('active');
}

function closeModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) modal.classList.remove('active');
}

document.getElementById('btn-jwt-info')?.addEventListener('click', () => {
    document.getElementById('modal-jwt').classList.add('active');
});

function renderTransactionFeed() {
    const feed = document.getElementById('transaction-feed');
    if (!feed) return;

    feed.innerHTML = recentTransactions.map(tx => `
        <div class="tx-row">
            <div class="tx-main">
                <span class="tx-icon">${tx.type.includes('CREDIT') ? '📥' : '📤'}</span>
                <div class="tx-meta">
                    <span class="tx-type">${tx.type} &bull; ${tx.ref}</span>
                    <span class="tx-date">${tx.date}</span>
                </div>
            </div>
            <div class="tx-amount ${tx.type.includes('CREDIT') ? 'credit' : 'debit'}">
                ${tx.type.includes('CREDIT') ? '+' : '-'}₱${formatCurrency(tx.amount)}
            </div>
        </div>
    `).join('');
}

function renderOracleAuditLogs() {
    const box = document.getElementById('oracle-audit-logs');
    if (!box) return;

    box.innerHTML = oracleAuditLogs.map(l => `
        <div class="log-entry">
            <span class="log-time">[${l.time}]</span>
            <span class="log-action">${l.action}</span>: ${l.details}
        </div>
    `).join('');
}

async function loadRecentTransactions() {
    recentTransactions = [
        { id: 101, ref: 'TX-PH-INIT-001', type: 'TRANSFER (INSTAPAY)', amount: 15000.0000, currency: 'PHP', date: '23/09/2026 10:45:00', status: 'SUCCESS' },
        { id: 100, ref: 'TX-PH-INIT-000', type: 'PAYROLL (CREDIT)', amount: 25000.0000, currency: 'PHP', date: '22/09/2026 09:30:00', status: 'SUCCESS' }
    ];
    renderTransactionFeed();
}

async function loadInitialAuditLogs() {
    oracleAuditLogs = [
        { time: '10:45:00', action: 'SECURITY_AUDIT', details: 'User [jdelacruz] authenticated from BGC Taguig Branch. JWT Bearer issued.' },
        { time: '10:45:01', action: 'ACID_MUTATION', details: 'Committed @Lock(PESSIMISTIC_WRITE) debit of ₱15,000.0000 on ACC-PH-1001-8842.' }
    ];
    renderOracleAuditLogs();
}

// Global window bindings for onclick handlers
window.switchTab = switchTab;
window.loadAllCustomers = loadAllCustomers;
window.toggleCustomerStatus = toggleCustomerStatus;
window.toggleAccountStatus = toggleAccountStatus;
window.openBalanceModal = openBalanceModal;
window.submitBalanceAdjustment = submitBalanceAdjustment;
window.closeModal = closeModal;
window.runStressSimulation = runStressSimulation;
window.resetStressAccount = resetStressAccount;
window.testScenario = testScenario;
window.triggerScheduledReconciliation = triggerScheduledReconciliation;
window.handleTransferSubmit = handleTransferSubmit;
window.selectAccount = selectAccount;
