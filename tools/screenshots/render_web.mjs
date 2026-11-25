// Renders the staged web pages like the app's WebView: 360x704 dp at xxhdpi (1080x2112 px).
// Usage: npx -y -p playwright@1.55.0 node tools/screenshots/render_web.mjs build/docs-web
// (first run: npx -y playwright@1.55.0 install chromium)
import { chromium } from 'playwright';
import { pathToFileURL } from 'node:url';
import path from 'node:path';

const dir = path.resolve(process.argv[2] ?? 'build/docs-web');
const browser = await chromium.launch();
const context = await browser.newContext({
  viewport: { width: 360, height: 704 },
  deviceScaleFactor: 3,
  isMobile: true,
  hasTouch: true,
  colorScheme: 'light',
});
// QR login: scan the test code, type the PIN; "filled" also confirms it and waits for the decrypted sign-in data.
const steps = {
  'qr-login-pin': async (page) => { await page.evaluate(() => window.qrLoginScan()); await page.waitForTimeout(300); await page.keyboard.type('482913'); },
  'qr-login-filled': async (page) => {
    await page.evaluate(() => window.qrLoginScan()); await page.waitForTimeout(300);
    await page.keyboard.type('482913'); await page.keyboard.press('Enter');
    await page.waitForFunction(() => document.querySelector('[name=password]').value !== '');
    await page.focus('button');
  },
};
for (const name of ['demo', 'status', 'qr-login-pin', 'qr-login-filled']) {
  const page = await context.newPage();
  await page.goto(pathToFileURL(path.join(dir, `${name}.html`)).href);
  await page.waitForTimeout(300);
  if (steps[name]) await steps[name](page);
  await page.waitForTimeout(300);
  await page.screenshot({ path: path.join(dir, `${name}.png`) });
  console.log(`${name}.png`);
}
await browser.close();
