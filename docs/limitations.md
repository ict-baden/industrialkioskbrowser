[← README](../README.md)

# Limitations

- [Test scope](#test-scope)
- [Security trade-offs for intranets](#security-trade-offs-for-intranets)
- [Navigation and kiosk](#navigation-and-kiosk)
- [Languages](#languages)
- [Releases and updates](#releases-and-updates)

## Test scope

| What | Status |
|---|---|
| Zebra devices with DataWedge | Supported through the DataWedge intent API |
| Honeywell (Data Collection Service) | **Untested on hardware** – implemented from the documented intent API |
| Other Android devices | Run as a plain kiosk browser; scans only from a scanner app that sends the generic broadcast |
| SOTI MobiControl, Intune, Workspace ONE | Managed configuration follows the Android standard; not tested against each EMM |
| Android 8–10 | Only in unit tests (Robolectric), not on a device |
| Fast scanning (3–19 scans/s) | Measured on an Android 15 emulator with the generic scan broadcast, not on a Zebra device: [Scan stress test](testing-scan-stress.md) |

Everything else is covered by unit tests (`./gradlew testDebugUnitTest`): configuration format and
examples, URL policy, scan delivery, provisioning rules, back navigation, store texts.

## Security trade-offs for intranets

These are deliberate, because many warehouse and production web apps run in an intranet:

- **Plain HTTP is allowed** and **user-installed CA certificates are trusted** (the EMM installs
  company CAs into the user store), as are the CAs and pinned certificates in
  `global.trustedCertificates`. On an untrusted network an attacker could change HTTP pages.
  Use HTTPS wherever possible. An `allowList` entry written as a plain host (`wms.example.com`)
  allows `http` and `https` on every port; write `https://wms.example.com` to refuse plain HTTP
  for that host ([`allowList` entries](configuration.md#allowlist-entries)).
- **The JavaScript API (`window.IndustrialKioskBrowser`) is also offered to `http://` pages** of the allowed hosts.
  It can only beep, vibrate, show a toast, report device and scanner information and switch the
  scanner – no files, no configuration, no navigation outside the allow list.
- **An empty `allowList` allows every host.** Then the JavaScript API is limited to the host of
  the start URL. Set an allow list on kiosk devices.
- **The allow list applies to the page, not to what the page embeds.** Images, scripts and frames
  from other hosts are loaded. They cannot open the sign-in dialog for HTTP authentication, though:
  with an allow list only allowed hosts may ask for a user name and password. Without one every
  embedded host can – check the host name the dialog shows.
- **Scan broadcasts can be sent by any app.** DataWedge is a separate app, so the scan receiver
  has to be open to other apps; the same applies to the generic `<package>.action.BARCODE`
  broadcast, which is therefore off on Zebra and Honeywell devices by default
  (`scanner.genericIntent`).
- **Configuration from other apps** (`INSTALL_CONFIG`, file in the app folder) needs the admin
  password once one is set. On an EMM-managed device **without** admin password every app may
  send a configuration – always set one ([Deployment](deployment.md)).
- **Google Safe Browsing** of the Android System WebView stays on ([Data and privacy](data-and-privacy.md)).

## Scanner

- **Scans are also typed into password fields**, followed by the terminator – like a hardware
  scanner in keyboard mode. That is intended: signing in with a badge barcode needs it. A page that
  does not want it calls `preventDefault()` on the scan event while a password field has the focus
  ([What happens on a scan](web-integration.md#what-happens-on-a-scan)).

## Navigation and kiosk

- Lock task (real kiosk) needs a device owner, i.e. an EMM in *dedicated device* mode. Without it
  `hideExit` only hides the exit; the Android home and recents buttons still work.
- Pages that open new windows (`target="_blank"`, `window.open`) stay in the same view.
- **Android 12 and later show "Viewing full screen" once** when a profile with `kiosk.immersive`
  goes full screen for the first time on a device. The hint is Android's, lies above the page and
  stays until someone taps *Got it*; the back key does not close it. On a newly provisioned device
  either confirm it once, or let the EMM or the staging script switch it off before the first start
  (`adb shell settings put secure immersive_mode_confirmations confirmed`). According to the
  Android source it is not shown in lock task mode; not yet confirmed on a device.
- **A connection error reloads by itself when the network comes back** (Wi-Fi reconnects, the
  device leaves a dead spot) and when the app returns to the front. This only watches the network
  of the device: when the Wi-Fi is fine and only the server is down, the error page stays until
  someone taps *Try again* or a hotkey reloads. Certificate and HTTP errors never reload by
  themselves.
- An embedded resource (image, script, frame) from another host with an invalid certificate is not
  loaded; the page itself stays. Only a certificate error of the page, or of a resource on the same
  host, shows the error page `IKB-0007`.
- The menu that appears when text is selected only offers cut, copy, paste and select all. *Share*,
  *Web search* and the text actions of other apps (translate, assistant) are removed, because each
  of them opens another app.
- Downloads go to the app folder and need `allowDownloads`; `blob:` downloads are not supported.
  With an `allowList` only files from allowed hosts are downloaded (`IKB-0014`). The check looks at
  the address the page offers: a redirect answered by an allowed host is followed by Android's
  DownloadManager without a second check.
- `allowDownloads` is on by default. The notification of a finished download can open the file in
  another app when the device is not in lock task mode – switch downloads off on kiosk devices
  that do not need them.
- Some features (document-start injections, JavaScript API) need a recent Android System WebView;
  on old WebViews injections run after the page has loaded, and the API is missing.

## Languages

The user interface is available in English and German. **Spanish, French and Chinese are machine
translations** and are marked as such in *Settings → Language*; a review by native speakers is
still pending. The documentation is in English.

## Releases and updates

- Version 0.x: settings, JSON format and JavaScript API may still change.
- **Renamed in 0.7.0:** Industrial Kiosk Browser was called *Pforte* (0.6.x) and *EnterpriseBrowser* (up to 0.5.0)
  before. The package name changed from `de.ictbaden.pforte` and `de.ictbaden.enterprisebrowser` to
  `de.ictbaden.industrialkioskbrowser`, so Android treats it as a new app:
  there is no in-place update, and the old app keeps its data until it is uninstalled. See
  [Deployment](deployment.md) for the switch.
- Release APKs and the App Bundle for Google Play are signed by the CI with the release key of the
  new package name; the rolling `latest` build of `main` carries the same signature.
- The app does not update itself; updates come from the EMM or Google Play.
