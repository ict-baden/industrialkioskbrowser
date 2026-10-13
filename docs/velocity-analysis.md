# Velocity 3.0 – Analyse (Referenz für EnterpriseBrowser)

Analysiert wurde `Velocity_ARM_3.0.0.26271.1e33d6f.apk` (SHA-256
`9be4e089809d83d81a459abd3620019ee0a3b50a1db310a7900ddaced60a8351`) mit
[REA](https://github.com/morluto/rea) und ILSpy. Dieses Dokument beschreibt
**beobachtetes Verhalten** in eigenen Worten. Es enthält keinen Ivanti-Code; der
EnterpriseBrowser ist eine eigenständige Neuimplementierung.

## Aufbau der App

- .NET for Android (Xamarin, .NET 10). Die Logik steckt in IL-Assemblies in
  `lib/<abi>/libassembly-store.so` (Store-Format `XABA` v3, LZ4/`XALZ`),
  der Java-DEX-Code ist nur Binding-Schicht.
- Kern-Assemblies: `VelocityAndroid` (UI, Scanner, Scripting), `ClientLibrary`
  (Sessions, Netzwerk, Lizenz, Konfiguration), `HostProfiles` (Profilmodell,
  Symbologien je Hersteller), `ScreenCompiler`, `Zebra` (EMDK-Bindings).
- Velocity ist primär ein Terminal-Emulations-Client (Telnet/5250/3270/VT,
  SSH) mit Web-Sessions als weiterem Session-Typ, dazu Sprachsteuerung
  (separate App „Speakeasy“), Ivanti-Neurons-Forms und Lizenzserver.
- Package `com.wavelink.velocity`, minSdk 21, targetSdk 36.

## App-Shell

- `SplashActivity` (Launcher) → Berechtigungen → optionaler Startup-Hook
  (`VELOCITY_STARTUP`, Abbruch per RESULT_CANCELED) → bindet
  `SessionService` → wartet auf Remote-Config → `MainActivity`.
- `SessionService` ist ein Foreground-Service (Typ `connectedDevice`) nur
  solange Sessions existieren, hält einen WLAN-Lock und besitzt alle Sessions.
- `MainActivity` (singleTask) hält bis zu 4 Sessions in einem `ViewFlipper`,
  je Session eine WebView (auch TE wird per WebView gerendert); Web-Sessions
  können mehrere Tabs haben.
- Startseite: Profilliste als generiertes HTML in einer WebView; Anlegen,
  Bearbeiten, Löschen etc. erfordern das Konfigurationspasswort (5 min
  Kulanzfenster).
- Zurück-Taste beendet die App nie (Fallback `moveTaskToBack`).
- Kein Autostart beim Booten, kein echter Lock-Task-Modus (Flag ohne
  Verwendung). Kiosk entsteht durch: Toolbar ausblenden, Immersive Mode,
  Exit ausblenden/Passwort, Back-Handling.
- Externe Steuerung per Broadcast `CLOSE_VELOCITY` (Extras `clearCache`,
  `clearCookies`, `clearStorage`, `forceStop`).

## Konfiguration

- Host-Profile als XML (`XmlSerializer`), Schema-Version 12 mit Migrationen.
- Bereitstellung:
  - `.wldep`-Bundles (ZIP: Profil, Tastaturen, Skripte, Themes, Ressourcen)
    im App-External-Files-Verzeichnis oder `/enterprise/usr` (Zebra
    persistent).
  - Android Managed Configurations (`app_restrictions.xml`): ein Profil
    (`hp_name`, `hp_type`, `hp_address`, `hp_port`), globale Werte,
    Lizenzserver, Manifest-URL für Remote-Fetch, MDM-Variablen.
  - Remote-Fetch: Manifest-URL → ZIP oder Dateiliste, If-Modified-Since,
    optionales Zertifikats-Pinning (ohne Pin werden alle Zertifikate
    akzeptiert).
  - Intent `INSTALL_CONFIG` / `CLEAR_CONFIG` (StageNow/MDM), Datei-Öffnen
    von `.wldep`.
  - Scan-to-Config (Barcode mit Manifest-URL/JSON), Tap-to-Share (NFC HCE +
    HTTP-Server Port 9696).
- Globale Einstellungen: Konfig-/Exit-Passwort (PBKDF2-Hash), max. Sessions,
  Exit ausblenden, Telemetrie, Tasten für Session-Wechsel.

### Wichtige Web-Profil-Einstellungen

Startadresse, Allowlist (Host-Suffix-Matching auf Label-Grenzen, eigener Host
automatisch erlaubt, nur Navigationen), Autostart, User-Agent,
Hardware-Back-Belegung, Scanner-Einstellungen je Hersteller,
Tastatur-Optionen, Toolbar ausblenden, Immersive Mode, Bildschirmrotation,
Wake-Lock, Pinch-Zoom aus, Akku/WLAN-Icons, Copy/Paste, Datei-Upload,
Proxy, eigene vertrauenswürdige Zertifikate, Cache/Cookies beim Start löschen,
Textgröße, mehrere Tabs, Out-of-Range-Erkennung.

## Web-Session

- WebView: JavaScript, DOM-Storage an, Formulardaten speichern aus,
  Zoom-Controls versteckt, Textzoom pro Profil, Remote-Debugging immer an.
- Navigation: Allowlist in `shouldOverrideUrlLoading`, gesperrte URL →
  Toast (bzw. Tab schließen). Sonder-Schemata für App-Aktionen und lokale
  Assets.
- Seitenlebenszyklus: Overlay während des Ladens, danach HTML/JS/CSS-Inserts
  per URL-Match, Auswertung von `<meta http-equiv>`-Tags, Skript-Events.
- SSL: unbekanntes Zertifikat → gegen gepinnte Profilzertifikate prüfen,
  sonst fataler Dialog; andere Fehler → Nutzer kann fortfahren.
  Client-Zertifikate aus AndroidKeyStore/KeyChain.
- Downloads: nur PDF (inkl. blob:/data:), Anzeige per FileProvider.
- Statusseiten (Verbinden, 404, 500, SSL, Demo abgelaufen) aus einem lokalen
  HTML-Dokument.

### JS-Bridges

- Injiziertes Objekt `Velocity` (Scroll, Toast, Meta-Tag-Verarbeitung,
  Scopes, Tastaturwechsel, `trigger(fn, json)` als generischer Kanal in die
  Skript-Engine, Offline-Forms).
- Gerätefunktionen (Beep, Vibration, LED, Seriennummer, Akku, Scanner,
  Tastatur, Einstellungen, Intents starten) liegen **nicht** im Seiten-Bridge,
  sondern in einer separaten JavaScript-Engine (Jurassic) für Profil-Skripte,
  die per `evaluateJavascript`/`javascript:` in die Seite wirkt.
- WebAuthn/U2F-Polyfills für NFC/USB-Sicherheitsschlüssel (Cotech SDK).

### Meta-Tags (pro Seite)

`scanner` (Enable/Disable/Suspend/Resume/AutoTab/AutoEnter/…),
`ScannerNavigate` (URL/JS-Vorlage mit `%s`, verbraucht Scan),
`ScannerProcessed`, `OnKey<hex>`, `OnAllKeys`, Druck-Tags.

## Scanner

- Ein Interface (`Init`, `Enable`, `Toggle` = Softtrigger, `Config`,
  Event `Scanned(symbology, data)`), Auswahl über `Build.MANUFACTURER`
  (Datalogic, Zebra, Honeywell, Bluebird, CipherLab, Panasonic, PointMobile,
  Seuic, Cognex, M3, Casio, Koamtac, Keyence, Urovo, sonst Kamera/ZXing).
- Scanner wird bei jedem `onPause` komplett freigegeben und bei `onResume`
  neu initialisiert; Sperren als benannte Locks.
- **Zebra, Android 12+: DataWedge-Intents**
  - Status abfragen (`GET_DATAWEDGE_STATUS`), dann `SET_CONFIG` mit Profil
    „Velocity“ (`CREATE_IF_NOT_EXIST`, App-Zuordnung, Plugins BARCODE,
    INTENT = Broadcast an eigene Action, KEYSTROKE aus).
  - Aktivieren/Deaktivieren über `SCANNER_INPUT_PLUGIN`
    (`ENABLE/SUSPEND/RESUME_PLUGIN`), Softtrigger über `SOFT_SCAN_TRIGGER`.
  - Daten aus `com.symbol.datawedge.data_string`, Symbologie aus
    `label_type` ohne `LABEL-TYPE-`.
- Zebra < Android 12: EMDK Barcode API direkt.
- Honeywell: AIDC-SDK (Properties `DEC_*`, `DPR_*`, `TRIG_*`).
- Generischer Intent-Empfänger: Extras-Schlüssel per Regex
  (`…data_string`, `…decode_string`, `data` / `…label_type`, `codeId`).
- Zustellung in Web-Sessions: Skript-Hook `Scan` (darf Daten ändern) →
  Text per `InputConnection.commitText` in das fokussierte Element, Tab/CR
  als Key-Events → konfigurierbarer Terminator (`{TAB}`, `{ENTER}`, …).
  Es gibt kein DOM-Event.

## Bewusst nicht übernommen

Terminal-Emulation, Sprachsteuerung, Neurons Forms/MQTT, Lizenzserver,
Telemetrie (Application Insights), Tap-to-Share, verschlüsselte Bundles mit
hartkodiertem Schlüssel, Annahme beliebiger Zertifikate ohne Pin,
dauerhaft aktiviertes WebView-Remote-Debugging.
