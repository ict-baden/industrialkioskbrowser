# Changelog

## 0.3.1

- App, documentation, examples and screenshots are now in **English**.
- Example configurations use neutral placeholder systems only.

## 0.3.0

- **New design** (Material 3): start screen with gradient header and profile cards,
  toolbar with address line and scan button, bottom sheet menu, redesigned settings and
  dialogs, splash screen.
- **Custom design:** `global.branding` (title, subtitle, brand color, logo) and
  `profiles[].color`; in the app under Settings → Design including a color palette and
  logo import. Text and icon colors adapt to light brand colors.
- Clear error pages (server not found, timeout …) in the brand color; fixed the error
  page in dark mode.
- The built-in demo is called "Built-in demo" in the UI.
- Example configurations in `examples/`, automatically tested against the parser.
- Documentation with screenshots (rendered from the UI code), deployment guide and web
  integration guide.

## 0.2.0

- `viewport`, `keyboard` (remapping, hotkeys, macros), `injections`, `resources`,
  `errorPages`, `sounds`.
- PNG launcher icons, so MDM consoles such as SOTI show the icon.

## 0.1.0

- First version: profiles, allow list, Zebra DataWedge / Honeywell / intent scanners,
  meta tags, JavaScript API, kiosk options, managed configuration, configuration file,
  INSTALL_CONFIG broadcast, QR import.
