package io.github.saksen2k1.aura;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** OK on a pinned app picks it up, Up/Down moves it, OK or Back drops it. Remove/Add sit to the right of each row. */
public class AppOrderActivity extends Activity {

    private AuraPrefs prefs;
    private Map<String, AppRepo.Entry> apps;
    private final List<String> order = new ArrayList<>();
    private final Map<String, View> keys = new HashMap<>();
    private String moving, focusKey, firstKey;
    private LinearLayout list;
    private TextView help;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new AuraPrefs(this);
        AuraTheme.immersive(this);
        apps = AppRepo.load(this);
        order.addAll(AppRepo.effectiveDock(prefs, new ArrayList<>(apps.keySet())));

        FrameLayout root = AuraTheme.backdrop(this, prefs);
        LinearLayout cols = new LinearLayout(this);
        cols.setPadding(dp(64), dp(48), dp(56), 0);
        root.addView(cols, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView t = AuraTheme.text(this, 34, "sans-serif-thin", AuraTheme.TEXT);
        t.setText(R.string.app_order);
        TextView s = AuraTheme.text(this, 13, "sans-serif-medium", AuraTheme.TEXT_SEC);
        s.setText(R.string.app_order_sub);
        help = AuraTheme.text(this, 14, "sans-serif", AuraTheme.TEXT_SEC);
        help.setLineSpacing(dp(4), 1f);
        left.addView(t);
        left.addView(s, AuraTheme.llp(-2, -2, dp(2), dp(6), 0, 0));
        left.addView(help, AuraTheme.llp(-1, -2, dp(2), dp(56), dp(28), 0));
        cols.addView(left, new LinearLayout.LayoutParams(dp(300), -1));

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        sv.setPadding(0, 0, 0, dp(60));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(10), dp(8), dp(10), 0);
        sv.addView(list, new FrameLayout.LayoutParams(-1, -2));
        cols.addView(sv, new LinearLayout.LayoutParams(0, -1, 1f));

        setContentView(root);
        build();
    }

    private void build() {
        list.removeAllViews();
        keys.clear();
        firstKey = null;
        section(getString(R.string.in_row, order.size()));
        for (int i = 0; i < order.size(); i++) row(apps.get(order.get(i)), i);
        section(getString(R.string.add_apps));
        for (AppRepo.Entry e : apps.values()) if (!order.contains(e.pkg)) row(e, -1);
        View f = focusKey == null ? null : keys.get(focusKey);
        if (f == null && firstKey != null) f = keys.get(firstKey);
        if (f != null) f.requestFocus();
        updateHelp();
    }

    private void section(String s) {
        TextView t = AuraTheme.text(this, 12, "sans-serif-medium", AuraTheme.TEXT_DIM);
        t.setText(s.toUpperCase(java.util.Locale.getDefault()));
        t.setLetterSpacing(0.16f);
        list.addView(t, AuraTheme.llp(-2, -2, dp(6), list.getChildCount() == 0 ? 0 : dp(18), 0, dp(10)));
    }

    private void row(AppRepo.Entry e, int index) {
        boolean pinned = index >= 0;
        boolean isMoving = e.pkg.equals(moving);
        int acc = prefs.accent();

        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout main = new LinearLayout(this);
        main.setGravity(Gravity.CENTER_VERTICAL);
        main.setPadding(dp(16), 0, dp(18), 0);
        main.setFocusable(true);
        main.setClickable(true);
        main.setId(View.generateViewId());
        GradientDrawable bg = AuraTheme.rounded(AuraTheme.GLASS, dp(14));
        main.setBackground(bg);
        paint(bg, false, isMoving, acc);

        TextView num = AuraTheme.text(this, 14, "sans-serif-medium", AuraTheme.TEXT_DIM);
        num.setText(pinned ? String.valueOf(index + 1) : "+");
        main.addView(num, new LinearLayout.LayoutParams(dp(28), -2));
        main.addView(Cards.app(this, e, 0, dp(64), dp(36), acc), new LinearLayout.LayoutParams(dp(64), dp(36)));
        TextView name = AuraTheme.text(this, 15, "sans-serif-medium", AuraTheme.TEXT);
        name.setText(e.label);
        name.setSingleLine(true);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, -2, 1f);
        np.leftMargin = dp(16);
        main.addView(name, np);
        TextView hint = AuraTheme.text(this, 13, "sans-serif-medium", acc);
        hint.setText(isMoving ? getString(R.string.moving) : "");
        main.addView(hint);

        main.setOnFocusChangeListener((v, f) -> {
            paint(bg, f, isMoving, acc);
            if (f) { focusKey = "m:" + e.pkg; updateHelp(); }
        });
        main.setOnClickListener(v -> {
            if (pinned) moving = e.pkg.equals(moving) ? null : e.pkg;
            else { order.add(e.pkg); save(); }
            focusKey = "m:" + e.pkg;
            build();
        });

        TextView act = AuraTheme.text(this, 14, "sans-serif-medium", AuraTheme.TEXT);
        act.setText(pinned ? R.string.remove : R.string.add);
        act.setGravity(Gravity.CENTER);
        act.setPadding(dp(22), 0, dp(22), 0);
        act.setFocusable(true);
        act.setClickable(true);
        act.setId(View.generateViewId());
        GradientDrawable abg = AuraTheme.rounded(AuraTheme.GLASS, dp(14));
        abg.setStroke(dp(1), AuraTheme.GLASS_EDGE);
        act.setBackground(abg);
        act.setOnFocusChangeListener((v, f) -> {
            abg.setColor(f ? acc : AuraTheme.GLASS);
            act.setTextColor(f ? AuraTheme.BG : AuraTheme.TEXT);
            if (f) { focusKey = "x:" + e.pkg; updateHelp(); }
        });
        act.setOnClickListener(v -> {
            if (pinned) {
                order.remove(e.pkg);
                if (e.pkg.equals(moving)) moving = null;
                focusKey = order.isEmpty() ? "m:" + e.pkg : "m:" + order.get(Math.min(index, order.size() - 1));
            } else {
                order.add(e.pkg);
                focusKey = "m:" + e.pkg;
            }
            save();
            build();
        });

        r.addView(main, new LinearLayout.LayoutParams(0, dp(52), 1f));
        r.addView(act, AuraTheme.llp(-2, dp(52), dp(12), 0, 0, 0));
        list.addView(r, AuraTheme.llp(-1, -2, 0, 0, 0, dp(10)));
        main.setNextFocusLeftId(main.getId());
        act.setNextFocusRightId(act.getId());
        keys.put("m:" + e.pkg, main);
        keys.put("x:" + e.pkg, act);
        if (firstKey == null) firstKey = "m:" + e.pkg;
    }

    private void paint(GradientDrawable bg, boolean focused, boolean isMoving, int acc) {
        if (isMoving) {
            bg.setColor(AuraTheme.alpha(acc, 0.30f));
            bg.setStroke(dp(2), acc);
        } else if (focused) {
            bg.setColor(0xE61B2433);
            bg.setStroke(dp(2), acc);
        } else {
            bg.setColor(AuraTheme.GLASS);
            bg.setStroke(dp(1), AuraTheme.GLASS_EDGE);
        }
    }

    private void updateHelp() {
        if (moving != null && apps.get(moving) != null) {
            help.setText(getString(R.string.help_moving, apps.get(moving).label));
        } else {
            help.setText(R.string.help_order);
        }
    }

    private void save() {
        prefs.dock(new ArrayList<>(order));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        if (moving != null && ev.getAction() == KeyEvent.ACTION_DOWN) {
            int k = ev.getKeyCode();
            if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN) {
                int i = order.indexOf(moving);
                int j = i + (k == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
                if (i >= 0 && j >= 0 && j < order.size()) {
                    Collections.swap(order, i, j);
                    save();
                }
                focusKey = "m:" + moving;
                build();
                return true;
            }
            if (k == KeyEvent.KEYCODE_BACK) {
                focusKey = "m:" + moving;
                moving = null;
                build();
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) return true;
        }
        return super.dispatchKeyEvent(ev);
    }

    @Override
    public void finish() {
        super.finish();
        AuraTheme.closeAnim(this);
    }

    private int dp(float v) { return AuraTheme.dp(this, v); }
}
