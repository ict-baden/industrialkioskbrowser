// Writes examples/qr-login.json: a profile for a sign-in page with examples/qr-login/qr-login.js as a resource.
// Run after every change of the script:   node tools/qr-login/make-example.mjs
// tools/qr-login/test.mjs fails when the copy in the JSON differs from the script.
import { readFileSync, writeFileSync } from 'node:fs';

const script = readFileSync(new URL('../../examples/qr-login/qr-login.js', import.meta.url), 'utf8').replace(/\r\n/g, '\n');
const config = {
  profiles: [
    {
      id: 'login',
      name: 'Login with QR code',
      startUrl: 'https://login.example.com/',
      allowList: ['login.example.com'],
      launchOnStartup: true,
      scanner: { terminator: 'enter' },
      injections: [
        { match: { url: 'https://login.example.com/', type: 'prefix' }, when: 'pageLoaded', js: 'res:qr-login.js' },
      ],
    },
  ],
  resources: { 'qr-login.js': { text: script } },
};
writeFileSync(new URL('../../examples/qr-login.json', import.meta.url), JSON.stringify(config, null, 2) + '\n');
console.log('examples/qr-login.json written');
