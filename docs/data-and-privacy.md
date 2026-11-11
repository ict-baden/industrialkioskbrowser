[← README](../README.md)

# Data and privacy

- [Summary](#summary)
- [What is stored on the device](#what-is-stored-on-the-device)
- [Connections](#connections)
- [Text recognition (ML Kit)](#text-recognition-ml-kit)
- [Broadcasts](#broadcasts)
- [Deleting everything](#deleting-everything)

## Summary

Industrial Kiosk Browser **sends no data to ICT-Baden**. There is no telemetry, no crash reporting,
no update check and no account. The text recognition library of Google (ML Kit) may send anonymous
usage statistics to Google – see [Text recognition](#text-recognition-ml-kit). The browser connects to the web apps you configure (the start
URLs) and to the pages they lead to. With an `allowList` only those hosts can be opened;
**with an empty `allowList` every host is allowed** – set one for kiosk devices.

The Android System WebView, which renders the pages, checks addresses against Google Safe
Browsing (see [Connections](#connections)).

The operator of the devices (your company) is responsible for the data in those web apps;
ICT-Baden processes none of it.

## What is stored on the device

In the app's private storage (other apps cannot read it):

| What | Content |
|---|---|
| `files/config.json` | The [configuration](configuration.md), including resources (logos, CSS, JS). Passwords only as salted PBKDF2 hashes. |
| `shared_prefs/provisioning.xml` | Which configuration sources were applied already and the last provisioning error. |
| Cookies, cache, web storage | Like any browser, for the configured web apps. Cleared per profile with `clearCookiesOnStart` / `clearCacheOnStart`. |
| `Android/data/<package>/files/Download/` | Files downloaded from web apps (only if `allowDownloads` is on). |

Configuration and browser data are excluded from Android backup and device transfer.

## Connections

| Connection | When |
|---|---|
| The web apps of your profiles | When a profile is opened, and to the hosts its pages navigate to. With an `allowList` only those hosts; with an empty `allowList` any host. |
| Google Safe Browsing (by the Android System WebView) | While pages load, the WebView compares shortened hashes of the addresses with a list from Google to warn about malware and phishing. This is a feature of Android that Industrial Kiosk Browser deliberately leaves on; the app adds nothing to it. |
| Links in *Settings → Info* (source code, support, website, legal notice) | Only when you tap them; they open in the device's browser. |

| Google (ML Kit usage statistics) | Possibly after text recognition was used – see [Text recognition](#text-recognition-ml-kit). |

Nothing else from the app itself. Debug builds additionally allow remote debugging with Chrome DevTools over USB.

## Text recognition (ML Kit)

Text recognition (`scanner.ocr`) uses Google ML Kit with the model built into the app: the camera
picture is analysed on the device, the picture and the recognised text are **not** sent anywhere.
ML Kit is not open source; it is used under the
[ML Kit Terms of Service](https://developers.google.com/ml-kit/terms). Google states that ML Kit
may send anonymous usage and performance statistics (for example which API was used, how long it
took, device model and Android version) to Google through Google's data transport library; the app
cannot switch this off for the bundled model. Where that is not acceptable, set
`scanner.ocr.enabled: false` in every profile – then no text is ever recognised (ML Kit only registers
itself when the app starts) – or block the device's access to
Google with your firewall/MDM.

NFC tags (`scanner.nfc`) are read on the device; nothing is sent.

## Broadcasts

On the device, the browser exchanges broadcasts with scanner services and the MDM – see
[Configuration → scanner](configuration.md#scanner) and [Deployment](deployment.md):

| Direction | What |
|---|---|
| sends | DataWedge or Honeywell API calls (profile, soft trigger) |
| receives | Scans from DataWedge, Honeywell or `<package>.action.BARCODE` |
| receives | `<package>.action.INSTALL_CONFIG` – only with the admin password, or on an EMM-managed device while no password is set |

## Deleting everything

- A profile with `clearCookiesOnStart` and `clearCacheOnStart` starts without old browser data.
- Android *Settings → Apps → Industrial Kiosk Browser → Storage → Clear storage*, or uninstalling the app, removes all of it.
