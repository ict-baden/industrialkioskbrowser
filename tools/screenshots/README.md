# Regenerating the documentation screenshots

The images in `docs/images/` are rendered from the real UI code, without a device or an
emulator:

1. **App UI** – Robolectric renders the activities
   (`app/src/test/.../screenshots/DocScreenshots.kt`) into `app/build/screenshots`:
   ```bash
   ./gradlew recordRoborazziDebug -Pscreenshots
   ```
2. **Web content** – the bundled pages (demo, error page) are put into a realistic state
   and rendered with Chromium at WebView resolution:
   ```bash
   python tools/screenshots/stage_web.py . build/docs-web
   npx -y -p playwright@1.55.0 node tools/screenshots/render_web.mjs build/docs-web
   ```
3. **Composition** – status bar, device frame, hero banner, branding comparison:
   ```bash
   python tools/screenshots/compose.py . build/docs-web
   ```

Requirements: JDK 17–21, Python 3 with Pillow, Node.js (for Playwright), Roboto
(available on Windows, otherwise set `FONTS_DIR`).

Robolectric cannot render the WebView itself, so for the browser screens the Chromium
render of the page is placed into the content area of the rendered activity.
