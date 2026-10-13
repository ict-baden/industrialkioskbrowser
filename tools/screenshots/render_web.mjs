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
for (const name of ['demo', 'status']) {
  const page = await context.newPage();
  await page.goto(pathToFileURL(path.join(dir, `${name}.html`)).href);
  await page.waitForTimeout(300);
  await page.screenshot({ path: path.join(dir, `${name}.png`) });
  console.log(`${name}.png`);
}
await browser.close();
