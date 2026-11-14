[← README](../README.md)

# Google Play

How Industrial Kiosk Browser gets into Google Play, and what to answer in the Play Console.

- [What is in the repo](#what-is-in-the-repo)
- [Before the first upload](#before-the-first-upload)
- [App signing](#app-signing)
- [Play Console answers](#play-console-answers)
- [Publishing a version](#publishing-a-version)
  - [Automatic upload](#automatic-upload)

## What is in the repo

| Path | Content |
|---|---|
| `fastlane/metadata/android/<locale>/` | Title, short and full description in `en-US`, `de-DE`, `es-ES`, `fr-FR`, `zh-CN` (limits: 30, 80, 4000 characters) |
| `fastlane/metadata/android/en-US/images/` | Hi-res icon 512 × 512, feature graphic 1024 × 500, six phone screenshots 1080 × 1920. Play shows them for every language. |
| Release `vX.Y.Z` | `IndustrialKioskBrowser-X.Y.Z.aab` – the App Bundle for Play, signed by the CI |

The folder layout is the one of [fastlane supply](https://docs.fastlane.tools/actions/supply/),
so the texts can later be uploaded automatically. Until then, copy them into the Play Console.

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
  app). The text recognition library Google ML Kit may send anonymous usage statistics to Google
  (no pictures, no recognised text); declare this as Google's SDK data collection in the form
  ("App info and performance → diagnostics", collected, not shared, not optional) if the current
  Play definitions require it for SDKs. Re-check this answer against the current Play definitions
  before submitting.
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
| `NFC` | Reading NFC tags while the browser is in front (`scanner.nfc`) |

No sensitive permissions that need a declaration form (no SMS, call log, location,
accessibility, all-files access, package queries beyond the two scanner services).

## Publishing a version

The CI uploads to Google Play by itself once the secret `PLAY_SERVICE_ACCOUNT_JSON` exists
(see [Automatic upload](#automatic-upload)):

| Push | Google Play |
|---|---|
| to `main` | *Internal testing*, rolled out to the internal testers at once |
| tag `vX.Y.Z` | *Production*, full rollout once Google has reviewed it (usually within days) |
| tag with a hyphen (`vX.Y.Z-rc1`) | *Internal testing* |

Every push to `main` therefore reaches the internal testers; the production devices only get a tagged version.

1. Update `CHANGELOG.md` and the short Play release notes in `play/whatsnew/whatsnew-de-DE` and
   `whatsnew-en-US` (at most 500 characters each; the CI fails on longer ones).
2. Push the tag `vX.Y.Z` – only with the explicit go of the release owner, it reaches
   every device with the app from Google Play.
3. The CI builds and signs APK and App Bundle, attaches both to the GitHub release and uploads the
   bundle to *Production*. Google reviews it; the review status is under *Publishing overview*.

Without the secret nothing goes to Play: upload the `.aab` from the GitHub release by hand under
*Testing → Internal testing*, then promote it to *Production*.

Before the first production upload of a new version, check *Release dashboard → supported devices*
in the internal test: a permission without `<uses-feature android:required="false">` (camera, microphone)
or a missing processor type silently drops devices.

The `versionCode` is the CI run number, so every build is higher than the one before. Play
refuses an App Bundle whose `versionCode` was uploaded already – use a new tag, not a re-run.

### Automatic upload

Done once, by a person with admin access to the Play Console:

1. [Google Cloud Console](https://console.cloud.google.com/) → a project for ICT-Baden → enable the
   *Google Play Android Developer API*.
2. *IAM & Admin → Service accounts* → create one (e.g. `play-upload`) → *Keys → Add key → JSON*.
3. Play Console → *Users and permissions* → invite the service account's e-mail address, access
   to **Industrial Kiosk Browser** only, permissions *Release to production, exclude devices, and use Play App
   Signing* and *Release apps to testing tracks*.
4. GitHub → repository → *Settings → Secrets and variables → Actions* → new secret
   `PLAY_SERVICE_ACCOUNT_JSON` with the content of the JSON file. Delete the file afterwards.

One service account can serve both apps (Industrial Keyboard and Industrial Kiosk Browser): give it
access to both and store the same secret in both repositories, or once as an organisation secret.
The upload uses [r0adkll/upload-google-play](https://github.com/r0adkll/upload-google-play), pinned
to a commit because it handles the key.
