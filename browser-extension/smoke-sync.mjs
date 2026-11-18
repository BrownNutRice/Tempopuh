// Smoke test for sync error taxonomy + backoff helpers (pure functions).
import * as esbuild from 'esbuild';
import { mkdtempSync } from 'fs';
import { webcrypto } from 'node:crypto';
import { tmpdir } from 'os';
import { join } from 'path';
import { pathToFileURL } from 'url';

const dir = mkdtempSync(join(tmpdir(), 'tempo-sync-'));
const out = join(dir, 'sync.mjs');

await esbuild.build({
  entryPoints: ['src/background/sync.ts'],
  bundle: true,
  format: 'esm',
  platform: 'node',
  target: 'node18',
  outfile: out,
  logLevel: 'silent',
  plugins: [{
    name: 'mock-sync-storage',
    setup(build) {
      build.onResolve({ filter: /^\.\/storage$/ }, () => ({ path: 'storage', namespace: 'smoke' }));
      build.onLoad({ filter: /.*/, namespace: 'smoke' }, () => ({
        contents: [
          'getSettings', 'getPairing', 'retryFailedPlays', 'getQueuedPlays', 'saveSyncBackoff',
          'recordSync', 'recordConnectionSuccess', 'recordConnectionFailure', 'getConnectionHistory',
          'recordConnectionFailures', 'savePairing', 'getSyncCheckpoint', 'clearSyncCheckpoint',
          'saveSyncCheckpoint', 'markPlaysSynced', 'markPlaysFailed', 'cleanupOldRecords', 'enforceMaxRecords',
        ].map(name => `export const ${name} = (...args) => globalThis.tempoSmokeStorage.${name}(...args);`).join('\n'),
      }));
    },
  }],
});

const mod = await import(pathToFileURL(out).href);
const { isTransientSyncError, backoffDelayMinutes, SyncError } = mod;

let pass = 0, fail = 0;
function check(name, cond, detail = '') {
  if (cond) { pass++; console.log(`  ✓ ${name}`); }
  else { fail++; console.log(`  ✗ ${name}${detail ? ' — ' + detail : ''}`); }
}

console.log('\n[1] Error taxonomy: transient vs fatal');
{
  check('rate_limited is transient', isTransientSyncError('rate_limited'));
  check('server is transient', isTransientSyncError('server'));
  check('network is transient', isTransientSyncError('network'));
  check('unreachable is transient', isTransientSyncError('unreachable'));
  check('battery is transient', isTransientSyncError('battery'));
  check('auth is NOT transient', !isTransientSyncError('auth'));
  check('rejected is NOT transient', !isTransientSyncError('rejected'));
  check('unknown is NOT transient', !isTransientSyncError('unknown'));
}

console.log('\n[2] Exponential backoff: 0.5,1,2,4,8,… capped at 60');
{
  check('1 failure → 0.5 min', backoffDelayMinutes(1) === 0.5, `got ${backoffDelayMinutes(1)}`);
  check('2 failures → 1 min', backoffDelayMinutes(2) === 1, `got ${backoffDelayMinutes(2)}`);
  check('3 failures → 2 min', backoffDelayMinutes(3) === 2, `got ${backoffDelayMinutes(3)}`);
  check('4 failures → 4 min', backoffDelayMinutes(4) === 4, `got ${backoffDelayMinutes(4)}`);
  check('5 failures → 8 min', backoffDelayMinutes(5) === 8, `got ${backoffDelayMinutes(5)}`);
  check('10 failures → capped at 60', backoffDelayMinutes(10) === 60, `got ${backoffDelayMinutes(10)}`);
  check('0 failures → 0.5 min floor', backoffDelayMinutes(0) === 0.5, `got ${backoffDelayMinutes(0)}`);
}

console.log('\n[3] SyncError carries kind, status, retryAfter');
{
  const e = new SyncError('boom', 'rate_limited', 429, 30_000);
  check('kind preserved', e.kind === 'rate_limited');
  check('httpStatus preserved', e.httpStatus === 429);
  check('retryAfterMs preserved', e.retryAfterMs === 30_000);
  check('is an Error', e instanceof Error);
  const auth = new SyncError('denied', 'auth', 401);
  check('auth error has no retryAfter', auth.retryAfterMs === undefined);
}

console.log('\n[4] Permission blocks delivery without changing queued records');
{
  Object.defineProperty(globalThis, 'navigator', { configurable: true, value: { userAgent: 'Chrome' } });
  Object.defineProperty(globalThis, 'crypto', { configurable: true, value: webcrypto });

  let pairing = { phoneIp: 'tempo-paired.local', phonePort: 8765, authToken: 'fixture' };
  const history = [
    { ip: pairing.phoneIp, port: 8765, successCount: 10, lastSeen: 3 },
    { ip: pairing.phoneIp, port: 9877, successCount: 9, lastSeen: 2 },
    { ip: pairing.phoneIp, port: 9876, successCount: 8, lastSeen: 1 },
  ];
  const plays = [{ id: 11, title: 'First', status: 'queued' }, { id: 12, title: 'Second', status: 'queued' }];
  const syncResults = [];
  const successes = [];
  const requests = [];
  const alarms = [];
  let grant = false;
  let revokeAtBattery = false;
  let livePort = 8765;

  globalThis.chrome = {
    permissions: { contains: async () => grant },
    alarms: { clear: async name => { alarms.push(['clear', name]); }, create: (...args) => alarms.push(args) },
  };
  globalThis.tempoSmokeStorage = {
    getSettings: async () => ({ offlineMode: false }),
    getPairing: async () => pairing,
    getQueuedPlays: async () => plays.filter(play => play.status === 'queued'),
    retryFailedPlays: async () => 0,
    saveSyncBackoff: async () => {},
    recordSync: async (...result) => syncResults.push(result),
    recordConnectionSuccess: async (ip, port) => successes.push({ ip, port }),
    recordConnectionFailure: async () => {},
    recordConnectionFailures: async () => {},
    getConnectionHistory: async () => history,
    savePairing: async value => { pairing = value; },
    getSyncCheckpoint: async () => null,
    clearSyncCheckpoint: async () => {},
    saveSyncCheckpoint: async () => {},
    markPlaysSynced: async ids => plays.filter(play => ids.includes(play.id)).forEach(play => { play.status = 'synced'; }),
    markPlaysFailed: async ids => plays.filter(play => ids.includes(play.id)).forEach(play => { play.status = 'failed'; }),
    cleanupOldRecords: async () => {},
    enforceMaxRecords: async () => {},
  };
  globalThis.fetch = async url => {
    requests.push(url);
    const endpoint = new URL(url);
    if (endpoint.hostname !== pairing.phoneIp || Number(endpoint.port) !== livePort) {
      return new Response(null, { status: 503 });
    }
    if (endpoint.pathname === '/api/battery' && revokeAtBattery) grant = false;
    return Response.json({ ok: true });
  };

  const sync = async options => {
    try { return await mod.syncToPhone(options); }
    catch (error) { return error.kind; }
  };
  check('missing grant is reported separately', await sync() === 'permission_required');
  check('no phone traffic or retry alarm while blocked', requests.length === 0 && alarms.every(([action]) => action === 'clear'));
  check('queued IDs and statuses survive', plays.map(play => `${play.id}:${play.status}`).join() === '11:queued,12:queued');
  check('next automatic sync avoids redundant failure work', await sync() === 0 && syncResults.length === 1);

  grant = true;
  revokeAtBattery = true;
  check('revoking after discovery prevents transmission', await sync() === 'permission_required');
  check('nothing is posted after revocation', !requests.some(url => url.endsWith('/api/plays')));
  check('both records survive mid-sync revocation', plays.every(play => play.status === 'queued'));

  console.log('\n[5] Permission restoration and history recovery');
  revokeAtBattery = false;
  grant = true;
  livePort = 9876;
  check('grant lets ordinary sync drain both records', await sync() === 2 && plays.every(play => play.status === 'synced'));
  check('same host at a different port is probed', requests.includes('http://tempo-paired.local:9877/api/ping'));
  check('successful port reaches delivery URL', requests.includes('http://tempo-paired.local:9876/api/plays'));
  check('successful endpoint is persisted and recorded',
    pairing.phonePort === 9876 && successes.some(endpoint => endpoint.ip === pairing.phoneIp && endpoint.port === 9876));
}

console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail > 0 ? 1 : 0);
