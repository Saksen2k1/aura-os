package io.github.saksen2k1.aura;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

final class AuraTheme {
    private AuraTheme() {}

    static final int BG = 0xFF090D16;
    static final int SURFACE = 0xFF111827;
    static final int ELEVATED = 0xFF1B2433;
    static final int TEXT = 0xFFF8FAFC;
    static final int TEXT_SEC = 0xFFAAB4C4;
    static final int TEXT_DIM = 0xFF6B7280;
    static final int GLASS = 0x99111827;
    static final int GLASS_EDGE = 0x1FFFFFFF;
    static final int CARD_BG = 0xFF09090F;
    static final int CARD_EDGE = 0x17FFFFFF;

    static final int[] ACCENTS = {0xFF67DCE5, 0xFFA78BFA, 0xFFF5B860, 0xFFF472B6, 0xFFF8FAFC};

    static final int FOCUS_IN = 200;
    static final int FOCUS_OUT = 160;


    static int alpha(int c, float a) {
        a = Math.max(0f, Math.min(1f, a));
        return Color.argb(Math.round(a * 255), Color.red(c), Color.green(c), Color.blue(c));
    }

    static int darken(int c, float f) {
        return Color.rgb((int) (Color.red(c) * f), (int) (Color.green(c) * f), (int) (Color.blue(c) * f));
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context c, float sp, String family, int color) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        t.setTextColor(color);
        return t;
    }

    static void shadow(TextView t) {
        t.setShadowLayer(dp(t.getContext(), 10), 0, dp(t.getContext(), 1), 0xB3000000);
    }

    static LinearLayout.LayoutParams llp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(l, t, r, b);
        return p;
    }

    static FrameLayout.LayoutParams flp(int w, int h, int gravity, int l, int t, int r, int b) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w, h, gravity);
        p.setMargins(l, t, r, b);
        return p;
    }

    static GradientDrawable rounded(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(r);
        g.setColor(color);
        return g;
    }

    static void roundOutline(View v, float r) {
        v.setClipToOutline(true);
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        });
    }

    static void immersive(Activity a) {
        a.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    static void open(Activity a, Class<?> cls) {
        a.startActivity(new Intent(a, cls));
        a.overridePendingTransition(R.anim.aura_in, R.anim.aura_out);
    }

    static void closeAnim(Activity a) {
        a.overridePendingTransition(R.anim.aura_back_in, R.anim.aura_back_out);
    }

    static String cap(String s) {
        return s == null || s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.getDefault()) + s.substring(1);
    }

    /** Full-screen root with the current wallpaper decoded tiny and stretched, which reads as a soft blur. */
    static FrameLayout backdrop(Activity a, AuraPrefs p) {
        FrameLayout root = new FrameLayout(a);
        root.setBackgroundColor(BG);
        ImageView iv = new ImageView(a);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        View dim = new View(a);
        dim.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xF5090D16, 0xE0090D16, 0xCC090D16}));
        root.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        String id = p.shownWallpaper();
        if (id != null) new Thread(() -> {
            Bitmap bm = Wallpapers.decode(a, id, 120, 68, Bitmap.Config.RGB_565);
            if (bm != null) a.runOnUiThread(() -> {
                iv.setImageBitmap(bm);
                iv.setAlpha(0f);
                iv.animate().alpha(1f).setDuration(400).start();
            });
        }).start();
        return root;
    }
}
