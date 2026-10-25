# CleanFrame for Roku

CleanFrame's look on a Roku TV (built for a TCL Roku TV). Roku runs its own OS, so this is a
native Roku app, not the Android APK. One package does two things:

- **CleanFrame** — open it from Roku Home: photo, clock and date, and one frosted row of your
  channels and HDMI inputs, with CleanFrame's white marks where it has them. Left/Right moves,
  OK opens, `*` changes the wallpaper, Back returns to Roku Home.
- **CleanFrame Clock** — a screensaver: the wallpaper, dimmed, with a clock that drifts each
  minute; the photo changes every ten minutes.

## Limits Roku sets

- Roku Home and the Home button always belong to Roku. No app can replace them.
- Opening another channel from an app is the test here. CleanFrame asks the TV's own control
  API (the one phone remote apps use). If Roku refuses, CleanFrame says so on screen; setting
  **Settings › System › Advanced system settings › Control by mobile apps › Network access**
  to **Permissive** usually allows it.
- Roku allows one sideloaded developer app at a time; installing another replaces this one.

## Install (Developer Mode, on your own network)

1. On the Roku remote press **Home ×3, Up ×2, Right, Left, Right, Left, Right**. Enable the
   installer, accept the licence, set a password and let the TV restart.
2. On your Mac, open `http://<TV address>` (for example `http://10.13.10.173`), sign in as
   `rokudev` with that password.
3. **Upload** `CleanFrame-Roku.zip` and press **Install**. CleanFrame opens on the TV.
4. Screensaver: **Settings › Theme › Screensavers › CleanFrame Clock**.

To update, upload the new zip the same way. To remove it, press **Delete** on that page.
If something misbehaves, `telnet <TV address> 8085` shows the app's error log.

## Build

`roku/` is the app. The zip is everything except `tools/` and this README:

```sh
cd roku && zip -r ../dist/CleanFrame-Roku.zip manifest source components images fonts -x '*.DS_Store'
```

Images, marks and fonts are generated from CleanFrame's own assets by
`python3 roku/tools/build_assets.py` and `node roku/tools/marks.mjs roku/images/marks`.
One wallpaper ships in the package; the rest load from this repo on GitHub when the TV is
online (falling back to the bundled one). Fonts: Geist (OFL) static weights.
