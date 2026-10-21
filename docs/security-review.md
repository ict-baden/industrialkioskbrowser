[← README](../README.md)

# Security review

Independent reviews before each 0.x version (ICT-Baden guideline 09): reviewers who did not
write the code check it, and the findings are cross-checked. This page lists the findings for
0.6.0 and how each was handled.

- [Review for 0.6.0](#review-for-060)
- [Review of the 0.6.0 changes](#review-of-the-060-changes)
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
| A tag release without signing key would become "Latest" without APK. | Such a release is not marked Latest; once signed, the note is removed and it becomes Latest. |

## Accepted risks

- **Broadcast lockout:** any app can send wrong passwords and lock `INSTALL_CONFIG` for
  15 minutes at a time. Configuration files and the managed configuration still work.
- **Short passwords:** 5 attempts per 15 minutes still allow about 480 guesses per day. Use
  at least 10 characters.
- **Exported DataWedge receiver, plain HTTP, user CAs:** see [Limitations](limitations.md).
