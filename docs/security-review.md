[← README](../README.md)

# Security review

Independent reviews before each 0.x version (ICT-Baden guideline 09): reviewers who did not
write the code check it, and the findings are cross-checked. This page lists the findings for
0.6.x and 0.7.0 and how each was handled.

- [Review for 0.6.0](#review-for-060)
- [Review of the 0.6.0 changes](#review-of-the-060-changes)
- [Final check of 0.6.0 (fixed in 0.6.1)](#final-check-of-060-fixed-in-061)
- [Review for 0.7.0](#review-for-070)
- [Accepted risks](#accepted-risks)

## Review for 0.6.0

Review of the 0.5.0 state (October 2026). Focus: input from other apps, configuration
sources, WebView settings, store texts, licences.

| # | Finding | Resolution |
|---|---|---|
| 1 | A configuration file in the app folder could replace admin password, start URL and allow list without any check (other apps can write there up to Android 10). | Fixed: the admin password is required once set, never replaced by the file, and the file is deleted after import; without a password only on managed devices or Android 11+. |
| 2 | Privacy texts claimed connections only to configured addresses; an empty allow list allows every host, Safe Browsing was not mentioned. | Fixed: texts corrected. Safe Browsing stays on (protection on kiosk devices) and is documented. |
| 3 | French store texts contained control characters. | Fixed; a unit test checks all store texts. |
| 4 | A tap on the launcher icon closed the web session (`singleTask`). | Fixed (`singleTop`). |
| 5 | Configuration changes only took effect after an app restart. | Fixed: an open profile restarts with the new configuration. |
| 6 | Back key returned to error pages; a kiosk without toolbar could get stuck. | Fixed: error pages and the failed address are skipped, otherwise the menu opens. |
| 7 | `THIRD-PARTY-NOTICES.txt` incomplete or outdated. | Fixed; the CI compares it with the libraries of the release APK. |
| 8 | On EMM devices without admin password every app may send `INSTALL_CONFIG`; no limit on password attempts. | Lockout after 5 wrong passwords (15 minutes); deployment guide asks to always set a password. |
| 9 | Exported scan receivers accept fake scans from any app. | Generic broadcast configurable (`scanner.genericIntent`), off on Zebra/Honeywell by default; the DataWedge receiver has to stay exported (documented). |
| 10 | PBKDF2 iteration count of imported hashes not limited. | Fixed: at most 600,000. |
| 11 | JavaScript API also for `http://` origins, plain HTTP and user CAs allowed. | Accepted for intranet apps, documented in [Limitations](limitations.md). |
| 12 | `ScannerNavigate` with leading spaces before `javascript:` did not work. | Fixed. |
| 13 | No page about limitations and test scope. | Added [Limitations](limitations.md). |
| 14 | No SBOM, no build provenance. | SBOM in every release; provenance attestation runs once the repository is public. |
| 15 | Review not documented. | This page. |
| 16 | Support page linked to disabled Discussions. | Fixed. |

## Review of the 0.6.0 changes

A second reviewer checked the diff of the fixes above.

| Finding | Resolution |
|---|---|
| Any app could lock the admin-password check with wrong broadcasts and thereby also block the configuration file; a correct file arriving during the lock was dropped. | Separate counters for broadcast and file; a file that arrives during a lock is read once the lock has expired. |
| The plain password stayed readable in the app folder (Android 8–10). | The file is deleted after a successful import. |
| Back navigation relied on the history URL of the error page. | Error pages are also recognised by their `data:` URL. Still to be confirmed on a device. |
| A clock that runs ahead could extend the lock. | Locks more than 15 minutes in the future are ignored. |
| Password checks run on the main thread. | Iteration limit lowered to 600,000; the app's own hashes use 60,000. |
| A tag release without signing key would become "Latest" without APK. | The release says at the top that the signed APK will follow; a run with the key adds APK and App Bundle and removes the note. *Corrected in 0.6.1:* `--latest=false` did not help, GitHub marks the only release Latest anyway – since 0.6.1 the release with the highest version is marked Latest on purpose. |

## Final check of 0.6.0 (fixed in 0.6.1)

A further review of the released 0.6.0, with a cross-check of each finding.

| Finding | Resolution |
|---|---|
| Finding 5 above was only half fixed: an `INSTALL_CONFIG` broadcast or a configuration file reached an open profile only when the app came back to the front – on a kiosk that never leaves the browser, not at all. | Fixed: the configuration store notifies the open session, which restarts with the new configuration; configuration files are checked every five seconds while the browser is in front. Unit test `ConfigChangeTest`. |
| Safe Browsing stays on, which means a connection to Google the app could switch off. | Accepted on purpose (protection against malware and phishing on unattended devices) and documented in [Data and privacy](data-and-privacy.md); an unproven sentence about switching it off by device policy was removed. |
| Store texts said the device "stays locked to the apps you allow", but an empty allow list allows every host. | Fixed in all five languages. |
| The README download badge and `adb` example pointed to a rolling build that does not exist before the signing key is set up. | They point to the latest release; [Limitations](limitations.md#releases-and-updates) states what is missing. |
| The Play guide contained internal notes. | Removed. |

## Review for 0.7.0

Independent review of `main` (f63c5c0) on 2026-11-02 by a reviewer who did not write the code,
before the first public release. Scope: WebView settings and the JavaScript bridge, exported
receivers and the `INSTALL_CONFIG` gate, all configuration sources, allow list and URL rules,
TLS handling, kiosk escape paths, password hashing and storage, logging, concurrency and
lifecycle, build and workflow. Method: the whole manifest, `res/xml`, every Kotlin source,
`bridge.js` and `status.html` were read; each suspected finding was re-checked for a concrete
attacker (another app on the device, a page in the intranet, a person at the device, a network
attacker) and, where possible, proven with a JUnit or Robolectric probe. Findings that need a
device are marked *test* in the issue tracker. Accepted risks from the sections above were not
reported again.

| # | Severity | Finding | Status |
|---|---|---|---|
| 1 | high | Allow list bypass: `UrlPolicy.hostOf` falls back to a regex when `java.net.URI` rejects a URL; Chromium leaves `\|`, `^`, `{`, `}` unescaped, so `https://allowed.de:443@evil.de/?q=a\|b` was judged as `allowed.de` while the WebView loads `evil.de`. Affects navigation, the POST block, `ScannerNavigate` and the camera grant. Proven by unit test. | fixed in PR #20 ([#25](https://github.com/ict-baden/industrialkioskbrowser/issues/25)) |
| 2 | medium | Android's `org.json` appends the whole input to its error message; an invalid configuration (StageNow typo) therefore put the plain `adminPassword` into Logcat, the preferences and *Settings → Last provisioning error*. Proven with Robolectric. | fixed in PR #21 ([#26](https://github.com/ict-baden/industrialkioskbrowser/issues/26)) |
| 3 | medium | `onReceivedSslError` fires for every resource; a single image or iframe with a bad certificate replaces the whole page with the error page (`cancel()` itself is correct). | open ([#27](https://github.com/ict-baden/industrialkioskbrowser/issues/27)) |
| 4 | high if confirmed | `/enterprise/usr/industrialkioskbrowser.json` is imported as trusted and may set the admin password, assuming only MX/StageNow/adb can write there. Zebra documents the enterprise folder as writable by applications; needs a device test (Android 10 and 11+). | open, device test ([#28](https://github.com/ict-baden/industrialkioskbrowser/issues/28)) |
| 5 | medium | Deeply nested JSON in a configuration file throws `StackOverflowError`, which `runSource` does not catch; the file fingerprint is saved only after the import, so the app crashed in a loop. Parsing happens before the password check; no size limit. Proven with Robolectric. | fixed in PR #22 ([#29](https://github.com/ict-baden/industrialkioskbrowser/issues/29)) |
| 6 | medium | The WebView text-selection menu offers *Share* and *Web search*, which open other apps when the device is not in lock task mode; the app does not trim the action mode. | open, device test ([#30](https://github.com/ict-baden/industrialkioskbrowser/issues/30)) |
| 7 | low–medium | The HTTP authentication dialog is shown for sub-resources of hosts outside the allow list, which allows credential phishing from an embedded image. | open ([#31](https://github.com/ict-baden/industrialkioskbrowser/issues/31)) |
| 8 | low–medium | Downloads do not check the allow list (`<a download>` and script-started downloads skip `shouldOverrideUrlLoading`); `allowDownloads` is on by default and the notification opens files in other apps. | open, device test ([#32](https://github.com/ict-baden/industrialkioskbrowser/issues/32)) |
| 9 | low | The in-app admin and exit password dialogs have no attempt limit; only broadcasts and files have the 5-attempt lock. | open ([#33](https://github.com/ict-baden/industrialkioskbrowser/issues/33)) |
| 10 | low | An IPv6 start URL produced the invalid origin rule `http://fd00::1`, which silently disabled the JavaScript bridge. | fixed in PR #23 ([#34](https://github.com/ict-baden/industrialkioskbrowser/issues/34)) |
| 11 | low | The allow list ignores scheme and port (`https://wms.example.com` also allows `http://wms.example.com:8080`); not documented. | open ([#35](https://github.com/ict-baden/industrialkioskbrowser/issues/35)) |
| 12 | low | Workflow permissions (`contents: write`, `id-token: write`) apply to every job including pull-request builds; actions are pinned by major tag, not by commit. | open ([#36](https://github.com/ict-baden/industrialkioskbrowser/issues/36)) |

Checked and found in order: `allowFileAccess`/`allowContentAccess` off, non-web schemes
(`file:`, `content:`, `intent:`, `tel:`, `mailto:`, `market:`, `data:`, `blob:`) blocked for the
main frame, mixed content blocked by default, `setSupportMultipleWindows(false)`,
`onReceivedSslError` never calls `proceed()`, client certificates through `KeyChain`, bridge
origin rules from the allow list (foreign iframes and redirect targets get no bridge), scan data
quoted with `JSONObject.quote` (also U+2028/2029), `ConfigReceiver` checks the password before
parsing, PBKDF2-HMAC-SHA256 with 16-byte salt, 60,000 iterations, constant-time compare,
`allowBackup="false"` with full extraction exclusions, no scan data or page URLs in Logcat,
receivers and polling serialised on the main thread, no static context leaks, release builds
without WebView debugging, no secrets or customer names in the repository.

### Cross-check of the 2026-10-20 review (0.5.0 state)

The 18 numbered findings of the 2026-10-20 review were checked against today's code:
16 are fixed, 2 are accepted, 1 has a new open part.

| 2026-10-20 finding | Status today |
|---|---|
| 1 Configuration file replaces admin password without check | Fixed for the app folder (`Provisioning.kt:58-63,135-138`, `AdminGate`). `/enterprise/usr` stays trusted – see finding 4 above ([#28](https://github.com/ict-baden/industrialkioskbrowser/issues/28)). |
| 2 Privacy/store texts, Safe Browsing | Texts fixed (`data-and-privacy.md`, `limitations.md`); Safe Browsing stays on, accepted (see above). |
| 3 French store texts with control characters | Fixed; `StoreTextsTest` rejects control characters. |
| 4 `singleTask` closed the session | Fixed: `singleTop` (`AndroidManifest.xml:40`). |
| 5 Configuration only after restart | Fixed: `BrowserActivity.restartIfConfigChanged`, store listener, five-second file check (`BrowserActivity.kt:160-197`). |
| 6 Back key on error pages | Fixed: `BackNavigation.kt`, `BrowserActivity.kt:482-492`; device confirmation still listed in the 0.6.0 section. |
| 7 `THIRD-PARTY-NOTICES.txt` | Fixed; `.github/scripts/check-notices.sh` runs in the CI. |
| 8 `INSTALL_CONFIG` on EMM devices, no attempt limit | Fixed: `AdminGate` lockout, deployment guide; residual risk accepted (see above). |
| 9 Exported scan receivers | Fixed: `scanner.genericIntent` (`Model.kt:39-47`, `ScannerManager.kt:57-61`); DataWedge receiver stays exported, documented. |
| 10 PBKDF2 iterations unbounded | Fixed: `PasswordHasher.MAX_ITERATIONS = 600_000` (`PasswordHasher.kt:15,43`). |
| 11 Bridge for `http://`, cleartext, user CAs | Accepted and documented (`limitations.md`). |
| 12 `ScannerNavigate` with leading spaces | Fixed: `ScanScripts.kt:95` (`template.trim()`). |
| 13 Limitations page | Fixed: `docs/limitations.md`. |
| 14 SBOM and provenance | Fixed: SBOM step and conditional attestation in `build.yml`. |
| 15 Review documentation | Fixed: this page. |
| 16 Discussions link | Fixed: `SUPPORT.md` links to issues. |
| 17 Version 0.6.0 release | Done (0.6.0, 0.6.1); 0.7.0 is the next tag. |
| 18 Repository metadata | Fixed: description, topics and homepage are set. |

The two decisions of that review (new name, fresh history before the repository goes public) are
done for the name (Industrial Kiosk Browser) and in progress for the history (PR #17).

## Accepted risks

- **Broadcast lockout:** any app can send wrong passwords and lock `INSTALL_CONFIG` for
  15 minutes at a time. Configuration files and the managed configuration still work.
- **Short passwords:** 5 attempts per 15 minutes still allow about 480 guesses per day. Use
  at least 10 characters.
- **Exported DataWedge receiver, plain HTTP, user CAs:** see [Limitations](limitations.md).
