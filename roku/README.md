# CleanFrame Clock for Roku

CleanFrame's look as a Roku TV screensaver (built for a TCL Roku TV): a CleanFrame wallpaper,
dimmed, with a clock and date that drift a little each minute so no part of the panel holds the
same image. The photo changes every ten minutes.

Roku runs its own OS, so this is a native Roku package, not the Android APK.

## Settings

**Settings › Theme › Screensavers › CleanFrame Clock › Custom Settings**:

- **Clock** — 12-hour or 24-hour.
- **Change photo** — every 5, 10 or 30 minutes, or every hour.
- **Photos from** — CleanFrame's photos, Unsplash landscapes, all your Unsplash collections, or one
  of them.
- **Unsplash access key** / **Unsplash username** — OK opens the Roku keyboard (the Roku mobile app's
  keyboard is easier for a long key). Create the key at <https://unsplash.com/oauth/applications>.
- **Get Unsplash photos now** — fetches 20 photos; the screensaver also refreshes them each time it
  starts. The photographer's name shows in the corner, as Unsplash asks.

## Why only a screensaver

Roku Home and the Home button belong to Roku. A CleanFrame-style launcher was tried
(`experiments/`): Roku answered **403** when an app on the TV asked for the channel list, even with
**Control by mobile apps › Network access** set to **Permissive**, so an app can't list or open
other channels. Roku also expects a screensaver package to be a screensaver only.

## Install (Developer Mode, on your own network)

1. On the Roku remote press **Home ×3, Up ×2, Right, Left, Right, Left, Right**; enable the
   installer and set a password.
2. On your Mac open `http://<TV address>` (e.g. `http://10.13.10.173`) and sign in as `rokudev`.
3. **Upload** `CleanFrame-Roku.zip`, then **Replace with zip** (or **Install**).
4. **Settings › Theme › Screensavers** → **CleanFrame Clock** → set it, or **Preview**.

Roku allows one developer package at a time. `telnet <TV address> 8087` shows the screensaver's
error log if something misbehaves.

## Build

```sh
cd roku && zip -r ../dist/CleanFrame-Roku.zip manifest source components images/walls \
  images/splash_fhd.jpg images/icon_focus_fhd.jpg images/icon_focus_hd.jpg \
  images/plate-solid.9.png fonts -x '*.DS_Store'
```

Images and fonts come from CleanFrame's own assets (`python3 roku/tools/build_assets.py`). One
wallpaper ships in the package; the other fourteen load from this repo on GitHub while the TV is
online, falling back to the bundled one. Fonts: Geist (OFL) static weights.
