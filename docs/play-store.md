[← README](../README.md)

# Google Play

How Industrial Kiosk Browser gets into Google Play, and what to answer in the Play Console.

- [What is in the repo](#what-is-in-the-repo)
- [Before the first upload](#before-the-first-upload)
- [App signing](#app-signing)
- [Play Console answers](#play-console-answers)
- [Publishing a version](#publishing-a-version)
- [Google Play access for the CI](#google-play-access-for-the-ci)

## What is in the repo

| Path | Content |
|---|---|
| `fastlane/metadata/android/<locale>/` | Title, short and full description in `en-US`, `de-DE`, `es-ES`, `fr-FR`, `zh-CN` (limits: 30, 80, 4000 characters) |
| `fastlane/metadata/android/en-US/images/` | Hi-res icon 512 × 512, feature graphic 1024 × 500, six phone screenshots 1080 × 1920. Play shows them for every language. |
| Release `vX.Y.Z` | `IndustrialKioskBrowser-X.Y.Z.aab` – the App Bundle for Play, signed by the CI |

The folder layout is the one of [fastlane supply](https://docs.fastlane.tools/actions/supply/),
so the CI uploads them with every production release (see below).

The graphics are rendered from the real UI like the documentation images: run the steps in
[tools/screenshots](../tools/screenshots/README.md); `compose.py` also writes the Play graphics.
Captions are in `PLAY_SCREENS` at the end of `compose.py`.

## Before the first upload

Done once, by a person with access to the company accounts:

1. **Developer account** as an organisation (ICT-Baden GmbH, needs a D-U-N-S number). An
   organisation account does not need the 14-day closed test with 12 testers that new personal
   accounts need.
2. **Package name** is final once the app exists in Play: `de.ictbaden.industrialkioskbrowser`.
   The domain `ict-baden.de` belongs to ICT-Baden.
3. **Privacy policy URL:** `https://ict-baden.de/datenschutz/industrial-kiosk-browser/` (German and English). It
   must be online before the app is submitted.
4. **Contact details** for the store listing: e-mail (e.g. `support@ict-baden.de`), website.
5. **Translations** of the app and of the store texts read by native speakers (richtlinien/13).

## App signing

Play signs the apps it delivers with its *app signing key*; we upload with an *upload key*.
The CI signs the App Bundle with our release key (secret `ANDROID_KEYSTORE`).

Decision: **our release key is also the app signing key**, so Play and
MDM/GitHub APKs carry the same signature and devices can switch between both sources.

At the first upload, in *Setup → App signing* (or when creating the first release):

1. Choose *Use a different key* → *Export and upload a key from Java keystore*.
2. Download Google's `pepk.jar` and the encryption key shown there, then run the command the
   Console shows with our keystore (the file behind the `ANDROID_KEYSTORE` secret), alias and
   password. It writes an encrypted ZIP; upload it.
3. Upload key: if the Console offers it, keep our key as upload key too, then the CI-signed
   `.aab` can be uploaded as it is. If it insists on a separate upload key, the CI needs a second
   keystore secret for the App Bundle – a small change in `.github/workflows/build.yml`.

This can only be done by someone with the keystore file and password; it cannot be changed later.

## Play Console answers

**App details:** category *Business*, tags e.g. *Browser*, *Enterprise*. Free, no in-app
purchases.

**App access:** all functions are available without login. For the review: the app opens a
built-in demo page; *Settings* has no password until one is configured.

**Ads:** the app contains no ads.

**Content rating (IARC questionnaire):** category *Utility, productivity, communication or
other*. Violence, sexuality, language, controlled substances, gambling: no. User interaction /
sharing of user content: no. *Unrestricted internet access:* **yes** – it is a browser, even if
the administrator usually limits it to an allow list.

**Target audience:** 18 and over; not designed for children.

**News app:** no. **Government app:** no. **Financial features:** none. **Health:** none.

**Data safety:**

- *Does the app collect or share user data?* **No.** The app has no servers of its own and
  sends nothing to ICT-Baden (no telemetry, no crash reports, no account). Like any browser it
  transmits what the user enters into a web page to that web page's server – the servers the
  device owner configures as start URL and allow list (an empty allow list allows every host).
  Google Safe Browsing in the Android System WebView stays on (a function of Android, not of the
  app). Re-check this answer against the current Play definitions before submitting.
- Encryption in transit: the configured web apps decide (HTTPS recommended; the app enforces
  strict TLS checks).
- Data deletion: no account, nothing to delete on our side; *Clear storage* removes everything
  on the device.

**Permissions:**

| Permission | Why |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Loading the configured web apps, error pages without network |
| `CAMERA` | Scanning with the camera on devices without a scan engine (or with `scanner.engine: "camera"`), and when a web page of an allowed host asks for the camera (e.g. a camera barcode scan in the web app); the user is asked first |
| `VIBRATE` | Feedback after a scan |

No sensitive permissions that need a declaration form (no SMS, call log, location,
accessibility, all-files access, package queries beyond the two scanner services).

## Publishing a version

The pipeline in `.github/workflows/build.yml` (job `play`) publishes by itself once the CI has Play access:

| Event | What happens | Play track |
|---|---|---|
| Pull request | build, unit tests, lint, licence check – nothing is signed or uploaded | – |
| Merge to `main` | signed App Bundle, release notes = commit subject | **Internal testing**, available to the testers at once |
| Tag `vX.Y.Z-…` (e.g. `v0.7.0-rc1`) | GitHub pre-release and signed App Bundle | **Closed testing** (`alpha`) |
| Tag `vX.Y.Z` | GitHub release (APK, App Bundle, SBOM, checksums) and upload with release notes from `CHANGELOG.md` (plain text, max. 500 characters), store texts and graphics from `fastlane/` | **Production**, staged: starts with `PLAY_ROLLOUT` (repository variable, default `0.2` = 20 %) |

**Creating the tag is the release decision.** Only the GitHub users in the repository variable `PLAY_RELEASERS`
(default `pieewiee gear1900`) can publish to production; for anyone else the job stops. Claude never creates
release tags.

Raising, halting or resuming the staged rollout: *Actions → Google Play Rollout → Run workflow*
(`.github/workflows/play-rollout.yml`), or

```bash
gh workflow run play-rollout.yml -f rollout=0.5          # 50 %
gh workflow run play-rollout.yml -f rollout=1.0          # everyone, rollout complete
gh workflow run play-rollout.yml -f action=halt          # stop the rollout (e.g. crash reports)
```

The `versionCode` is the CI run number, so every build is higher than the one before. Play refuses an App Bundle
whose `versionCode` was uploaded already; a new build (new tag or `gh workflow run`) gets a new number.

Without the secret `PLAY_SERVICE_ACCOUNT_JSON` the job only leaves a notice and uploads nothing. The very first
version has to be uploaded by hand anyway (see below).

## Google Play access for the CI

Done once by a person with owner access to the Play Console and to Google Cloud:

1. **Create the app** in the Play Console (name *Industrial Kiosk Browser*, default language English, free, app)
   and **upload the first App Bundle by hand** – from the GitHub release – to *Testing → Internal testing*,
   including the app signing setup described above. The Play API cannot create apps or do the first upload.
2. **Google Cloud:** a project for ICT-Baden, *Google Play Android Developer API* enabled, a **service account**
   (e.g. `github-play@….iam.gserviceaccount.com`) with a **JSON key**.
3. **Play Console → Users and permissions → Invite new users:** the e-mail address of the service account, for
   this app with *Release apps to testing tracks*, *Release to production, exclude devices, and use Play App
   Signing* and *Manage store presence*.
4. **GitHub:** the JSON key as repository secret `PLAY_SERVICE_ACCOUNT_JSON`
   (`gh secret set PLAY_SERVICE_ACCOUNT_JSON -R ict-baden/industrialkioskbrowser < key.json`), then delete the
   local file. Optional: repository variables `PLAY_RELEASERS` and `PLAY_ROLLOUT`.
5. **Internal testers:** in *Testing → Internal testing*, a list of Google accounts (or a Google group) and the
   opt-in link for the test devices.

From then on: merge → internal testers, tag → production.
