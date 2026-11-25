/*
 * QR login for Industrial Kiosk Browser – sign in to a web app by scanning an encrypted QR code and entering a PIN.
 *
 * Add it as an injection (see docs/qr-login.md):
 *   "injections": [ { "match": { "url": "https://login.example.com/" }, "when": "pageLoaded", "js": "res:qr-login.js" } ]
 *
 * The QR code holds user name and password, encrypted with AES-256-GCM. The key is derived from the PIN with
 * PBKDF2-SHA256 (iterations stored in the code). Codes are made with the generator at
 * https://ict-baden.de/werkzeuge/qr-login/ (runs in the browser only, nothing is sent).
 *
 * Format: "IKBL1." + base64url( version 0x01 | iterations uint32 big-endian | salt 16 bytes | iv 12 bytes |
 *         ciphertext with 16-byte GCM tag ), additional data "IKBL1"; plain text is UTF-8 JSON
 *         { "u": user, "p": password, "x": expiry in Unix seconds or 0 }.
 *
 * Needs a secure context for WebCrypto: the login page must be https (or http://localhost). Without it the script
 * says so and does nothing else.
 *
 * Settings: define window.IKB_QR_LOGIN before this script (an earlier injection), all fields optional:
 *   { user: CSS selector, password: CSS selector, submit: true|false, maxTries: 5, lockMinutes: 15 }
 *
 * Licence: MIT, © ICT Baden GmbH. Use and adapt freely.
 */
(() => {
  'use strict';
  if (window.__ikbQrLogin) return; // injected twice (frames, reload of the injection)
  window.__ikbQrLogin = true;

  const PREFIX = 'IKBL1.';
  const AAD = new TextEncoder().encode('IKBL1');
  const SET = Object.assign({
    user: 'input[autocomplete="username"], input[name="username"], input[name="user"], input[name="login"], input[name="sap-user"], input[type="email"], input#username, input#user',
    password: 'input[type="password"]',
    submit: true,
    maxTries: 5,
    lockMinutes: 15,
  }, window.IKB_QR_LOGIN || {});
  const LOCK_KEY = 'ikb-qr-login-lock';
  const de = (navigator.language || '').toLowerCase().startsWith('de');
  const T = de ? {
    title: 'Anmelden mit QR-Code', pin: 'PIN', ok: 'Anmelden', cancel: 'Abbrechen', busy: 'Prüfe …',
    wrong: (n, m) => `Falsche PIN (${n} von ${m}).`, locked: (min) => `Zu viele falsche PINs. Wieder möglich in ${min} Minuten.`,
    expired: 'Dieser QR-Code ist abgelaufen. Bitte einen neuen erzeugen.', broken: 'Kein gültiger Login-QR-Code.',
    insecure: 'QR-Login braucht eine https-Seite.', noField: 'Kein Anmeldeformular auf dieser Seite gefunden.',
  } : {
    title: 'Sign in with QR code', pin: 'PIN', ok: 'Sign in', cancel: 'Cancel', busy: 'Checking …',
    wrong: (n, m) => `Wrong PIN (${n} of ${m}).`, locked: (min) => `Too many wrong PINs. Try again in ${min} minutes.`,
    expired: 'This QR code has expired. Please create a new one.', broken: 'Not a valid login QR code.',
    insecure: 'QR login needs an https page.', noField: 'No sign-in form found on this page.',
  };

  // ---------------------------------------------------------------- Format
  function fromBase64Url(text) {
    const b64 = text.replace(/-/g, '+').replace(/_/g, '/') + '==='.slice((text.length + 3) % 4);
    const bin = atob(b64);
    const out = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
    return out;
  }

  function parse(code) {
    const raw = fromBase64Url(code.slice(PREFIX.length).trim());
    if (raw.length < 1 + 4 + 16 + 12 + 17 || raw[0] !== 1) throw new Error('format');
    const iterations = new DataView(raw.buffer).getUint32(1);
    if (iterations < 100000 || iterations > 5000000) throw new Error('format');
    return { iterations, salt: raw.slice(5, 21), iv: raw.slice(21, 33), data: raw.slice(33) };
  }

  async function decrypt(code, pin) {
    const c = parse(code);
    const base = await crypto.subtle.importKey('raw', new TextEncoder().encode(pin), 'PBKDF2', false, ['deriveKey']);
    const key = await crypto.subtle.deriveKey(
      { name: 'PBKDF2', hash: 'SHA-256', salt: c.salt, iterations: c.iterations },
      base, { name: 'AES-GCM', length: 256 }, false, ['decrypt']);
    let plain;
    try {
      plain = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: c.iv, additionalData: AAD, tagLength: 128 }, key, c.data);
    } catch {
      return null; // wrong PIN (or a changed code – GCM cannot tell the two apart)
    }
    return JSON.parse(new TextDecoder().decode(plain));
  }

  // ---------------------------------------------------------------- Attempts (a speed bump on the device; the real
  // limit is the PIN length, see docs/qr-login.md)
  function lockState() {
    try { return JSON.parse(localStorage.getItem(LOCK_KEY)) || { fails: 0, until: 0 }; } catch { return { fails: 0, until: 0 }; }
  }
  function saveLock(state) { try { localStorage.setItem(LOCK_KEY, JSON.stringify(state)); } catch { /* private mode */ } }

  // ---------------------------------------------------------------- Form
  function setValue(input, value) {
    // The native setter, so React, Vue & co. notice the change
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
    setter.call(input, value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new Event('change', { bubbles: true }));
  }

  function fill(login) {
    const pass = document.querySelector(SET.password);
    const user = document.querySelector(SET.user) ||
      (pass && pass.form && [...pass.form.querySelectorAll('input')].find((i) => i !== pass && /^(text|email|tel)$/.test(i.type)));
    if (!user || !pass) return false;
    setValue(user, login.u);
    setValue(pass, login.p);
    if (SET.submit && pass.form) {
      // Like pressing the sign-in button: the page's own submit handlers run
      const button = pass.form.querySelector('[type=submit], button:not([type])');
      if (button) button.click();
      else if (pass.form.requestSubmit) pass.form.requestSubmit();
      else pass.form.submit();
    }
    return true;
  }

  // ---------------------------------------------------------------- Dialog (shadow DOM: the page's CSS does not reach it)
  function dialog(code) {
    const host = document.createElement('div');
    host.style.cssText = 'position:fixed;inset:0;z-index:2147483647';
    const root = host.attachShadow({ mode: 'closed' });
    root.innerHTML = `
      <style>
        .bg { position: fixed; inset: 0; box-sizing: border-box; padding: 16px; background: rgb(0 0 0 / .55); display: grid; place-items: center; font: 18px/1.4 system-ui, sans-serif; }
        .box { box-sizing: border-box; width: 100%; max-width: 380px; margin: 0; background: #fff; color: #111; border-radius: 16px; padding: 22px; box-shadow: 0 20px 60px rgb(0 0 0 / .4); }
        h2 { margin: 0 0 14px; font-size: 20px; }
        input { box-sizing: border-box; width: 100%; font-size: 26px; letter-spacing: .25em; padding: 12px; border: 2px solid #888; border-radius: 10px; }
        input:focus { outline: none; border-color: #111; }
        .msg { min-height: 1.4em; margin: 10px 0 0; color: #b00020; font-size: 16px; }
        .row { display: flex; gap: 10px; margin-top: 16px; }
        button { flex: 1; font-size: 18px; padding: 14px; border-radius: 10px; border: 0; }
        .ok { background: #111; color: #fff; } .cancel { background: #e7e5df; color: #111; }
      </style>
      <div class="bg"><form class="box" autocomplete="off">
        <h2>${T.title}</h2>
        <label><span style="display:block;margin-bottom:6px">${T.pin}</span>
          <input type="password" inputmode="numeric" autocomplete="off" required></label>
        <p class="msg" role="alert"></p>
        <div class="row"><button type="button" class="cancel">${T.cancel}</button><button class="ok">${T.ok}</button></div>
      </form></div>`;
    const form = root.querySelector('form');
    const input = root.querySelector('input');
    const msg = root.querySelector('.msg');
    const ok = root.querySelector('.ok');
    const close = () => host.remove();
    root.querySelector('.cancel').onclick = close;
    form.onsubmit = async (e) => {
      e.preventDefault();
      const lock = lockState();
      if (lock.until > Date.now()) { msg.textContent = T.locked(Math.ceil((lock.until - Date.now()) / 60000)); return; }
      ok.disabled = true; msg.textContent = T.busy;
      let login;
      try { login = await decrypt(code, input.value); } catch { msg.textContent = T.broken; ok.disabled = false; return; }
      input.value = '';
      if (!login) {
        lock.fails += 1;
        if (lock.fails >= SET.maxTries) { lock.until = Date.now() + SET.lockMinutes * 60000; lock.fails = 0; msg.textContent = T.locked(SET.lockMinutes); }
        else msg.textContent = T.wrong(lock.fails, SET.maxTries);
        saveLock(lock); ok.disabled = false; input.focus(); return;
      }
      saveLock({ fails: 0, until: 0 });
      if (login.x && login.x * 1000 < Date.now()) { msg.textContent = T.expired; ok.disabled = false; return; }
      close();
      if (!fill(login)) alert(T.noField);
      login.p = login.u = '';
    };
    document.documentElement.appendChild(host);
    input.focus();
  }

  function notice(text) {
    if (window.IndustrialKioskBrowser && IndustrialKioskBrowser.toast) IndustrialKioskBrowser.toast(text);
    else alert(text);
  }

  if (SET.test) window.__ikbQrLoginTest = { parse, decrypt }; // only for tools/qr-login/test.mjs

  // ---------------------------------------------------------------- Scan
  window.addEventListener('industrialkioskbrowser:scan', (event) => {
    const data = event.detail && event.detail.data;
    if (typeof data !== 'string' || !data.startsWith(PREFIX)) return;
    event.preventDefault(); // never type the code into a field
    if (!window.isSecureContext || !window.crypto || !crypto.subtle) { notice(T.insecure); return; }
    try { parse(data); } catch { notice(T.broken); return; }
    dialog(data);
  });
})();
