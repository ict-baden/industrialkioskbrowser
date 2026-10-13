# Doku-Screenshots neu erzeugen

Die Bilder in `docs/images/` werden aus dem echten UI-Code gerendert, ohne Gerät
oder Emulator:

1. **App-Oberfläche** – Robolectric rendert die Activities
   (`app/src/test/.../screenshots/DocScreenshots.kt`) nach `app/build/screenshots`:
   ```bash
   ./gradlew recordRoborazziDebug -Pscreenshots
   ```
2. **Webinhalt** – die mitgelieferten Seiten (Demo, Fehlerseite) werden in einen
   realistischen Zustand gebracht und mit Chromium in WebView-Auflösung gerendert:
   ```bash
   python tools/screenshots/stage_web.py . build/docs-web
   npx -y -p playwright@1.55.0 node tools/screenshots/render_web.mjs build/docs-web
   ```
3. **Zusammensetzen** – Statusleiste, Gerätrahmen, Hero-Banner, Branding-Vergleich:
   ```bash
   python tools/screenshots/compose.py . build/docs-web
   ```

Voraussetzungen: JDK 17–21, Python 3 mit Pillow, Node.js (für Playwright), Roboto
(unter Windows vorhanden, sonst `FONTS_DIR` setzen).

Die WebView selbst kann Robolectric nicht rendern; für die Browser-Ansichten wird
deshalb der Chromium-Render der Seite in den Inhaltsbereich der gerenderten
Activity eingesetzt.
