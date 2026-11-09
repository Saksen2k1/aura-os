package io.github.saksen2k1.aura;

import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AllAppsActivity extends Activity {

    private static final int COLS = 6;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        AuraPrefs prefs = new AuraPrefs(this);
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

        Map<String, AppRepo.Entry> apps = AppRepo.load(this);
        TextView title = AuraTheme.text(this, 34, "sans-serif-thin", AuraTheme.TEXT);
        title.setText(R.string.all_apps);
        col.addView(title);
        TextView sub = AuraTheme.text(this, 14, "sans-serif", AuraTheme.TEXT_SEC);
        sub.setText(getString(R.string.apps_installed, apps.size()));
        col.addView(sub, AuraTheme.llp(-2, -2, dp(2), dp(6), 0, dp(28)));

        int gap = dp(20);
        int w = (getResources().getDisplayMetrics().widthPixels - dp(128) - gap * (COLS - 1)) / COLS;
        int h = w * 9 / 16;
        List<View> cards = new ArrayList<>();
        LinearLayout row = null;
        int i = 0;
        for (AppRepo.Entry e : apps.values()) {
            if (i % COLS == 0) {
                row = new LinearLayout(this);
                row.setClipChildren(false);
                col.addView(row, AuraTheme.llp(-1, -2, 0, 0, 0, dp(22)));
            }
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setClipChildren(false);
            FrameLayout card = Cards.app(this, e, prefs.cardStyle(), w, h, prefs.accent());
            card.setId(View.generateViewId());
            card.setFocusable(true);
            card.setClickable(true);
            tile.addView(card, new LinearLayout.LayoutParams(w, h));
            TextView l = AuraTheme.text(this, 12, "sans-serif-medium", AuraTheme.TEXT_SEC);
            l.setText(e.label);
            l.setSingleLine(true);
            l.setEllipsize(TextUtils.TruncateAt.END);
            tile.addView(l, AuraTheme.llp(w, -2, dp(2), dp(9), 0, 0));
            row.addView(tile, AuraTheme.llp(-2, -2, i % COLS == 0 ? 0 : gap, 0, 0, 0));
            card.setOnFocusChangeListener((v, has) -> {
                Cards.focus(v, has, 1.1f);
                l.setTextColor(has ? AuraTheme.TEXT : AuraTheme.TEXT_SEC);
            });
            card.setOnClickListener(v -> {
                prefs.launched(e.pkg);
                try { startActivity(e.intent()); } catch (RuntimeException ignored) { }
            });
            cards.add(card);
            i++;
        }
        for (int j = 0; j < cards.size(); j++) {
            View c = cards.get(j);
            if (j % COLS == 0) c.setNextFocusLeftId(c.getId());
            if (j % COLS == COLS - 1 || j == cards.size() - 1) c.setNextFocusRightId(c.getId());
        }
        setContentView(root);
        if (!cards.isEmpty()) cards.get(0).requestFocus();
    }

    @Override
    public void finish() {
        super.finish();
        AuraTheme.closeAnim(this);
    }

    private int dp(float v) { return AuraTheme.dp(this, v); }
}
