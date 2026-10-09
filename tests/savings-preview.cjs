// Offline UI checks using installed Chrome/Edge; no packages or banking services.
// Run: node tests/savings-preview.cjs
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { spawn } = require('node:child_process');

const executable = process.env.CHROME_PATH || [
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  '/usr/bin/google-chrome', '/usr/bin/chromium'
].find(candidate => fs.existsSync(candidate));
assert.ok(executable, 'Install Chrome/Edge or set CHROME_PATH to run the offline UI checks.');
const artifacts = fs.mkdtempSync(path.join(os.tmpdir(), 'paypink-savings-'));
const browser = spawn(executable, ['--headless=new', '--disable-gpu', '--no-first-run',
  '--no-default-browser-check', '--disable-background-networking', '--disable-extensions',
  '--remote-debugging-pipe', `--user-data-dir=${path.join(artifacts, 'profile')}`],
{ stdio: ['ignore', 'ignore', 'pipe', 'pipe', 'pipe'], windowsHide: true });
let nextId = 0;
let buffer = '';
const pending = new Map();
const errors = [];
const apiRequests = [];
browser.stdio[4].on('data', chunk => {
  buffer += chunk.toString();
  let boundary;
  while ((boundary = buffer.indexOf('\0')) >= 0) {
    const message = JSON.parse(buffer.slice(0, boundary)); buffer = buffer.slice(boundary + 1);
    if (message.id && pending.has(message.id)) {
      const task = pending.get(message.id); pending.delete(message.id); clearTimeout(task.timer);
      if (message.error) task.reject(new Error(message.error.message)); else task.resolve(message.result);
    }
    if (message.method === 'Runtime.exceptionThrown') errors.push(message.params.exceptionDetails.text);
    if (message.method === 'Network.requestWillBeSent' && /\/api\//.test(message.params.request.url)) apiRequests.push(message.params.request.url);
  }
});
function send(method, params = {}, sessionId) {
  return new Promise((resolve, reject) => {
    const id = ++nextId;
    const timer = setTimeout(() => { pending.delete(id); reject(new Error(`Browser timed out: ${method}`)); }, 20000);
    pending.set(id, { resolve, reject, timer });
    browser.stdio[3].write(`${JSON.stringify({ id, method, params, ...(sessionId ? { sessionId } : {}) })}\0`);
  });
}

(async () => {
  try {
    const { targetId } = await send('Target.createTarget', { url: 'about:blank' });
    const { sessionId } = await send('Target.attachToTarget', { targetId, flatten: true });
    const command = (method, params = {}) => send(method, params, sessionId);
    const evaluate = async expression => {
      const result = await command('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
      if (result.exceptionDetails) throw new Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text);
      return result.result.value;
    };
    await command('Page.enable'); await command('Runtime.enable'); await command('Network.enable');
    await command('Network.setBlockedURLs', { urls: ['http://*', 'https://*'] });
    await command('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1100, deviceScaleFactor: 1, mobile: false });
    await command('Page.navigate', { url: pathToFileURL(path.resolve(__dirname, '../frontend/bank/savings-preview.html')).href });
    await evaluate(`new Promise((resolve, reject) => {
      const deadline = Date.now() + 10000;
      const check = () => document.querySelector('.savings-view') ? resolve(true) : Date.now() > deadline ? reject(new Error('Preview did not render')) : setTimeout(check, 50);
      check();
    })`);
    const screenshot = async name => {
      await evaluate('window.scrollTo(0, 0)');
      const { cssContentSize } = await command('Page.getLayoutMetrics');
      const { data } = await command('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true,
        clip: { x: 0, y: 0, width: cssContentSize.width, height: cssContentSize.height, scale: 1 } });
      fs.writeFileSync(path.join(artifacts, name), Buffer.from(data, 'base64'));
    };
    await screenshot('desktop.png');
    const results = await evaluate(`(() => {
      const passed = [];
      const check = (condition, label) => { if (!condition) throw new Error(label); passed.push(label); };
      const click = action => document.querySelector('[data-savings="' + action + '"]').click();
      const close = () => document.querySelector('#savings-dialog').close();
      const fill = (name, value) => { document.querySelector('#savings-dialog [name="' + name + '"]').value = value; };
      const submit = () => document.querySelector('#savings-dialog form').requestSubmit();
      const text = () => document.querySelector('.savings-view').textContent;

      check(document.querySelector('.sv-total-amount').textContent.includes('23,500'),'Personal total in pink card');
      check(!text().includes('UI concept') && !document.querySelector('.sv-balances'),'Concept banner and balance breakdown removed');
      check(!document.querySelector('.topbar').textContent.includes('Demo preview'),'Preview topbar label removed');
      check(document.querySelectorAll('.sv-badge').length===4,'Four collectible badges restored');
      click('badge'); check(document.querySelector('#savings-dialog').textContent.includes('Save your first'),'Badge detail dialog'); close();
      click('summary-groups'); check(document.querySelector('.sv-total-amount').textContent.includes('10,700'),'Group total counts own contributions only');
      click('circles'); click('circle-add'); fill('amount','500'); submit(); check(document.querySelector('.sv-total-amount').textContent.includes('11,200'),'Group total updates on allocation');
      click('circle-release'); fill('amount','500'); submit(); check(document.querySelector('.sv-total-amount').textContent.includes('10,700'),'Group total updates on release');
      click('personal'); click('summary-personal'); click('add'); fill('amount','500'); submit(); check(document.querySelector('.sv-total-amount').textContent.includes('24,000'),'Personal total updates');
      click('reset'); click('new'); fill('name','<img src=x onerror=alert(1)>'); fill('deadline','2099-12-15'); submit();
      fill('initial','15001'); submit(); check(!document.querySelector('.sv-form-error').hidden,'Insufficient sample funds rejected');
      fill('initial','1000'); submit(); submit(); check(text().includes('24,500') && !document.querySelector('.sv-goals img'),'Three-step creation with escaped name');
      click('split'); fill('budget','100'); submit(); check(!document.querySelector('.sv-form-error').hidden,'Over-budget split rejected'); close();
      click('reset'); check(document.querySelectorAll('.sv-goals article').length===4,'Reset restores goals');
      return passed;
    })()`);
    for (const result of results) console.log(`PASS ${result}`);
    await command('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true });
    assert.equal(await evaluate('document.documentElement.scrollWidth <= innerWidth'), true, 'No mobile horizontal overflow');
    await screenshot('mobile.png');
    console.log('PASS Mobile layout has no horizontal overflow');
    await command('Emulation.setDeviceMetricsOverride', { width: 320, height: 700, deviceScaleFactor: 1, mobile: true });
    assert.equal(await evaluate('document.documentElement.scrollWidth <= innerWidth'), true, 'No overflow at 320px');
    await evaluate(`document.querySelector('[data-savings="new"]').click()`);
    assert.equal(await evaluate(`document.querySelector('#savings-dialog').getBoundingClientRect().width <= innerWidth`), true, 'Dialog fits narrow screen');
    await command('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Escape', code: 'Escape', windowsVirtualKeyCode: 27 });
    await command('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Escape', code: 'Escape', windowsVirtualKeyCode: 27 });
    assert.equal(await evaluate(`document.querySelector('#savings-dialog').open`), false, 'Escape dismisses dialog');
    console.log('PASS Narrow layout and keyboard dialog dismissal');
    assert.deepEqual(apiRequests, [], 'Standalone preview makes no API requests');
    console.log('PASS Standalone preview makes no API calls');

    // Load the real bank shell with local assets, then provide an in-memory test
    // session. This exercises navigation without credentials or a backend.
    await command('Page.navigate', { url: 'about:blank' });
    const bankDirectory = path.resolve(__dirname, '../frontend/bank');
    const bankHtml = fs.readFileSync(path.join(bankDirectory, 'index.html'), 'utf8')
      .replaceAll('/bank/', pathToFileURL(bankDirectory).href + '/');
    // Use a file-origin page so the browser can load the bank's local scripts.
    await command('Page.navigate', { url: pathToFileURL(path.join(bankDirectory, 'savings-preview.html')).href });
    const bankFrame = await command('Page.getFrameTree');
    await command('Page.setDocumentContent', { frameId: bankFrame.frameTree.frame.id, html: bankHtml });
    await evaluate(`new Promise((resolve, reject) => {
      const deadline = Date.now() + 10000;
      const check = () => typeof renderShell === 'function' && typeof state !== 'undefined' && window.PayPinkSavingsLive ? resolve(true) : Date.now() > deadline ? reject(new Error('Bank shell did not load')) : setTimeout(check, 50);
      check();
    })`);
    const liveResults = await evaluate(fs.readFileSync(path.join(__dirname, 'savings-live-checks.js'), 'utf8'));
    for (const result of liveResults) console.log(`PASS ${result}`);
    assert.equal(await evaluate(`document.querySelector('#breadcrumb-page').textContent`), 'Savings Hub', 'Savings Hub navigation updates breadcrumb');
    assert.equal(await evaluate(`document.querySelector('.nav-link.active').dataset.page`), 'savings', 'Savings navigation is active');
    assert.equal(await evaluate(`document.querySelector('[data-page="loans"]').nextElementSibling.dataset.page`), 'savings', 'Savings follows Loans');
    assert.equal(await evaluate(`document.documentElement.scrollWidth <= innerWidth`), true, 'Bank shell fits narrow viewport');
    assert.equal(await evaluate(`Array.from(document.querySelectorAll('.sidebar .nav-link, .sidebar .logout')).every(node => { const rect = node.getBoundingClientRect(); return rect.left >= 0 && rect.right <= innerWidth && rect.width > 0; })`), true, 'All navigation destinations remain on screen');
    await screenshot('bank-live-mobile.png');
    await evaluate(`document.querySelector('[data-sv="badge"]').click(); logout();`);
    assert.equal(await evaluate(`document.querySelector('#savings-live-dialog').open`), false, 'Logout closes savings dialog');
    assert.equal(await evaluate(`document.querySelector('#savings-live') === null`), true, 'Logout removes customer savings');
    console.log('PASS Bank navigation, narrow shell, API actions, and logout cleanup');
    // The real shell retains its existing notification polling. The no-API
    // assertion above covers the isolated Savings entry point specifically.
    assert.deepEqual(errors, [], 'No browser script errors');
    console.log('PASS No runtime errors in preview or banking shell');
    console.log(`Screenshots: ${artifacts}`);
  } finally {
    await send('Browser.close').catch(() => browser.kill());
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
