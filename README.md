# EnterpriseBrowser

Kiosk-fähiger Enterprise-Browser für robuste Android-Geräte (Zebra, Honeywell
u. a.), funktional angelehnt an den Web-Teil von Ivanti Velocity – als
eigenständige Neuimplementierung in Kotlin.

- **Profile** mit Start-URL, Host-Allowlist, User-Agent, Textgröße, Kiosk-Optionen
- **Barcode-Scanner**: Zebra DataWedge, Honeywell, generischer Intent; Scan wird
  ins fokussierte Feld eingefügt (Keyboard-Wedge inkl. Tab/Enter) und als
  DOM-Event `enterprisebrowser:scan` gemeldet
- **Seitensteuerung** per `<meta http-equiv="scanner" …>` / `ScannerNavigate`
- **JavaScript-API** `window.EnterpriseBrowser` (Beep, Vibration, Softtrigger,
  Geräteinfo) – nur für erlaubte Hosts
- **Kiosk**: Toolbar/Systemleisten ausblenden, Exit verbergen bzw.
  Passwortschutz, Lock Task über Device Owner, Display an
- **Provisionierung**: Android Managed Configurations, Konfigurationsdatei,
  Broadcast-Intent (StageNow), QR-Code
- Admin-Bereich mit Passwort, Import/Export
- Strenge TLS-Behandlung, Client-Zertifikate über KeyChain, HTTP-Auth,
  Downloads über DownloadManager

Details: [Konfiguration & JS-API](docs/configuration.md) ·
[Analyse von Velocity 3.0](docs/velocity-analysis.md)

## Build

Voraussetzungen: JDK 17–21, Android SDK (Platform 36).

```bash
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Das mitgelieferte Profil „Demo & Scanner-Test“ zeigt eine lokale Testseite für
Scanner und JS-API. Scan ohne Hardware simulieren:

```bash
adb shell am broadcast -a io.github.pieewiee.enterprisebrowser.action.BARCODE \
  -p io.github.pieewiee.enterprisebrowser --es data 4006381333931
```

## Projektstruktur

```
app/src/main/java/io/github/pieewiee/enterprisebrowser/
  config/        Profil-Modell, JSON-Format, Speicherung, Passwort-Hashing
  web/           BrowserActivity, URL-Policy, Scan-Zustellung, JS-Bridge
  scanner/       DataWedge, Honeywell, generischer Intent
  provisioning/  Managed Configurations, Config-Dateien, INSTALL_CONFIG
  ui/, admin/    Profilliste, Einstellungen
app/src/main/assets/
  js/bridge.js   window.EnterpriseBrowser
  demo/          Test-/Demoseite
```

APK-Dateien und lokale Tools (`rea/`) sind per `.gitignore` ausgeschlossen.
