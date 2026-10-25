/* CleanFrame for Google TV: platform layer read by main.js (window.CleanFramePlatform).
   Supplies Android app targets, Google TV wording, and the Arc Launcher-style options:
   clock format, status line (date, network, Wi-Fi usage), Back button action,
   navigation sounds and screensaver shortcuts. ES5 only, like the rest of the UI. */
(function () {
  'use strict';
  function N() { return window.CleanFrameNative || null; }

  /* ---------- Preferences (native SharedPreferences, localStorage in a browser) ---------- */
  var DEFAULTS = { clock: '12', date: 'on', network: 'off', usage: 'off', back: 'ask', sound: 'off' };
  var CHOICES = {
    clock: [['12', '12-hour'], ['24', '24-hour']],
    date: [['on', 'Shown'], ['off', 'Hidden']],
    network: [['on', 'Shown'], ['off', 'Hidden']],
    usage: [['off', 'Off'], ['day', 'Today'], ['week', 'This week'], ['month', 'This month']],
    back: [['ask', 'Ask before leaving'], ['none', 'Do nothing'], ['clock', 'Show clock'], ['screensaver', 'Start screensaver']],
    sound: [['off', 'Off'], ['on', 'On']]
  };
  function getPref(key) {
    var v = null;
    try { v = N() ? N().getPref(key) : window.localStorage.getItem('cleanframe.android.' + key); } catch (e) { v = null; }
    return valid(key, v) ? v : DEFAULTS[key];
  }
  function setPref(key, value) {
    try { if (N()) N().setPref(key, value); else window.localStorage.setItem('cleanframe.android.' + key, value); } catch (e) { /* keep running */ }
  }
  function valid(key, v) { return CHOICES[key].some(function (c) { return c[0] === v; }); }
  function nameOf(key) { var v = getPref(key), out = v; CHOICES[key].forEach(function (c) { if (c[0] === v) out = c[1]; }); return out; }
  function cycle(key) {
    var list = CHOICES[key], v = getPref(key), i = 0;
    list.forEach(function (c, j) { if (c[0] === v) i = j; });
    setPref(key, list[(i + 1) % list.length][0]);
  }

  /* ---------- Apps ---------- */
  /* Brand ids match the monochrome marks in app/assets/icons/. First installed package wins. */
  var KNOWN = [
    { id: 'youtube', name: 'YouTube', pkgs: ['com.google.android.youtube.tv', 'com.google.android.youtube.googletv'] },
    { id: 'netflix', name: 'Netflix', pkgs: ['com.netflix.ninja'] },
    { id: 'disneyplus', name: 'Disney+', pkgs: ['com.disney.disneyplus'] },
    { id: 'primevideo', name: 'Prime Video', pkgs: ['com.amazon.amazonvideo.livingroom', 'com.amazon.avod.thirdpartyclient'] },
    { id: 'appletv', name: 'Apple TV', pkgs: ['com.apple.atve.androidtv.appletv'] },
    { id: 'hbomax', name: 'HBO Max', pkgs: ['com.wbd.stream', 'com.hbo.hbonow'] },
    { id: 'tubi', name: 'Tubi', pkgs: ['com.tubitv'] },
    { id: 'spotify', name: 'Spotify', pkgs: ['com.spotify.tv.android'] },
    { id: 'stremio', name: 'Stremio', pkgs: ['com.stremio.one'] },
    { id: 'plex', name: 'Plex', pkgs: ['com.plexapp.android'] },
    { id: 'abciview', name: 'ABC iview', pkgs: ['au.net.abc.iview'] },
    { id: 'sbs', name: 'SBS On Demand', pkgs: ['com.sbs.ondemand.tv'] }
  ];
  function installed(pkg) { try { return N().isInstalled(pkg); } catch (e) { return false; } }
  var primary = [];
  KNOWN.forEach(function (k) {
    var pkg = null;
    if (!N()) pkg = k.pkgs[0];
    else k.pkgs.forEach(function (p) { if (!pkg && installed(p)) pkg = p; });
    if (pkg && primary.length < 9) primary.push({ id: k.id, name: k.name, mark: true, appId: pkg });
  });
  function brandFor(pkg) {
    for (var i = 0; i < primary.length; i++) if (primary[i].appId === pkg) return primary[i].id;
    return '';
  }
  function primaryById(id) { for (var i = 0; i < primary.length; i++) if (primary[i].id === id) return primary[i]; return null; }

  /* ---------- Status line (Arc's date / network / Wi-Fi usage widgets) ---------- */
  function nativeStatus() { try { return JSON.parse(N().status()); } catch (e) { return null; } }
  function bytes(n) {
    if (n >= 1e9) return (n / 1e9).toFixed(n >= 1e10 ? 0 : 1) + ' GB';
    if (n >= 1e6) return Math.round(n / 1e6) + ' MB';
    return Math.max(0, Math.round(n / 1e3)) + ' KB';
  }
  var PERIOD = { day: 'today', week: 'this week', month: 'this month' };
  function status() {
    var parts = [], st = (getPref('network') === 'on' || getPref('usage') !== 'off') && N() ? nativeStatus() : null;
    if (getPref('date') === 'on') {
      try { parts.push(new Date().toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' })); }
      catch (e) { parts.push(new Date().toDateString().slice(0, 10)); }
    }
    if (getPref('network') === 'on' && st) parts.push(st.network || 'Offline');
    if (getPref('usage') !== 'off' && st) {
      if (st.usage === -2) parts.push('Wi‑Fi usage needs access');
      else if (st.usage >= 0) parts.push(bytes(st.usage) + ' ' + PERIOD[getPref('usage')]);
    }
    return parts.join('  ·  ');
  }

  /* ---------- Full-screen clock (Arc's "Show clock" Back action) ---------- */
  var clockEl = null, clockTimer = 0;
  function two(n) { return (n < 10 ? '0' : '') + n; }
  function clockLine() {
    var d = new Date(), h = d.getHours();
    if (getPref('clock') === '24') return two(h) + ':' + two(d.getMinutes());
    return (h % 12 || 12) + ':' + two(d.getMinutes());
  }
  function paintClock() {
    if (!clockEl) return;
    var d = new Date(), date = '';
    try { date = d.toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' }); } catch (e) { date = d.toDateString(); }
    clockEl.firstChild.firstChild.textContent = clockLine();
    clockEl.firstChild.lastChild.textContent = date;
    /* Small drift each minute keeps OLED and LCD panels from holding one image. */
    clockEl.firstChild.style.transform = 'translate(' + (Math.round(Math.random() * 160) - 80) + 'px,' + (Math.round(Math.random() * 120) - 60) + 'px)';
    clockTimer = setTimeout(paintClock, 60000 - (Date.now() % 60000) + 30);
  }
  function showClock() {
    if (clockEl) return;
    clockEl = document.createElement('div');
    clockEl.className = 'cf-fullclock';
    clockEl.innerHTML = '<div class="cf-fullclock-inner"><div class="cf-fullclock-time"></div><div class="cf-fullclock-date"></div></div>';
    document.body.appendChild(clockEl);
    clockEl.addEventListener('click', hideClock);
    paintClock();
  }
  function hideClock() {
    if (!clockEl) return false;
    clearTimeout(clockTimer);
    clockEl.parentNode.removeChild(clockEl); clockEl = null;
    return true;
  }
  /* Any remote key dismisses the clock without reaching the Home screen underneath. */
  window.addEventListener('keydown', function (ev) {
    if (!clockEl) return;
    ev.preventDefault(); ev.stopPropagation();
    hideClock();
  }, true);

  function homeBack() {
    var b = getPref('back');
    if (b === 'ask') return false;
    if (b === 'clock') showClock();
    else if (b === 'screensaver' && N()) { try { N().startScreensaver(); } catch (e) { /* ignore */ } }
    return true;
  }

  /* ---------- Settings groups ---------- */
  function usageNeedsAccess() { var st = N() ? nativeStatus() : null; return !!(st && st.usage === -2); }
  function item(action, label, description) { return { action: action, label: label, description: description }; }
  var SECTIONS = {
    googletv: function () {
      var system = [
        item('sys.home', 'Google TV Home', 'Open the built-in Google TV home screen'),
        item('sys.settings', 'TV settings', 'Picture, sound, apps and system'),
        item('sys.wifi', 'Network and internet', 'Wi\u2011Fi and Ethernet settings'),
        item('sys.screensaver', 'Start screensaver', 'Ambient mode, right now'),
        item('sys.dream', 'Screensaver settings', 'Choose CleanFrame Clock as the screensaver')
      ];
      if (usageNeedsAccess()) system.push(item('sys.usage', 'Usage access', 'Needed for the Wi\u2011Fi usage counter'));
      return [
        { id: 'arc-clock', title: 'Clock and status line', items: [
          item('arc.clock', 'Clock format', nameOf('clock')),
          item('arc.date', 'Date', nameOf('date')),
          item('arc.network', 'Network', nameOf('network')),
          item('arc.usage', 'Wi\u2011Fi usage', nameOf('usage') + (getPref('usage') !== 'off' && usageNeedsAccess() ? ' \u2014 needs usage access (below)' : ''))
        ] },
        { id: 'arc-remote', title: 'Remote', items: [
          item('arc.back', 'Back button on Home', nameOf('back')),
          item('arc.sound', 'Navigation sounds', nameOf('sound'))
        ] },
        { id: 'arc-system', title: 'Google TV', items: system }
      ];
    }
  };
  var ARC = { 'arc.clock': 'clock', 'arc.date': 'date', 'arc.network': 'network', 'arc.usage': 'usage', 'arc.back': 'back', 'arc.sound': 'sound' };
  var SYS = { 'sys.home': ['Google TV Home', 'home'], 'sys.settings': ['TV settings', 'settings'], 'sys.wifi': ['Network and internet', 'wifi'],
    'sys.dream': ['Screensaver settings', 'dream'], 'sys.usage': ['Usage access', 'usage'] };
  function action(id, perform) {
    if (ARC[id]) {
      cycle(ARC[id]);
      if (ARC[id] === 'sound' && N()) try { N().setPref('sound', getPref('sound')); } catch (e) { /* ignore */ }
      if (ARC[id] === 'usage' && N()) try { N().refreshStatus(); } catch (e) { /* ignore */ }
      return true;
    }
    if (id === 'sys.screensaver') { perform('Screensaver', window.FrameDevice.art); return true; }
    if (SYS[id]) {
      perform(SYS[id][0], function (done) {
        if (!N()) { window.FrameDevice.settings(done); return; }
        setTimeout(function () {
          var err = '';
          try { err = N().openSystem(SYS[id][1]); } catch (e) { err = 'Google TV could not open this screen.'; }
          done(err ? { message: err } : null);
        }, 0);
      });
      return true;
    }
    return false;
  }

  window.CleanFramePlatform = {
    primaryApps: primary,
    primaryById: primaryById,
    brandFor: brandFor,
    iconPrefix: 'https://appassets.androidplatform.net/icon/',
    artLabel: 'Screensaver',
    homeName: 'Google TV Home',
    leave: { title: 'Leave CleanFrame?', line: 'Open Google TV Home?', note: '', actions: [['stay', 'Stay'], ['samsung', 'Google TV Home']] },
    settingsRows: function () {
      return [{ id: 'googletv', label: 'Google TV', value: 'Clock, status line, remote and TV settings' }];
    },
    groupNames: { googletv: 'Google TV' },
    sections: SECTIONS,
    action: action,
    clock24: function () { return getPref('clock') === '24'; },
    status: status,
    homeBack: homeBack
  };

  /* The Google TV page is taller than the screen: keep the focused option in view. */
  function followFocus() {
    var rows = document.getElementById('appearanceRows');
    if (!rows || !window.MutationObserver) return;
    new MutationObserver(function () {
      var f = rows.querySelector('.is-focus');
      if (!f) { rows.scrollTop = 0; return; }
      var top = 0, el = f;
      while (el && el !== rows) { top += el.offsetTop; el = el.offsetParent; }
      var bottom = top + f.offsetHeight;
      if (top - 64 < rows.scrollTop) rows.scrollTop = Math.max(0, top - 64);
      else if (bottom + 24 > rows.scrollTop + rows.clientHeight) rows.scrollTop = bottom + 24 - rows.clientHeight;
    }).observe(rows, { subtree: true, childList: true, attributes: true, attributeFilter: ['class'] });
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', followFocus); else followFocus();

  /* The WebView can settle its size after the first paint; re-run CleanFrame's fit() as it does. */
  function refit() { try { window.dispatchEvent(new Event('resize')); } catch (e) { /* ignore */ } }
  window.addEventListener('load', function () { refit(); setTimeout(refit, 300); setTimeout(refit, 1500); });

  /* ---------- Hooks the Android activity calls ---------- */
  window.CleanFrameAndroid = {
    /* Remote Home pressed while CleanFrame is already in front. */
    onHome: function () {
      hideClock();
      try { if (window.ShoreQA && window.ShoreQA.scene) window.ShoreQA.scene('home'); } catch (e) { /* ignore */ }
    },
    /* Network or usage changed: main.js re-renders the caption on window focus. */
    refresh: function () { try { window.dispatchEvent(new Event('focus')); } catch (e) { /* ignore */ } },
    /* Android Back is not a web key; deliver it as Escape so main.js treats it as Back. */
    back: function (type) {
      var ev;
      try { ev = new KeyboardEvent(type, { key: 'Escape', code: 'Escape', keyCode: 27, which: 27, bubbles: true, cancelable: true }); }
      catch (e) { return; }
      if (ev.keyCode !== 27) { try { Object.defineProperty(ev, 'keyCode', { get: function () { return 27; } }); } catch (e2) { /* ignore */ } }
      (document.activeElement || document).dispatchEvent(ev);
    }
  };
}());
