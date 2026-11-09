package io.github.saksen2k1.aura;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class AppRepo {
    private AppRepo() {}

    static final class Entry {
        String pkg, label;
        ComponentName component;
        Drawable banner, icon;
        int color;
        /** Colourful logo on transparency, trimmed to its content; or, when plate is true, the app's own icon tile. */
        Bitmap logo;
        boolean plate;

        Drawable art() { return banner != null ? banner : icon; }

        Intent intent() {
            return new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .setComponent(component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        }
    }

    private static final Map<String, Integer> colors = new HashMap<>();

    static Set<String> hidden(Context c) {
        Set<String> h = new HashSet<>(Arrays.asList(c.getResources().getStringArray(R.array.hidden_apps)));
        h.add(c.getPackageName());
        return h;
    }

    /** Package names of launchable TV apps, cheap (no icons), sorted by label. */
    static List<String> launchable(Context c) {
        PackageManager pm = c.getPackageManager();
        Set<String> hidden = hidden(c);
        List<ResolveInfo> ris = pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
        Collections.sort(ris, (x, y) -> String.valueOf(x.loadLabel(pm)).compareToIgnoreCase(String.valueOf(y.loadLabel(pm))));
        List<String> out = new ArrayList<>();
        for (ResolveInfo ri : ris) {
            String p = ri.activityInfo.packageName;
            if (!hidden.contains(p) && !out.contains(p)) out.add(p);
        }
        return out;
    }

    /** The app row as shown: the saved order filtered to installed apps, or the first eight apps on a fresh device. */
    static List<String> effectiveDock(AuraPrefs prefs, List<String> installedSorted) {
        List<String> dock = new ArrayList<>();
        for (String p : prefs.dock()) if (installedSorted.contains(p) && !dock.contains(p)) dock.add(p);
        if (dock.isEmpty()) for (String p : installedSorted) {
            if (dock.size() >= 8) break;
            dock.add(p);
        }
        return dock;
    }

    /** Launchable TV apps, sorted by name. */
    static LinkedHashMap<String, Entry> load(Context c) {
        PackageManager pm = c.getPackageManager();
        Set<String> hidden = hidden(c);
        Set<String> seen = new HashSet<>();
        List<Entry> list = new ArrayList<>();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
            ActivityInfo ai = ri.activityInfo;
            if (hidden.contains(ai.packageName) || !seen.add(ai.packageName)) continue;
            Entry e = new Entry();
            e.pkg = ai.packageName;
            e.label = String.valueOf(ri.loadLabel(pm));
            e.component = new ComponentName(ai.packageName, ai.name);
            Drawable b = ai.loadBanner(pm);
            if (b == null) b = ai.applicationInfo.loadBanner(pm);
            e.banner = b;
            e.icon = ri.loadIcon(pm);
            e.color = sampleColor(e.pkg, e.art());
            String key = e.pkg + "_" + Integer.toHexString(ai.applicationInfo.sourceDir.hashCode());
            Logo l;
            synchronized (logos) { l = logos.get(key); }
            if (l == null) l = readCached(c, key);
            if (l == null) {
                l = buildLogo(e.banner, e.icon);
                Log.i("AURA", "logo " + e.pkg + " <- " + l.source);
                writeCached(c, key, l);
            }
            synchronized (logos) { logos.put(key, l); }
            e.logo = l.bmp;
            e.plate = l.plate;
            list.add(e);
        }
        Collections.sort(list, (x, y) -> x.label.compareToIgnoreCase(y.label));
        LinkedHashMap<String, Entry> out = new LinkedHashMap<>();
        for (Entry e : list) out.put(e.pkg, e);
        return out;
    }

    private static final class Logo {
        Bitmap bmp;
        boolean plate;
        String source;
    }

    private static final Map<String, Logo> logos = new HashMap<>();

    /**
     * Picks a logo that can sit on a near-black card without recolouring it: a banner that is already transparent,
     * else the foreground layer of the adaptive icon, else a transparent legacy icon. Only when none exists does
     * the app's own icon tile get shown as a small rounded plate.
     */
    private static Logo buildLogo(Drawable banner, Drawable icon) {
        Logo out = new Logo();
        try {
            if (banner != null) {
                Bitmap b = render(banner, 320, 180);
                if (transparentEdges(b) && visibleOnDark(b)) return done(out, trim(b), false, "transparent banner");
            }
            if (icon instanceof AdaptiveIconDrawable) {
                Drawable fg = ((AdaptiveIconDrawable) icon).getForeground();
                if (fg != null) {
                    // The layer is 108 units; only the middle 72 are ever visible, so crop to that before testing.
                    Bitmap f = render(fg, 216, 216);
                    int in = 216 * 18 / 108;
                    Bitmap c = Bitmap.createBitmap(f, in, in, 216 - 2 * in, 216 - 2 * in);
                    if (transparentEdges(c) && visibleOnDark(c)) return done(out, trim(c), false, "adaptive icon foreground");
                }
            }
            Bitmap legacy = null;
            if (icon != null && !(icon instanceof AdaptiveIconDrawable)) {
                legacy = render(icon, 160, 160);
                if (transparentEdges(legacy) && visibleOnDark(legacy)) return done(out, trim(legacy), false, "transparent icon");
            }
            if (banner != null) {
                Bitmap k = keyNeutral(render(banner, 320, 180));
                if (k != null && legible(k)) return done(out, trim(k), false, "banner, white/black backdrop removed");
            }
            if (legacy != null) {
                Bitmap k = keyNeutral(legacy);
                if (k != null && legible(k)) return done(out, trim(k), false, "icon, white/black backdrop removed");
            }
            if (icon != null) return done(out, render(icon, 160, 160), true, "icon plate");
            if (banner != null) return done(out, render(banner, 320, 180), true, "banner plate");
        } catch (RuntimeException | OutOfMemoryError ex) {
            Log.w("AURA", "logo render failed: " + ex);
        }
        return done(out, null, true, "none");
    }

    private static Logo done(Logo l, Bitmap b, boolean plate, String source) {
        l.bmp = b;
        l.plate = plate;
        l.source = source;
        return l;
    }

    private static Bitmap render(Drawable d, int w, int h) {
        Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, w, h);
        d.draw(new Canvas(bm));
        return bm;
    }

    /** True when the outer band of the image is mostly see-through, i.e. there is no built-in backdrop. */
    private static boolean transparentEdges(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight(), band = Math.max(2, Math.min(w, h) / 20);
        int clear = 0, total = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if (x >= band && x < w - band && y >= band && y < h - band) continue;
            total++;
            if (Color.alpha(b.getPixel(x, y)) < 48) clear++;
        }
        return total > 0 && clear >= total * 0.8f;
    }

    /** Rejects logos that are mostly black or empty, which would disappear on a black card. */
    private static boolean visibleOnDark(Bitmap b) {
        int[] px = new int[b.getWidth() * b.getHeight()];
        b.getPixels(px, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
        float[] hsv = new float[3];
        int solid = 0, bright = 0;
        for (int p : px) {
            if (Color.alpha(p) < 128) continue;
            solid++;
            Color.colorToHSV(p, hsv);
            if (hsv[2] > 0.35f) bright++;
        }
        return solid > px.length / 50 && bright >= solid * 0.3f;
    }

    /** Stricter check for keyed logos: also rejects ones with much black text, which would vanish on the card. */
    private static boolean legible(Bitmap b) {
        if (!visibleOnDark(b)) return false;
        int[] px = new int[b.getWidth() * b.getHeight()];
        b.getPixels(px, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
        float[] hsv = new float[3];
        int solid = 0, dark = 0, clear = 0;
        for (int p : px) {
            if (Color.alpha(p) < 128) continue;
            solid++;
            Color.colorToHSV(p, hsv);
            if (hsv[2] < 0.25f && hsv[1] < 0.35f) dark++;
            double luma = (0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)) / 255.0;
            if (luma > 0.35 || (hsv[1] > 0.5f && hsv[2] > 0.65f)) clear++;
        }
        // Deep navy or maroon wordmarks pass the colour test but still sink into a #09090F card.
        return solid > 0 && dark <= solid * 0.12f && clear >= solid * 0.5f;
    }

    private static int diff(int a, int r, int g, int b) {
        return Math.max(Math.abs(Color.red(a) - r), Math.max(Math.abs(Color.green(a) - g), Math.abs(Color.blue(a) - b)));
    }

    /**
     * Removes a plain white or black backdrop, but only the part connected to the image border, so white or
     * black details inside the logo survive. Edge pixels are un-blended from the backdrop for a clean outline.
     * Coloured backdrops are left alone: they are part of the brand. Returns null when this doesn't apply.
     */
    private static Bitmap keyNeutral(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        long sr = 0, sg = 0, sb = 0;
        int n = 0;
        for (int i = 0; i < px.length; i++) {
            int x = i % w, y = i / w;
            if (x != 0 && y != 0 && x != w - 1 && y != h - 1) continue;
            if (Color.alpha(px[i]) < 200) continue;
            sr += Color.red(px[i]); sg += Color.green(px[i]); sb += Color.blue(px[i]); n++;
        }
        if (n < (w + h)) return null;
        int br = (int) (sr / n), bgc = (int) (sg / n), bb = (int) (sb / n);
        int spread = Math.max(br, Math.max(bgc, bb)) - Math.min(br, Math.min(bgc, bb));
        boolean white = br > 215 && bgc > 215 && bb > 215, black = br < 40 && bgc < 40 && bb < 40;
        if (spread > 24 || !(white || black)) return null;

        int uniform = 0;
        for (int i = 0; i < px.length; i++) {
            int x = i % w, y = i / w;
            if ((x == 0 || y == 0 || x == w - 1 || y == h - 1) && diff(px[i], br, bgc, bb) <= 40) uniform++;
        }
        if (uniform < n * 0.85f) return null;

        boolean[] bg = new boolean[px.length];
        int[] queue = new int[px.length];
        int head = 0, tail = 0;
        for (int i = 0; i < px.length; i++) {
            int x = i % w, y = i / w;
            if ((x == 0 || y == 0 || x == w - 1 || y == h - 1) && (Color.alpha(px[i]) < 48 || diff(px[i], br, bgc, bb) <= 48)) {
                bg[i] = true;
                queue[tail++] = i;
            }
        }
        while (head < tail) {
            int i = queue[head++], x = i % w, y = i / w;
            int[] nb = {x > 0 ? i - 1 : -1, x < w - 1 ? i + 1 : -1, y > 0 ? i - w : -1, y < h - 1 ? i + w : -1};
            for (int j : nb) {
                if (j < 0 || bg[j]) continue;
                if (Color.alpha(px[j]) < 48 || diff(px[j], br, bgc, bb) <= 48) { bg[j] = true; queue[tail++] = j; }
            }
        }
        if (tail > px.length * 0.97f) return null;

        int[] out = new int[px.length];
        for (int i = 0; i < px.length; i++) {
            if (bg[i]) continue;
            int p = px[i], x = i % w, y = i / w;
            boolean edge = (x > 0 && bg[i - 1]) || (x < w - 1 && bg[i + 1]) || (y > 0 && bg[i - w]) || (y < h - 1 && bg[i + w]);
            int d = diff(p, br, bgc, bb);
            if (edge && d < 150) {
                float a = Math.max(0.05f, d / 150f);
                int r = clamp(br + (Color.red(p) - br) / a), g = clamp(bgc + (Color.green(p) - bgc) / a),
                        b = clamp(bb + (Color.blue(p) - bb) / a);
                out[i] = Color.argb((int) (Color.alpha(p) * a), r, g, b);
            } else {
                out[i] = p;
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888);
    }

    private static int clamp(float v) { return Math.max(0, Math.min(255, Math.round(v))); }

    private static java.io.File cacheFile(Context c, String key, boolean plate) {
        java.io.File dir = new java.io.File(c.getCacheDir(), "logos");
        dir.mkdirs();
        return new java.io.File(dir, "v4_" + key + (plate ? "_p" : "_l") + ".png");
    }

    private static Logo readCached(Context c, String key) {
        for (boolean plate : new boolean[]{false, true}) {
            java.io.File f = cacheFile(c, key, plate);
            if (!f.exists()) continue;
            Bitmap b = android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath());
            if (b != null) return done(new Logo(), b, plate, "cache");
        }
        return null;
    }

    private static void writeCached(Context c, String key, Logo l) {
        if (l.bmp == null) return;
        try (java.io.FileOutputStream o = new java.io.FileOutputStream(cacheFile(c, key, l.plate))) {
            l.bmp.compress(Bitmap.CompressFormat.PNG, 100, o);
        } catch (java.io.IOException ignored) { }
    }

    private static Bitmap trim(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        int l = w, t = h, r = -1, bo = -1;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if (Color.alpha(px[y * w + x]) < 24) continue;
            if (x < l) l = x;
            if (x > r) r = x;
            if (y < t) t = y;
            if (y > bo) bo = y;
        }
        if (r < l || bo < t) return b;
        return Bitmap.createBitmap(b, l, t, r - l + 1, bo - t + 1);
    }

    /** Average the art's vivid pixels, then push saturation so the glow reads as a colour. */
    static int sampleColor(String pkg, Drawable d) {
        synchronized (colors) {
            Integer cached = colors.get(pkg);
            if (cached != null) return cached;
        }
        int color = 0xFF67DCE5;
        try {
            Bitmap bm = Bitmap.createBitmap(24, 14, Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, 24, 14);
            d.draw(new Canvas(bm));
            float[] hsv = new float[3];
            double r = 0, g = 0, b = 0, w = 0;
            for (int y = 0; y < 14; y++) for (int x = 0; x < 24; x++) {
                int p = bm.getPixel(x, y);
                if (Color.alpha(p) < 128) continue;
                Color.colorToHSV(p, hsv);
                double weight = 0.05 + hsv[1] * hsv[2];
                r += Color.red(p) * weight; g += Color.green(p) * weight; b += Color.blue(p) * weight; w += weight;
            }
            bm.recycle();
            if (w > 0) {
                Color.colorToHSV(Color.rgb((int) (r / w), (int) (g / w), (int) (b / w)), hsv);
                if (hsv[1] > 0.12f) { hsv[1] = Math.max(hsv[1], 0.7f); hsv[2] = 0.9f; }
                else { hsv[1] = 0f; hsv[2] = 0.75f; }
                color = Color.HSVToColor(hsv);
            }
        } catch (RuntimeException ignored) { }
        synchronized (colors) { colors.put(pkg, color); }
        return color;
    }
}
