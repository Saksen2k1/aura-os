package io.github.saksen2k1.aura;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Bundled photos ("a:01.jpg") plus the user's own in the app-specific files/wallpapers folder ("f:/path"). */
final class Wallpapers {
    private Wallpapers() {}

    static File customDir(Context c) {
        return c.getExternalFilesDir("wallpapers");
    }

    static List<String> list(Context c) {
        List<String> out = new ArrayList<>();
        try {
            String[] a = c.getAssets().list("wallpapers");
            if (a != null) {
                Arrays.sort(a);
                for (String s : a) out.add("a:" + s);
            }
        } catch (IOException ignored) { }
        File dir = customDir(c);
        File[] fs = dir == null ? null : dir.listFiles();
        if (fs != null) {
            Arrays.sort(fs);
            for (File f : fs) {
                String n = f.getName().toLowerCase(Locale.ROOT);
                if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp"))
                    out.add("f:" + f.getAbsolutePath());
            }
        }
        return out;
    }

    static String label(Context c, String id) {
        if (id.startsWith("f:")) return new File(id.substring(2)).getName();
        int n = assetIndex(c, id);
        return c.getString(R.string.picture_n, n + 1);
    }

    /** Photographer and licence for a bundled photo (CC BY-SA requires showing it); null for the user's own files. */
    static String credit(Context c, String id) {
        int n = assetIndex(c, id);
        String[] credits = c.getResources().getStringArray(R.array.wallpaper_credits);
        return id.startsWith("a:") && n >= 0 && n < credits.length ? credits[n] : null;
    }

    private static int assetIndex(Context c, String id) {
        int n = -1;
        for (String s : list(c)) {
            if (s.startsWith("a:")) n++;
            if (s.equals(id)) return n;
        }
        return Math.max(0, n);
    }

    private static InputStream open(Context c, String id) throws IOException {
        if (id.startsWith("a:")) return c.getAssets().open("wallpapers/" + id.substring(2));
        return new FileInputStream(id.substring(2));
    }

    /** Decode at the smallest power-of-two size that still covers reqW x reqH. */
    static Bitmap decode(Context c, String id, int reqW, int reqH, Bitmap.Config cfg) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = open(c, id)) { BitmapFactory.decodeStream(in, null, o); }
            if (o.outWidth <= 0) return null;
            int s = 1;
            while (o.outWidth / (s * 2) >= reqW && o.outHeight / (s * 2) >= reqH) s *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = s;
            d.inPreferredConfig = cfg;
            try (InputStream in = open(c, id)) { return BitmapFactory.decodeStream(in, null, d); }
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }
}
