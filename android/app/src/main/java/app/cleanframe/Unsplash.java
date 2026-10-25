package app.cleanframe;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebResourceResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unsplash wallpapers (Arc's Unsplash slideshow, done the CleanFrame way).
 *
 * Uses your own developer Access Key. Photos come from one of your collections, all of
 * them, or Unsplash landscapes; each is downloaded once at 1920x1080 and then handed to
 * the materials worker, which renders CleanFrame's glass and blur files on the TV.
 * The key stays in this app's private storage and is never given to the web UI.
 */
final class Unsplash {
    static final String PREFIX = "u-";
    private static final String API = "https://api.unsplash.com";
    private static final int BATCH = 10, KEEP = 40;
    private static final String[] SUFFIXES = {"", "-thumb", "-panel", "-field-blur", "-field-frost", "-field-clear", "-study-soft"};
    private static final Pattern SERVED = Pattern.compile(
            "www/assets/(?:wallpapers|materials)/(u-[A-Za-z0-9_-]{1,64}(?:-thumb|-panel|-field-blur|-field-frost|-field-clear|-study-soft)?)\\.jpg");

    private static Unsplash instance;

    static synchronized Unsplash get(Context c) {
        if (instance == null) instance = new Unsplash(c.getApplicationContext());
        return instance;
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final File dir;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile String status = "";
    private volatile boolean busy;
    private Runnable onChange = () -> { };
    private Runnable onNewWork = () -> { };

    private Unsplash(Context c) {
        context = c;
        prefs = c.getSharedPreferences("unsplash", Context.MODE_PRIVATE);
        dir = new File(c.getFilesDir(), "unsplash");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
    }

    void setListeners(Runnable onChange, Runnable onNewWork) {
        this.onChange = onChange == null ? () -> { } : onChange;
        this.onNewWork = onNewWork == null ? () -> { } : onNewWork;
    }

    /* ---------- Settings ---------- */

    void setKey(String key) {
        key = key == null ? "" : key.trim();
        prefs.edit().putString("key", key).remove("collections").apply();
        status = key.isEmpty() ? "" : "Key saved.";
        if (!key.isEmpty() && !username().isEmpty()) loadCollections();
        changed();
    }

    void setUsername(String user) {
        user = user == null ? "" : user.trim().replaceFirst("^@", "");
        prefs.edit().putString("user", user).remove("collections").apply();
        if (!user.isEmpty() && !key().isEmpty()) loadCollections(); else changed();
    }

    String key() { return prefs.getString("key", ""); }
    String username() { return prefs.getString("user", ""); }

    /** Cycle: each of your collections → all of them → Unsplash landscapes. */
    void nextSource() {
        List<String[]> sources = sources();
        String cur = prefs.getString("source", "landscape");
        int i = 0;
        for (int j = 0; j < sources.size(); j++) if (sources.get(j)[0].equals(cur)) i = j;
        prefs.edit().putString("source", sources.get((i + 1) % sources.size())[0]).apply();
        if (username().length() > 0 && collections().length() == 0 && !busy) loadCollections();
        changed();
    }

    private List<String[]> sources() {
        List<String[]> out = new ArrayList<>();
        JSONArray cols = collections();
        for (int i = 0; i < cols.length(); i++) {
            JSONObject c = cols.optJSONObject(i);
            if (c != null) out.add(new String[]{c.optString("id"), c.optString("title") + " (" + c.optInt("total_photos") + ")"});
        }
        if (cols.length() > 1) out.add(new String[]{"all", "All your collections"});
        out.add(new String[]{"landscape", "Unsplash landscapes"});
        return out;
    }

    private JSONArray collections() {
        try { return new JSONArray(prefs.getString("collections", "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    /** Summary for the Settings page. Never includes the key itself. */
    String infoJson() {
        try {
            String k = key(), src = prefs.getString("source", "landscape"), srcName = "Unsplash landscapes";
            for (String[] s : sources()) if (s[0].equals(src)) srcName = s[1];
            return new JSONObject()
                    .put("keySet", !k.isEmpty())
                    .put("keyHint", k.length() > 4 ? "…" + k.substring(k.length() - 4) : "")
                    .put("user", username())
                    .put("source", srcName)
                    .put("collections", collections().length())
                    .put("photos", readyPhotos().length())
                    .put("busy", busy)
                    .put("status", status)
                    .toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    /* ---------- Fetching ---------- */

    private void loadCollections() {
        if (key().isEmpty() || username().isEmpty()) return;
        busy = true; status = "Loading your collections…"; changed();
        worker.execute(() -> {
            try {
                JSONArray all = new JSONArray(get(API + "/users/" + URLEncoder.encode(username(), "UTF-8") + "/collections?per_page=30"));
                JSONArray slim = new JSONArray();
                for (int i = 0; i < all.length(); i++) {
                    JSONObject c = all.getJSONObject(i);
                    if (c.optInt("total_photos") <= 0) continue;
                    slim.put(new JSONObject().put("id", c.getString("id")).put("title", c.optString("title")).put("total_photos", c.optInt("total_photos")));
                }
                prefs.edit().putString("collections", slim.toString()).apply();
                if (slim.length() > 0 && "landscape".equals(prefs.getString("source", "landscape"))) {
                    prefs.edit().putString("source", slim.length() > 1 ? "all" : slim.getJSONObject(0).getString("id")).apply();
                }
                status = slim.length() == 0 ? "@" + username() + " has no collections with photos." : "Found " + slim.length() + " collections.";
            } catch (Exception e) {
                status = message(e);
            }
            busy = false; changed();
        });
    }

    /** Downloads a new batch. keepId is the wallpaper on screen now; it is never pruned. */
    void fetch(String keepId) {
        if (busy) return;
        if (key().isEmpty()) { status = "Add your Unsplash access key first."; changed(); return; }
        busy = true; status = "Asking Unsplash for photos…"; changed();
        worker.execute(() -> {
            int got = 0;
            try {
                String src = prefs.getString("source", "landscape");
                StringBuilder url = new StringBuilder(API + "/photos/random?count=" + BATCH + "&orientation=landscape&content_filter=high");
                if ("landscape".equals(src)) url.append("&query=landscape");
                else if ("all".equals(src)) {
                    JSONArray cols = collections();
                    StringBuilder ids = new StringBuilder();
                    for (int i = 0; i < cols.length(); i++) ids.append(i == 0 ? "" : ",").append(cols.getJSONObject(i).getString("id"));
                    url.append("&collections=").append(URLEncoder.encode(ids.toString(), "UTF-8"));
                } else url.append("&collections=").append(URLEncoder.encode(src, "UTF-8"));
                JSONArray photos = new JSONArray(get(url.toString()));
                JSONArray index = index();
                for (int i = 0; i < photos.length(); i++) {
                    JSONObject p = photos.getJSONObject(i);
                    String id = PREFIX + p.getString("id").replaceAll("[^A-Za-z0-9_-]", "");
                    if (find(index, id) != null) continue;
                    status = "Downloading photo " + (i + 1) + " of " + photos.length() + "…"; changed();
                    String raw = p.getJSONObject("urls").getString("raw");
                    byte[] jpg = download(raw + (raw.contains("?") ? "&" : "?") + "w=1920&h=1080&fit=crop&crop=entropy&fm=jpg&q=82");
                    try (FileOutputStream f = new FileOutputStream(new File(dir, id + ".jpg"))) { f.write(jpg); }
                    /* Unsplash asks apps to report each download. */
                    try { get(p.getJSONObject("links").getString("download_location")); } catch (Exception ignored) { }
                    JSONObject user = p.optJSONObject("user");
                    index.put(new JSONObject().put("id", id)
                            .put("author", user == null ? "Unsplash" : user.optString("name", "Unsplash"))
                            .put("profile", user == null ? "" : user.optJSONObject("links") == null ? "" : user.getJSONObject("links").optString("html"))
                            .put("ready", false)
                            .put("added", System.currentTimeMillis()));
                    got++;
                    saveIndex(index);
                }
                prune(keepId);
                status = got == 0 ? "No new photos this time." : "Preparing " + got + " photos for CleanFrame…";
            } catch (Exception e) {
                status = message(e);
            }
            busy = false; changed();
            if (got > 0) main.post(onNewWork);
        });
    }

    void clear(String keepId) {
        worker.execute(() -> {
            try {
                JSONArray index = index(), kept = new JSONArray();
                for (int i = 0; i < index.length(); i++) {
                    JSONObject p = index.getJSONObject(i);
                    if (p.getString("id").equals(keepId)) kept.put(p); else delete(p.getString("id"));
                }
                saveIndex(kept);
                status = kept.length() == 0 ? "Unsplash photos removed." : "Removed all but the photo on screen.";
            } catch (Exception e) { status = message(e); }
            changed();
        });
    }

    /* ---------- Materials worker hand-off ---------- */

    /** Photos downloaded but not yet rendered into CleanFrame materials. */
    synchronized String pendingJson() {
        JSONArray out = new JSONArray(), index = index();
        for (int i = 0; i < index.length(); i++) {
            JSONObject p = index.optJSONObject(i);
            if (p != null && !p.optBoolean("ready") && new File(dir, p.optString("id") + ".jpg").exists()) out.put(p.optString("id"));
        }
        return out.toString();
    }

    synchronized void saveMaterial(String id, String suffix, byte[] jpg) throws Exception {
        if (!id.matches("u-[A-Za-z0-9_-]{1,64}") || !java.util.Arrays.asList(SUFFIXES).contains(suffix) || suffix.isEmpty()) return;
        try (FileOutputStream f = new FileOutputStream(new File(dir, id + suffix + ".jpg"))) { f.write(jpg); }
    }

    synchronized void finish(String id, JSONObject look) throws Exception {
        JSONArray index = index();
        JSONObject p = find(index, id);
        if (p == null) return;
        p.put("ready", true).put("tone", look.optString("tone", "dark")).put("tr", look.optString("tr", "light")).put("br", look.optString("br", "light"));
        saveIndex(index);
        boolean more = new JSONArray(pendingJson()).length() > 0;
        if (!more) status = "New photos are ready. Press Home to see them in Wallpaper.";
        changed();
    }

    /** A photo the worker could not render is dropped rather than retried forever. */
    synchronized void fail(String id) {
        try {
            JSONArray index = index(), kept = new JSONArray();
            for (int i = 0; i < index.length(); i++) if (!id.equals(index.getJSONObject(i).optString("id"))) kept.put(index.getJSONObject(i));
            delete(id);
            saveIndex(kept);
        } catch (Exception ignored) { }
    }

    /** Ready photos as CleanFrame wallpaper catalogue entries. */
    synchronized JSONArray readyPhotos() {
        JSONArray out = new JSONArray(), index = index();
        for (int i = 0; i < index.length(); i++) {
            JSONObject p = index.optJSONObject(i);
            if (p == null || !p.optBoolean("ready")) continue;
            try {
                out.put(new JSONObject().put("id", p.getString("id"))
                        .put("name", p.optString("author") + " · Unsplash")
                        .put("author", p.optString("author"))
                        .put("tone", p.optString("tone", "dark"))
                        .put("ut", new JSONObject().put("tr", p.optString("tr", "light")).put("br", p.optString("br", "light")))
                        .put("frostVeil", 0.12));
            } catch (Exception ignored) { }
        }
        return out;
    }

    /* ---------- Serving files to the WebViews ---------- */

    static WebResourceResponse serve(Context c, String path) {
        Matcher m = SERVED.matcher(path);
        if (!m.matches()) return null;
        File f = new File(new File(c.getFilesDir(), "unsplash"), m.group(1) + ".jpg");
        if (!f.exists()) return null;
        try {
            return new WebResourceResponse("image/jpeg", null, new FileInputStream(f));
        } catch (Exception e) {
            return null;
        }
    }

    /* ---------- Helpers ---------- */

    private void prune(String keepId) throws Exception {
        JSONArray index = index();
        while (index.length() > KEEP) {
            int oldest = -1;
            for (int i = 0; i < index.length(); i++) {
                if (index.getJSONObject(i).getString("id").equals(keepId)) continue;
                if (oldest < 0 || index.getJSONObject(i).optLong("added") < index.getJSONObject(oldest).optLong("added")) oldest = i;
            }
            if (oldest < 0) break;
            delete(index.getJSONObject(oldest).getString("id"));
            index.remove(oldest);
        }
        saveIndex(index);
    }

    private void delete(String id) {
        for (String s : SUFFIXES) {
            //noinspection ResultOfMethodCallIgnored
            new File(dir, id + s + ".jpg").delete();
        }
    }

    private synchronized JSONArray index() {
        File f = new File(dir, "index.json");
        if (!f.exists()) return new JSONArray();
        try { return new JSONArray(new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private synchronized void saveIndex(JSONArray index) throws Exception {
        File tmp = new File(dir, "index.json.tmp");
        try (FileOutputStream f = new FileOutputStream(tmp)) { f.write(index.toString().getBytes("UTF-8")); }
        //noinspection ResultOfMethodCallIgnored
        tmp.renameTo(new File(dir, "index.json"));
    }

    private static JSONObject find(JSONArray index, String id) {
        for (int i = 0; i < index.length(); i++) {
            JSONObject p = index.optJSONObject(i);
            if (p != null && id.equals(p.optString("id"))) return p;
        }
        return null;
    }

    private void changed() { main.post(onChange); }

    private String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(20000);
        c.setRequestProperty("Authorization", "Client-ID " + key());
        c.setRequestProperty("Accept-Version", "v1");
        int code = c.getResponseCode();
        if (code == 401) throw new IllegalStateException("Unsplash didn't accept the access key.");
        if (code == 403) throw new IllegalStateException("Unsplash's hourly limit is used up. Try again later.");
        if (code == 404) throw new IllegalStateException("Unsplash couldn't find that user or collection.");
        if (code >= 400) throw new IllegalStateException("Unsplash returned an error (" + code + ").");
        return new String(read(c.getInputStream()), "UTF-8");
    }

    private static byte[] download(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(30000);
        if (c.getResponseCode() >= 400) throw new IllegalStateException("A photo failed to download.");
        return read(c.getInputStream());
    }

    private static byte[] read(InputStream in) throws Exception {
        try (InputStream i = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16384];
            for (int n; (n = i.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static String message(Exception e) {
        if (e instanceof IllegalStateException) return e.getMessage();
        if (e instanceof java.net.UnknownHostException) return "The TV is offline. Check the network and try again.";
        return "Something went wrong talking to Unsplash.";
    }
}
