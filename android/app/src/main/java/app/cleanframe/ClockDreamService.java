package app.cleanframe;

import android.service.dreams.DreamService;
import android.webkit.WebView;

/** "CleanFrame Clock" screensaver: the chosen wallpaper, dimmed, with a drifting clock. */
public class ClockDreamService extends DreamService {
    private WebView web;
    private StatusMonitor status;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);
        status = new StatusMonitor(this, () -> { });
        web = new WebView(this);
        WebSupport.configure(this, web, new NativeBridge(this, status), null);
        setContentView(web);
        web.loadUrl(WebSupport.DREAM_URL);
    }

    @Override
    public void onDetachedFromWindow() {
        if (web != null) { web.destroy(); web = null; }
        super.onDetachedFromWindow();
    }
}
