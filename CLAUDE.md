# CLAUDE.md – Industrial Kiosk Browser

Industrial Kiosk Browser: Kiosk-Browser (Kotlin, WebView) für robuste Android-Geräte von Zebra und Honeywell – Web-Apps mit Barcode-Scan, Kiosk-Modus und zentraler Konfiguration per MDM.

Projekt von ICT-Baden. Es gelten die Richtlinien in [`ict-baden/richtlinien`](https://github.com/ict-baden/richtlinien). Stand und Zuständigkeit stehen in `ict-baden/ict-baden-projekte` (`projekte.json`, `in-arbeit.md`).

## Bauen und testen

```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

JDK 17–21, Android SDK mit Compile-SDK 37.

## Aufbau

- `app/src/main/java/de/ictbaden/industrialkioskbrowser/`
  - `config/` – Profilmodell, JSON-Format, Speicher, Passwort-Hash
  - `web/` – BrowserActivity, URL-Regeln, Scan-Übergabe, JS-Bridge, Tastatur, Injektionen
  - `scanner/` – DataWedge, Honeywell, generischer Intent
  - `provisioning/` – Managed Configuration, Konfigurationsdateien, `INSTALL_CONFIG`
  - `ui/`, `admin/` – Startseite, Menü, Einstellungen, Branding
- `app/src/test/` – Unit-Tests (JUnit, Robolectric, Roborazzi für die Doku-Bilder)
- `docs/` – Doku auf Englisch, `examples/` – Konfigurationsvorlagen, von den Tests geprüft

## Besonderheiten

- Paketname `de.ictbaden.industrialkioskbrowser`, Debug `….debug` (Richtlinie 12). Frühere Namen: „EnterpriseBrowser“ (`de.ictbaden.enterprisebrowser`, bis 0.5.0, wegen Zebras gleichnamigem Produkt aufgegeben) und „Pforte“ (`de.ictbaden.pforte`, 0.6.x); seit 0.7.0 „Industrial Kiosk Browser“ passend zu Industrial Keyboard. Fehlercodes `IKB-…` (vorher `PFT-…`, `EB-…`, gleiche Nummern). In README, Doku und Store-Texten keine einzelnen Testgeräte nennen. Die Broadcast-Actions hängen am Paketnamen (`<paket>.action.BARCODE`, `<paket>.action.INSTALL_CONFIG`) und sind öffentliche API – nicht nebenbei ändern. Im Manifest steht deshalb `${applicationId}`.
- Release-APKs signiert nur die CI mit dem Schlüssel aus den Secrets `ANDROID_KEYSTORE` (Base64), `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`. `ANDROID_KEY_PASSWORD` ist optional (PKCS12: gleiches Passwort). Ohne Schlüssel baut und testet die CI nur. Den Schlüssel hat nur der Release-Verantwortliche von ICT-Baden, Claude bekommt ihn nie.
- `LICENSE` und `THIRD-PARTY-NOTICES.txt` werden beim Bauen als Assets in die APK kopiert. Neue oder geänderte Abhängigkeit (auch Dependabot) → `THIRD-PARTY-NOTICES.txt` ergänzen, lesbarer Teil und Liste „Components“; `.github/scripts/check-notices.sh` vergleicht die Liste mit `releaseRuntimeClasspath` und lässt die CI sonst scheitern. Dieselbe Liste wird zur SBOM (`.github/scripts/sbom.py`, `<Name>-<version>.cdx.json` im Release).
- Ohne Signier-Secrets legt ein Tag `v*` das Release trotzdem an (Text aus dem CHANGELOG, SBOM, Prüfsummen, oben der Hinweis „signed APK … will be added“) und macht es zu „Latest“. Nach dem Setzen der Secrets genügt `gh workflow run build.yml --ref <tag>` (ein Re-run des alten Laufs geht nur 30 Tage): APK und `.aab` kommen dazu, der Hinweis verschwindet, und der Lauf stößt einen Build von `main` an, der das rollierende „latest“ anlegt. Das gilt ab `v0.6.1`. `v0.6.0` nicht nachsignieren: Sein Workflow-Stand würde es wieder zu „Latest“ machen; 0.6.1 ersetzt es. Danach in `docs/limitations.md` (Abschnitt „Releases and updates“) den Satz zum fehlenden Schlüssel entfernen.
- Google Play: Store-Texte und -Grafiken in `fastlane/metadata/android/`, Anleitung und Console-Antworten in `docs/play-store.md`. Pipeline (Job `play` in `build.yml`, `play-rollout.yml`): Merge auf `main` → interner Test, Tag `vX.Y.Z-…` → geschlossener Test, Tag `vX.Y.Z` → Produktion gestaffelt. **Claude setzt nie Release-Tags (`v*`)** – der Tag ist die Veröffentlichungsentscheidung eines Menschen; Claude erhält auch nie den Play-Schlüssel (`PLAY_SERVICE_ACCOUNT_JSON`).
- `gradlew` muss in Git ausführbar sein (`git update-index --chmod=+x gradlew`), sonst scheitert die CI.

## Regeln

- Commit-Nachrichten auf Deutsch.
- Kein Text fest im Code: jeden neuen Text in alle Sprachdateien eintragen (`values`, `values-de`, `values-es`, `values-fr`, `values-zh-rCN`), Fachbegriffe aus `ict-baden/richtlinien/sprachen/glossar.csv`. Unsichere Übersetzung: englischen Text eintragen, mit `TODO-übersetzen` markieren. Regeln in Richtlinie 13. Fehlermeldungen bekommen einen Code aus `web/ErrorCodes.kt` (nie eine Nummer wiederverwenden, `docs/error-codes.md` nachziehen).
- Nach Arbeit an diesem Projekt: Logbuch in `ict-baden-projekte` ergänzen.
- Vor jedem Release: Abschnitt „Unreleased“ im `CHANGELOG.md` pflegen.
