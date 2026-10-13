# Umstieg von Ivanti Velocity

Velocity-Web-Profile lassen sich direkt in EnterpriseBrowser-Konfigurationen übersetzen.
Seiten, die Velocity-Meta-Tags nutzen (`scanner`, `ScannerNavigate`), laufen ohne
Änderung weiter; die Tastatur-Makros (`{TAB}`, `{F1}`, `{BACK}` …) sind kompatibel.

## Schnellstart: „wie Velocity“

```json
{
  "version": 2,
  "profiles": [
    {
      "name": "Mein Web-Profil",
      "startUrl": "https://wms.example.com/",
      "viewport": { "mode": "device", "fitToScreen": false, "pinchZoom": true },
      "keyboard": { "soft": "system", "showOnFocus": false, "backKey": "{BACK}" },
      "scanner":  { "terminator": "enter" }
    }
  ]
}
```

- **Viewport:** Velocity rendert Web-Seiten immer in Gerätebreite und passt breite
  Seiten nicht automatisch ein → `mode: device`, `fitToScreen: false`.
- **Tastatur:** Velocity blendet die Tastatur in Web-Sitzungen nicht automatisch ein,
  sie kommt nur über das Toolbar-Symbol → `showOnFocus: false`.

## Einstellungen im Vergleich

| Velocity (Host-Profil / global) | EnterpriseBrowser |
|---|---|
| Adresse | `profiles[].startUrl` |
| Name | `profiles[].name` |
| AllowList / AllowList aktiv | `allowList` (leer = keine Einschränkung) |
| Beim Start verbinden (`IsLaunchOnStartup`) | `launchOnStartup` |
| User-Agent | `userAgent` (`{default}` = Standard-UA) |
| Cache / Cookies beim Start löschen | `clearCacheOnStart`, `clearCookiesOnStart` |
| Datei-Upload erlauben | `allowFileUpload` |
| Toolbar ausblenden | `kiosk.hideToolbar` |
| Immersive Mode | `kiosk.immersive` |
| Wake Lock | `kiosk.keepScreenOn` |
| Bildschirmrotation | `kiosk.orientation` |
| Hardware-Zurück-Taste | `keyboard.backKey` |
| Velocity-/Systemtastatur | `keyboard.soft`, `keyboard.showOnFocus` |
| Hotkey Tastatur ein/aus | `keyboard.hotkeys.toggleKeyboard` |
| `Keyboard.mapKeypress` (Skript) | `keyboard.remap` |
| Ziffern lang drücken = F-Tasten | `keyboard.longPressDigitsAsF` |
| Scan-Terminator / Meta `scanner` AutoTab … | `scanner.terminator` / gleiches Meta-Tag |
| Meta `ScannerNavigate` | gleich |
| `View.insertCss` / `insertJavaScript` / `insertHtml` | `injections` |
| Dateien unter `resources/` im Bundle | `resources` (`res:<name>`) |
| Konfigurations-Passwort | `global.adminPassword` |
| Exit-Passwort | `global.exitPassword` |
| Exit im Menü ausblenden | `global.hideExit` |
| Profilfarbe in der Profilliste | `profiles[].color` |
| Branding der Profilliste (`profiles.css`) | `global.branding` (Titel, Farbe, Logo) |
| Managed Config `hp_name`, `hp_address` | `profile_name`, `start_url` (oder `config_json`) |
| Intent `INSTALL_CONFIG` mit Datei | Broadcast `INSTALL_CONFIG` mit JSON-Extra `config` |
| Eigene SSL-Zertifikate im Profil | Firmen-CA per MDM auf dem Gerät installieren |

## Noch nicht enthalten

- Eigene Bildschirmtastatur mit Layouts (Velocity `keyboards.xml`) – derzeit wird die
  Android-Tastatur verwendet.
- Session-Skripte (`session.js`) und `OnKey`-Meta-Tags – Ersatz: `injections` mit
  JavaScript und `keyboard.remap`.
- Seitenzoom „Textgröße“ mit gemerktem Pinch-Zoom.
- Mehrere Tabs pro Sitzung, Kamera-Scanner als Fallback.
- Terminal-Emulation, Sprachsteuerung (Speakeasy), Velocity Forms – bewusst nicht
  Teil des EnterpriseBrowsers.
