# Changelog

## 0.5.0

- **New package name `de.ictbaden.enterprisebrowser`** (previously `io.github.pieewiee.enterprisebrowser`), following the ICT-Baden guidelines. Android and the MDM see a **new app**: uninstall the old one, distribute the new one and roll out the configuration again.
- The broadcast actions change accordingly: `de.ictbaden.enterprisebrowser.action.BARCODE` and `…action.INSTALL_CONFIG`. Update scanner apps or StageNow/MDM profiles that send them.
- Debug builds are called `de.ictbaden.enterprisebrowser.debug` and can be installed next to the release.
- Release APKs are signed by the CI with the ICT-Baden key and published under fixed links (release `latest`) with checksums.
- Licence: MIT, © ICT-Baden.

Fixed – all found and verified on a Zebra HC20 (Android 15, DataWedge):

- **Every error page crashed the app** (no Wi-Fi, server down, certificate error, blocked
  page): the placeholder pattern of the error page is invalid for Android's regex engine.
- **The error tone could crash the app** when the audio track could not be created.
- **Form posts bypassed the allow list:** a `<form method="post">` could send data to and open a
  host outside `allowList`. Such requests are now stopped before they leave the device.
- `viewport.mode: fixed` and `viewport.metaOverride` had no effect on pages with their own
  viewport tag.
- `ScannerNavigate` with a relative target (`/item?code=%s`) did nothing; it is now resolved
  against the current page.

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
