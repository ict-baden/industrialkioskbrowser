[← README](../README.md)

# Scan stress test

- [Why](#why)
- [Setup](#setup)
- [Method](#method)
- [Results](#results)
- [Logcat](#logcat)
- [What the numbers do not show](#what-the-numbers-do-not-show)
- [Repeating the test on a device](#repeating-the-test-on-a-device)
- [Conclusion](#conclusion)

## Why

Zebra's Enterprise Browser 5.5 has been reported to drop to the home screen on a TC53e when
scans arrive at 3–5 per second (Reddit r/browsers, thread `1qbvdp3`, January 2026, Zebra
tier-3 support without a fix). This page documents how Industrial Kiosk Browser 0.7.0 behaves
under the same and higher load, measured on 2026-11-02. Everything below is from one measured
session; nothing is extrapolated.

## Setup

| | |
|---|---|
| App | Industrial Kiosk Browser 0.7.0 (`versionCode` 9), release build of `main` (`f63c5c0`), signed with a debug key |
| Device | Android Emulator 37.1 (`-no-window -gpu swiftshader_indirect`), Pixel 4 profile, Android 15 (API 35, Google APIs, arm64), 2 GB RAM, on an Apple M-series Mac |
| Scanner | Generic broadcast `de.ictbaden.industrialkioskbrowser.action.BARCODE` (`scanner.genericIntent: "on"`), extras `data` and `symbology`; sent from a shell script **on the device** with `am broadcast` so that adb latency does not shape the rate |
| Page | [`tools/scan-stress/index.html`](../tools/scan-stress/index.html), served from the host (`http://10.0.2.2:8766/`), allow list `10.0.2.2`, terminator `enter`, on-screen keyboard off |
| Scan values | `SCAN0001` … `SCANnnnn`, symbology `CODE128` |

The page keeps a text field focused and counts, independently of each other:

- `industrialkioskbrowser:scan` events on `window` (with sequence numbers, so missing, duplicate
  and out-of-order scans are visible),
- Enter key presses that reach the field, and whether the field held exactly one scan value at
  that moment (the way a keyboard-wedge web app sees a scan),
- a `preventDefault()` switch to compare "event only" with "event + typed into the field".

The counters are also written into `document.title`, so they can be read with
`uiautomator dump` without touching the page.

## Method

Per run: reset the counters, start `logcat`, read `dumpsys meminfo` (PSS), fire the
broadcasts, wait four seconds, read the counters, `meminfo` again and check that
`BrowserActivity` is still the resumed activity. The rate is calculated from timestamps taken
on the device before the first and after the last `am broadcast`; `am` itself takes about
70 ms per call on the emulator, which is why "no delay" ends up at 14–19 scans/s rather than
hundreds.

## Results

All runs in one app process (PID 4561) without restarting it in between, so the PSS column also
shows what accumulates over 1 600 scans. Run 1 includes the first WebView warm-up after a cold
start, which is where most of its +11 MB comes from (runs 1b–5b start at the same 97–99 MB).

| Run | Scans | Delay | Measured rate | Events received | Enter received | Lost | Order | PSS before → after | ANR / crash | App stayed in front |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 200 | 0.3 s | 2.6 /s (76.5 s) | 200 / 200 | 200, all with the right value | 0 | in order | 88.7 → 100.0 MB | none | yes |
| 1b | 200 | 0.2 s | **4.5 /s** (44.6 s) | 200 / 200 | 200, all with the right value | 0 | in order | 97.5 → 100.1 MB | none | yes |
| 2 | 200 | none | **14.0 /s** (14.3 s) | 200 / 200 | 200, but see below | 0 events | in order | 97.6 → 100.2 MB | none | yes |
| 3 | 600 | 0.25 s | **3.7 /s** (164.5 s) | 600 / 600 | 600, all with the right value | 0 | in order | 97.9 → 99.0 MB | none | yes |
| 5a | 200 | 0.2 s, `preventDefault` | 4.5 /s (44.4 s) | 200 / 200 | – (not typed) | 0 | in order | 98.7 → 101.0 MB | none | yes |
| 5b | 200 | none, `preventDefault` | **19.3 /s** (10.4 s) | 200 / 200 | – (not typed) | 0 | in order | 98.9 → 100.9 MB | none | yes |

Memory during run 3 (`TOTAL PSS` every 30 s): 97.9 · 99.5 · 99.5 · 99.9 · 98.8 · 99.0 MB.
Java heap 4.3 → 6.8 MB, native heap 15.8 → 18.0 MB from the first to the last measurement;
the ART log shows ordinary concurrent GCs (`GC freed 932KB … paused 64us`). No `lowmemorykiller`
action against the app, no `StrictMode` report, no `Choreographer: Skipped frames` line.

**Run 2 – what "but see below" means.** At 14 scans/s every scan still arrived as an event and
every Enter still arrived in the field, but the pairing between value and Enter broke: 44 Enter
presses found more than one scan in the field (`"SCAN0001SCAN0002SCAN0003SCAN0004"`,
`"SCAN0005SCAN0006"`, …) and 47 found it empty; 109 were clean. The cause is in the delivery code:
`BrowserActivity.onScan` inserts the text with `evaluateJavascript` and sends the Enter key
50 ms later via `postDelayed` (`BrowserActivity.kt`, `sendTerminator`, `KEY_DELAY_MS`). When
the next broadcast arrives inside that window, its text is appended before the previous Enter
fires. A web app that listens to the `industrialkioskbrowser:scan` event is not affected
(runs 5a/5b); a pure keyboard-wedge web app would receive merged values at this rate. At
4.5 scans/s (run 1b) and 3.7 scans/s over 600 scans (run 3) the pairing was correct every time.
Tracked as [issue #19](https://github.com/ict-baden/industrialkioskbrowser/issues/19); see also [Limitations](limitations.md).

## Logcat

Filtered for `ANR`, `AndroidRuntime`, `Choreographer`, `StrictMode`, `FATAL`, `lowmemorykiller`
and the app's own tags, all runs:

```
run1   : 200 broadcasts seen by ActivityManager, 1 GC in the app process, no warnings
run1b  : 200 broadcasts, 2 GCs, no warnings
run2   : 200 broadcasts, 2 GCs, no warnings
run3   : 600 broadcasts, 8 GCs, no warnings against the app
         (the emulator did reclaim unrelated cached processes: "Process com.google.android.apps.wellbeing (pid 1318) has died: cch+35 CEM")
run5a/b: 200 broadcasts each, no warnings
```

Example of the app's own GC lines during run 3:

```
11-02 11:53:26.800 I/ialkioskbrowser( 4561): Explicit concurrent mark compact GC freed 1568KB AllocSpace bytes, 1(20KB) LOS objects, 49% free, 4457KB/8914KB, paused 87us
11-02 11:53:46.808 I/ialkioskbrowser( 4561): Explicit concurrent mark compact GC freed 932KB AllocSpace bytes, 1(20KB) LOS objects, 49% free, 4421KB/8842KB, paused 64us
```

`dumpsys gfxinfo` reports 82–97 % janky frames in every run. That number is dominated by the
software renderer of the headless emulator (`EGL_emulation: app_time_stats: avg=500ms` while the
page is idle) and says nothing about a device; it is listed only for completeness.

## What the numbers do not show

- **Emulator, not a Zebra device.** No DataWedge, no scanner hardware, no Zebra firmware. The
  Enterprise Browser report concerns a TC53e; this test cannot reproduce that device's behaviour,
  it only shows how this app's scan path behaves under comparable and higher rates.
- **Broadcast instead of DataWedge.** The generic intent receiver and the DataWedge receiver share
  everything from `ScannerManager` onwards (`ScanScripts.deliver`, terminator keys), but DataWedge
  itself was not part of the test.
- **Debug-signed release build** of `main`, not a published release.
- **Rate ceiling of the method:** `am broadcast` costs about 70 ms per call, so the fastest rate
  tested was 14 scans/s (typed) and 19 scans/s (event only).
- **Rendering metrics** from a headless emulator with a software GPU are not meaningful.
- One session, one device profile, one page. The page is deliberately small.

## Repeating the test on a device

```bash
cd tools/scan-stress && python3 -m http.server 8766          # serve the page from your PC
```

Install a profile that opens `http://<pc-ip>:8766/` with `"scanner": { "genericIntent": "on",
"terminator": "enter" }` and `"allowList": ["<pc-ip>"]`, open it, then:

```bash
tools/scan-stress/run.sh -s <adb serial> -n 200 -d 0.3     # 200 scans, 0.3 s apart
tools/scan-stress/run.sh -s <adb serial> -n 200 -d 0       # as fast as am broadcast goes
tools/scan-stress/run.sh -s <adb serial> -n 600 -d 0.25    # longer run for memory
```

The script resets the counters (the page treats the scan value `RESET` as a control code;
`PREVENT-ON` / `PREVENT-OFF` toggle `preventDefault`), fires the broadcasts from the device,
reads the counters back from the window title, records PSS before and after, the resumed
activity, a screenshot and a filtered logcat into `scan-stress-out/`. On a Zebra device with
DataWedge the same page works with real scans; only the broadcast part of the script is then
irrelevant.

## Conclusion

On an Android 15 emulator, Industrial Kiosk Browser 0.7.0 received 1 600 of 1 600 scans across
six runs at 2.6 to 19 scans per second, in order, without an ANR, crash, memory growth or leaving
the foreground, including 600 scans at 3.7/s over 165 seconds. Up to 4.5 scans per second every
scan also arrived in the input field as its own value followed by Enter; at 14 per second the
text still arrived completely but values started to merge in the field, which is logged as an
issue – a real device test with DataWedge is still open.
