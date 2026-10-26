/* Android / Google TV device layer. Replaces app/device.js inside the APK.
   Same FrameDevice contract as the Tizen file, backed by the CleanFrameNative bridge. */
(function () {
  'use strict';
  function N() { return window.CleanFrameNative || null; }
  function unavailable(done) {
    setTimeout(function () {
      done({ message: 'Open CleanFrame on the TV to use this control.', detail: 'The web preview cannot control the TV.' });
    }, 0);
  }
  /* Bridge calls are synchronous; defer them so the remote press renders first. */
  function call(done, run) {
    if (!N()) { unavailable(done); return; }
    setTimeout(function () {
      var err = '';
      try { err = run(N()) || ''; } catch (e) { err = e && e.message || 'The TV did not respond.'; }
      if (err) done({ message: err, detail: 'Press Home on the remote to return to CleanFrame.' });
      else done(null);
    }, 0);
  }
  function launchApp(id, done) { call(done, function (n) { return n.launch(String(id)); }); }
  function launch(brand, done) {
    var P = window.CleanFramePlatform, a = P && P.primaryById && P.primaryById(brand);
    if (!a) { done({ message: 'This app has no launch target.' }); return; }
    launchApp(a.appId, done);
  }
  function art(done) { call(done, function (n) { return n.startScreensaver(); }); }
  function home(done) { call(done, function (n) { return n.openSystem('home'); }); }
  function settings(done) { call(done, function (n) { return n.openSystem('settings'); }); }
  function apps(done) {
    if (!N()) { unavailable(done); return; }
    setTimeout(function () {
      var list, P = window.CleanFramePlatform || {}, out = [];
      try { list = JSON.parse(N().installedApps()); }
      catch (e) { done({ message: 'Could not read installed apps.' }); return; }
      list.forEach(function (a) {
        var brand = P.brandFor ? P.brandFor(a.pkg) : '';
        out.push({ appId: a.pkg, name: a.name, brand: brand, icon: brand ? '' : (P.iconPrefix || '') + encodeURIComponent(a.pkg) + '.png' });
      });
      done(null, out);
    }, 0);
  }
  window.FrameDevice = { launch: launch, launchApp: launchApp, art: art, home: home, settings: settings, apps: apps };
}());
