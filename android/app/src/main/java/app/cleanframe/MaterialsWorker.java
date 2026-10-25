package app.cleanframe;

import android.app.Activity;
import android.util.Base64;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.FrameLayout;

import org.json.JSONObject;

/**
 * Renders CleanFrame's glass and blur files for new Unsplash photos, using the same
 * canvas recipe as scripts/build-materials.mjs. Runs in an invisible WebView only while
 * there is work, so nothing heavy happens on the Home screen's animation path.
 */
final class MaterialsWorker {
    private static final String URL = WebSupport.ORIGIN + "/assets/www/materials.html";
    private final Activity activity;
    private final FrameLayout parent;
    private final Runnable onDone;
    private WebView web;

    MaterialsWorker(Activity activity, FrameLayout parent, Runnable onDone) {
        this.activity = activity;
        this.parent = parent;
        this.onDone = onDone;
    }

    /** Starts the worker if photos are waiting and it isn't already running. */
    void kick() {
        if (web != null) return;
        try {
            if (new org.json.JSONArray(Unsplash.get(activity).pendingJson()).length() == 0) return;
        } catch (Exception e) { return; }
        web = new WebView(activity);
        WebSupport.configure(activity, web, null, this::stop);
        web.addJavascriptInterface(new Bridge(), "CleanFrameWorker");
        web.setAlpha(0f);
        web.setFocusable(false);
        parent.addView(web, new FrameLayout.LayoutParams(2, 2));
        web.loadUrl(URL);
    }

    void stop() {
        if (web == null) return;
        ViewGroup p = (ViewGroup) web.getParent();
        if (p != null) p.removeView(web);
        web.destroy();
        web = null;
    }

    private final class Bridge {
        @JavascriptInterface public String pending() { return Unsplash.get(activity).pendingJson(); }

        @JavascriptInterface
        public void save(String id, String suffix, String base64) {
            try { Unsplash.get(activity).saveMaterial(id, suffix, Base64.decode(base64, Base64.DEFAULT)); }
            catch (Exception ignored) { }
        }

        @JavascriptInterface
        public void finish(String id, String look) {
            try { Unsplash.get(activity).finish(id, new JSONObject(look)); } catch (Exception ignored) { }
        }

        @JavascriptInterface public void fail(String id) { Unsplash.get(activity).fail(id); }

        @JavascriptInterface
        public void idle() {
            activity.runOnUiThread(() -> { stop(); if (onDone != null) onDone.run(); });
        }
    }
}
