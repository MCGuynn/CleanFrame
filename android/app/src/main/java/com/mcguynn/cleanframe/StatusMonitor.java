package com.mcguynn.cleanframe;

import android.app.AppOpsManager;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Network type and Wi-Fi usage for the status line (Arc's network + data usage widgets). */
final class StatusMonitor {
    static final long USAGE_OFF = -1, USAGE_NO_ACCESS = -2;
    private static final long USAGE_EVERY_MS = 5 * 60 * 1000;

    private final Context context;
    private final Runnable onChange;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile String network = "Offline";
    private volatile long usage = USAGE_OFF;
    private ConnectivityManager.NetworkCallback callback;
    private final Runnable usageTick = this::refreshUsage;

    StatusMonitor(Context context, Runnable onChange) {
        this.context = context.getApplicationContext();
        this.onChange = onChange;
    }

    String json() {
        try {
            return new JSONObject().put("network", network).put("usage", usage).toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    void start() {
        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        network = describe(cm, cm.getActiveNetwork());
        callback = new ConnectivityManager.NetworkCallback() {
            @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities caps) { update(cm); }
            @Override public void onLost(Network n) { update(cm); }
        };
        try { cm.registerDefaultNetworkCallback(callback); } catch (RuntimeException ignored) { }
        refreshUsage();
    }

    void stop() {
        main.removeCallbacks(usageTick);
        if (callback != null) {
            try { context.getSystemService(ConnectivityManager.class).unregisterNetworkCallback(callback); } catch (RuntimeException ignored) { }
            callback = null;
        }
    }

    private void update(ConnectivityManager cm) {
        String next = describe(cm, cm.getActiveNetwork());
        if (!next.equals(network)) { network = next; main.post(onChange); }
    }

    private static String describe(ConnectivityManager cm, Network n) {
        NetworkCapabilities c = n == null ? null : cm.getNetworkCapabilities(n);
        if (c == null) return "Offline";
        if (c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Ethernet";
        if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "Wi‑Fi";
        return "Online";
    }

    /** Recompute usage now, then every five minutes while running. */
    void refreshUsage() {
        main.removeCallbacks(usageTick);
        worker.execute(() -> {
            long next = computeUsage();
            boolean changed = next != usage;
            usage = next;
            if (changed) main.post(onChange);
            main.postDelayed(usageTick, USAGE_EVERY_MS);
        });
    }

    private long computeUsage() {
        String period = Prefs.usagePeriod(context);
        if ("off".equals(period)) return USAGE_OFF;
        if (!hasUsageAccess()) return USAGE_NO_ACCESS;
        Calendar from = Calendar.getInstance();
        from.set(Calendar.HOUR_OF_DAY, 0); from.set(Calendar.MINUTE, 0);
        from.set(Calendar.SECOND, 0); from.set(Calendar.MILLISECOND, 0);
        if ("week".equals(period)) from.set(Calendar.DAY_OF_WEEK, from.getFirstDayOfWeek());
        else if ("month".equals(period)) from.set(Calendar.DAY_OF_MONTH, 1);
        try {
            NetworkStatsManager nsm = context.getSystemService(NetworkStatsManager.class);
            NetworkStats.Bucket b = nsm.querySummaryForDevice(ConnectivityManager.TYPE_WIFI, null,
                    from.getTimeInMillis(), System.currentTimeMillis());
            return b == null ? 0 : b.getRxBytes() + b.getTxBytes();
        } catch (Exception e) {
            return 0;
        }
    }

    @SuppressWarnings("deprecation")
    private boolean hasUsageAccess() {
        AppOpsManager ops = context.getSystemService(AppOpsManager.class);
        int mode = Build.VERSION.SDK_INT >= 29
                ? ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName())
                : ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }
}
