package app.cleanframe;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.text.InputType;
import android.app.AlertDialog;

/** CleanFrame as the Google TV Home app: one full-screen WebView plus remote handling. */
public class MainActivity extends Activity implements NativeBridge.Host {
    private FrameLayout root;
    private MaterialsWorker materials;
    /* Set when new Unsplash photos are ready; the page reloads on the next Home press. */
    private boolean reloadOnHome;
    private WebView web;
    private StatusMonitor status;
    private NativeBridge bridge;
    private boolean pageReady;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        status = new StatusMonitor(this, () -> js("window.CleanFrameAndroid && CleanFrameAndroid.refresh()"));
        bridge = new NativeBridge(this, status, this);
        root = new FrameLayout(this);
        root.setBackgroundColor(android.graphics.Color.BLACK);
        setContentView(root);
        materials = new MaterialsWorker(this, root, () -> { reloadOnHome = true; refreshUi(); });
        Unsplash.get(this).setListeners(this::refreshUi, () -> materials.kick());
        createWebView();
        status.start();
    }

    private void createWebView() {
        if (web != null) {
            ViewGroup p = (ViewGroup) web.getParent();
            if (p != null) p.removeView(web);
            web.destroy();
        }
        pageReady = false;
        web = new WebView(this);
        WebSupport.configure(this, web, bridge, this::createWebView);
        web.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int p) { if (p == 100) pageReady = true; }
        });
        root.addView(web, 0, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        web.loadUrl(WebSupport.HOME_URL);
        web.requestFocus();
    }

    /** Remote Home while CleanFrame is already showing: return to the main screen. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (reloadOnHome && web != null) { reloadOnHome = false; pageReady = false; web.reload(); }
            else js("window.CleanFrameAndroid && CleanFrameAndroid.onHome()");
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK) {
            /* Never leave Home on Back; CleanFrame decides (Arc's Back button action). */
            if (pageReady && e.getRepeatCount() == 0) {
                js("window.CleanFrameAndroid && CleanFrameAndroid.back('" + (e.getAction() == KeyEvent.ACTION_DOWN ? "keydown" : "keyup") + "')");
            }
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN && Prefs.soundsOn(this)) playNavigationSound(code);
        return super.dispatchKeyEvent(e);
    }

    private void playNavigationSound(int code) {
        int effect;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: effect = AudioManager.FX_FOCUS_NAVIGATION_LEFT; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: effect = AudioManager.FX_FOCUS_NAVIGATION_RIGHT; break;
            case KeyEvent.KEYCODE_DPAD_UP: effect = AudioManager.FX_FOCUS_NAVIGATION_UP; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: effect = AudioManager.FX_FOCUS_NAVIGATION_DOWN; break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER: effect = AudioManager.FX_KEY_CLICK; break;
            default: return;
        }
        AudioManager am = getSystemService(AudioManager.class);
        if (am != null) am.playSoundEffect(effect);
    }

    @Override public void reloadOnHome() { runOnUiThread(() -> reloadOnHome = true); }

    private void refreshUi() { js("window.CleanFrameAndroid && CleanFrameAndroid.refresh()"); }

    /** TV keyboard entry for the Unsplash key and username. */
    @Override
    public void prompt(String title, String hint, String current, boolean secret, java.util.function.Consumer<String> done) {
        runOnUiThread(() -> {
            EditText input = new EditText(this);
            input.setSingleLine(true);
            input.setHint(hint);
            input.setText(current);
            input.setInputType(InputType.TYPE_CLASS_TEXT | (secret ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : 0));
            int pad = (int) (24 * getResources().getDisplayMetrics().density);
            FrameLayout box = new FrameLayout(this);
            box.setPadding(pad, pad / 2, pad, 0);
            box.addView(input);
            AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(title)
                    .setView(box)
                    .setPositiveButton("Save", (dlg, which) -> done.accept(input.getText().toString()))
                    .setNegativeButton("Cancel", null)
                    .create();
            d.setOnDismissListener(x -> { if (web != null) web.requestFocus(); });
            d.show();
            input.requestFocus();
        });
    }

    private void js(String script) {
        runOnUiThread(() -> { if (web != null) web.evaluateJavascript(script, null); });
    }

    @Override protected void onResume() { super.onResume(); if (web != null) { web.onResume(); web.requestFocus(); } status.refreshUsage(); materials.kick(); }
    @Override protected void onPause() { if (web != null) web.onPause(); super.onPause(); }

    @Override
    protected void onDestroy() {
        status.stop();
        materials.stop();
        Unsplash.get(this).setListeners(null, null);
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}
