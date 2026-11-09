package io.github.saksen2k1.aura;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class WallpaperActivity extends Activity {

    private static final int COLS = 4;
    private AuraPrefs prefs;
    private volatile boolean alive = true;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new AuraPrefs(this);
        AuraTheme.immersive(this);
        FrameLayout root = AuraTheme.backdrop(this, prefs);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.setPadding(dp(64), dp(44), dp(64), dp(60));
        sv.addView(col, new FrameLayout.LayoutParams(-1, -2));
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));

        TextView title = AuraTheme.text(this, 34, "sans-serif-thin", AuraTheme.TEXT);
        title.setText(R.string.wallpaper);
        col.addView(title);
        TextView sub = AuraTheme.text(this, 14, "sans-serif", AuraTheme.TEXT_SEC);
        sub.setText(R.string.wallpaper_sub);
        col.addView(sub, AuraTheme.llp(-2, -2, dp(2), dp(6), 0, dp(28)));

        List<String> ids = new ArrayList<>();
        ids.add("auto");
        ids.addAll(Wallpapers.list(this));
        String sel = prefs.wallpaper();
        int accent = prefs.accent();
        int gap = dp(22);
        int w = (getResources().getDisplayMetrics().widthPixels - dp(128) - gap * (COLS - 1)) / COLS;
        int h = w * 9 / 16;

        List<View> cards = new ArrayList<>();
        List<ImageView> thumbs = new ArrayList<>();
        View selected = null;
        LinearLayout row = null;
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            boolean auto = "auto".equals(id);
            if (i % COLS == 0) {
                row = new LinearLayout(this);
                row.setClipChildren(false);
                col.addView(row, AuraTheme.llp(-1, -2, 0, 0, 0, dp(22)));
            }
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setClipChildren(false);

            float r = dp(12);
            FrameLayout card = new FrameLayout(this);
            card.setId(View.generateViewId());
            card.setFocusable(true);
            card.setClickable(true);
            AuraTheme.roundOutline(card, r);
            ImageView iv = null;
            if (auto) {
                GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                        new int[]{AuraTheme.alpha(accent, 0.35f), 0xCC111827});
                bg.setCornerRadius(r);
                card.setBackground(bg);
                LinearLayout txt = new LinearLayout(this);
                txt.setOrientation(LinearLayout.VERTICAL);
                txt.setGravity(Gravity.CENTER);
                TextView a = AuraTheme.text(this, 16, "sans-serif-medium", AuraTheme.TEXT);
                a.setText(R.string.auto_change);
                TextView b2 = AuraTheme.text(this, 12, "sans-serif", AuraTheme.TEXT_SEC);
                b2.setText(prefs.wallInterval() == 0 ? getString(R.string.auto_off)
                        : getResources().getStringArray(R.array.interval_names)[prefs.intervalIndex()]);
                a.setGravity(Gravity.CENTER);
                b2.setGravity(Gravity.CENTER);
                txt.addView(a, new LinearLayout.LayoutParams(-1, -2));
                txt.addView(b2, AuraTheme.llp(-1, -2, 0, dp(4), 0, 0));
                card.addView(txt, new FrameLayout.LayoutParams(-1, -1));
            } else {
                card.setBackground(AuraTheme.rounded(AuraTheme.SURFACE, r));
                iv = new ImageView(this);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                card.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            }
            thumbs.add(iv);
            boolean isSel = id.equals(sel);
            if (isSel) {
                TextView badge = AuraTheme.text(this, 11, "sans-serif-medium", AuraTheme.BG);
                badge.setText(R.string.selected);
                badge.setPadding(dp(10), dp(3), dp(10), dp(3));
                badge.setBackground(AuraTheme.rounded(accent, dp(10)));
                card.addView(badge, AuraTheme.flp(-2, -2, Gravity.TOP | Gravity.END, 0, dp(8), dp(8), 0));
                selected = card;
            }
            card.addView(Cards.ring(this, r, accent), new FrameLayout.LayoutParams(-1, -1));
            tile.addView(card, new LinearLayout.LayoutParams(w, h));

            TextView label = AuraTheme.text(this, 12, "sans-serif-medium", AuraTheme.TEXT_SEC);
            label.setText(auto ? getString(R.string.auto_short) : Wallpapers.label(this, id));
            label.setSingleLine(true);
            tile.addView(label, AuraTheme.llp(w, -2, dp(2), dp(9), 0, 0));
            String credit = auto ? null : Wallpapers.credit(this, id);
            if (credit != null) {
                TextView cr = AuraTheme.text(this, 10, "sans-serif", AuraTheme.TEXT_DIM);
                cr.setText(credit);
                cr.setSingleLine(true);
                cr.setEllipsize(android.text.TextUtils.TruncateAt.END);
                tile.addView(cr, AuraTheme.llp(w, -2, dp(2), dp(2), 0, 0));
            }
            row.addView(tile, AuraTheme.llp(-2, -2, i % COLS == 0 ? 0 : gap, 0, 0, 0));

            card.setOnFocusChangeListener((v, has) -> {
                Cards.focus(v, has, 1.07f);
                label.setTextColor(has ? AuraTheme.TEXT : AuraTheme.TEXT_SEC);
            });
            card.setOnClickListener(v -> { prefs.wallpaper(id); finish(); });
            cards.add(card);
        }
        for (int i = 0; i < cards.size(); i++) {
            View c = cards.get(i);
            if (i % COLS == 0) c.setNextFocusLeftId(c.getId());
            if (i % COLS == COLS - 1 || i == cards.size() - 1) c.setNextFocusRightId(c.getId());
        }

        File dir = Wallpapers.customDir(this);
        TextView hint = AuraTheme.text(this, 13, "sans-serif", AuraTheme.TEXT_DIM);
        hint.setText(getString(R.string.own_pictures,
                dir != null ? dir.getAbsolutePath() : "Android/data/" + getPackageName() + "/files/wallpapers"));
        hint.setLineSpacing(dp(3), 1f);
        col.addView(hint, AuraTheme.llp(-2, -2, dp(2), dp(12), 0, 0));

        setContentView(root);
        (selected != null ? selected : cards.get(0)).requestFocus();

        new Thread(() -> {
            for (int i = 0; i < ids.size() && alive; i++) {
                ImageView iv = thumbs.get(i);
                if (iv == null) continue;
                Bitmap bm = Wallpapers.decode(this, ids.get(i), w, h, Bitmap.Config.RGB_565);
                if (bm != null) runOnUiThread(() -> {
                    iv.setImageBitmap(bm);
                    iv.setAlpha(0f);
                    iv.animate().alpha(1f).setDuration(250).start();
                });
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        alive = false;
        super.onDestroy();
    }

    @Override
    public void finish() {
        super.finish();
        AuraTheme.closeAnim(this);
    }

    private int dp(float v) { return AuraTheme.dp(this, v); }
}
