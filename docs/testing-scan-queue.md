[← README](../README.md)

# Testing the scan queue

How to reproduce "scan while the form is saving" on an emulator or a device with the generic scan
broadcast, and how to read the result. The queue itself is described under
[`scanner.queue`](configuration.md#scannerqueue); its logic is unit-tested in
`app/src/test/…/web/ScanQueueTest.kt`. This page is the manual check in a real WebView.

- [Tools](#tools)
- [Setup](#setup)
- [Scenarios](#scenarios)
- [Reading the result](#reading-the-result)

## Tools

| File | What it does |
|---|---|
| [`tools/scan-queue/server.py`](../tools/scan-queue/server.py) | Python 3 test server without dependencies: a goods-receipt form whose save takes `delay` ms, then the next page; keeps every saved value in order |
| [`tools/scan-queue/run.sh`](../tools/scan-queue/run.sh) | Fires `SCAN0001` … `SCANnnnn` as broadcasts from a shell on the device, waits, prints the server's check and the scans the app discarded (logcat) |
| [`tools/scan-queue/profile-emulator.json`](../tools/scan-queue/profile-emulator.json) | Profile for the emulator (`http://10.0.2.2:8767/`, `genericIntent: "on"`, terminator Enter, queue with the defaults) |

Server pages (all take `delay=<ms>`, default 1500):

| URL | Page |
|---|---|
| `/?mode=form` | Classic form: Enter posts it, the server waits `delay`, then redirects (303) to page *n + 1* |
| `/?mode=js` | JavaScript saves with `fetch()`, then loads page *n + 1*. `&busy=1` calls `scanner.busy()` / `scanner.ready()` |
| `/?mode=spa` | JavaScript saves with `fetch()` and stays on the page (single-page app). `&busy=1` as above |
| `&focus=<ms>` | The field gets the focus only this long after the page loaded |
| `/log` | Saved values in order, first line `saved=… highest=… missing=… duplicates=… outOfOrder=…` |
| `/reset` | Clears the log (a scan with the value `RESET` does the same from the page) |

The JavaScript API (`busy=1`) is only available on the default port (80/443) – see
[Web integration](web-integration.md#javascript-api). For `busy=1` start the server with
`--port 80` and use `http://10.0.2.2/` (emulator) or `http://<pc-ip>/` (device on Wi-Fi).

## Setup

Debug builds have the package `de.ictbaden.industrialkioskbrowser.debug`; set `PKG` accordingly.

```bash
PKG=de.ictbaden.industrialkioskbrowser.debug
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
python3 tools/scan-queue/server.py                      # http://0.0.0.0:8767/
adb shell am start -n $PKG/de.ictbaden.industrialkioskbrowser.ui.MainActivity   # once, creates the app folder
adb push tools/scan-queue/profile-emulator.json \
  /sdcard/Android/data/$PKG/files/industrialkioskbrowser.json
```

The file is applied within a few seconds (Android 11 or later, no admin password set; otherwise see
[Deployment](deployment.md#configuration-file)). Open the profile "Scan queue test".

**On a device over USB** instead of the emulator: `adb reverse tcp:8767 tcp:8767`, and in the
profile `"startUrl": "http://127.0.0.1:8767/?mode=form&delay=1500"`, `"allowList": ["127.0.0.1"]`.

One scan by hand:

```bash
adb shell am broadcast -a $PKG.action.BARCODE -p $PKG --es data SCAN0001 --es symbology CODE128
```

## Scenarios

Each run: open the page in the app (address bar of the profile, or change `startUrl`), then
`PKG=$PKG tools/scan-queue/run.sh -n <count> -d <seconds>`. Expected with the queue on: every value
saved once, in order, nothing discarded. The two right-hand columns are what the design expects;
record what you measured.

| # | Page | Run | What it shows | Expected with the queue | Without (`queue.enabled: false`) |
|---|---|---|---|---|---|
| 1 | `/?mode=form&delay=1500` | `-n 10 -d 0.5` | Scans while the form posts and the next page loads (rules 1 and 2) | 10 saved, in order; the scan button shows the waiting count | scans go into the old page and are lost with the navigation |
| 1b | `/?mode=form&delay=1500` | `-n 15 -d 0.2` | A long queue that is worked off steadily | 15 saved, in order, nothing discarded (`timeoutMs` counts from the last progress) | – |
| 2 | `/?mode=form&delay=0` | `-n 20 -d 0` | Fast bursts with fast pages | 20 saved, in order | at high rates values can merge in the field ([scan stress test](testing-scan-stress.md), run 2) |
| 3 | `/?mode=spa&delay=1500&busy=1` (port 80) | `-n 10 -d 0.3` | Page reports busy while `fetch()` saves | 10 saved, in order | values typed into the field during the save |
| 4 | `/?mode=spa&delay=1500` (no busy) | `-n 10 -d 0.5` | JavaScript save without `busy()`, field emptied only after the save | 10 saved, in order: the next scan waits while the field still holds the previous one (`waitForFieldClear`) | values appended in the field (`other` shows `SCAN0001SCAN0002`) |
| 4b | as 4, profile with `"waitForFieldClear": false` | `-n 10 -d 0.5` | What the field check prevents | values appended after `navigationGraceMs` | same |
| 5 | `/?mode=js&delay=1500&busy=1` (port 80) | `-n 5 -d 0.3` | JavaScript save, then navigation, with `busy()` | 5 saved, in order | lost with the navigation |
| 6 | `/?mode=form&delay=500&focus=1500` | `-n 5 -d 0.2` | Next page focuses its field late (no field yet) | scans wait for the field (tried every 250 ms), 5 saved | event only, nothing typed |
| 7 | `/?mode=form&delay=20000` | `-n 3 -d 0.2` | Server slower than `timeoutMs` (15 s) | 15 s after the first save: error tone and `IKB-0041` for the second scan; the third gets 15 s again and goes into the next page | – |
| 8 | `/?mode=form&delay=3000` | `-n 30 -d 0` | More than `maxPending` (20) waiting | the 21st and later: error tone, `IKB-0040` | – |

For "without the queue" set `"queue": { "enabled": false }` in the profile, push it again and
repeat. While scans wait, `await IndustrialKioskBrowser.scanner.status()` in Chrome DevTools
(`chrome://inspect`, debug build) shows `pending` and `ready`.

## Reading the result

`run.sh` prints the first line of `/log`, for example

```
saved=10 highest=10 missing=none duplicates=0 outOfOrder=0 other=[]
```

`other` lists saved values that are not a single `SCANnnnn` – merged values such as
`"SCAN0003SCAN0004"` or empty saves show up there. The page itself lists the saved values, and the
same summary is in the window title (`IKB-Q saved=… miss=… dup=… ooo=… other=…`), readable with
`adb shell uiautomator dump`. Scans the app discarded are logged as
`W BrowserActivity: n scan(s) discarded: TIMEOUT|FULL|PAGE_LEFT|CLOSED`.

## Measured

On a Zebra device with Android 15 (DataWedge device, scans sent as generic broadcasts from a shell on
the device, terminator Enter), debug builds of `main`:

| Date | Page | Scans | Queue settings | Result |
|---|---|---|---|---|
| 2026-11-24 | `mode=form`, `delay=1500` | 10, 0.5 s apart | defaults | 10 saved, in order |
| 2026-11-24 | `mode=form`, `delay=1500` | 15, 0.2 s apart | defaults | 15 saved, in order |
| 2026-11-24 | `mode=spa` | 10, 0.5 s apart | defaults | 10 saved, in order |
| 2026-11-24 | `mode=form`, queue off | 10, 0.5 s apart | `enabled: false` | values run together (the behaviour before 0.9.0) |
| 2026-11-25 | `mode=form`, `delay=0` | 200 without a pause (about 20 per second) | `maxPending: 200` | 200 saved, in order, no duplicates, none discarded |

With the default `maxPending` of 20, a burst like the last one fills the queue; scans above the limit
are discarded with the error tone and a message instead of running together. Not measured: Honeywell
devices, Android 8 to 14, scans from the scan engine at this rate (a person does not scan that fast).
