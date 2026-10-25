package app.cleanframe;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.webkit.WebResourceResponse;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

/**
 * Arc-style white app marks made on the TV from each app's own icon.
 *
 * Most Android TV icons are adaptive: a background colour plus a transparent logo layer.
 * That logo layer, recoloured white, sits beside CleanFrame's bundled marks. Icons that are
 * a solid tile (no usable transparency) are skipped and keep their original colour icon.
 */
final class DeviceMarks {
    static final String PREFIX = "pkg-";
    private static final int N = 240;
    private static final double MAX_COVER = 0.62, MIN_COVER = 0.01;

    private DeviceMarks() {}

    /** {"pkg-<package>": {n,x,y,w,h,fill}} for every launchable app with a usable logo layer. */
    static synchronized String metricsJson(Context c) {
        JSONObject out = new JSONObject();
        PackageManager pm = c.getPackageManager();
        Set<String> seen = new HashSet<>();
        seen.add(c.getPackageName());
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                String pkg = r.activityInfo.packageName;
                if (!seen.add(pkg)) continue;
                try {
                    JSONObject m = metrics(c, pkg);
                    if (m != null) out.put(PREFIX + pkg, m);
                } catch (Exception ignored) { }
            }
        }
        return out.toString();
    }

    /** /assets/www/assets/icons/pkg-<package>.svg → the white mark (served as PNG; browsers sniff the type). */
    static WebResourceResponse serve(Context c, String path) {
        String prefix = "www/assets/icons/" + PREFIX;
        if (!path.startsWith(prefix) || !path.endsWith(".svg")) return null;
        String pkg = path.substring(prefix.length(), path.length() - 4);
        if (!pkg.matches("[A-Za-z0-9._]{1,200}")) return null;
        try {
            File png = cacheFile(c, pkg, ".png");
            if (!png.exists() && metrics(c, pkg) == null) return null;
            if (!png.exists()) return null;
            return new WebResourceResponse("image/png", null, new ByteArrayInputStream(Files.readAllBytes(png.toPath())));
        } catch (Exception e) {
            return null;
        }
    }

    private static File cacheFile(Context c, String pkg, String ext) throws PackageManager.NameNotFoundException {
        PackageInfo info = c.getPackageManager().getPackageInfo(pkg, 0);
        File dir = new File(c.getCacheDir(), "marks");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        @SuppressWarnings("deprecation")
        long version = android.os.Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        return new File(dir, pkg + "-" + version + "-v3" + ext);
    }

    /** Builds (or reads back) the mark for one package; null when its icon has no usable logo layer. */
    private static JSONObject metrics(Context c, String pkg) throws Exception {
        File json = cacheFile(c, pkg, ".json"), png = cacheFile(c, pkg, ".png");
        if (json.exists()) {
            String s = new String(Files.readAllBytes(json.toPath()), "UTF-8");
            return s.isEmpty() ? null : new JSONObject(s);
        }
        Drawable icon = c.getPackageManager().getApplicationIcon(pkg);
        /* 1. The icon's own transparent logo layer (most Android TV apps). */
        int[] px = render(icon, true);
        JSONObject m = analyse(px);
        /* 2. A logo on one flat colour (a navy square with an owl, say): drop that colour. */
        if (m == null) {
            px = render(icon, false);
            if (keyBackground(px)) m = analyse(px);
        }
        if (m != null) {
            Bitmap bmp = Bitmap.createBitmap(px, N, N, Bitmap.Config.ARGB_8888);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            bmp.recycle();
            try (FileOutputStream f = new FileOutputStream(png)) { f.write(out.toByteArray()); }
        }
        try (FileOutputStream f = new FileOutputStream(json)) { f.write((m == null ? "" : m.toString()).getBytes("UTF-8")); }
        return m;
    }

    /** Draws the icon at N×N. Adaptive icons: the logo layer alone, or both layers without the mask. */
    private static int[] render(Drawable icon, boolean logoLayerOnly) {
        Bitmap bmp = Bitmap.createBitmap(N, N, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        if (icon instanceof AdaptiveIconDrawable) {
            /* Adaptive layers are 108 units; the visible icon is the middle 72. Map those 72 to the canvas. */
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) icon;
            int pad = N / 4;
            if (!logoLayerOnly && a.getBackground() != null) { a.getBackground().setBounds(-pad, -pad, N + pad, N + pad); a.getBackground().draw(canvas); }
            if (a.getForeground() != null) { a.getForeground().setBounds(-pad, -pad, N + pad, N + pad); a.getForeground().draw(canvas); }
        } else {
            icon.setBounds(0, 0, N, N);
            icon.draw(canvas);
        }
        int[] px = new int[N * N];
        bmp.getPixels(px, 0, N, 0, 0, N, N);
        bmp.recycle();
        return px;
    }

    /** Metrics for a usable logo shape, turning its pixels white; null for a solid tile or nothing. */
    private static JSONObject analyse(int[] px) throws Exception {
        int left = N, top = N, right = -1, bottom = -1, covered = 0;
        double ink = 0;
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                int i = y * N + x, a = px[i] >>> 24;
                ink += a / 255.0;
                if (a > 40) {
                    covered++;
                    if (x < left) left = x;
                    if (y < top) top = y;
                    if (x > right) right = x;
                    if (y > bottom) bottom = y;
                }
                px[i] = (a << 24) | 0x00FFFFFF; // white, same shape
            }
        }
        double cover = covered / (double) (N * N);
        if (cover < MIN_COVER || cover > MAX_COVER || right < left) return null;
        int w = right - left + 1, h = bottom - top + 1;
        return new JSONObject().put("n", N).put("x", left).put("y", top).put("w", w).put("h", h).put("fill", ink / (w * h));
    }

    /**
     * Makes the icon's dominant flat colour transparent (soft edge for anti-aliasing).
     * Returns false when there is no clear single background colour, e.g. a photo.
     */
    private static boolean keyBackground(int[] px) {
        int[] bins = new int[4096];
        int opaque = 0;
        for (int p : px) {
            if ((p >>> 24) < 200) continue;
            opaque++;
            bins[((p >> 20) & 0xF) << 8 | ((p >> 12) & 0xF) << 4 | ((p >> 4) & 0xF)]++;
        }
        if (opaque < N * N / 4) return false;
        int best = 0;
        for (int i = 1; i < bins.length; i++) if (bins[i] > bins[best]) best = i;
        if (bins[best] < opaque * 0.35) return false;
        /* Average the real colour inside the winning bin. */
        long r = 0, g = 0, b = 0; int n = 0;
        for (int p : px) {
            if ((p >>> 24) < 200) continue;
            if ((((p >> 20) & 0xF) << 8 | ((p >> 12) & 0xF) << 4 | ((p >> 4) & 0xF)) != best) continue;
            r += (p >> 16) & 0xFF; g += (p >> 8) & 0xFF; b += p & 0xFF; n++;
        }
        r /= n; g /= n; b /= n;
        /* On a dark background, anything darker (pupils, shadows) is a cut-out, not part of the logo. */
        double bgLuma = .2126 * r + .7152 * g + .0722 * b;
        boolean dark = bgLuma < 90;
        for (int i = 0; i < px.length; i++) {
            int p = px[i], a = p >>> 24;
            if (a == 0) continue;
            int pr = (p >> 16) & 0xFF, pg = (p >> 8) & 0xFF, pb = p & 0xFF;
            double dr = pr - r, dg = pg - g, db = pb - b;
            double d = Math.sqrt(dr * dr + dg * dg + db * db);
            if (dark && .2126 * pr + .7152 * pg + .0722 * pb <= bgLuma + 6) a = 0;
            else if (d < 40) a = 0;
            else if (d < 70) a = (int) (a * (d - 40) / 30);
            px[i] = (a << 24) | (p & 0x00FFFFFF);
        }
        return true;
    }
}
