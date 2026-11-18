import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const controls = {
  'setting-tracking': ['Enable Tracking', 'setting-tracking-help'],
  'setting-offline': ['Offline Mode', 'setting-offline-help'],
  'setting-sync-interval': ['Sync Interval', 'setting-sync-interval-help'],
  'setting-polling-interval': ['Polling Interval', 'setting-polling-interval-help'],
  'new-artist': ['Artist name', 'artist-help'],
  'new-channel': ['Channel name', 'channel-help'],
};

for (const directory of ['src/popup', 'dist/chrome/popup', 'dist/firefox/popup']) {
  const html = readFileSync(new URL(directory + '/popup.html', import.meta.url), 'utf8');
  const css = readFileSync(new URL(directory + '/popup.css', import.meta.url), 'utf8');
  for (const [id, [label, descriptionId]] of Object.entries(controls)) {
    const context = directory + ': ' + id;
    assert.match(html, new RegExp(`<label\\b[^>]*for="${id}"[^>]*>\\s*${label}\\s*</label>`), context);
    const field = html.match(new RegExp(`<(?:input|select)\\b[^>]*id="${id}"[^>]*>`))?.[0];
    assert.ok(field?.includes(`aria-describedby="${descriptionId}"`), context);
    assert.match(html, new RegExp(`id="${descriptionId}"[^>]*>\\s*[^<\\s]`), context);
  }
  assert.match(css, /\.toggle input:focus-visible\s*\+\s*\.toggle-slider\s*\{[^}]*outline\s*:/);
}
console.log('Popup labels, help text and toggle focus styles passed for source and both packages.');
