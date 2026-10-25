package app.cleanframe;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.LruCache;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.webkit.WebViewAssetLoader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/** Serves CleanFrame's files and installed-app icons from https://appassets.androidplatform.net. */
final class WebSupport {
    static final String ORIGIN = "https://appassets.androidplatform.net";
    static final String HOME_URL = ORIGIN + "/assets/www/index.html";
    static final String DREAM_URL = ORIGIN + "/assets/www/dream.html";
    private static final int ICON_PX = 192;
    private static final LruCache<String, byte[]> ICONS = new LruCache<>(4 * 1024 * 1024) {
        @Override protected int sizeOf(String key, byte[] value) { return value.length; }
    };

    private WebSupport() {}

    @SuppressLint("SetJavaScriptEnabled")
    static void configure(Context context, WebView web, NativeBridge bridge, Runnable onCrash) {
        Context app = context.getApplicationContext();
        WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(app))
                .addPathHandler("/icon/", path -> iconResponse(app, path))
                .build();
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setTextZoom(100);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        /* Honour the page's width=1920 viewport and scale it to fill the screen. */
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        web.setBackgroundColor(Color.BLACK);
        web.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        if (bridge != null) web.addJavascriptInterface(bridge, "CleanFrameNative");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                /* The launcher never navigates away from its own files. */
                return !ORIGIN.equals(request.getUrl().getScheme() + "://" + request.getUrl().getHost());
            }
            @Override
            public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                if (onCrash != null) onCrash.run();
                return true;
            }
        });
    }

    /** /icon/<package>.png → the app's launcher icon as a PNG. */
    private static WebResourceResponse iconResponse(Context c, @NonNull String path) {
        String pkg = Uri.decode(path);
        if (pkg.endsWith(".png")) pkg = pkg.substring(0, pkg.length() - 4);
        if (!pkg.matches("[A-Za-z0-9._]{1,200}")) return null;
        byte[] png = ICONS.get(pkg);
        if (png == null) {
            try {
                Drawable d = c.getPackageManager().getApplicationIcon(pkg);
                Bitmap bmp = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bmp);
                d.setBounds(0, 0, ICON_PX, ICON_PX);
                d.draw(canvas);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
                bmp.recycle();
                png = out.toByteArray();
                ICONS.put(pkg, png);
            } catch (PackageManager.NameNotFoundException e) {
                return null;
            }
        }
        WebResourceResponse r = new WebResourceResponse("image/png", null, new ByteArrayInputStream(png));
        r.setResponseHeaders(java.util.Collections.singletonMap("Cache-Control", "max-age=86400"));
        return r;
    }
}
