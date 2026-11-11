# Configuration

The whole configuration is **one JSON file**. It is distributed through your MDM
([Deployment](deployment.md)), imported/exported in the app under **Settings**, or read
from a QR code. Ready-made templates are in [`examples/`](../examples) – each of them is
checked against the app by the automated tests.

> 💡 Only `startUrl` is required. Everything else has sensible defaults – the minimal
> configuration is [three lines long](../examples/minimal.json).

## Contents

- [Overview](#overview)
- [`global`](#global) – passwords, kiosk, branding
- [Profiles](#profiles) – start URL, allow list, basics
- [`viewport`](#viewport) · [`keyboard`](#keyboard) · [`injections`](#injections) ·
  [`errorPages` and `sounds`](#errorpages-and-sounds) · [`kiosk`](#kiosk) · [`scanner`](#scanner) ·
  [`scanner.camera`](#scannercamera) · [`scanner.ocr`](#scannerocr) (text recognition) ·
  [`scanner.nfc`](#scannernfc) · [`scanner.data`](#scannerdata) (scan data formatting)
- [Resources](#resources)
- [Custom design](#custom-design)

## Overview

```json
{
  "version": 2,
  "global": {
    "adminPassword": "secret",
    "exitPassword": "also-secret",
    "hideExit": false,
    "branding": { "title": "North Logistics Center", "color": "#00796B", "logo": "res:logo.png" }
  },
  "resources": {
    "logo.png": { "base64": "iVBORw0KGgo..." },
    "fix.css":  { "text": "input { min-height: 44px; }" }
  },
  "profiles": [
    {
      "id": "warehouse",
      "name": "Warehouse",
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
      "scanner":    { "enabled": true, "terminator": "enter", "injectIntoFocusedField": true, "genericIntent": "auto", "engine": "auto",
                      "camera": { "aim": "frame", "continuous": false }, "data": { "prefix": "", "suffix": null } }
    }
  ]
}
```

Unknown fields are ignored; invalid values produce a clear error message (import dialog
or **Settings → Last provisioning error**). Every source replaces the whole profile list;
passwords that are not set keep their current value.

## `global`

| Field | Meaning |
|---|---|
| `adminPassword` / `adminPasswordHash` | Protects the settings. Plain text is hashed on import (PBKDF2); exports only contain the hash. |
| `exitPassword` / `exitPasswordHash` | Asked for when exiting. |
| `hideExit` | Hides "Exit" and "Profiles" (kiosk). |
| `language` | `auto` (default: the device language), `en`, `de`, `es`, `fr` or `zh-Hans`. Can also be changed in the app under **Settings → Design → Language**. |
| `branding.title` | Title of the start screen (default: "Industrial Kiosk Browser") |
| `branding.subtitle` | Subtitle (default: device manufacturer and model) |
| `branding.color` | Brand color `#RRGGBB` for the start screen, toolbars and error pages |
| `branding.logo` | Logo as a resource, e.g. `res:logo.png` (PNG/JPEG/WebP) |

## Profiles

| Field | Default | Meaning |
|---|---|---|
| `id` | from `name` | Unique ID |
| `name` | `startUrl` | Display name |
| `startUrl` | – | **Required**, `http://` or `https://` |
| `color` | brand color | Color of the profile card and toolbar, `#RRGGBB` |
| `allowList` | `[]` | Allowed hosts including subdomains (`example.com` also allows `a.example.com`). **Empty = every host allowed** – set an allow list on kiosk devices. The host of the start URL is always allowed. Applies to main-frame navigations. |
| `launchOnStartup` | `false` | Open this profile directly when the app starts |
| `userAgent` | default | Custom user agent; `{default}` is replaced by the default user agent |
| `clearCacheOnStart`, `clearCookiesOnStart` | `false` | Clear when the profile opens (e.g. a fresh SAP session) |
| `allowFileUpload` | `false` | Allow `<input type="file">` |
| `allowDownloads` | `true` | Downloads through the Android DownloadManager |

## `viewport`

| Field | Default | Meaning |
|---|---|---|
| `mode` | `page` | `page`: the page decides its width (like Chrome). `device`: always the device width. `fixed`: fixed layout width `width`, scaled to the screen. |
| `width` | `0` | Layout width in CSS pixels (only for `fixed`, required) |
| `initialScale` | `0` | Initial zoom in percent, `0` = automatic |
| `fitToScreen` | `true` | Fit pages that are too wide when loading |
| `textZoom` | `100` | Font size in percent (25–400) |
| `pinchZoom` | `true` | Pinch to zoom |
| `metaOverride` | – | Replaces the page's viewport meta tag, e.g. `width=device-width, initial-scale=1` |

> **Tip for classic scanner web apps:** `"viewport": { "mode": "device", "fitToScreen": false }`
> renders at device width without zooming out.
> Version 1 files with `textZoom`/`pinchZoom` directly on the profile are still read.

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

| Field | Meaning |
|---|---|
| `soft` | `system` (Android keyboard) or `none` (never shown) |
| `showOnFocus` | Default `true`. `false`: the keyboard does not appear when tapping a field, only via the keyboard button in the toolbar or the hotkey |
| `showOnPageLoad` | Show the keyboard after every page load |
| `backKey` | Macro for the back key; `{BACK}` = go back or open the menu |
| `remap` | Hardware key (`key`: Android name without `KEYCODE_`, e.g. `F1`, `BUTTON_L1`, `ENTER`, or a number) → macro. `system` passes the key through, empty swallows it. |
| `hotkeys` | Action → key. Actions: `toggleKeyboard`, `menu`, `home`, `reload`, `back`, `forward`, `scan` |
| `longPressDigitsAsF` | Long press on 1–9/0 sends F1–F10 |

**Macros** (case-insensitive): plain text is typed,
`{TAB}`, `{BACK-TAB}`, `{ENTER}`, `{ESC}`, `{BS}`, `{DELETE}`, `{INSERT}`, `{HOME}`,
`{END}`, `{PAGE-UP}`, `{PAGE-DOWN}`, `{UP}` `{DOWN}` `{LEFT}` `{RIGHT}`, `{SPACE}`,
`{F1}`–`{F12}`, `{CTRL-C}`, `{HEX:00E4}`, `{OPEN-BRACE}`/`{CLOSE-BRACE}`,
`{PAUSE}` / `{PAUSE:500}`, actions `{BACK}`, `{NAVIGATE-BACK}`,
`{NAVIGATE-FORWARD}`, `{NAVIGATE-STOP}`, `{NAVIGATE-HOME}`, `{NAVIGATE-REFRESH}`,
`{SPECIAL:SCAN}`, `{MENU}`, `{KEYBOARD}`. Unknown tokens are typed as text.

## `injections`

Add your own CSS, JavaScript or HTML to pages – for example larger input fields for a
third-party web app, or a scan hook.

```json
"injections": [
  { "match": { "url": "/sap/bc/gui/sap/its/", "type": "contains" }, "when": "documentStart", "css": "res:sap.css" },
  { "match": { "url": "^https://wms\\.example\\.com/pick", "type": "regex", "source": "id=\"pickForm\"" },
    "when": "pageLoaded", "js": "res:pick.js", "frames": "main" },
  { "html": "<div class='banner'>TEST SYSTEM</div>", "position": "bodyStart" }
]
```

| Field | Meaning |
|---|---|
| `match.url` / `match.type` | `contains` (default), `prefix`, `regex`; without `url` the injection applies to every page |
| `match.source` | Only inject if the page source contains this text |
| `when` | `documentStart` (before first render, no flicker – default) or `pageLoaded` |
| `frames` | `main` (default) or `all` (only with `documentStart`) |
| `css` / `js` / `html` | Inline content or `res:<name>`; `<script>` inside HTML is executed |
| `position` | For HTML: `bodyEnd` (default), `bodyStart`, `head` |

## `errorPages` and `sounds`

```json
"errorPages": { "template": "res:error.html", "showHttpErrors": false },
"sounds": { "scanOk": "none", "error": "tone:1400:200:1000:200" }
```

- `template` – your own HTML with the placeholders `{{title}}`, `{{message}}`, `{{url}}`,
  `{{color}}` (brand color), `{{code}}` (translated "Error code: IKB-…", see [error codes](error-codes.md)),
  `{{retry}}` (label of the retry button) and `{{lang}}` (language tag for `<html lang>`). The link `industrialkioskbrowser://retry` reloads (`ebrowser://retry` from older templates still works). Without a template
  the built-in error page is used (see [image](images/screen-error.png)).
- `showHttpErrors` – also replaces the server's error pages (HTTP ≥ 400).
- Sounds: `none`, `beep`, `error`, `tone:<Hz>:<ms>[:<Hz>:<ms>…]` or `res:<name>`
  (audio file). `error` plays on error pages and blocked pages.

## `kiosk`

| Field | Default | Meaning |
|---|---|---|
| `hideToolbar` | `false` | Hide the toolbar (menu then via the back key or the hotkey) |
| `immersive` | `false` | Hide status and navigation bars |
| `keepScreenOn` | `true` | Keep the display on |
| `orientation` | `auto` | `auto`, `portrait`, `landscape` |

For a full kiosk also set `global.hideExit` and allow the app for **lock task** in your
MDM (dedicated device) – it then starts locked automatically.
Template: [`kiosk-single-app.json`](../examples/kiosk-single-app.json).

## `scanner`

| Field | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Scanner active when a page loads |
| `terminator` | `enter` | Keys sent after inserting: `none`, `tab`, `enter`, `tab+enter`, `enter+tab` |
| `injectIntoFocusedField` | `true` | Type the scan into the focused input field |
| `genericIntent` | `auto` | Accept scans from the broadcast `<package>.action.BARCODE` (see *Others* below): `auto` = only on devices without a Zebra or Honeywell scanner, `on`, `off`. Any app on the device can send this broadcast and thus type fake scans – turn it `off` where no other scanner app is used. |
| `engine` | `auto` | What reads the barcodes: `auto` = the scan engine of the device, or the camera if it has none; `hardware` = always the scan engine (as before 0.7.1); `camera` = always the camera, also on devices with a scan engine. |

| Device | Integration |
|---|---|
| Zebra | DataWedge intent API. On start a DataWedge profile "Industrial Kiosk Browser" is created (intent output on, keystroke off). Decoder settings are left alone and can be tuned in DataWedge/StageNow. DataWedge is a separate app, so the scan receiver has to be open to other apps as well; Android cannot tell who sent a scan. |
| Honeywell | Intent API of the Data Collection Service (the scanner is claimed with data intent output). |
| Camera | Devices without a scan engine (`engine: "auto"`) or `engine: "camera"`: the scan button and `scanner.trigger()` open a full-screen camera view with an aiming frame and a torch button; the first barcode read closes it and is delivered like a hardware scan. Decoding with zxing-cpp on the device, nothing leaves the device. Android asks for the camera permission on the first scan (or the MDM grants it). On Zebra, "no scan engine" means DataWedge lists neither a built-in scanner (`INTERNAL_IMAGER`, `INTERNAL_LASER` …) nor a connected Bluetooth/USB scanner; until DataWedge answers (at most 2 s after app start) the scan engine is assumed. Honeywell devices always count as having one. |
| Others | Any scanner app that broadcasts `de.ictbaden.industrialkioskbrowser.action.BARCODE` with the barcode in an extra (`data`, `…data_string`, `…decode_string`, `barcode_string`). By default only on devices without a Zebra or Honeywell scanner – `scanner.genericIntent: "on"` accepts it everywhere. |

Pages can control the scanner with meta tags – see [Web integration](web-integration.md).

Every field of a profile except the lists (injections, key remapping, hotkeys, replacements and
rules of the scan data) can also be changed on the device: **Settings → tap a profile**.

## `scanner.camera`

Settings of the camera scanner (`engine: "camera"` or devices without a scan engine). They do not
change the scan engine of the device – its decoders, aiming and illumination are set in
DataWedge or the Honeywell settings. The same fields exist in Industrial Keyboard.

| Field | Default | Meaning |
|---|---|---|
| `symbologies` | `[]` | Barcode types the camera reads; empty = all. Names: `aztec`, `codabar`, `code39`, `code93`, `code128`, `databar`, `databarexpanded`, `databarlimited`, `datamatrix`, `ean8`, `ean13`, `itf`, `maxicode`, `pdf417`, `micropdf417`, `qr`, `microqr`, `rmqr`, `upca`, `upce` |
| `aim` | `frame` | `frame`: only codes whose centre lies inside the aiming frame. `center`: only the code under the red crosshair in the middle (pick list – for labels with many codes side by side). `anywhere`: the whole picture, the code nearest the middle wins |
| `continuous` | `false` | The camera stays open and enters every new code at once (stock taking); close with ✕. The screen shows how many codes were entered and the last one |
| `duplicateDelayMs` | `1500` | Continuous mode: the same code is entered again only after this time (0–10000) |
| `confirmReads` | `1` | The same code must be read in this many frames in a row (1–3) – against misreads of damaged 1D codes |
| `minLength`, `maxLength` | `0` | Accepted text length (0 = no limit). Other codes are ignored and the camera keeps looking |
| `zoom` | `1.0` | Zoom when the camera opens (1.0–4.0). A button on the camera screen switches 1× → 2× → 3× |
| `torch` | `false` | Flashlight on when the camera opens |
| `inverted` | `true` | Also read light codes on a dark background |
| `beep`, `vibrate` | `true` | Feedback for every code read |
| `timeoutSeconds` | `60` | The camera closes after this time without a code (0–600, 0 = never) |

A configuration barcode (*Settings → Import from barcode*) is always read with the defaults
above, in the whole picture and at a higher resolution.

## `scanner.ocr`

Text recognition (OCR) with the camera – serial numbers, batch numbers, best-before dates, meter
readings that have no barcode. Recognition with Google ML Kit (bundled Latin model) on the device;
see [Data and privacy](data-and-privacy.md#text-recognition-ml-kit) for what ML Kit sends to
Google. The camera screen gets a switch **Barcode | Text**. Text recognition is also in the menu
(*Recognise text*) and in the JS bridge (`scanner.trigger({mode: "text"})`) – on devices with a
scan engine too, as long as there is a camera. The recognised text goes through
[`scanner.data`](#scannerdata) with the type `ocr` and reaches the page with `source: "ocr"`.
The camera settings `continuous`, `duplicateDelayMs`, `beep`, `vibrate`, `torch`, `zoom` and
`timeoutSeconds` apply in text mode as well.

| Field | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Text recognition is offered (switch on the camera screen, menu, JS bridge) |
| `mode` | `pick` | `pick`: the recognised lines inside the frame are shown as yellow boxes, tap one to take it. `pattern`: the first line inside the frame that fully matches `pattern` is taken by itself |
| `pattern` | `null` | Java regular expression the whole (cleaned) line must match. Required for `pattern`; in `pick` mode it limits the lines offered |
| `startInOcr` | `false` | The camera opens in text mode instead of barcode mode |
| `confirmReads` | `2` | `pattern` mode: the same text must be read in this many frames in a row (1–5) – OCR misreads single characters more often than a barcode decoder |
| `minLength`, `maxLength` | `0` | Accepted line length after the clean-up (0–999, 0 = no limit) |
| `charset` | `any` | Clean-up of each line before length and pattern: `any` (spaces collapsed), `digits` (only `0-9 , . -`), `alnum` (letters and digits), `upper` (letters and digits, upper case) |

Examples:

```json
"ocr": { "mode": "pattern", "pattern": "[A-Z]{2}\\d{6}", "charset": "upper" }
```

takes serial numbers like `AB123456` by itself (in JSON the backslash is written twice).

```json
"ocr": { "mode": "pattern", "pattern": "\\d{2}\\.\\d{2}\\.\\d{4}", "startInOcr": true }
```

reads a best-before date like `31.12.2027` as soon as the camera opens. A meter reading:
`{ "charset": "digits", "minLength": 4 }`.

## `scanner.nfc`

Reading NFC tags (badges, containers, storage locations) while the browser is in front, with
Android's reader mode – no other app gets the tag meanwhile. Only when the profile's scanner is
active and the device has NFC switched on (*Settings → profile → NFC* shows the state and opens
the system settings). The value goes through [`scanner.data`](#scannerdata) with the type `nfc` and
reaches the page like a scan with `source: "nfc"`.

| Field | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Read NFC tags |
| `read` | `ndefOrUid` | `uid`: the tag ID. `ndef`: the text or link stored on the tag (NDEF Text, URI, `text/…` records). `ndefOrUid`: the stored text, the tag ID when there is none |
| `uidFormat` | `hex` | `hex`: upper-case hexadecimal (`04A23B7F`); `decimal`: the bytes as one unsigned number (`77740927`) |
| `uidReversed` | `false` | Reverse the byte order – many badge readers show the UID reversed |
| `uidSeparator` | `""` | Between hex bytes, e.g. `":"` → `04:A2:3B:7F` |
| `ndefRecord` | `first` | `first`: the first readable record; `allLines`: all readable records, one per line |
| `beep`, `vibrate` | `true` | Feedback for every tag read |

A tag without the data `read` asks for shows `IKB-0034`. Example for badge numbers as an
access-control reader prints them: `{ "read": "uid", "uidFormat": "decimal", "uidReversed": true }`.

## `scanner.data`

Formatting of the barcode text before it is entered – for every scan the app receives: camera,
Zebra DataWedge, Honeywell and the `BARCODE` broadcast. (Scans that another app types as
keystrokes never pass through the app.) The same fields exist in Industrial Keyboard.

| Field | Default | Meaning |
|---|---|---|
| `prefix` | `""` | Text before the code |
| `suffix` | `null` | Text after the code. Keys after the scan stay `scanner.terminator` |
| `trim` | `true` | Remove spaces and control characters at the start and end (not the GS separator) |
| `removeSpaces` | `false` | Remove all spaces |
| `case` | `none` | `none`, `upper`, `lower` |
| `stripLeading`, `stripTrailing` | `0` | Remove this many characters at the start / end (0–999), e.g. a check digit |
| `replace` | `[]` | Replacements in order: `{ "find": "-", "with": "" }`; with `"regex": true` `find` is a Java regular expression and `with` may use `$1` |
| `gs1` | `hri` | Camera only: GS1 codes as `(01)…(10)…` (`hri`) or raw with the group separator GS, U+001D (`raw`) |
| `gsReplacement` | `null` | Replaces the GS separator in every scan, e.g. `""` (remove) or `"|"`; `null` keeps it |
| `rules` | `[]` | Rules per code, see below |
| `unmatched` | `send` | When `rules` exist and none matches: `send` (with the settings above) or `drop` |

Order: GS replacement and trim → strip → remove spaces → replace → case → prefix and suffix. A
code with nothing left is dropped. On the device, the dialog *Settings → profile → Scan data*
offers every field except `replace` and `rules`.

**Rules** – the first rule whose conditions all match a code decides; its fields override the
ones above for that code, fields left out keep them:

```json
{ "name": "GTIN", "symbologies": ["ean13", "upca"], "minLength": 0, "maxLength": 0,
  "startsWith": "", "pattern": "", "action": "send",
  "prefix": null, "suffix": null, "case": null, "stripLeading": null, "stripTrailing": null,
  "removeSpaces": null, "replace": null }
```

Conditions: `symbologies` (names as above; a code whose type is unknown matches only rules
without `symbologies`), `minLength`/`maxLength`, `startsWith`, `pattern` (Java regular expression
that has to match the whole trimmed text). `action`: `send` or `drop`. A code the rules drop is
not entered; the camera ignores it and keeps looking.

### Examples

Rules can also name `ocr` (text recognition) and `nfc` (NFC tags) in `symbologies`.

Strip the check digit of EAN-13 codes, leave everything else as it is:

```json
"data": { "rules": [ { "symbologies": ["ean13"], "stripTrailing": 1 } ] }
```

Accept EAN-13 only, ignore every other code:

```json
"data": { "rules": [ { "symbologies": ["ean13"] } ], "unmatched": "drop" }
```

GS1 codes (e.g. GS1 DataMatrix with batch and expiry date) read raw by the camera, with the
group separator turned into a tab character. The browser inserts the text as it is – a tab
character inside a field does not move on to the next field, so the page has to split the value;
`"|"` instead of `"\t"` may be easier for the page:

```json
"data": { "gs1": "raw", "gsReplacement": "\t" }
```

## Resources

```json
"resources": {
  "sap.css":  { "text": "input { min-height: 44px; font-size: 18px; }" },
  "logo.png": { "base64": "iVBORw0KGgo..." }
}
```

Resources are shared by all profiles. They are referenced as `res:<name>` and available to
pages at `https://appassets.androidplatform.net/res/<name>` (e.g. images in injected CSS).
Names may contain folders (`css/a.css`), but no `..`.

## Custom design

![Default design and custom branding](images/branding.png)

Three levels:

1. **Branding** (`global.branding`) – title, subtitle, logo and brand color of the start
   screen; the color also applies to the settings and error pages.
2. **Profile color** (`profiles[].color`) – tints the profile card, toolbar and menu of
   that web app, so people can see at a glance where they are.
3. **Injections** – fine-tuning of the web app itself (font sizes, buttons, banners).

Without an MDM the design can also be set right on the device: **Settings → Design**
(title, brand color from a palette or as a hex value, logo from an image file). Text and
icon colors adapt automatically – even light brand colors stay readable.

<p>
  <img src="images/screen-color-picker.png" width="260" alt="Pick the brand color">
  <img src="images/screen-profile-editor.png" width="260" alt="Profile with its own color">
</p>
