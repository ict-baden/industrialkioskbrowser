// Round trip of the QR login format: encrypt like the generator on ict-baden.de, decrypt with examples/qr-login/qr-login.js.
//
//   node tools/qr-login/test.mjs [path to qr-login-core.js of the website]
//
// The website module is optional: without it the test uses the copy of encrypt() below, which must stay identical
// in format (docs/qr-login.md).
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { webcrypto } from 'node:crypto';
import vm from 'node:vm';
import { pathToFileURL } from 'node:url';

const corePath = process.argv[2];
const { encrypt, PREFIX } = corePath ? await import(pathToFileURL(corePath).href) : await import('./encrypt.mjs');

// The injection script in a minimal browser-like sandbox
const listeners = {};
const sandbox = {
  window: null, crypto: webcrypto, TextEncoder, TextDecoder, atob, btoa, DataView, Uint8Array, JSON, Object, Error, Date, Math,
  navigator: { language: 'de-DE' }, localStorage: { getItem: () => null, setItem: () => {} },
  addEventListener: (name, fn) => { listeners[name] = fn; },
};
sandbox.window = sandbox;
sandbox.IKB_QR_LOGIN = { test: true };
vm.createContext(sandbox);
vm.runInContext(readFileSync(new URL('../../examples/qr-login/qr-login.js', import.meta.url), 'utf8'), sandbox);
const { decrypt, parse } = sandbox.__ikbQrLoginTest;
assert.ok(listeners['industrialkioskbrowser:scan'], 'scan listener registered');

const iterations = 120000; // fast enough for a test; the generator uses 600000
const code = await encrypt({ user: 'lager.müller@example.com', password: 'Sommer2026!"\\ä', expires: null }, '482913', iterations);
assert.ok(code.startsWith(PREFIX));
assert.equal(parse(code).iterations, iterations);
const login = await decrypt(code, '482913');
assert.equal(login.u, 'lager.müller@example.com');
assert.equal(login.p, 'Sommer2026!"\\ä');
assert.equal(login.x, 0);
assert.equal(await decrypt(code, '482914'), null, 'wrong PIN');

// A changed character makes the code invalid (GCM tag), never a different login
const tampered = code.slice(0, -3) + (code.at(-3) === 'A' ? 'B' : 'A') + code.slice(-2);
assert.equal(await decrypt(tampered, '482913'), null);

const until = new Date('2027-01-31T23:59:59Z');
const withExpiry = await encrypt({ user: 'u', password: 'p', expires: until }, '0000', iterations);
assert.equal((await decrypt(withExpiry, '0000')).x, Math.floor(until.getTime() / 1000));

// The scan handler leaves other barcodes alone and takes login codes out of the page
let prevented = false;
listeners['industrialkioskbrowser:scan']({ detail: { data: '4006381333931' }, preventDefault: () => { prevented = true; } });
assert.equal(prevented, false);
// examples/qr-login.json carries the script as a resource: it must be the current one
const example = JSON.parse(readFileSync(new URL('../../examples/qr-login.json', import.meta.url), 'utf8'));
assert.equal(example.resources['qr-login.js'].text, readFileSync(new URL('../../examples/qr-login/qr-login.js', import.meta.url), 'utf8').replace(/\r\n/g, '\n'),
  'examples/qr-login.json is out of date: node tools/qr-login/make-example.mjs');

console.log('QR login: all checks passed (' + code.length + ' characters in the QR code)');
