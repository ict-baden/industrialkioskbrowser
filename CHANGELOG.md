# Changelog

## Unreleased

### Changed

- The release APK contains only ARM code (`arm64-v8a`, `armeabi-v7a`); rugged Android devices are ARM only. With ML Kit for OCR the APK would otherwise be 57 MB instead of 30 MB. Debug builds keep x86 for the emulator.

### Added

- **Text recognition (OCR)** with the camera ([#49](https://github.com/ict-baden/industrialkioskbrowser/issues/49)):
  switch *Barcode | Text* on the camera screen, menu entry *Recognise text*, JS bridge
  `scanner.trigger({mode: "text"})`. `scanner.ocr`: tap a recognised line (`pick`) or take the first line that
  matches a regular expression by itself (`pattern`, confirmed over several frames), clean-up to digits/letters,
  length limits, start in text mode. Result through `scanner.data` with the type `ocr`, `source: "ocr"` for the
  page. Recognition with **Google ML Kit** 16.0.1 (bundled Latin model, offline) – the first component that is
  not open source; ML Kit may send anonymous usage statistics to Google (docs/data-and-privacy.md). The APK grows
  from about 14 to about 57 MB (ML Kit's native OCR library for four processor types; an app bundle from Google
  Play only carries the one of the device, about +11 MB). New error code `IKB-0032`.
- **NFC tags** ([#50](https://github.com/ict-baden/industrialkioskbrowser/issues/50)): reader mode while the
  browser is in front. `scanner.nfc`: tag ID (hex or decimal, reversed byte order, separator), stored NDEF text
  or link, or "NDEF, otherwise ID". Delivered like a scan through `scanner.data` with the type `nfc`,
  `source: "nfc"` for the page. Profile settings show whether NFC is on. New permission `NFC`, error codes
  `IKB-0033`, `IKB-0034`.
- `scanner.status()` of the JS bridge also reports `ocr`, `nfc` and `nfcOn`.

- **Scanning with the camera** on devices without a scan engine ([#48](https://github.com/ict-baden/industrialkioskbrowser/issues/48)):
  the app detects whether the device has one (Zebra: DataWedge's scanner list, Honeywell: scanner service) and
  otherwise opens a full-screen camera view with aiming frame and torch from the scan button and from
  `scanner.trigger()`. New setting `scanner.engine` (`auto`, `hardware`, `camera`), also in the profile dialog
  ("Scan with"). Decoding with [zxing-cpp](https://github.com/zxing-cpp/zxing-cpp) 3.1.1, camera with CameraX 1.5.3
  (see THIRD-PARTY-NOTICES.txt). New error codes `IKB-0030` (no camera permission) and `IKB-0031` (camera not
  available).
- **Camera scanner settings** `scanner.camera` (same fields as Industrial Keyboard): barcode types, aiming
  (`frame`, `center` with crosshair as pick list, `anywhere`), continuous scanning with duplicate delay and a
  counter, confirmation over 1–3 frames, length limits, zoom (with a 1×/2×/3× button), flashlight at start,
  light-on-dark codes, beep, vibration, timeout.
- **Scan data formatting** `scanner.data` for every scan (camera, DataWedge, Honeywell, broadcast): prefix,
  suffix, trim, remove spaces, upper/lower case, strip characters at start/end, replacements (also regular
  expressions), GS1 as `(01)…` or raw, group separator replacement, and rules per barcode type, length, start
  or pattern that change or drop a code. See [Configuration](docs/configuration.md#scannerdata).
- **All profile settings on the device:** *Settings → tap a profile* opens a screen with every field of the
  profile – general, kiosk, display, keyboard, sounds, error pages, scanner, camera and scan data. Lists that do
  not fit a phone (injections, key remapping, hotkeys, replacements, rules) show their count. New profiles are
  still created with the short dialog.
- **Export as barcode:** *Settings → Export as barcode* shows the configuration as a QR code for the next
  device; long configurations are compressed (`ICTZ1:` + base64url of DEFLATE). All imports (barcode, file,
  broadcast, managed configuration) accept this form. Too large for a QR code: `IKB-0104`. QR codes are made with
  the QR Code generator library by Project Nayuki (MIT).

## [0.7.0] – 2026-11-06

### Changed

- **New name: Industrial Kiosk Browser** (formerly *Pforte*, before that *EnterpriseBrowser*), matching
  [Industrial Keyboard](https://github.com/ict-baden/industrialkeyboard). Package name
  `de.ictbaden.industrialkioskbrowser`, so Android and the EMM see a new app – see [Deployment](docs/deployment.md).
  Also renamed: broadcast actions (`de.ictbaden.industrialkioskbrowser.action.BARCODE`, `…action.INSTALL_CONFIG`),
  JavaScript API `window.IndustrialKioskBrowser` and event `industrialkioskbrowser:scan`, DataWedge profile
  "Industrial Kiosk Browser", configuration file `industrialkioskbrowser.json`, error codes `IKB-…` (same numbers as
  `PFT-…`), retry link `industrialkioskbrowser://retry` (`pforte://retry` and `ebrowser://retry` still work).
  Release files are called `IndustrialKioskBrowser-<version>`.

### Docs

- [Limitations](docs/limitations.md): the signing key of the new package name is set up; releases contain the
  signed APK and the App Bundle for Google Play.

## [0.6.1] – 2026-10-21

### Fixed

- **A new configuration reaches an open profile right away**, as 0.6.0 promised: an `INSTALL_CONFIG` broadcast
  restarts the session immediately, a configuration file (app folder or `/enterprise/usr/`) within about five
  seconds. Before, both only took effect when the app came back to the front (the MDM already worked).
- Store texts no longer say the device *stays* locked to the allowed apps: it *can* be limited to them, and
  without an allow list every host is allowed.

### Changed

- Release notes are taken from this changelog. A release built before the signing key is set up is marked
  *Latest* and says at the top that the signed APK will follow.
- The download badge and the install steps in the README point to the latest release.

## [0.6.0] – 2026-10-21

### Changed

- **New name: Pforte** (formerly *EnterpriseBrowser*; the old name is a product of another vendor).
  Package name `de.ictbaden.pforte`, so **Android and the EMM see a new app**: distribute Pforte, roll out the
  configuration again (same JSON format, file name now `pforte.json`) and remove the old app – see
  [Deployment](docs/deployment.md). Also renamed: broadcast actions (`de.ictbaden.pforte.action.BARCODE`,
  `…action.INSTALL_CONFIG`), JavaScript API `window.Pforte` and event `pforte:scan`, DataWedge profile "Pforte",
  error codes `PFT-…` (same numbers as `EB-…`), retry link `pforte://retry` (`ebrowser://retry` still works).
- A tap on the launcher icon no longer closes the open web session.
- Settings, configuration files and the MDM take effect in an open profile right away: the session starts again
  with the new configuration instead of after an app restart.
- The back key skips error pages and the address that failed; with nothing to go back to it opens the menu, so a
  kiosk without toolbar is never stuck on an error page.
- `ScannerNavigate` templates with leading spaces before `javascript:` work.
- Store texts and [Data and privacy](docs/data-and-privacy.md) are precise: an empty `allowList` allows every
  host, the admin password is optional, Google Safe Browsing of the Android WebView stays on.
- Info links point to https://ict-baden.de (privacy policy, website, legal notice).
- Spanish, French and Chinese are marked as machine translations in the language picker.

### Added

- `scanner.genericIntent` (`auto`/`on`/`off`): the generic scan broadcast `<package>.action.BARCODE`, which any
  app can send, is by default only accepted on devices without a Zebra or Honeywell scanner.
- Releases contain an SBOM (CycloneDX, `Pforte-<version>.cdx.json`); build provenance attestation is prepared
  for the public repository. The CI checks `THIRD-PARTY-NOTICES.txt` against the libraries in the APK.
- [Limitations](docs/limitations.md) and [Security review](docs/security-review.md).

### Security

- **A configuration file in the app folder** (`Android/data/<package>/files/`) needs the admin password once one
  is set (`global.adminPassword` in plain text) and never replaces it; without a password it is only accepted on
  managed devices and from Android 11 on. After the import the file is deleted, so the plain password does not
  stay readable. Files in `/enterprise/usr/` are trusted as before.
- After 5 wrong admin passwords, broadcasts (and, counted separately, app-folder files) are refused for 15 minutes.
- Password hashes with more than 600,000 PBKDF2 iterations are rejected.

### Fixed

- French store texts contained broken characters instead of dashes.
- `THIRD-PARTY-NOTICES.txt` listed outdated versions and missed AndroidX WebKit.

### Also new since 0.5.0 (developed under the former name)

- **Security: configuration broadcasts (`INSTALL_CONFIG`) are only accepted with the admin password, or – while
  no password is set – on devices managed by an EMM.** Before, any app on an unmanaged device could replace the
  configuration (e.g. the start URL). **Action needed** if you configure unmanaged devices by broadcast (StageNow
  without EMM, adb): set an admin password first and send it as `admin_password`, or use the configuration file
  (`/enterprise/usr/pforte.json`). Refused broadcasts are shown under Settings → Configuration.
- An empty password (settings dialog or a broadcast without `admin_password`) no longer crashes the app on Android.

- **User interface in five languages:** English, German, Spanish, French and Chinese (simplified). It follows the
  device language; `global.language` or **Settings → Design → Language** set it per device.
- Error messages and error pages show a code such as `PFT-0001` ([list](docs/error-codes.md)), so support can identify
  them in any language. Custom error page templates get the placeholders `{{code}}`, `{{retry}}` and `{{lang}}`.
- **Settings → Info** now shows data and privacy, licence, third-party licences, source code, support, website and
  legal notice; new page [Data and privacy](docs/data-and-privacy.md).
- Long single words in the menu are hyphenated instead of being cut in the middle.
- Prepared for Google Play: the CI also builds a signed App Bundle (`.aab`), store texts in five languages and store
  graphics in `fastlane/`, Play Console answers in [docs/play-store.md](docs/play-store.md).

## 0.5.0

Released under the former name *EnterpriseBrowser*.

- **New package name `de.ictbaden.enterprisebrowser`** (previously a personal `io.github.…` package name), following the ICT-Baden guidelines. Android and the MDM see a **new app**: uninstall the old one, distribute the new one and roll out the configuration again.
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
