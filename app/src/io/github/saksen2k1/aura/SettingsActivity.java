package io.github.saksen2k1.aura;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Left/Right (or OK) changes a value in place; rows with › open a page. Help text for the focused row sits on the left. */
public class SettingsActivity extends Activity {

    interface BoolSet { void set(boolean v); }

    private AuraPrefs prefs;
    private LinearLayout list;
    private TextView helpTitle, helpText;
    private final List<Runnable> refreshers = new ArrayList<>();
    private View firstRow;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new AuraPrefs(this);
        AuraTheme.immersive(this);
        FrameLayout root = AuraTheme.backdrop(this, prefs);

        LinearLayout cols = new LinearLayout(this);
        cols.setPadding(dp(64), dp(48), dp(56), 0);
        root.addView(cols, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView t = AuraTheme.text(this, 34, "sans-serif-thin", AuraTheme.TEXT);
        t.setText(R.string.settings);
        TextView s = AuraTheme.text(this, 13, "sans-serif-medium", AuraTheme.TEXT_SEC);
        s.setText(R.string.settings_sub);
        s.setLetterSpacing(0.06f);
        helpTitle = AuraTheme.text(this, 18, "sans-serif-medium", AuraTheme.TEXT);
        helpText = AuraTheme.text(this, 14, "sans-serif", AuraTheme.TEXT_SEC);
        helpText.setLineSpacing(dp(4), 1f);
        left.addView(t);
        left.addView(s, AuraTheme.llp(-2, -2, dp(2), dp(6), 0, 0));
        left.addView(helpTitle, AuraTheme.llp(-1, -2, dp(2), dp(64), 0, 0));
        left.addView(helpText, AuraTheme.llp(-1, -2, dp(2), dp(10), dp(28), 0));
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

        section(R.string.sec_appearance);
        nav(R.string.wallpaper, this::wallpaperName, R.string.set_wallpaper_help,
                () -> AuraTheme.open(this, WallpaperActivity.class));
        cycle(R.string.set_interval, R.array.interval_names, prefs::intervalIndex, prefs::intervalIndex,
                R.string.set_interval_help);
        cycle(R.string.set_dim, R.array.dim_names, prefs::dim, prefs::dim, R.string.set_dim_help);
        toggle(R.string.set_camera, prefs::kenBurns, prefs::kenBurns, R.string.set_camera_help);
        cycle(R.string.set_accent, R.array.accent_names, prefs::accentIndex, prefs::accentIndex,
                R.string.set_accent_help);

        section(R.string.sec_home);
        nav(R.string.app_order, () -> getString(R.string.n_apps, AppRepo.effectiveDock(prefs, AppRepo.launchable(this)).size()),
                R.string.set_order_help,
                () -> AuraTheme.open(this, AppOrderActivity.class));
        cycle(R.string.set_style, R.array.style_names, prefs::cardStyle, prefs::cardStyle, R.string.set_style_help);
        cycle(R.string.set_size, R.array.size_names, prefs::cardSize, prefs::cardSize, R.string.set_size_help);
        cycle(R.string.set_labels, R.array.label_names, () -> prefs.alwaysLabels() ? 1 : 0,
                i -> prefs.alwaysLabels(i == 1), R.string.set_labels_help);
        toggle(R.string.set_recent, prefs::showRecent, prefs::showRecent, R.string.set_recent_help);
        toggle(R.string.set_greeting, prefs::greeting, prefs::greeting, R.string.set_greeting_help);

        section(R.string.sec_system);
        nav(R.string.set_usage, () -> getString(MainActivity.usageAccess(this) ? R.string.granted : R.string.not_granted),
                R.string.set_usage_help, () -> openSystem(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        nav(R.string.set_home, () -> "", R.string.set_home_help, () -> openSystem(Settings.ACTION_HOME_SETTINGS));
        nav(R.string.all_apps, () -> "", R.string.set_all_apps_help,
                () -> AuraTheme.open(this, AllAppsActivity.class));
        nav(R.string.system_settings, () -> "", R.string.set_system_help, () -> openSystem(Settings.ACTION_SETTINGS));
        nav(R.string.set_about, () -> getString(R.string.version, versionName()), R.string.set_about_help, null);

        setContentView(root);
        if (firstRow != null) firstRow.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        for (Runnable r : refreshers) r.run();
    }

    @Override
    public void finish() {
        super.finish();
        AuraTheme.closeAnim(this);
    }

    private String wallpaperName() {
        String sel = prefs.wallpaper();
        return "auto".equals(sel) ? getString(R.string.auto_change) : Wallpapers.label(this, sel);
    }

    private String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (android.content.pm.PackageManager.NameNotFoundException e) { return "?"; }
    }

    /** System screens differ between TV makers; fall back to the main settings when one is missing. */
    private void openSystem(String action) {
        Intent i = new Intent(action);
        if (Settings.ACTION_USAGE_ACCESS_SETTINGS.equals(action))
            i.setData(android.net.Uri.parse("package:" + getPackageName()));
        for (Intent attempt : new Intent[]{i, new Intent(action), new Intent(Settings.ACTION_SETTINGS)}) {
            try { startActivity(attempt); return; }
            catch (RuntimeException ignored) { }
        }
    }

    private void section(int res) {
        TextView t = AuraTheme.text(this, 12, "sans-serif-medium", AuraTheme.TEXT_DIM);
        t.setText(getString(res).toUpperCase(java.util.Locale.getDefault()));
        t.setLetterSpacing(0.16f);
        list.addView(t, AuraTheme.llp(-2, -2, dp(6), list.getChildCount() == 0 ? 0 : dp(18), 0, dp(10)));
    }

    private void cycle(int title, int namesRes, IntSupplier get, IntConsumer set, int help) {
        String[] names = getResources().getStringArray(namesRes);
        row(getString(title), () -> names[Math.floorMod(get.getAsInt(), names.length)], getString(help), null,
                d -> set.accept(Math.floorMod(get.getAsInt() + d, names.length)));
    }

    private void toggle(int title, BooleanSupplier get, BoolSet set, int help) {
        row(getString(title), () -> getString(get.getAsBoolean() ? R.string.on : R.string.off), getString(help), null,
                d -> set.set(!get.getAsBoolean()));
    }

    private void nav(int title, Supplier<String> value, int help, Runnable click) {
        row(getString(title), value, getString(help), click, null);
    }

    private void row(String title, Supplier<String> value, String help, Runnable click, IntConsumer step) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(22), 0, dp(22), 0);
        r.setFocusable(true);
        r.setClickable(true);
        r.setId(View.generateViewId());
        GradientDrawable bg = AuraTheme.rounded(AuraTheme.GLASS, dp(14));
        bg.setStroke(dp(1), AuraTheme.GLASS_EDGE);
        r.setBackground(bg);
        TextView t = AuraTheme.text(this, 16, "sans-serif-medium", AuraTheme.TEXT);
        t.setText(title);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView v = AuraTheme.text(this, 14, "sans-serif", AuraTheme.TEXT_SEC);
        r.addView(v, new LinearLayout.LayoutParams(-2, -2));

        Runnable refresh = () -> {
            String s = value.get();
            v.setText(step != null ? "‹   " + s + "   ›" : click != null ? (s.isEmpty() ? "›" : s + "   ›") : s);
        };
        refresh.run();
        refreshers.add(refresh);

        boolean[] has = {false};
        Runnable paint = () -> {
            int acc = prefs.accent();
            bg.setColor(has[0] ? 0xE61B2433 : AuraTheme.GLASS);
            bg.setStroke(dp(has[0] ? 2 : 1), has[0] ? acc : AuraTheme.GLASS_EDGE);
            v.setTextColor(has[0] ? acc : AuraTheme.TEXT_SEC);
        };
        r.setOnFocusChangeListener((view, f) -> {
            has[0] = f;
            paint.run();
            view.animate().scaleX(f ? 1.015f : 1f).scaleY(f ? 1.015f : 1f)
                    .setDuration(f ? AuraTheme.FOCUS_IN : AuraTheme.FOCUS_OUT)
                    .setInterpolator(new DecelerateInterpolator()).start();
            if (f) {
                helpTitle.setText(title);
                helpText.setText(help);
                helpText.setAlpha(0f);
                helpText.animate().alpha(1f).setDuration(220).start();
            }
        });
        r.setOnClickListener(view -> {
            if (step != null) { step.accept(1); refresh.run(); paint.run(); }
            else if (click != null) click.run();
        });
        if (step != null) r.setOnKeyListener((view, code, ev) -> {
            if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                step.accept(code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                refresh.run();
                paint.run();
                return true;
            }
            return false;
        });
        list.addView(r, AuraTheme.llp(-1, dp(56), 0, 0, 0, dp(10)));
        if (firstRow == null) firstRow = r;
    }

    private int dp(float v) { return AuraTheme.dp(this, v); }
}
