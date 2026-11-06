# Deployment

How the app and its configuration get onto the devices. The configuration is always the
same JSON file ([reference](configuration.md), [templates](../examples)) – only the
transport differs.

| Method | Suited for | Configuration |
|---|---|---|
| [Managed configuration](#managed-configuration-all-emms) | SOTI, Intune, Workspace ONE, … (Android Enterprise) | key `config_json` |
| [Configuration file](#configuration-file) | SOTI File Sync, Zebra StageNow/MX, adb | `industrialkioskbrowser.json` |
| [Broadcast intent](#broadcast-intent-stagenow-adb) | Zebra StageNow (Intent CSP), scripts, adb | extra `config` |
| [QR code](#qr-code) | single devices, testing | JSON inside the QR code |

Package name: **`de.ictbaden.industrialkioskbrowser`**

> **Always set an admin password** (`global.adminPassword`, managed configuration key
> `admin_password` or *Settings → Admin password*). Without one, the settings are open on the
> device, and on an EMM-managed device **every app** may replace the configuration with the
> `INSTALL_CONFIG` broadcast (see [Broadcast intent](#broadcast-intent-stagenow-adb)).
> Use at least 10 characters. After 5 wrong passwords, broadcasts (and, counted separately,
> app-folder files) are refused for 15 minutes.

> **Coming from Pforte or EnterpriseBrowser?** Industrial Kiosk Browser is the same app under a new name and package
> name (`de.ictbaden.industrialkioskbrowser` instead of `de.ictbaden.pforte` or `de.ictbaden.enterprisebrowser`). Android and the EMM treat it
> as a new app: distribute Industrial Kiosk Browser, roll out the configuration again (the JSON format is
> unchanged; rename the file to `industrialkioskbrowser.json`), then remove the old app. Broadcast actions,
> the JavaScript API (`window.IndustrialKioskBrowser`, event `industrialkioskbrowser:scan`), the DataWedge profile name and
> the error codes (`IKB-…` instead of `PFT-…`/`EB-…`, same numbers) changed with the name.

## Managed configuration (all EMMs)

The app ships a schema that every EMM with Android Enterprise support shows:

| Key | Type | Effect |
|---|---|---|
| `config_json` | String | **Complete configuration** (contents of a JSON file from `examples/`). Takes precedence. |
| `start_url` | String | Simple mode: creates a profile "managed" that opens on start |
| `profile_name` | String | Name of that profile |
| `kiosk_mode` | Bool | Toolbar and system bars hidden, exit hidden |
| `admin_password` | String | Sets the admin password |

The values are applied when the app starts, whenever you return to the profile list and,
while a profile is open, as soon as the MDM changes them. An open profile whose settings changed
starts again with the new configuration. Errors (e.g. invalid JSON) are shown in the app under
**Settings → Last provisioning error**.

## SOTI MobiControl

Menu names differ between MobiControl versions and device management modes
(Android Enterprise or Android+). The flow:

1. **Upload the app** – add the APK from the [releases](../../../releases/latest) to the app
   catalog as an enterprise app (or as a package) and assign it to the devices.
   Since version 0.2.0 the APK contains PNG icons, so MobiControl shows the app icon.
2. **Configuration – option A, managed configuration** (Android Enterprise):
   open the app configuration of the enterprise app and paste the contents of the JSON
   file into `config_json`.
3. **Configuration – option B, file** (e.g. Android+ with the Zebra agent):
   put `industrialkioskbrowser.json` into `/enterprise/usr/` on the device with File Sync or a
   package (Zebra; survives an enterprise reset).
4. **Kiosk** – allow Industrial Kiosk Browser as the only app in a lockdown/kiosk profile. Under
   Android Enterprise (dedicated device) the app then starts in lock task mode
   automatically. Also set `global.hideExit` in the configuration
   ([template](../examples/kiosk-single-app.json)).
5. **Check** – open the app on a device: the profiles from the configuration appear on the
   start screen, and a profile with `launchOnStartup` opens right away.

## Microsoft Intune

1. On Android Enterprise devices the app has to be provided through **managed Google Play**.
   Use the [listing of ICT-Baden](https://play.google.com/store/apps/details?id=de.ictbaden.industrialkioskbrowser) (approve it for your organisation in managed Google
   Play). **Do not upload the APK as your own private app:** package names are unique in all of
   Google Play, so your upload would take `de.ictbaden.industrialkioskbrowser` for your
   developer account. See [Google Play](play-store.md) for how the listing is maintained.
2. Create an **app configuration policy** (managed devices), use the configuration designer
   and set `config_json` or `start_url` etc.
3. For kiosk use: enroll the device as a *dedicated device* and allow the app as the only
   app in the kiosk profile.

## VMware Workspace ONE / other EMMs

Add the app as an internal app or through managed Google Play and set the keys from the
table above in the **app configuration**. Every EMM that supports Android managed
configurations shows the schema automatically.

## Zebra StageNow / MX

- **Install the app:** AppMgr CSP with the APK.
- **Configuration as a file:** FileMgr CSP, target `/enterprise/usr/industrialkioskbrowser.json`.
- **Configuration via intent:** Intent CSP, *send broadcast* with
  action `de.ictbaden.industrialkioskbrowser.action.INSTALL_CONFIG`,
  package `de.ictbaden.industrialkioskbrowser`, string extra `config` (JSON) and string extra
  `admin_password` – see [Broadcast intent](#broadcast-intent-stagenow-adb) for when it is needed.
  Without an EMM the configuration file above is the simpler way.
- **Scanner:** on first start the app creates a DataWedge profile "Industrial Kiosk Browser".
  Decoders (symbologies, lengths, illumination …) can then be tuned with the DataWedge
  CSP or in the DataWedge app – the app does not overwrite them.

## Configuration file

The app reads `industrialkioskbrowser.json` from

1. `Android/data/de.ictbaden.industrialkioskbrowser/files/` (app folder; exists after
   the app has been started once)
2. `/enterprise/usr/` (Zebra, persistent; writable only by MX/StageNow, the EMM agent or adb)

A file is applied as soon as its timestamp or size changes – when the app starts, when you
return to the profile list or to an open profile, and within about five seconds while a profile
is open. An open profile whose settings changed starts again with the new configuration.

The app folder is checked like a broadcast, because before Android 11 any app with storage
permission can write into it:

- Once an admin password is set, the file must contain it in plain text as
  `global.adminPassword`. It proves the file comes from the administrator; it never replaces
  the password that is set. Exported configurations contain only the hash – add the plain
  password before pushing them. After the import the app **deletes** such a file, because
  before Android 11 other apps could read the password there.
- Without an admin password the file is accepted on EMM-managed devices and from Android 11 on
  (there only adb, i.e. someone with the unlocked device on USB, can write into the folder).
  On unmanaged Android 8–10 devices set a password first or use `/enterprise/usr/`.

A refused file is shown under **Settings → Configuration** and is only read again when it
changes (a file that arrives while wrong passwords have locked the check is read once the
lock has expired). Files in `/enterprise/usr/` are trusted and may also set a new admin password.

```bash
adb push examples/sap-its-mobile.json \
  /sdcard/Android/data/de.ictbaden.industrialkioskbrowser/files/industrialkioskbrowser.json
```

## Broadcast intent (StageNow, adb)

Any app on the device can send this broadcast, and Android does not tell the browser which one
did. So it is only accepted

- with the admin password in the string extra `admin_password`, once a password is set, or
- while no admin password is set: only on a device managed by an EMM (the app has a managed
  configuration, or the device or work profile has an owner, i.e. Android Enterprise).

On an unmanaged device without a password the broadcast is refused (shown under
**Settings → Configuration**). After 5 wrong passwords in a row, broadcasts are refused for
15 minutes, even with the right password. Any app can trigger this lock; configuration files
and the managed configuration are not affected by it. Set an admin password first – in the app, in the configuration file
or with the managed configuration key `admin_password` – or use the configuration file.

```bash
adb shell am broadcast \
  -a de.ictbaden.industrialkioskbrowser.action.INSTALL_CONFIG \
  -n de.ictbaden.industrialkioskbrowser/.provisioning.ConfigReceiver \
  --es config '{"profiles":[{"name":"WMS","startUrl":"https://wms.example.com"}]}'   --es admin_password 'change-me'
```

Result code (for ordered broadcasts): `-1` = installed, `2` = refused (admin password
missing or wrong, locked after wrong passwords, or unmanaged device without a password),
`3` = invalid configuration. An open profile whose settings changed starts again right away.

## QR code

In the app choose **Settings → Import from barcode** and scan a QR code containing the JSON
configuration. A QR code holds a little under 3 KB – use a managed configuration or a file
for larger configurations (resources, logo).

```bash
qrencode -r examples/minimal.json -o config-qr.png
```

## Updates and signing

Android only installs an update if the new APK is signed with the same key as the installed
one. The current releases are **debug-signed**. When switching to a release key later, the
app has to be uninstalled and reinstalled once (then distribute the configuration again).
