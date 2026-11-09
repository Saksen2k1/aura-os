package io.github.saksen2k1.aura;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Card views shared by the home screen, All apps and the settings pages. The focus ring is always the last child. */
final class Cards {
    private Cards() {}

    private static final DecelerateInterpolator EASE = new DecelerateInterpolator(1.6f);

    static FrameLayout app(Context c, AppRepo.Entry e, int style, int w, int h, int accent) {
        float r = AuraTheme.dp(c, 12);
        FrameLayout card = new FrameLayout(c);
        AuraTheme.roundOutline(card, r);
        ImageView iv = new ImageView(c);
        if (style == 0) {
            GradientDrawable bg = AuraTheme.rounded(AuraTheme.CARD_BG, r);
            bg.setStroke(AuraTheme.dp(c, 1), AuraTheme.CARD_EDGE);
            card.setBackground(bg);
            addLogo(c, card, e, w, h);
        } else if (style == 1) {
            GradientDrawable bg = AuraTheme.rounded(AuraTheme.GLASS, r);
            bg.setStroke(AuraTheme.dp(c, 1), 0x33FFFFFF);
            card.setBackground(bg);
            addLogo(c, card, e, w, h);
        } else if (e.banner != null) {
            card.setBackground(AuraTheme.rounded(AuraTheme.SURFACE, r));
            iv.setImageDrawable(fresh(e.banner));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            card.setBackground(AuraTheme.rounded(AuraTheme.darken(e.color, 0.38f), r));
            iv.setImageDrawable(fresh(e.icon));
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int s = (int) (h * 0.64f);
            card.addView(iv, new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
        }
        shadow(card, e.color);
        card.addView(ring(c, r, accent), new FrameLayout.LayoutParams(-1, -1));
        return card;
    }

    /**
     * Centres the app's colourful logo on a dark card. Wide wordmarks are limited by width, square glyphs by
     * height, so every logo carries a similar visual weight. Icon plates get rounded corners of their own.
     */
    static void addLogo(Context c, FrameLayout box, AppRepo.Entry e, int w, int h) {
        ImageView iv = new ImageView(c);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams p;
        if (e.logo == null) {
            iv.setImageDrawable(fresh(e.icon));
            int s = (int) (h * 0.56f);
            p = new FrameLayout.LayoutParams(s, s, Gravity.CENTER);
        } else if (e.plate) {
            iv.setImageBitmap(e.logo);
            float aspect = e.logo.getWidth() / (float) Math.max(1, e.logo.getHeight());
            int ph = (int) (h * 0.6f), pw = (int) Math.min(w * 0.7f, ph * aspect);
            ph = (int) (pw / aspect);
            AuraTheme.roundOutline(iv, AuraTheme.dp(c, 8));
            p = new FrameLayout.LayoutParams(pw, ph, Gravity.CENTER);
        } else {
            iv.setImageBitmap(e.logo);
            p = new FrameLayout.LayoutParams((int) (w * 0.66f), (int) (h * 0.5f), Gravity.CENTER);
        }
        box.addView(iv, p);
    }

    static FrameLayout action(Context c, int iconRes, String label, int w, int h, int accent) {
        float r = AuraTheme.dp(c, 12);
        FrameLayout card = new FrameLayout(c);
        AuraTheme.roundOutline(card, r);
        GradientDrawable bg = AuraTheme.rounded(AuraTheme.CARD_BG, r);
        bg.setStroke(AuraTheme.dp(c, 1), AuraTheme.CARD_EDGE);
        card.setBackground(bg);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        ImageView iv = new ImageView(c);
        iv.setImageResource(iconRes);
        iv.setColorFilter(AuraTheme.TEXT);
        int s = (int) (h * 0.32f);
        col.addView(iv, new LinearLayout.LayoutParams(s, s));
        TextView t = AuraTheme.text(c, 12, "sans-serif-medium", AuraTheme.TEXT);
        t.setText(label);
        col.addView(t, AuraTheme.llp(-2, -2, 0, AuraTheme.dp(c, 6), 0, 0));
        card.addView(col, new FrameLayout.LayoutParams(-1, -1));
        shadow(card, accent);
        card.addView(ring(c, r, accent), new FrameLayout.LayoutParams(-1, -1));
        return card;
    }

    /** A "recently used" card: the app's art on top, a caption strip in the app's own dark tone below. */
    static FrameLayout recent(Context c, MainActivity.RecentItem it, int w, int h, int accent) {
        float r = AuraTheme.dp(c, 12);
        int col = it.app.color;
        int capH = AuraTheme.dp(c, 42);
        int imgH = h - capH;
        FrameLayout card = new FrameLayout(c);
        AuraTheme.roundOutline(card, r);
        GradientDrawable bg = AuraTheme.rounded(AuraTheme.CARD_BG, r);
        bg.setStroke(AuraTheme.dp(c, 1), AuraTheme.CARD_EDGE);
        card.setBackground(bg);

        FrameLayout art = new FrameLayout(c);
        addLogo(c, art, it.app, w, imgH + AuraTheme.dp(c, 8));
        card.addView(art, AuraTheme.flp(-1, imgH, Gravity.TOP, 0, AuraTheme.dp(c, 4), 0, 0));
        View rule = new View(c);
        rule.setBackgroundColor(AuraTheme.CARD_EDGE);
        card.addView(rule, AuraTheme.flp(-1, 1, Gravity.BOTTOM, AuraTheme.dp(c, 12), 0, AuraTheme.dp(c, 12), capH));

        LinearLayout txt = new LinearLayout(c);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setGravity(Gravity.CENTER_VERTICAL);
        int pad = AuraTheme.dp(c, 12);
        txt.setPadding(pad, 0, pad, 0);
        TextView t = AuraTheme.text(c, 13, "sans-serif-medium", AuraTheme.TEXT);
        t.setText(it.title);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        TextView s = AuraTheme.text(c, 11, "sans-serif", 0xD9E2E8F0);
        s.setText(it.subtitle);
        s.setSingleLine(true);
        s.setEllipsize(TextUtils.TruncateAt.END);
        txt.addView(t);
        txt.addView(s, AuraTheme.llp(-1, -2, 0, AuraTheme.dp(c, 1), 0, 0));
        card.addView(txt, new FrameLayout.LayoutParams(-1, capH, Gravity.BOTTOM));

        shadow(card, col);
        card.addView(ring(c, r, accent), new FrameLayout.LayoutParams(-1, -1));
        return card;
    }

    /** Tinted elevation shadow; Android 8 draws the default grey one instead. */
    private static void shadow(View v, int color) {
        if (android.os.Build.VERSION.SDK_INT < 28) return;
        v.setOutlineSpotShadowColor(color);
        v.setOutlineAmbientShadowColor(color);
    }

    static View ring(Context c, float r, int accent) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0x26FFFFFF, 0x00FFFFFF, 0x00FFFFFF});
        g.setCornerRadius(r);
        g.setStroke(AuraTheme.dp(c, 2.5f), accent);
        View v = new View(c);
        v.setBackground(g);
        v.setAlpha(0f);
        return v;
    }

    static void focus(View card, boolean has, float scale) {
        ViewGroup g = (ViewGroup) card;
        View ring = g.getChildAt(g.getChildCount() - 1);
        card.animate().setStartDelay(0)
                .scaleX(has ? scale : 1f).scaleY(has ? scale : 1f)
                .translationZ(has ? AuraTheme.dp(card.getContext(), 12) : 0)
                .setDuration(has ? AuraTheme.FOCUS_IN : AuraTheme.FOCUS_OUT)
                .setInterpolator(EASE).start();
        ring.animate().setStartDelay(0).alpha(has ? 1f : 0f).setDuration(has ? 160 : 120).start();
    }

    /** The same banner can sit in the app row and the recent row at once; give each view its own drawable. */
    private static Drawable fresh(Drawable d) {
        if (d == null) return null;
        Drawable.ConstantState cs = d.getConstantState();
        return cs != null ? cs.newDrawable() : d;
    }
}
