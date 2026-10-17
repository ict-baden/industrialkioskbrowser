# Deployment

How the app and its configuration get onto the devices. The configuration is always the
same JSON file ([reference](configuration.md), [templates](../examples)) – only the
transport differs.

| Method | Suited for | Configuration |
|---|---|---|
| [Managed configuration](#managed-configuration-all-emms) | SOTI, Intune, Workspace ONE, … (Android Enterprise) | key `config_json` |
| [Configuration file](#configuration-file) | SOTI File Sync, Zebra StageNow/MX, adb | `enterprisebrowser.json` |
| [Broadcast intent](#broadcast-intent-stagenow-adb) | Zebra StageNow (Intent CSP), scripts, adb | extra `config` |
| [QR code](#qr-code) | single devices, testing | JSON inside the QR code |

Package name: **`de.ictbaden.enterprisebrowser`**

## Managed configuration (all EMMs)

The app ships a schema that every EMM with Android Enterprise support shows:

| Key | Type | Effect |
|---|---|---|
| `config_json` | String | **Complete configuration** (contents of a JSON file from `examples/`). Takes precedence. |
| `start_url` | String | Simple mode: creates a profile "managed" that opens on start |
| `profile_name` | String | Name of that profile |
| `kiosk_mode` | Bool | Toolbar and system bars hidden, exit hidden |
| `admin_password` | String | Sets the admin password |

The values are applied when the app starts and whenever you return to the profile list,
as soon as they have changed. Errors (e.g. invalid JSON) are shown in the app under
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
   put `enterprisebrowser.json` into `/enterprise/usr/` on the device with File Sync or a
   package (Zebra; survives an enterprise reset).
4. **Kiosk** – allow EnterpriseBrowser as the only app in a lockdown/kiosk profile. Under
   Android Enterprise (dedicated device) the app then starts in lock task mode
   automatically. Also set `global.hideExit` in the configuration
   ([template](../examples/kiosk-single-app.json)).
5. **Check** – open the app on a device: the profiles from the configuration appear on the
   start screen, and a profile with `launchOnStartup` opens right away.

## Microsoft Intune

1. On Android Enterprise devices the app has to be provided through **managed Google Play**
   (private app). Google Play only accepts release-signed APKs – the current debug APK is
   not suitable for this yet (release signing is planned).
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
- **Configuration as a file:** FileMgr CSP, target `/enterprise/usr/enterprisebrowser.json`.
- **Configuration via intent:** Intent CSP, *send broadcast* with
  action `de.ictbaden.enterprisebrowser.action.INSTALL_CONFIG`,
  package `de.ictbaden.enterprisebrowser` and string extra `config` (JSON).
  If an admin password is already set, also send the string extra `admin_password`.
- **Scanner:** on first start the app creates a DataWedge profile "EnterpriseBrowser".
  Decoders (symbologies, lengths, illumination …) can then be tuned with the DataWedge
  CSP or in the DataWedge app – the app does not overwrite them.

## Configuration file

The app reads `enterprisebrowser.json` from

1. `Android/data/de.ictbaden.enterprisebrowser/files/` (app folder; exists after
   the app has been started once)
2. `/enterprise/usr/` (Zebra, persistent)

A file is applied as soon as its timestamp or size changes – when the app starts or when
you return to the profile list.

```bash
adb push examples/sap-its-mobile.json \
  /sdcard/Android/data/de.ictbaden.enterprisebrowser/files/enterprisebrowser.json
```

## Broadcast intent (StageNow, adb)

```bash
adb shell am broadcast \
  -a de.ictbaden.enterprisebrowser.action.INSTALL_CONFIG \
  -n de.ictbaden.enterprisebrowser/.provisioning.ConfigReceiver \
  --es config '{"profiles":[{"name":"WMS","startUrl":"https://wms.example.com"}]}'
```

Result code (for ordered broadcasts): `-1` = installed, `2` = admin password missing or
wrong, `3` = invalid configuration.

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
