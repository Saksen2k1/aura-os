package io.github.saksen2k1.aura;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Every user-facing setting bumps "stamp" so the home screen knows to rebuild when it comes back. */
final class AuraPrefs {
    static final long[] INTERVALS = {0, 5 * 60000L, 15 * 60000L, 30 * 60000L, 60 * 60000L};
    // Option counts; the visible names are the matching string-arrays in res/values/strings.xml.
    static final int DIM_COUNT = 3, STYLE_COUNT = 3, SIZE_COUNT = 2;

    private final Context ctx;
    private final SharedPreferences sp;

    AuraPrefs(Context c) {
        ctx = c.getApplicationContext();
        sp = ctx.getSharedPreferences("aura", Context.MODE_PRIVATE);
    }

    int stamp() { return sp.getInt("stamp", 0); }

    private void setInt(String k, int v) { sp.edit().putInt(k, v).putInt("stamp", stamp() + 1).apply(); }
    private void setBool(String k, boolean v) { sp.edit().putBoolean(k, v).putInt("stamp", stamp() + 1).apply(); }
    private void setString(String k, String v) { sp.edit().putString(k, v).putInt("stamp", stamp() + 1).apply(); }
    private int index(String k, int def, int n) { int v = sp.getInt(k, def); return v >= 0 && v < n ? v : def; }

    int accentIndex() { return index("accent_i", 0, AuraTheme.ACCENTS.length); }
    void accentIndex(int i) { setInt("accent_i", i); }
    int accent() { return AuraTheme.ACCENTS[accentIndex()]; }

    int intervalIndex() { return index("wall_iv", 2, INTERVALS.length); }
    void intervalIndex(int i) { setInt("wall_iv", i); }
    long wallInterval() { return INTERVALS[intervalIndex()]; }

    String wallpaper() { return sp.getString("wall_sel", "auto"); }
    void wallpaper(String id) { setString("wall_sel", id); }
    int wallIndex() { return sp.getInt("wall_i", 0); }
    void wallIndex(int i) { sp.edit().putInt("wall_i", i).apply(); }
    String shownWallpaper() { return sp.getString("wall_shown", null); }
    void shownWallpaper(String id) { sp.edit().putString("wall_shown", id).apply(); }

    int dim() { return index("dim", 1, DIM_COUNT); }
    void dim(int i) { setInt("dim", i); }
    boolean kenBurns() { return sp.getBoolean("kb", false); }
    void kenBurns(boolean b) { setBool("kb", b); }

    int cardStyle() { return index("card_style", 0, STYLE_COUNT); }
    void cardStyle(int i) { setInt("card_style", i); }
    int cardSize() { return index("card_size", 0, SIZE_COUNT); }
    void cardSize(int i) { setInt("card_size", i); }
    boolean alwaysLabels() { return sp.getBoolean("labels", false); }
    void alwaysLabels(boolean b) { setBool("labels", b); }
    boolean showRecent() { return sp.getBoolean("recent_row", true); }
    void showRecent(boolean b) { setBool("recent_row", b); }
    boolean greeting() { return sp.getBoolean("greeting", true); }
    void greeting(boolean b) { setBool("greeting", b); }

    List<String> dock() {
        String raw = sp.getString("dock", null);
        List<String> out = new ArrayList<>();
        if (raw == null) out.addAll(Arrays.asList(ctx.getResources().getStringArray(R.array.dock_apps)));
        else for (String s : raw.split(",")) if (!s.isEmpty()) out.add(s);
        return out;
    }

    void dock(List<String> pkgs) { setString("dock", TextUtils.join(",", pkgs)); }

    /** Launches made from AURA itself, newest first, as package -> epoch millis. */
    void launched(String pkg) {
        LinkedHashMap<String, Long> r = new LinkedHashMap<>();
        r.put(pkg, System.currentTimeMillis());
        for (Map.Entry<String, Long> e : recent().entrySet()) {
            if (r.size() >= 12) break;
            if (!e.getKey().equals(pkg)) r.put(e.getKey(), e.getValue());
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Long> e : r.entrySet()) {
            if (sb.length() > 0) sb.append(',');
            sb.append(e.getKey()).append('@').append(e.getValue());
        }
        sp.edit().putString("recent2", sb.toString()).apply();
    }

    LinkedHashMap<String, Long> recent() {
        LinkedHashMap<String, Long> out = new LinkedHashMap<>();
        String raw = sp.getString("recent2", "");
        for (String s : raw.split(",")) {
            int at = s.lastIndexOf('@');
            if (at <= 0) continue;
            try { out.put(s.substring(0, at), Long.parseLong(s.substring(at + 1))); }
            catch (NumberFormatException ignored) { }
        }
        return out;
    }
}
