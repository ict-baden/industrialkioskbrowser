[← README](../README.md)

# Emulator tests

Manual test run of the release build of `main` (f63c5c0, 0.7.0, versionCode 9) on the Android emulator,
2026-11-02. Two AVDs (Pixel 4, arm64, Google APIs): **API 30** (Android 11, Android System WebView 91) and
**API 35** (Android 15, WebView 124). The unsigned release APK was signed with the debug keystore for the test.
Test page: a local HTML form (text field, password field, links to another host and to unreachable
addresses) served from the host (`http://10.0.2.2:8765/`), scans sent with the generic broadcast
(`scanner.genericIntent: "on"`). Findings are tracked as issues #38–#41.

- [Results](#results)
- [Observations](#observations)
- [How the tests were run](#how-the-tests-were-run)
- [Not covered](#not-covered)

## Results

| Test | API 30 | API 35 | Result | Notes |
|---|---|---|---|---|
| Back key on an error page (`IKB-0001`, `IKB-0005`) skips it and returns to the previous page | ✅ | ✅ | pass | The previous page is reloaded from history (WebView behaviour); the text field is restored, the password field is not. |
| Back key with nothing to go back to opens the menu | ✅ | ✅ | pass | Also with `hideToolbar` (kiosk example). |
| Tap on the launcher icon keeps the web session | ✅ | ✅ | pass | Page not reloaded, a page counter keeps running. Only holds when the task was started by the launcher (`MAIN`/`LAUNCHER` intent); `am start -n …` without that action adds a second start screen – a test artefact, not a bug. |
| `INSTALL_CONFIG` while a profile is open takes effect right away | ✅ | ✅ | pass | `result=-1`, session restarts within ~0.3 s with the new profile name and allow list. |
| Configuration file while a profile is open takes effect right away | ✅ | ✅ | pass | Applied within the 5 s check, file deleted (it contained the password). |
| Configuration file needs the admin password | ✅ | ✅ | pass | Without or with a wrong `global.adminPassword`: refused (`admin_password is missing or wrong`), file stays, profile unchanged. With the right one: applied and deleted. The first file on a fresh install (no password yet) is accepted on Android 11+ as documented. |
| `INSTALL_CONFIG` needs the admin password | ✅ | ✅ | pass | `result=2` without or with a wrong `admin_password`; the refusal shows under *Settings → Last provisioning error* (`IKB-0100`). |
| `examples/minimal.json` | ✅ | ✅ | pass | Profile "Intranet", opens on start (error page `IKB-0001`, the host does not exist). |
| `examples/sap-its-mobile.json` | ✅ | ✅ | pass | Profile "SAP mobile", opens on start, portrait. Sets the admin password to `CHANGE-ME`. |
| `examples/kiosk-single-app.json` | ✅ | ⚠️ | pass | Toolbar and system bars hidden, menu without *Exit* and *Profiles*, scan + Enter. On Android 15 the system shows "Viewing full screen … Got it" once over the app (#40). |
| `examples/multi-profile-branding.json` | ✅ | ✅ | pass | Title "North Logistics Center", subtitle, brand colour `#00796B`, profile colours and badges (*Autostart*, *Kiosk*, *Allow list*), *Profiles* in the menu, "Shipping" sends Tab after a scan. |
| `examples/injections-and-keys.json` | ✅ | – | pass | `terminator: none` (no Enter), `BUTTON_L1` → Tab, `BUTTON_R1` → Shift+Tab, long press `1` → F1, `F5` reloads, `F12` toggles the keyboard, `showOnFocus: false`, CSS injection on `/pick/`, scan hook navigates on `LOC-…`. On WebView 91 injections run after page load (`DOCUMENT_START_SCRIPT unsupported`), as documented. |
| Examples as a configuration file | ✅ | ✅ | pass | Same mechanism as the password tests above; see [Deployment](deployment.md#configuration-file). |
| Examples as managed configuration | – | – | not tested | No EMM/DPC on the emulator, see [Not covered](#not-covered). |
| Scan broadcast into a focused text field | ✅ | ✅ | pass | `industrialkioskbrowser:scan` event, `input` event, Enter, form submitted. |
| Scan broadcast into a focused password field | ⚠️ | ⚠️ | observation | Typed into the password field and submitted with Enter, like a keyboard wedge (#38). |
| Scan broadcast without a focused field | ✅ | ✅ | pass | Only the event fires, nothing is typed. |
| Allow list blocks a foreign host | ✅ | ✅ | pass | Toast "Page not allowed: example.com (IKB-0010)", the page stays. |
| Allow list extended by `INSTALL_CONFIG` without restart | ✅ | ✅ | pass | Session restarts, the link loads. |
| Empty allow list allows every host | ✅ | ✅ | pass | Documented behaviour, confirmed. |
| Error page without network, network returns | ⚠️ | ⚠️ | observation | `IKB-0001 (net::ERR_INTERNET_DISCONNECTED)`; no automatic reload after 30–40 s online, *Try again* works, *Home* in the menu works (#41). |
| Rotation (portrait → landscape → reverse landscape → portrait) | ✅ | ✅ | pass | No reload, form content kept, no crash. |
| Language de / en / es / fr / zh-Hans | ✅ | – | pass | Start screen, password dialog, settings, menu and error page fully translated, nothing cut off, no `{0}`, no English leftovers. Spanish, French and Chinese are marked as machine translations. |
| Language change by `INSTALL_CONFIG` while a profile is open | ⚠️ | – | bug | The restarted session stays in the old language until the start screen is shown again (#39). |
| Update from 0.5.0 (`de.ictbaden.enterprisebrowser`) | – | – | not possible | Different package name and signing key (`aapt dump badging`, `apksigner verify`), as documented in [Limitations](limitations.md#releases-and-updates) and [Deployment](deployment.md): reinstall and configure again. |
| Update between two builds of the same package (8630ba3 → main, both versionCode 9) | ✅ | ✅ | pass | `adb install -r -d`: profiles, admin and exit password, language, branding (title, subtitle, colour) and profile colour survive. |
| Cold start | 4.5 s first start after install, then 0.3–0.6 s | 1.0 s, then 0.2–0.6 s | – | `am start -W` TotalTime of the start screen; the browser screen follows in 0.1–1.2 s. |
| Permission dialogs on first start | none | none | pass | The camera permission is only requested for *Import from barcode*. |
| Crash / ANR in logcat | none | none | pass | One `RuntimeException: Bad file descriptor` from a process that was being force-stopped by the test script on API 35, not reproducible with a continuous logcat capture; no `FATAL EXCEPTION`, no ANR. |
| Back key on the start screen | exits the app | exits the app | – | |
| Admin access | gear on the start screen, *Settings* in the menu | same | pass | Asks for the admin password once one is set; a wrong password closes the dialog. There is no gesture or hotkey for the settings; the `menu` hotkey opens the menu only. |

## Observations

- **Scan into a password field (#38):** a scan is typed into whatever field has the focus, password fields
  included, and the terminator submits the form. Decide whether this is wanted (badge login) and document it, or
  skip password fields.
- **Language change in an open profile (#39):** `AppLanguage.apply` runs in the application class, the start
  screen and the settings, but not when `BrowserActivity` restarts after a configuration change.
- **Android 15 full-screen hint (#40):** the first `kiosk.immersive` start shows the system hint "Viewing full
  screen", which has to be confirmed with *Got it*; the back key does not close it.
- **No automatic retry after the network returns (#41):** the error page stays until *Try again*, *Home*, a
  hotkey or the back key.
- A refused configuration file is logged with a full stack trace at error level every time it is checked
  (cosmetic).
- Blocked navigations show a toast, not an error page, so a kiosk without toolbar just stays on the current page.

## How the tests were run

- AVDs: `avdmanager create avd -n ikb-api30 -k "system-images;android-30;google_apis;arm64-v8a" -d pixel_4`
  (and `android-35`), started headless with `emulator -avd … -no-window -no-audio -no-snapshot -memory 2048`.
- APK: `apksigner sign --ks ~/.android/debug.keystore …` on `app-release-unsigned.apk`;
  `adb install -r`. The old build for the update test was built from commit 8630ba3 in a git worktree.
- Broadcasts: `adb shell am broadcast -a de.ictbaden.industrialkioskbrowser.action.INSTALL_CONFIG
  -n de.ictbaden.industrialkioskbrowser/.provisioning.ConfigReceiver --es config "$(cat file.json)"
  --es admin_password '…'` (the result code and message are printed), scans as in
  [Web integration](web-integration.md#testing-without-a-scanner).
- Configuration file: on the emulator the shell user may not write into `Android/data/<package>/files`
  (`adb push` fails with *Permission denied* on API 30 and 35). Workaround on the emulator only: `adb root`,
  copy the file to `/data/media/0/Android/data/<package>/files/industrialkioskbrowser.json`, then
  `chown <app uid>:ext_data_rw`, `chmod 660`, `chcon u:object_r:media_rw_data_file:s0`. On real devices the
  documented `adb push` works. The start screen reads the file on resume (press Home, reopen the app); an open
  profile checks it every five seconds.
- Network off/on: `adb shell svc wifi disable && adb shell svc data disable` and `enable`.
- Rotation: `settings put system accelerometer_rotation 0` and `settings put system user_rotation 1|3|0`.
- Language: `global.language` in the configuration, then the start screen shown again.
- UI text was read with `uiautomator dump`, screenshots with `screencap`, logs with `logcat -d -v time`.

## Not covered

- **Zebra DataWedge** and **Honeywell** scanning – no hardware; only the generic broadcast was tested.
- **Managed configuration** (`config_json`, `start_url`, `kiosk_mode`, `admin_password`) and **lock task** –
  the emulator has no EMM or device-owner app.
- **Release signature** – the test APK was signed with the debug key; the CI signing and the update path
  with the release key were not tested.
- **`/enterprise/usr`** (Zebra) – does not exist on the emulator.
- **Downloads, file upload, client certificates, QR import** – not part of this run.
- Android 8–10 – only in the unit tests (Robolectric).
