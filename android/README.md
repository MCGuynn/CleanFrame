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

### Home row, white icons and Unsplash

- **Up to 24 apps on Home**, still one row (Dock) or one column (List) that scrolls. Add, remove and reorder them in **Settings › Apps on Home** (OK on an app lifts it, Up/Down moves it, OK places it).
- **White app icons, Arc style.** Bundled marks for YouTube, Netflix, Disney+, Prime Video, Apple TV, HBO Max, Tubi, Spotify, Stremio, Plex, Crunchyroll and fubo. Every other app (Hulu, ESPN, Peacock, Bubo…) gets a white mark made on the TV from the logo layer of its own icon. Icons with no logo layer but a flat background (a logo on a navy square, say) get that colour removed instead. Only busy, photo-like icons keep their original colours. Switch with Settings › Google TV › App icons.
- **Unsplash wallpapers** with your own developer Access Key: Settings › Google TV › Unsplash wallpapers.
  1. Create an app at <https://unsplash.com/oauth/applications> and copy its **Access Key**.
  2. *Access key* → OK opens the TV keyboard (or type it with the Google TV remote app on your phone).
  3. Optional: *Your username* to use your own collections; *Photos from* cycles through each collection, all of them, or Unsplash landscapes.
  4. *Get new photos* downloads 10 at 1920×1080, then the TV renders CleanFrame's glass and blur versions in the background (a few seconds each). Press Home when it says they're ready; they appear in Settings › Background › Wallpaper and in timed Rotation.
  The photographer's name shows under the clock while their photo is on screen, as Unsplash asks. Up to 40 photos are kept; the one on screen is never removed. Demo keys allow 50 requests an hour, plenty for this.

The top bar's Art Mode button becomes **Screensaver**.

## Build the APK

Pick one:

**GitHub (no tools needed).** Push this repo to your fork. The *Google TV APK* workflow builds `CleanFrame.apk` and attaches it (with its SHA‑256) to the run under *Artifacts*. You can also run it by hand from the Actions tab.

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
4. **Make it Home.** Use *provision* to set `app.cleanframe/.MainActivity` as the HOME app (it switches the stock launcher off with `pm disable-user`, keeping its data). Google TV also has `com.google.android.tungsten.setupwraith`, which can switch the stock launcher back on; if the Home button still lands on Google TV after a reboot, that package needs disabling too.
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
  `adb shell appops set app.cleanframe GET_USAGE_STATS allow`
- **Screensaver:** choose *CleanFrame Clock* under Settings › System › Ambient mode › Screensaver (Screensaver settings in CleanFrame opens it where the TV allows).

## Things to know

- Input switching, live TV and Google's recommendations rows come from the stock launcher. CleanFrame doesn't replace them; keep the TCL's Inputs button / Live TV app for HDMI.
- "Start screensaver" uses the system's dream starter (`Somnambulator`). If TCL hides it, CleanFrame opens screensaver settings instead.
- The only network use is Unsplash, and only after you add a key. No analytics. The key stays in the app's private storage; the web UI never sees it.
- Unsplash photos are saved on the TV so CleanFrame can pre-render its glass. That's fine for your own TV; an app published for others would need to follow Unsplash's hotlinking guideline.
- Arc Launcher is GPL‑3.0; nothing here copies its code — the options are reimplemented. CleanFrame is MIT; keep `../LICENSE` and `../THIRD_PARTY_NOTICES.md` with any APK you share (brand marks and photos have their own terms).

## Check before shipping a change

```sh
npm run check                                   # CleanFrame source checks
CLEANFRAME_PUBLIC_FONTS=1 npm run check:browser # UI behaviour, Tizen path unchanged
cd android && ./gradlew assembleRelease         # APK
```
