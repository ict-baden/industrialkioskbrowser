# Konfiguration

Die gesamte Konfiguration ist eine JSON-Datei. Sie kann in der App unter
**Einstellungen** importiert/exportiert, per Barcode (QR) eingelesen oder über
ein EMM/MDM verteilt werden.

## Format

```json
{
  "version": 2,
  "global": {
    "adminPassword": "geheim",
    "exitPassword": "auch-geheim",
    "hideExit": false
  },
  "profiles": [
    {
      "id": "lager",
      "name": "Lager",
      "startUrl": "https://wms.example.com/",
      "allowList": ["example.com", "sso.example.org"],
      "launchOnStartup": true,
      "userAgent": "{default} WMS-Terminal",
      "clearCacheOnStart": false,
      "clearCookiesOnStart": false,
      "allowFileUpload": false,
      "allowDownloads": true,
      "kiosk": {
        "hideToolbar": false,
        "immersive": false,
        "keepScreenOn": true,
        "orientation": "auto"
      },
      "scanner": {
        "enabled": true,
        "terminator": "enter",
        "injectIntoFocusedField": true
      }
    }
  ]
}
```

### `global`

| Feld | Bedeutung |
|---|---|
| `adminPassword` / `adminPasswordHash` | Schützt die Einstellungen. Klartext wird beim Import gehasht (PBKDF2); Exporte enthalten nur den Hash. Fehlt das Feld, bleibt das bisherige Passwort erhalten. |
| `exitPassword` / `exitPasswordHash` | Wird beim Beenden abgefragt. |
| `hideExit` | Blendet „Beenden“ und „Profil wechseln“ aus (Kiosk). |

### Profile

| Feld | Standard | Bedeutung |
|---|---|---|
| `id` | aus `name` | Eindeutige ID |
| `name` | `startUrl` | Anzeigename |
| `startUrl` | – | Pflicht, `http://` oder `https://` |
| `allowList` | `[]` | Erlaubte Hosts inkl. Subdomains (`example.com` erlaubt auch `a.example.com`). Leer = alles erlaubt. Der Host der Start-URL ist immer erlaubt. Gilt für Navigationen im Hauptframe. |
| `launchOnStartup` | `false` | Profil beim App-Start direkt öffnen |
| `userAgent` | Standard | Eigener User-Agent, `{default}` wird durch den Standard-UA ersetzt |
| `clearCacheOnStart`, `clearCookiesOnStart` | `false` | Beim Öffnen des Profils löschen |
| `allowFileUpload` | `false` | `<input type="file">` erlauben |
| `allowDownloads` | `true` | Downloads über den Android-DownloadManager |
| `kiosk.hideToolbar` | `false` | Toolbar ausblenden (Menü dann über die Zurück-Taste) |
| `kiosk.immersive` | `false` | Status- und Navigationsleiste ausblenden |
| `kiosk.keepScreenOn` | `true` | Display bleibt an |
| `kiosk.orientation` | `auto` | `auto`, `portrait`, `landscape` |
| `scanner.enabled` | `true` | Scanner beim Laden einer Seite aktiv |
| `scanner.terminator` | `enter` | Nach dem Einfügen gesendete Tasten: `none`, `tab`, `enter`, `tab+enter`, `enter+tab` |
| `scanner.injectIntoFocusedField` | `true` | Scan in das fokussierte Eingabefeld einfügen |

### `viewport`

| Feld | Standard | Bedeutung |
|---|---|---|
| `mode` | `page` | `page`: Seite bestimmt per `<meta name="viewport">`. `device`: immer Gerätebreite. `fixed`: feste Layoutbreite `width`, auf den Bildschirm skaliert. |
| `width` | `0` | Layoutbreite in CSS-Pixeln (Pflicht bei `fixed`, z. B. `320` für alte MDE-Masken) |
| `initialScale` | `0` | Startzoom in Prozent, `0` = automatisch |
| `fitToScreen` | `true` | Zu breite Seiten beim Laden einpassen |
| `textZoom` | `100` | Schriftgröße in Prozent (25–400) |
| `pinchZoom` | `true` | Zoomen per Geste |
| `metaOverride` | – | Ersetzt das Viewport-Meta-Tag der Seite, z. B. `width=device-width, initial-scale=1` |

Version-1-Dateien mit `textZoom`/`pinchZoom` direkt im Profil werden weiter gelesen.

### `keyboard`

```json
"keyboard": {
  "soft": "system",
  "showOnFocus": false,
  "showOnPageLoad": false,
  "backKey": "{BACK}",
  "remap": [
    { "key": "BUTTON_L1", "send": "{TAB}" },
    { "key": "F5", "send": "{NAVIGATE-REFRESH}" },
    { "key": "VOLUME_UP", "send": "system" },
    { "key": "F7", "send": "" }
  ],
  "hotkeys": { "toggleKeyboard": "F12", "menu": "F11" },
  "longPressDigitsAsF": false
}
```

| Feld | Bedeutung |
|---|---|
| `soft` | `system` (Android-Tastatur) oder `none` (nie einblenden) |
| `showOnFocus` | `false`: Tastatur erscheint nicht beim Antippen eines Feldes, nur über das Tastatur-Symbol in der Toolbar oder den Hotkey (wie Velocity) |
| `showOnPageLoad` | Tastatur nach jedem Seitenladen einblenden |
| `backKey` | Makro für die Zurück-Taste; `{BACK}` = zurück bzw. Menü |
| `remap` | Hardware-Taste (`key`: Android-Name ohne `KEYCODE_`, z. B. `F1`, `BUTTON_L1`, `ENTER`, oder Zahl) → Makro. `system` reicht die Taste unverändert durch, leer verschluckt sie. |
| `hotkeys` | Aktion → Taste. Aktionen: `toggleKeyboard`, `menu`, `home`, `reload`, `back`, `forward`, `scan` |
| `longPressDigitsAsF` | Langes Drücken von 1–9/0 sendet F1–F10 |

**Makros** (Velocity-kompatibel, Groß-/Kleinschreibung egal): Text wird getippt,
`{TAB}`, `{BACK-TAB}`, `{ENTER}`, `{ESC}`, `{BS}`, `{DELETE}`, `{INSERT}`, `{HOME}`,
`{END}`, `{PAGE-UP}`, `{PAGE-DOWN}`, `{UP}` `{DOWN}` `{LEFT}` `{RIGHT}`, `{SPACE}`,
`{F1}`–`{F12}`, `{CTRL-C}`, `{HEX:00E4}`, `{OPEN-BRACE}`/`{CLOSE-BRACE}`,
`{PAUSE}` / `{PAUSE:500}`, Aktionen `{BACK}`, `{NAVIGATE-BACK}`,
`{NAVIGATE-FORWARD}`, `{NAVIGATE-STOP}`, `{NAVIGATE-HOME}`, `{NAVIGATE-REFRESH}`,
`{SPECIAL:SCAN}`, `{MENU}`, `{KEYBOARD}`. Unbekannte Tokens werden als Text getippt.

### `injections`

```json
"injections": [
  { "match": { "url": "/sap/zwm_mde_le", "type": "contains" }, "when": "documentStart", "css": "res:sap.css" },
  { "match": { "url": "^https://wms\.example\.com/pick", "type": "regex", "source": "id=\"pickForm\"" },
    "when": "pageLoaded", "js": "res:pick.js", "frames": "main" },
  { "html": "<div class='banner'>TEST</div>", "position": "bodyStart" }
]
```

| Feld | Bedeutung |
|---|---|
| `match.url` / `match.type` | `contains` (Standard), `prefix`, `regex`; ohne `url` gilt die Injection für alle Seiten |
| `match.source` | Nur einfügen, wenn der Seitenquelltext diesen Text enthält |
| `when` | `documentStart` (vor dem ersten Rendern, kein Flackern) oder `pageLoaded` |
| `frames` | `main` (Standard) oder `all` (nur bei `documentStart`) |
| `css` / `js` / `html` | Inline-Inhalt oder `res:<name>`; `<script>` in HTML wird ausgeführt |
| `position` | Für HTML: `bodyEnd` (Standard), `bodyStart`, `head` |

### `errorPages` und `sounds`

```json
"errorPages": { "template": "res:error.html", "showHttpErrors": false },
"sounds": { "scanOk": "none", "error": "tone:1400:200:1000:200" }
```

`template` ist HTML mit `{{title}}`, `{{message}}`, `{{url}}`; der Link
`ebrowser://retry` lädt neu. `showHttpErrors` ersetzt auch Server-Fehlerseiten
(HTTP ≥ 400). Sounds: `none`, `beep`, `error`, `tone:<Hz>:<ms>[:<Hz>:<ms>…]`
oder `res:<name>` (Audiodatei). `error` ertönt bei Fehlerseiten und gesperrten
Seiten.

## Ressourcen

```json
"resources": {
  "sap.css": { "text": "input { min-height: 44px; font-size: 18px; }" },
  "logo.png": { "base64": "iVBORw0KGgo..." }
}
```

Ressourcen gelten für alle Profile. Sie werden mit `res:<name>` referenziert und
sind für Seiten unter `https://appassets.androidplatform.net/res/<name>`
erreichbar (z. B. Bilder in injiziertem CSS).

## Verteilung

1. **Managed Configuration (EMM, z. B. Intune, Workspace ONE, SOTI, Zebra
   StageNow/MX):**
   - `config_json` – komplette Konfiguration (wie oben).
   - Alternativ einfach: `start_url`, `profile_name`, `kiosk_mode`
     (legt ein Profil `managed` an, das beim Start geöffnet wird).
   - `admin_password` – setzt das Admin-Passwort.
2. **Datei:** `enterprisebrowser.json` nach
   `Android/data/io.github.pieewiee.enterprisebrowser/files/` oder (Zebra)
   `/enterprise/usr/` legen. Wird beim Start bzw. bei Rückkehr in die
   Profilliste übernommen, sobald sich Datei-Zeitstempel oder -Größe ändern.
3. **Broadcast (StageNow Intent-CSP, adb):**
   ```sh
   adb shell am broadcast \
     -a io.github.pieewiee.enterprisebrowser.action.INSTALL_CONFIG \
     -n io.github.pieewiee.enterprisebrowser/.provisioning.ConfigReceiver \
     --es config '{"profiles":[{"name":"WMS","startUrl":"https://wms.example.com"}]}'
   ```
   Ist ein Admin-Passwort gesetzt, muss es als `--es admin_password …`
   mitgeschickt werden.
4. **QR-Code:** Einstellungen → „Per Barcode importieren“, QR-Code mit dem
   JSON scannen.

Jede Quelle ersetzt die Profilliste vollständig; nicht gesetzte Passwörter
bleiben erhalten.

## Kiosk / Lock Task

Ist die App per Device-Owner (EMM) für Lock Task freigegeben, startet sie
automatisch im gesperrten Modus (`lockTaskMode="if_whitelisted"`). Zusammen
mit `hideExit`, `kiosk.hideToolbar` und `kiosk.immersive` ergibt das einen
vollständigen Kiosk.

## Scanner

| Gerät | Anbindung |
|---|---|
| Zebra | DataWedge-Intent-API. Beim Start wird ein DataWedge-Profil „EnterpriseBrowser“ für die App angelegt (Intent-Ausgabe, Keystroke aus). Decoder-Einstellungen werden nicht überschrieben und können in DataWedge/StageNow angepasst werden. |
| Honeywell | Intent-API des Data Collection Service (Scanner wird mit Data-Intent beansprucht). |
| Andere | Jede Scanner-App, die einen Broadcast `io.github.pieewiee.enterprisebrowser.action.BARCODE` mit dem Barcode in einem Extra (`data`, `…data_string`, `…decode_string`, `barcode_string`) sendet. |

Test ohne Scanner:

```sh
adb shell am broadcast -a io.github.pieewiee.enterprisebrowser.action.BARCODE \
  -p io.github.pieewiee.enterprisebrowser --es data 4006381333931 --es symbology EAN13
```

### Steuerung per Meta-Tag

```html
<meta http-equiv="scanner" content="Disable">              <!-- Scanner auf dieser Seite aus -->
<meta http-equiv="scanner" content="Enable,AutoTab">       <!-- an, nach Scan Tab senden -->
<meta http-equiv="scanner" content="NoAuto">               <!-- keine Abschlusstaste -->
<meta http-equiv="ScannerNavigate" content="https://wms.example.com/item?code=%s">
<meta http-equiv="ScannerNavigate" content="javascript:lookup(%s)">
```

`content="…"` von `scanner` kann `Enable`, `Disable`, `AutoTab`, `AutoEnter`,
`AutoTabAndEnter`, `AutoEnterAndTab` und `NoAuto` kombinieren. Bei
`ScannerNavigate` wird `%s` URL-kodiert bzw. als JavaScript-String eingesetzt
und der Scan nicht in ein Feld eingefügt.

## JavaScript-API

Auf erlaubten Hosts (Allowlist bzw. Host der Start-URL) steht
`window.EnterpriseBrowser` zur Verfügung. Alle Methoden liefern Promises.

```js
EnterpriseBrowser.onScan(function (scan, event) {
  // scan = { data, symbology, source, timestamp }
  if (scan.data.startsWith('LOC-')) {
    event.preventDefault();          // nicht ins Feld einfügen
    openLocation(scan.data);
  }
});

EnterpriseBrowser.scanner.trigger();         // Scan auslösen (Softtrigger)
EnterpriseBrowser.scanner.disable();         // bis zum nächsten Seitenwechsel
EnterpriseBrowser.beep('error');
EnterpriseBrowser.vibrate(300);
EnterpriseBrowser.toast('Gebucht');
EnterpriseBrowser.deviceInfo().then(console.log);
```

Das DOM-Event `enterprisebrowser:scan` wird bei jedem Scan auf `window`
ausgelöst, auch auf Seiten ohne Bridge-Zugriff.
