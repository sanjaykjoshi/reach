# Reach: a minimalist, one-hand launcher for Android

There are no ads and no tracking. The app doesn't have internet permission.

## What it does
- **Home screen:** a big clock and date, your pinned apps near the bottom where your thumb is, and a search bar.
- **A–Z thumb strip:** slide along the letters on the edge. The letters bulge out in a wave, and that letter's apps appear just above your thumb.
- **Search:** swipe up or tap Search. The best match shows up right above the keyboard, and **Go** opens it. The last row searches the web.
- **Swipe down:** opens your notifications.
- **Long-press an app:** pin it, unpin it, move it up or down, hide it, open App info, or uninstall it.
- **Long-press empty space:** open settings (left-handed mode, hidden apps, set as your home app, help).
- **First run:** your dialer, messages, camera and browser are pinned for you.

## Build the APK

### Option A: GitHub (works from a phone)
1. Put this folder in a GitHub repository.
2. The **Build APK** workflow (`.github/workflows/build.yml`) runs on every push to `main`.
3. When it finishes, open the repo's **Releases** and download `Reach.apk`. You can also use this link:
   `https://github.com/<you>/<repo>/releases/latest/download/Reach.apk`

### Option B: Android Studio
Open the folder in Android Studio, then choose **Build → Build APK(s)**.

## Install
1. Open `Reach.apk` on your phone and allow "install unknown apps" for your browser or file manager.
2. Open Reach, long-press empty space, and choose **Make Reach your home app**.

Every build is signed with the same key (`app/reach.keystore`), so a new build installs as an update over the old one. That key is only for personal sideloading. Don't use it to publish on the Play Store.

## Code
It's plain Java with no libraries and minSdk 26 (Android 8+). The code is in `app/src/main/java/app/reach/launcher/`:
- `MainActivity.java`: the home screen, panels, search, menus and settings
- `AlphabetBar.java`: the wave-style A–Z strip
- `AppAdapter.java`: the app list rows
- `AppInfo.java`: the app model
