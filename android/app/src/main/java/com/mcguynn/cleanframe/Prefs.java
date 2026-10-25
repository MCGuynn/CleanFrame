package com.mcguynn.cleanframe;

import android.content.Context;
import android.content.SharedPreferences;

/** Launcher options (Arc-style settings). The look itself lives in the UI's localStorage. */
final class Prefs {
    private static final String FILE = "cleanframe";
    private Prefs() {}

    static SharedPreferences of(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
    static String get(Context c, String key) { return of(c).getString(key, null); }
    static void set(Context c, String key, String value) { of(c).edit().putString(key, value).apply(); }
    static boolean soundsOn(Context c) { return "on".equals(get(c, "sound")); }
    static String usagePeriod(Context c) { String v = get(c, "usage"); return v == null ? "off" : v; }
}
