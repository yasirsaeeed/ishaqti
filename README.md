# iShaQti Android App (WebView Wrapper)

This is a complete Android Studio project that wraps **https://ishaqti.nhmmp.gov.in/**
into a real Android app with an icon, so field workers can just tap the icon —
no browser, no typing URLs.

## What's included
- Full WebView wrapper with your app icon and colors (sampled from your logo)
- **Mandatory permission gate**: on first open, the app shows a full-screen prompt asking
  for Camera and Location. The website is not loaded at all until both are granted — the
  screen keeps reappearing with a "Grant Permissions" button until the user allows both.
  If the user permanently denies (checks "Don't ask again"), the button switches to
  "Open App Settings" so they can enable it manually from there. This is checked every
  time the app is opened or resumed.
- Because the native permission is granted before the website ever loads, when your web
  pages call the browser's own geolocation/camera APIs, the WebView auto-approves them
  silently — the user will NOT see a second "does this site want your location" popup.
- Handles file uploads and photo capture inside web forms
- Downloads (PDF/report generation) open properly via the system Download Manager
- "No internet" screen with Retry button, and pull-to-refresh
- Back button navigates web history instead of closing the app abruptly
- External links (anything not on ishaqti.nhmmp.gov.in) open in the phone's normal browser

## Easiest option: Build the APK in the cloud (no software install)

This project includes a ready-made GitHub Actions workflow that builds the APK
for you automatically — you never touch Android Studio or any build tools.

1. Go to https://github.com and create a free account if you don't have one.
2. Click **New repository** (top right, "+" icon) → name it e.g. `ishaqti-app` →
   set it to **Private** (recommended, since this contains your app config) → **Create repository**.
3. On the new repo page, click **uploading an existing file** (a link on the empty-repo screen).
4. Drag the *entire contents* of this `iShaQtiApp` folder (not the folder itself — its contents:
   `app`, `.github`, `build.gradle`, `settings.gradle`, `gradle.properties`, `gradle`, `README.md`)
   into the upload box, then click **Commit changes**.
   - If the browser upload won't take folders, install the free **GitHub Desktop** app
     (https://desktop.github.com) instead — it lets you publish this whole folder as a
     repository in two clicks, no command line.
5. Go to the **Actions** tab of your repo. A workflow called "Build APK" will already be running
   (it starts automatically on upload). Wait 3-5 minutes for it to finish (green checkmark).
6. Click on the finished run → scroll down to **Artifacts** → download `iShaQti-debug-apk`.
   That's a zip containing your `app-debug.apk` — send that APK file to your field workers.

This produces a "debug" APK, which is completely fine for internal distribution
(not the Play Store) — it installs and runs exactly like a normal app. If you ever
want a Play Store-ready signed release build, that needs the local Android Studio
steps below (to create and manage a signing key yourself).

## How to build the APK yourself using Android Studio (one-time setup on your computer)

1. Install **Android Studio** (free): https://developer.android.com/studio
2. Open Android Studio → **Open** → select this `iShaQtiApp` folder.
3. Let it sync (first time it may ask to install a missing Gradle wrapper — click **OK/Yes**, it downloads automatically). This needs internet access once.
4. Once synced, go to **Build → Generate Signed Bundle / APK → APK**.
   - First time: create a new keystore (click "Create new..."), fill in any details, remember the password — you'll need the SAME keystore for every future update, or installed apps will break.
   - Choose **release** build variant.
5. Android Studio will produce an `app-release.apk` file (path shown in a popup notification when done, usually under `app/release/`).

## How to distribute it to field workers

- Share the `app-release.apk` file via WhatsApp, Google Drive link, or a shared folder.
- On their phone, they tap the file → Android will show **"Install blocked"** or ask to allow installing from that source (Chrome/WhatsApp) — this is a normal one-time device setting, not an error. They tap **Settings → Allow** → then **Install**.
- After that, the iShaQti icon appears on their home screen like any other app.

## If you'd rather not manage a keystore/Play listing yourself
You can also distribute internally without the Play Store — the WhatsApp/Drive method
above is exactly how many government field apps in India are rolled out. Just make sure
you keep the keystore file safe; you'll need the same one for every future app update.

## Updating the app later
- If you only change your **website** (ishaqti.nhmmp.gov.in), you don't need to touch this
  app at all — it just loads whatever is live on the site.
- You only need to rebuild/redistribute the APK if you change the app icon, app name,
  permissions, or add new native features.

## Notes
- Package name: `in.gov.nhmmp.ishaqti`
- Minimum Android version supported: Android 5.0 (covers virtually all field devices)
- The app only allows navigation within `ishaqti.nhmmp.gov.in`; any other link opens in
  the phone's regular browser instead of inside the app.
- **Important Android limitation**: permissions cannot be checked/enforced at *install* time —
  only after the app is opened, since that's how Android's permission system works for every
  app on the platform. This app enforces it as strictly as Android allows: the site is
  completely inaccessible until Camera + Location are both granted, and it re-checks every
  time the app opens.
