# CleanFrame for Google TV

CleanFrame's home screen running as an Android TV / Google TV launcher, built for a TCL Google TV and installed with TV Box Tools. It keeps CleanFrame's look (photo, clock, dock or list, glass focus) and adds the practical options from Arc Launcher 1.2.0.

The UI is not forked: the build copies `../app/` into the APK at build time, swaps `device.js` for the Android version in `web/`, and adds `platform.js`. Pull upstream CleanFrame changes and rebuild.

## What's in it

| Area | Where |
| --- | --- |
| Home app (HOME + Leanback intents) | `app/src/main/AndroidManifest.xml` |
| Full-screen WebView, Home/Back handling, key sounds | `MainActivity.java` |
| `window.CleanFrameNative` bridge: apps, launch, settings screens, prefs | `NativeBridge.java` |
| App icons served to the UI | `WebSupport.java` (`/icon/<package>.png`) |
| Network + Wi‑Fi usage | `StatusMonitor.java` |
| "CleanFrame Clock" screensaver | `ClockDreamService.java` + `web/dream.html` |
| Google TV wording, app list, Arc options | `web/platform.js`, `web/device.js`, `web/android.css` |

### Borrowed from Arc Launcher 1.2.0

All under **Settings › Google TV**. Each option changes with OK.

- **Clock format** — 12‑hour or 24‑hour (home clock, full-screen clock, screensaver).
- **Status line** under the clock — Date, Network (Wi‑Fi / Ethernet / Offline), Wi‑Fi usage (today, this week, this month).
- **Back button on Home** — Ask before leaving, Do nothing, Show clock, Start screensaver.
- **Navigation sounds** — system click on D‑pad moves.
- **Shortcuts** — Google TV Home, TV settings, Network and internet, Start screensaver, Screensaver settings.
- **Screensaver** — "CleanFrame Clock": your wallpaper, dimmed, with a clock that drifts to protect the panel.

The top bar's Art Mode button becomes **Screensaver**. Known apps (YouTube, Netflix, Disney+, Prime Video, Apple TV, HBO Max, Tubi, Spotify, Stremio) use CleanFrame's monochrome marks; everything else in All apps uses its own icon.

## Build the APK

Pick one:

**GitHub (no tools needed).** Push this repo to your fork. The *Google TV APK* workflow builds `CleanFrame-GoogleTV.apk` and attaches it (with its SHA‑256) to the run under *Artifacts*. You can also run it by hand from the Actions tab.

**Android Studio on your Mac.** Open the `android/` folder, let Gradle sync, then *Build › Build App Bundle(s) / APK(s) › Build APK(s)*. Or from a terminal: `cd android && ./gradlew assembleRelease`. Output: `android/app/build/outputs/apk/release/app-release.apk`.

Without a signing key the release build is signed with the debug key. That's fine for one TV, but each GitHub run uses a different debug key, so an update would need an uninstall first. To keep one key, create it once:

```sh
keytool -genkeypair -v -keystore cleanframe.jks -alias cleanframe -keyalg RSA -keysize 2048 -validity 10000
base64 -i cleanframe.jks | pbcopy   # paste into the CF_KEYSTORE_B64 secret
```

and add the repo secrets `CF_KEYSTORE_B64`, `CF_KEYSTORE_PASSWORD`, `CF_KEY_ALIAS`, `CF_KEY_PASSWORD`. Keep `cleanframe.jks` out of git.

## Install on the TCL with TV Box Tools

1. **Connect.** On the TV turn on Developer options and USB/Wireless debugging. In TV Box Tools run *discover*, then *pair* (Android 11+) and *connect*, and allow debugging on the TV. Run *check* and note the current HOME app — that's your recovery value (normally `com.google.android.apps.tv.launcherx`).
2. **Install.** Give TV Box Tools the APK and its SHA‑256 (from the `.sha256` file) for *install*.
3. **Try it first.** Open **CleanFrame** from the Google TV app list. Arrow around, open an app, press Home to come back. Don't make it Home until this works.
4. **Make it Home.** Use *provision* to set `com.mcguynn.cleanframe/.MainActivity` as the HOME app (it switches the stock launcher off with `pm disable-user`, keeping its data). Google TV also has `com.google.android.tungsten.setupwraith`, which can switch the stock launcher back on; if the Home button still lands on Google TV after a reboot, that package needs disabling too.
5. **Test.** Run *test* (checks who answers HOME and reboots). After the reboot the TV should open CleanFrame.

Order matters: install and open CleanFrame **before** disabling the stock launcher. With no working home app, the TV boots to a blank screen.

### Get back to stock Google TV

- From CleanFrame: Settings › Google TV › **Google TV Home** works only while the stock launcher is enabled.
- With TV Box Tools / adb, re-enable it:
  ```sh
  adb shell pm enable com.google.android.apps.tv.launcherx
  adb shell pm enable com.google.android.tungsten.setupwraith
  ```
- Uninstalling CleanFrame while it's the only home app is not a recovery step — re-enable the stock launcher first.

### Optional permissions

- **Wi‑Fi usage** needs usage access. Settings › Google TV › Usage access opens the screen where the TV has one; Google TV often hides it, so grant it once with adb:
  `adb shell appops set com.mcguynn.cleanframe GET_USAGE_STATS allow`
- **Screensaver:** choose *CleanFrame Clock* under Settings › System › Ambient mode › Screensaver (Screensaver settings in CleanFrame opens it where the TV allows).

## Things to know

- Input switching, live TV and Google's recommendations rows come from the stock launcher. CleanFrame doesn't replace them; keep the TCL's Inputs button / Live TV app for HDMI.
- "Start screensaver" uses the system's dream starter (`Somnambulator`). If TCL hides it, CleanFrame opens screensaver settings instead.
- The UI is offline: no INTERNET permission, no analytics.
- Arc Launcher is GPL‑3.0; nothing here copies its code — the options are reimplemented. CleanFrame is MIT; keep `../LICENSE` and `../THIRD_PARTY_NOTICES.md` with any APK you share (brand marks and photos have their own terms).

## Check before shipping a change

```sh
npm run check                                   # CleanFrame source checks
CLEANFRAME_PUBLIC_FONTS=1 npm run check:browser # UI behaviour, Tizen path unchanged
cd android && ./gradlew assembleRelease         # APK
```
