import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';

const source=readFileSync(new URL('../frontend/src/js/app.js',import.meta.url),'utf8');
const handler=source.slice(source.indexOf('async function triggerScheduledReconciliation()'),source.indexOf('let latestReconciliationLogs'));
function app(fetch){
  const alerts=[],calls=[];
  const context=vm.createContext({API_BASE:'/api/v1',fetch,getAuthHeaders:()=>({Authorization:'Bearer test'}),
    alert:value=>alerts.push(value),loadReconciliationLogs:async()=>calls.push('logs'),
    syncBackendTransactions:async()=>{},renderTransactionMonitor:()=>{}});
  vm.runInContext(handler,context);
  return {run:()=>context.triggerScheduledReconciliation(),alerts,calls};
}
test('manual reconciliation shows actual recovery and remaining drift counts',async()=>{
  const message='Checked 50 transactions: 10 missing audit records recovered, 45 matched, 5 still drifted.';
  const a=app(async()=>({ok:true,json:async()=>({message})}));await a.run();
  assert.deepEqual(a.alerts,[message]);assert.deepEqual(a.calls,['logs']);
});
test('failed request does not claim matched or automatically retry the mutation',async()=>{
  let requests=0;const a=app(async()=>{requests++;return {ok:false};});await a.run();
  assert.equal(requests,1);assert.match(a.alerts[0],/did not complete/);assert.doesNotMatch(a.alerts[0],/matched/);
});
test('network failure cannot produce a success message',async()=>{
  const a=app(async()=>{throw Error('Network unavailable');});await a.run();
  assert.deepEqual(a.alerts,['Network unavailable']);assert.deepEqual(a.calls,[]);
});
