package app.cleanframe;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.Settings;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * window.CleanFrameNative — the only door from the web UI into Android.
 * Every method returns a string ("" = success, otherwise a short message for the UI).
 */
final class NativeBridge {
    /** Stock home screens CleanFrame can hand over to ("Google TV Home"). */
    private static final String[] STOCK_HOMES = {
            "com.google.android.apps.tv.launcherx",   // Google TV
            "com.google.android.tvlauncher",          // Android TV
            "com.google.android.leanbacklauncher"
    };
    private static final Set<String> ALLOWED_PREFS = new HashSet<>(java.util.Arrays.asList(
            "clock", "date", "network", "usage", "back", "sound", "marks"));

    /** What the bridge needs from the visible activity (text entry with the TV keyboard). */
    interface Host {
        void prompt(String title, String hint, String current, boolean secret, java.util.function.Consumer<String> done);
        /** Reload the page on the next Home press (settings that change what the page loads). */
        void reloadOnHome();
    }

    private final Context context;
    private final StatusMonitor status;
    private final Host host;

    NativeBridge(Context context, StatusMonitor status) { this(context, status, null); }

    NativeBridge(Context context, StatusMonitor status, Host host) {
        this.context = context.getApplicationContext();
        this.status = status;
        this.host = host;
    }

    /* ---------- Apps ---------- */

    @JavascriptInterface
    public String installedApps() {
        PackageManager pm = context.getPackageManager();
        JSONArray out = new JSONArray();
        Set<String> seen = new HashSet<>();
        seen.add(context.getPackageName());
        List<ResolveInfo> all = new ArrayList<>();
        all.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        int tvCount = all.size();
        all.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        for (int i = 0; i < all.size(); i++) {
            ResolveInfo r = all.get(i);
            String pkg = r.activityInfo.packageName;
            if (!seen.add(pkg)) continue;
            if (isStockHome(pkg)) continue;
            try {
                JSONObject o = new JSONObject();
                o.put("pkg", pkg);
                o.put("name", String.valueOf(r.loadLabel(pm)));
                o.put("tv", i < tvCount);
                out.put(o);
            } catch (Exception ignored) { }
        }
        return out.toString();
    }

    @JavascriptInterface
    public boolean isInstalled(String pkg) {
        if (pkg == null) return false;
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(pkg, 0);
            return info.enabled && launchIntent(pkg) != null;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @JavascriptInterface
    public String launch(String pkg) {
        Intent intent = launchIntent(pkg);
        if (intent == null) return "This app isn’t installed on the TV.";
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return start(intent) ? "" : "The TV could not open this app.";
    }

    private Intent launchIntent(String pkg) {
        PackageManager pm = context.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }

    private static boolean isStockHome(String pkg) {
        for (String h : STOCK_HOMES) if (h.equals(pkg)) return true;
        return false;
    }

    /* ---------- System screens (Arc's Wi-Fi / screensaver / settings shortcuts) ---------- */

    @JavascriptInterface
    public String openSystem(String what) {
        switch (String.valueOf(what)) {
            case "home":
                for (String pkg : STOCK_HOMES) {
                    Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(pkg)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (start(home)) return "";
                }
                return "Google TV Home is turned off. Use TV Box Tools to turn it back on if you need it.";
            case "settings":
                return start(new Intent(Settings.ACTION_SETTINGS)) ? "" : "TV settings are not available.";
            case "wifi":
                if (start(new Intent(Settings.ACTION_WIFI_SETTINGS))) return "";
                if (start(new Intent(Settings.ACTION_WIRELESS_SETTINGS))) return "";
                return start(new Intent(Settings.ACTION_SETTINGS)) ? "" : "Network settings are not available.";
            case "dream":
                return start(new Intent(Settings.ACTION_DREAM_SETTINGS)) ? ""
                        : "This TV hides screensaver settings. Find them under Settings › System › Ambient mode.";
            case "usage":
                if (start(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:" + context.getPackageName())))) return "";
                if (start(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))) return "";
                return "Google TV hides this screen. Grant it once with adb: appops set " + context.getPackageName() + " GET_USAGE_STATS allow";
            default:
                return "Unknown screen.";
        }
    }

    @JavascriptInterface
    public String startScreensaver() {
        Intent dream = new Intent(Intent.ACTION_MAIN)
                .setComponent(new ComponentName("com.android.systemui", "com.android.systemui.Somnambulator"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (start(dream)) return "";
        return start(new Intent(Settings.ACTION_DREAM_SETTINGS)) ? "" : "This TV can’t start the screensaver directly.";
    }

    /* ---------- White marks made from installed apps' icons ---------- */

    @JavascriptInterface
    public void reloadOnHome() { if (host != null) host.reloadOnHome(); }

    @JavascriptInterface
    public String deviceMarks() { return DeviceMarks.metricsJson(context); }

    /* ---------- Unsplash ---------- */

    @JavascriptInterface public String unsplashInfo() { return Unsplash.get(context).infoJson(); }
    @JavascriptInterface public String unsplashPhotos() { return Unsplash.get(context).readyPhotos().toString(); }
    @JavascriptInterface public void unsplashSource() { Unsplash.get(context).nextSource(); }
    @JavascriptInterface public void unsplashFetch(String keepId) { Unsplash.get(context).fetch(String.valueOf(keepId)); }
    @JavascriptInterface public void unsplashClear(String keepId) { Unsplash.get(context).clear(String.valueOf(keepId)); }

    /** Opens the TV keyboard for the access key ("key") or Unsplash username ("user"). */
    @JavascriptInterface
    public String unsplashEdit(String field) {
        if (host == null) return "Text entry isn't available here.";
        Unsplash u = Unsplash.get(context);
        if ("key".equals(field)) {
            host.prompt("Unsplash access key", "Paste or type the Access Key from unsplash.com/oauth/applications", "", true, u::setKey);
        } else if ("user".equals(field)) {
            host.prompt("Unsplash username", "Your username, to use your collections (optional)", u.username(), false, u::setUsername);
        } else return "Unknown field.";
        return "";
    }

    /* ---------- Preferences and status ---------- */

    @JavascriptInterface
    public String getPref(String key) {
        if (!ALLOWED_PREFS.contains(key)) return null;
        return Prefs.get(context, key);
    }

    @JavascriptInterface
    public void setPref(String key, String value) {
        if (!ALLOWED_PREFS.contains(key) || value == null || value.length() > 32) return;
        Prefs.set(context, key, value);
        if ("usage".equals(key) || "network".equals(key)) status.refreshUsage();
    }

    @JavascriptInterface
    public String status() { return status.json(); }

    @JavascriptInterface
    public void refreshStatus() { status.refreshUsage(); }

    private boolean start(Intent intent) {
        if (!(context instanceof android.app.Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }
}
