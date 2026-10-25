package com.mcguynn.cleanframe;

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

/** CleanFrame as the Google TV Home app: one full-screen WebView plus remote handling. */
public class MainActivity extends Activity {
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
        bridge = new NativeBridge(this, status);
        createWebView();
        status.start();
    }

    private void createWebView() {
        if (web != null) {
            ((ViewGroup) web.getParent()).removeView(web);
            web.destroy();
        }
        pageReady = false;
        web = new WebView(this);
        WebSupport.configure(this, web, bridge, this::createWebView);
        web.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int p) { if (p == 100) pageReady = true; }
        });
        setContentView(web);
        web.loadUrl(WebSupport.HOME_URL);
        web.requestFocus();
    }

    /** Remote Home while CleanFrame is already showing: return to the main screen. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.hasCategory(Intent.CATEGORY_HOME)) {
            js("window.CleanFrameAndroid && CleanFrameAndroid.onHome()");
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

    private void js(String script) {
        runOnUiThread(() -> { if (web != null) web.evaluateJavascript(script, null); });
    }

    @Override protected void onResume() { super.onResume(); if (web != null) { web.onResume(); web.requestFocus(); } status.refreshUsage(); }
    @Override protected void onPause() { if (web != null) web.onPause(); super.onPause(); }

    @Override
    protected void onDestroy() {
        status.stop();
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}
