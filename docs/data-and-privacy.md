[← README](../README.md)

# Data and privacy

- [Summary](#summary)
- [What is stored on the device](#what-is-stored-on-the-device)
- [Connections](#connections)
- [Broadcasts](#broadcasts)
- [Deleting everything](#deleting-everything)

## Summary

Pforte **sends no data to ICT-Baden**. There is no telemetry, no crash reporting,
no update check and no account. The browser connects to the web apps you configure (the start
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
| Google Safe Browsing (by the Android System WebView) | While pages load, the WebView compares shortened hashes of the addresses with a list from Google to warn about malware and phishing. This is a feature of Android that Pforte deliberately leaves on; the app adds nothing to it. |
| Links in *Settings → Info* (source code, support, website, legal notice) | Only when you tap them; they open in the device's browser. |

Nothing else from the app itself. Debug builds additionally allow remote debugging with Chrome DevTools over USB.

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
- Android *Settings → Apps → Pforte → Storage → Clear storage*, or uninstalling the app, removes all of it.
