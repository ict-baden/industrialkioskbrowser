# CLAUDE.md – EnterpriseBrowser

EnterpriseBrowser: Kiosk-Browser (Kotlin, WebView) für robuste Android-Geräte von Zebra und Honeywell – Web-Apps mit Barcode-Scan, Kiosk-Modus und zentraler Konfiguration per MDM.

Projekt von ICT-Baden. Es gelten die Richtlinien in [`ict-baden/richtlinien`](https://github.com/ict-baden/richtlinien). Stand und Zuständigkeit stehen in `ict-baden/ict-baden-projekte` (`projekte.json`, `in-arbeit.md`).

## Bauen und testen

```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

JDK 17–21, Android SDK mit Compile-SDK 36.

## Aufbau

- `app/src/main/java/de/ictbaden/enterprisebrowser/`
  - `config/` – Profilmodell, JSON-Format, Speicher, Passwort-Hash
  - `web/` – BrowserActivity, URL-Regeln, Scan-Übergabe, JS-Bridge, Tastatur, Injektionen
  - `scanner/` – DataWedge, Honeywell, generischer Intent
  - `provisioning/` – Managed Configuration, Konfigurationsdateien, `INSTALL_CONFIG`
  - `ui/`, `admin/` – Startseite, Menü, Einstellungen, Branding
- `app/src/test/` – Unit-Tests (JUnit, Robolectric, Roborazzi für die Doku-Bilder)
- `docs/` – Doku auf Englisch, `examples/` – Konfigurationsvorlagen, von den Tests geprüft

## Besonderheiten

- Paketname `de.ictbaden.enterprisebrowser`, Debug `….debug` (Richtlinie 12). Die Broadcast-Actions hängen am Paketnamen (`<paket>.action.BARCODE`, `<paket>.action.INSTALL_CONFIG`) und sind öffentliche API – nicht nebenbei ändern. Im Manifest steht deshalb `${applicationId}`.
- Release-APKs signiert nur die CI mit dem Schlüssel aus den Secrets `ANDROID_KEYSTORE` (Base64), `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`. `ANDROID_KEY_PASSWORD` ist optional (PKCS12: gleiches Passwort). Ohne Schlüssel baut und testet die CI nur. Den Schlüssel hat nur Till, Claude bekommt ihn nie.
- `LICENSE` und `THIRD-PARTY-NOTICES.txt` werden beim Bauen als Assets in die APK kopiert. Neue Abhängigkeit → `THIRD-PARTY-NOTICES.txt` ergänzen; die CI gibt die Liste der Abhängigkeiten im Schritt „Abhängigkeiten der APK“ aus.
- `gradlew` muss in Git ausführbar sein (`git update-index --chmod=+x gradlew`), sonst scheitert die CI.

## Regeln

- Commit-Nachrichten auf Deutsch.
- Nach Arbeit an diesem Projekt: Logbuch in `ict-baden-projekte` ergänzen.
- Vor jedem Release: Abschnitt „Unreleased“ im `CHANGELOG.md` pflegen.
