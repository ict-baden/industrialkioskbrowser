// Copy of qr-login-core.js from ict-baden/website (public/js/), the generator on ict-baden.de/werkzeuge/qr-login/.
// Keep both identical; tools/qr-login/test.mjs checks the format against examples/qr-login/qr-login.js.
// QR-Login für Industrial Kiosk Browser: verschlüsselt Benutzername und Passwort mit einer PIN (läuft nur im Browser).
// Format wie im Skript examples/qr-login/qr-login.js des Repos ict-baden/industrialkioskbrowser:
//   "IKBL1." + base64url( 0x01 | Iterationen uint32 big-endian | Salt 16 Byte | IV 12 Byte | Chiffretext mit GCM-Tag )
//   AES-256-GCM, Schlüssel per PBKDF2-SHA256 aus der PIN, zusätzliche Daten "IKBL1",
//   Klartext JSON { "u": Benutzer, "p": Passwort, "x": Ablauf in Unix-Sekunden oder 0 }.

export const PREFIX = 'IKBL1.';
export const ITERATIONS = 600000; // OWASP-Empfehlung für PBKDF2-SHA256 (2023)
const AAD = new TextEncoder().encode('IKBL1');

function toBase64Url(bytes) {
  let bin = '';
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/** Verschlüsselt { user, password, expires (Date oder null) } mit der PIN; Ergebnis ist der Text für den QR-Code. */
export async function encrypt({ user, password, expires }, pin, iterations = ITERATIONS) {
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const base = await crypto.subtle.importKey('raw', new TextEncoder().encode(pin), 'PBKDF2', false, ['deriveKey']);
  const key = await crypto.subtle.deriveKey(
    { name: 'PBKDF2', hash: 'SHA-256', salt, iterations },
    base, { name: 'AES-GCM', length: 256 }, false, ['encrypt']);
  const plain = new TextEncoder().encode(JSON.stringify({ u: user, p: password, x: expires ? Math.floor(expires.getTime() / 1000) : 0 }));
  const data = new Uint8Array(await crypto.subtle.encrypt({ name: 'AES-GCM', iv, additionalData: AAD, tagLength: 128 }, key, plain));
  const out = new Uint8Array(1 + 4 + 16 + 12 + data.length);
  out[0] = 1;
  new DataView(out.buffer).setUint32(1, iterations);
  out.set(salt, 5);
  out.set(iv, 21);
  out.set(data, 33);
  return PREFIX + toBase64Url(out);
}
