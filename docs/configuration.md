# Konfiguration

Die gesamte Konfiguration ist **eine JSON-Datei**. Sie wird per MDM verteilt
([Verteilung](deployment.md)), in der App unter **Einstellungen** importiert/exportiert
oder per QR-Code eingelesen. Fertige Vorlagen liegen in [`examples/`](../examples) –
jede davon wird in den automatischen Tests gegen die App geprüft.

> 💡 Nur `startUrl` ist Pflicht. Alles andere hat sinnvolle Standardwerte – die
> minimale Konfiguration ist [drei Zeilen lang](../examples/minimal.json).

## Inhalt

- [Überblick](#überblick)
- [`global`](#global) – Passwörter, Kiosk, Branding
- [Profile](#profile) – Start-URL, Allowlist, Grundeinstellungen
- [`viewport`](#viewport) · [`keyboard`](#keyboard) · [`injections`](#injections) ·
  [`errorPages` und `sounds`](#errorpages-und-sounds) · [`kiosk`](#kiosk) · [`scanner`](#scanner)
- [Ressourcen](#ressourcen)
- [Design anpassen](#design-anpassen)

## Überblick

```json
{
  "version": 2,
  "global": {
    "adminPassword": "geheim",
    "exitPassword": "auch-geheim",
    "hideExit": false,
    "branding": { "title": "Logistikzentrum Nord", "color": "#00796B", "logo": "res:logo.png" }
  },
  "resources": {
    "logo.png": { "base64": "iVBORw0KGgo..." },
    "fix.css":  { "text": "input { min-height: 44px; }" }
  },
  "profiles": [
    {
      "id": "lager",
      "name": "Lager",
      "startUrl": "https://wms.example.com/",
      "color": "#1565C0",
      "allowList": ["example.com", "sso.example.org"],
      "launchOnStartup": true,
      "userAgent": "{default} WMS-Terminal",
      "clearCacheOnStart": false,
      "clearCookiesOnStart": false,
      "allowFileUpload": false,
      "allowDownloads": true,
      "viewport":   { "mode": "device", "fitToScreen": false, "textZoom": 100, "pinchZoom": true },
      "keyboard":   { "soft": "system", "showOnFocus": false, "hotkeys": { "toggleKeyboard": "F12" } },
      "injections": [ { "match": { "url": "/pick/" }, "css": "res:fix.css" } ],
      "errorPages": { "showHttpErrors": false },
      "sounds":     { "scanOk": "none", "error": "error" },
      "kiosk":      { "hideToolbar": false, "immersive": false, "keepScreenOn": true, "orientation": "auto" },
      "scanner":    { "enabled": true, "terminator": "enter", "injectIntoFocusedField": true }
    }
  ]
}
```

Unbekannte Felder werden ignoriert, ungültige Werte führen zu einer klaren
Fehlermeldung (Import-Dialog bzw. **Einstellungen → Letzter Provisionierungsfehler**).
Jede Quelle ersetzt die Profilliste vollständig; nicht gesetzte Passwörter bleiben erhalten.

## `global`

| Feld | Bedeutung |
|---|---|
| `adminPassword` / `adminPasswordHash` | Schützt die Einstellungen. Klartext wird beim Import gehasht (PBKDF2); Exporte enthalten nur den Hash. |
| `exitPassword` / `exitPasswordHash` | Wird beim Beenden abgefragt. |
| `hideExit` | Blendet „Beenden“ und „Profil wechseln“ aus (Kiosk). |
| `branding.title` | Titel der Startseite (Standard: „EnterpriseBrowser“) |
| `branding.subtitle` | Untertitel (Standard: Gerätehersteller und -modell) |
| `branding.color` | Markenfarbe `#RRGGBB` für Startseite, Toolbars und Fehlerseiten |
| `branding.logo` | Logo als Ressource, z. B. `res:logo.png` (PNG/JPEG/WebP) |

## Profile

| Feld | Standard | Bedeutung |
|---|---|---|
| `id` | aus `name` | Eindeutige ID |
| `name` | `startUrl` | Anzeigename |
| `startUrl` | – | **Pflicht**, `http://` oder `https://` |
| `color` | Markenfarbe | Farbe von Profilkarte und Toolbar, `#RRGGBB` |
| `allowList` | `[]` | Erlaubte Hosts inkl. Subdomains (`example.com` erlaubt auch `a.example.com`). Leer = alles erlaubt. Der Host der Start-URL ist immer erlaubt. Gilt für Navigationen im Hauptframe. |
| `launchOnStartup` | `false` | Profil beim App-Start direkt öffnen |
| `userAgent` | Standard | Eigener User-Agent; `{default}` wird durch den Standard-UA ersetzt |
| `clearCacheOnStart`, `clearCookiesOnStart` | `false` | Beim Öffnen des Profils löschen (z. B. frische SAP-Sitzung) |
| `allowFileUpload` | `false` | `<input type="file">` erlauben |
| `allowDownloads` | `true` | Downloads über den Android-DownloadManager |

## `viewport`

| Feld | Standard | Bedeutung |
|---|---|---|
| `mode` | `page` | `page`: die Seite bestimmt ihre Breite (wie Chrome). `device`: immer Gerätebreite – **so wie Velocity**. `fixed`: feste Layoutbreite `width`, auf den Bildschirm skaliert. |
| `width` | `0` | Layoutbreite in CSS-Pixeln (nur bei `fixed`, Pflicht) |
| `initialScale` | `0` | Startzoom in Prozent, `0` = automatisch |
| `fitToScreen` | `true` | Zu breite Seiten beim Laden einpassen (Velocity: aus) |
| `textZoom` | `100` | Schriftgröße in Prozent (25–400) |
| `pinchZoom` | `true` | Zoomen per Geste |
| `metaOverride` | – | Ersetzt das Viewport-Meta-Tag der Seite, z. B. `width=device-width, initial-scale=1` |

> **Wie in Velocity:** `"viewport": { "mode": "device", "fitToScreen": false }`.
> Version-1-Dateien mit `textZoom`/`pinchZoom` direkt im Profil werden weiter gelesen.

## `keyboard`

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
| `showOnFocus` | `false`: Tastatur erscheint nicht beim Antippen eines Feldes, nur über das Tastatur-Symbol in der Toolbar oder den Hotkey – wie Velocity |
| `showOnPageLoad` | Tastatur nach jedem Seitenladen einblenden |
| `backKey` | Makro für die Zurück-Taste; `{BACK}` = zurück bzw. Menü |
| `remap` | Hardware-Taste (`key`: Android-Name ohne `KEYCODE_`, z. B. `F1`, `BUTTON_L1`, `ENTER`, oder Zahl) → Makro. `system` reicht die Taste durch, leer verschluckt sie. |
| `hotkeys` | Aktion → Taste. Aktionen: `toggleKeyboard`, `menu`, `home`, `reload`, `back`, `forward`, `scan` |
| `longPressDigitsAsF` | Langes Drücken von 1–9/0 sendet F1–F10 |

**Makros** (Velocity-kompatibel, Groß-/Kleinschreibung egal): Text wird getippt,
`{TAB}`, `{BACK-TAB}`, `{ENTER}`, `{ESC}`, `{BS}`, `{DELETE}`, `{INSERT}`, `{HOME}`,
`{END}`, `{PAGE-UP}`, `{PAGE-DOWN}`, `{UP}` `{DOWN}` `{LEFT}` `{RIGHT}`, `{SPACE}`,
`{F1}`–`{F12}`, `{CTRL-C}`, `{HEX:00E4}`, `{OPEN-BRACE}`/`{CLOSE-BRACE}`,
`{PAUSE}` / `{PAUSE:500}`, Aktionen `{BACK}`, `{NAVIGATE-BACK}`,
`{NAVIGATE-FORWARD}`, `{NAVIGATE-STOP}`, `{NAVIGATE-HOME}`, `{NAVIGATE-REFRESH}`,
`{SPECIAL:SCAN}`, `{MENU}`, `{KEYBOARD}`. Unbekannte Tokens werden als Text getippt.

## `injections`

Eigenes CSS, JavaScript oder HTML in Seiten einfügen – etwa größere Eingabefelder
für eine fremde Web-App oder ein Scan-Hook.

```json
"injections": [
  { "match": { "url": "/sap/zwm_mde_le", "type": "contains" }, "when": "documentStart", "css": "res:sap.css" },
  { "match": { "url": "^https://wms\\.example\\.com/pick", "type": "regex", "source": "id=\"pickForm\"" },
    "when": "pageLoaded", "js": "res:pick.js", "frames": "main" },
  { "html": "<div class='banner'>TESTSYSTEM</div>", "position": "bodyStart" }
]
```

| Feld | Bedeutung |
|---|---|
| `match.url` / `match.type` | `contains` (Standard), `prefix`, `regex`; ohne `url` gilt die Injection für alle Seiten |
| `match.source` | Nur einfügen, wenn der Seitenquelltext diesen Text enthält |
| `when` | `documentStart` (vor dem ersten Rendern, kein Flackern – Standard) oder `pageLoaded` |
| `frames` | `main` (Standard) oder `all` (nur bei `documentStart`) |
| `css` / `js` / `html` | Inline-Inhalt oder `res:<name>`; `<script>` in HTML wird ausgeführt |
| `position` | Für HTML: `bodyEnd` (Standard), `bodyStart`, `head` |

## `errorPages` und `sounds`

```json
"errorPages": { "template": "res:error.html", "showHttpErrors": false },
"sounds": { "scanOk": "none", "error": "tone:1400:200:1000:200" }
```

- `template` – eigenes HTML mit den Platzhaltern `{{title}}`, `{{message}}`, `{{url}}`
  und `{{color}}` (Markenfarbe). Der Link `ebrowser://retry` lädt neu. Ohne Vorlage wird
  die eingebaute Fehlerseite verwendet (siehe [Bild](images/screen-error.png)).
- `showHttpErrors` – ersetzt auch Fehlerseiten des Servers (HTTP ≥ 400).
- Sounds: `none`, `beep`, `error`, `tone:<Hz>:<ms>[:<Hz>:<ms>…]` oder `res:<name>`
  (Audiodatei). `error` ertönt bei Fehlerseiten und gesperrten Seiten.

## `kiosk`

| Feld | Standard | Bedeutung |
|---|---|---|
| `hideToolbar` | `false` | Toolbar ausblenden (Menü dann über die Zurück-Taste bzw. den Hotkey) |
| `immersive` | `false` | Status- und Navigationsleiste ausblenden |
| `keepScreenOn` | `true` | Display bleibt an |
| `orientation` | `auto` | `auto`, `portrait`, `landscape` |

Für einen vollständigen Kiosk zusätzlich `global.hideExit` setzen und die App im MDM
für **Lock Task** freigeben (Dedicated Device) – sie startet dann automatisch gesperrt.
Vorlage: [`kiosk-single-app.json`](../examples/kiosk-single-app.json).

## `scanner`

| Feld | Standard | Bedeutung |
|---|---|---|
| `enabled` | `true` | Scanner beim Laden einer Seite aktiv |
| `terminator` | `enter` | Nach dem Einfügen gesendete Tasten: `none`, `tab`, `enter`, `tab+enter`, `enter+tab` |
| `injectIntoFocusedField` | `true` | Scan in das fokussierte Eingabefeld schreiben |

| Gerät | Anbindung |
|---|---|
| Zebra | DataWedge-Intent-API. Beim Start wird ein DataWedge-Profil „EnterpriseBrowser“ angelegt (Intent-Ausgabe an, Keystroke aus). Decoder-Einstellungen bleiben unangetastet und können in DataWedge/StageNow angepasst werden. |
| Honeywell | Intent-API des Data Collection Service (Scanner wird mit Data-Intent beansprucht). |
| Andere | Jede Scanner-App, die `io.github.pieewiee.enterprisebrowser.action.BARCODE` mit dem Barcode in einem Extra (`data`, `…data_string`, `…decode_string`, `barcode_string`) sendet. |

Seiten können den Scanner per Meta-Tag steuern – siehe [Web-Integration](web-integration.md).

## Ressourcen

```json
"resources": {
  "sap.css":  { "text": "input { min-height: 44px; font-size: 18px; }" },
  "logo.png": { "base64": "iVBORw0KGgo..." }
}
```

Ressourcen gelten für alle Profile. Sie werden mit `res:<name>` referenziert und sind
für Seiten unter `https://appassets.androidplatform.net/res/<name>` erreichbar
(z. B. Bilder in injiziertem CSS). Namen dürfen Unterordner enthalten (`css/a.css`),
aber kein `..`.

## Design anpassen

![Standard-Design und eigenes Branding](images/branding.png)

Drei Ebenen:

1. **Branding** (`global.branding`) – Titel, Untertitel, Logo und Markenfarbe der
   Startseite; die Farbe gilt auch für Einstellungen und Fehlerseiten.
2. **Profilfarbe** (`profiles[].color`) – färbt Profilkarte, Toolbar und Menü der
   jeweiligen Web-App, damit Mitarbeiter sofort sehen, wo sie sind.
3. **Injections** – Feinschliff an der Web-App selbst (Schriftgrößen, Buttons, Banner).

Ohne MDM lässt sich das Design auch direkt am Gerät setzen: **Einstellungen → Design**
(Titel, Markenfarbe aus Palette oder Hex-Wert, Logo aus einer Bilddatei). Text- und
Symbolfarben passen sich automatisch an – auch helle Markenfarben bleiben lesbar.

<p>
  <img src="images/screen-color-picker.png" width="260" alt="Markenfarbe wählen">
  <img src="images/screen-profile-editor.png" width="260" alt="Profil mit eigener Farbe">
</p>
