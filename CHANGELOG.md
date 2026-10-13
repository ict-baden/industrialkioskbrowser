# Changelog

## 0.3.0

- **Neues Design** (Material 3): Startseite mit Farbverlauf-Header und Profilkarten,
  Toolbar mit Adresszeile und Scan-Button, Menü als Bottom Sheet, neu gestaltete
  Einstellungen und Dialoge, Splash-Screen.
- **Design anpassen:** `global.branding` (Titel, Untertitel, Markenfarbe, Logo) und
  `profiles[].color`; in der App unter Einstellungen → Design inkl. Farbpalette und
  Logo-Import. Text- und Symbolfarben passen sich hellen Markenfarben an.
- Verständliche Fehlerseiten (Server nicht gefunden, Zeitüberschreitung …) in
  Markenfarbe; Dark-Mode-Darstellung der Fehlerseite korrigiert.
- Eingebaute Demo heißt in der Oberfläche „Integrierte Demo“.
- Beispielkonfigurationen in `examples/`, automatisch gegen den Parser getestet.
- Dokumentation mit Screenshots (aus dem UI-Code gerendert), Verteilungs- und
  Migrationsleitfaden.

## 0.2.0

- `viewport`, `keyboard` (Remap, Hotkeys, Makros), `injections`, `resources`,
  `errorPages`, `sounds`.
- PNG-Launcher-Icons, damit MDM-Konsolen wie SOTI das Icon anzeigen.

## 0.1.0

- Erste Version: Profile, Allowlist, Zebra DataWedge / Honeywell / Intent-Scanner,
  Meta-Tags, JavaScript-API, Kiosk-Optionen, Managed Configuration, Konfigurationsdatei,
  INSTALL_CONFIG-Broadcast, QR-Import.
