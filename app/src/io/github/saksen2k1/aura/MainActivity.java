package io.github.saksen2k1.aura;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AURA home screen: cinematic wallpaper with a slow camera move, clock top-left, small icon buttons top-right,
 * one horizontal app row and a "recently used" row. Plain framework views only.
 */
public class MainActivity extends Activity {

    static final class RecentItem {
        AppRepo.Entry app;
        long time;
        String title, subtitle;
    }

    private static final DecelerateInterpolator EASE = new DecelerateInterpolator(1.6f);
    private static final int ICON_BG = 0xA60B1020;
    private static final float PAN_COVER = 1.08f;

    private AuraPrefs prefs;
    private int builtStamp = -1;
    private String liveTvPkg, inputPickerPkg;
    private Map<String, AppRepo.Entry> apps = new LinkedHashMap<>();

    private FrameLayout root;
    private View header;
    private ImageView wallBack, wallFront;
    private boolean firstWall = true;
    private View glow;
    private GradientDrawable glowDrawable;
    private int glowColor = AuraTheme.ACCENTS[0];
    private boolean glowJump = true;
    private ValueAnimator glowAnim;
    private ObjectAnimator glowMove, nameMove;
    private LinearLayout iconBar, appRow, recentRow;
    private RowScroll appScroll, recentScroll;
    private TextView clock, dateLine, greetingTv, focusName, recentLabel, wifiLabel;
    private ImageView wifiIcon;
    private final List<View> icons = new ArrayList<>();
    private final List<View> appCards = new ArrayList<>();
    private final List<View> recentCards = new ArrayList<>();
    private final Map<String, View> byKey = new HashMap<>();
    private String focusKey;
    private View focused;

    private String shownWall;
    private long wallChangedAt;
    private ObjectAnimator kenBurns;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable rotate = () -> wallpaperTick(true);

    private PopupWindow inputsPopup;
    private int packageSeq;
    private boolean firstShow = true;

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateClock(); updateWifi(); }
    };
    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { buildRows(); }
    };

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new AuraPrefs(this);
        liveTvPkg = firstInstalled(getResources().getStringArray(R.array.live_tv_packages));
        inputPickerPkg = firstInstalled(getResources().getStringArray(R.array.input_picker_packages));
        AuraTheme.immersive(this);
        rebuild();
        // The root paints the dark colour until the first photo is in; a window background would be one more full-screen fill.
        getWindow().setBackgroundDrawable(null);
        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        registerReceiver(packages, pf);
    }

    private void rebuild() {
        if (kenBurns != null) kenBurns.cancel();
        kenBurns = null;
        setContentView(buildUi());
        builtStamp = prefs.stamp();
        shownWall = null;
        buildRows();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter tf = new IntentFilter(Intent.ACTION_TIME_TICK);
        tf.addAction(Intent.ACTION_TIME_CHANGED);
        tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        tf.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        tf.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(tick, tf);
        if (prefs.stamp() != builtStamp) rebuild();
        else if (getPackageManager().getChangedPackages(packageSeq) != null) buildRows();
        else refreshRecent();
        updateClock();
        updateWifi();
        restoreFocus();
        if (firstShow) { firstShow = false; playEntrance(); }
        wallpaperTick(false);
        if (kenBurns != null && kenBurns.isPaused()) kenBurns.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(rotate);
        unregisterReceiver(tick);
        if (inputsPopup != null) inputsPopup.dismiss();
        if (kenBurns != null) kenBurns.pause();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packages);
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        goHome();
    }

    @Override
    public void onBackPressed() {
        goHome();
    }

    private void goHome() {
        if (inputsPopup != null) inputsPopup.dismiss();
        appScroll.smoothScrollTo(0, 0);
        recentScroll.smoothScrollTo(0, 0);
        if (!appCards.isEmpty()) appCards.get(0).requestFocus();
    }

    // ---------------------------------------------------------------- layout

    private View buildUi() {
        icons.clear();
        appCards.clear();
        recentCards.clear();
        byKey.clear();
        focused = null;
        firstWall = true;
        glowJump = true;

        root = new FrameLayout(this);
        root.setBackgroundColor(AuraTheme.BG);
        wallBack = wallLayer();
        wallFront = wallLayer();
        root.addView(wallBack, wallParams());
        root.addView(wallFront, wallParams());

        glowDrawable = new GradientDrawable();
        glowDrawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glowDrawable.setGradientRadius(dp(110));
        setGlowColor(glowColor);
        glow = new View(this);
        glow.setBackground(glowDrawable);
        glow.setAlpha(0f);
        root.addView(glow, new FrameLayout.LayoutParams(dp(220), dp(220)));

        FrameLayout content = new FrameLayout(this);
        content.setClipChildren(false);
        root.addView(content, match());

        header = buildHeader();
        content.addView(header, AuraTheme.flp(-1, -2, Gravity.TOP, dp(56), dp(36), dp(48), 0));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setClipChildren(false);
        content.addView(bottom, AuraTheme.flp(-1, -2, Gravity.BOTTOM, 0, 0, 0, dp(24)));

        appScroll = new RowScroll(this, dp(150));
        appRow = row(dp(18), dp(18));
        appScroll.addView(appRow, new FrameLayout.LayoutParams(-2, -2));
        bottom.addView(appScroll, new LinearLayout.LayoutParams(-1, -2));

        focusName = AuraTheme.text(this, 15, "sans-serif-medium", AuraTheme.TEXT);
        AuraTheme.shadow(focusName);
        focusName.setSingleLine(true);
        focusName.setAlpha(0f);
        bottom.addView(focusName, AuraTheme.llp(-2, -2, 0, 0, 0, dp(16)));
        if (prefs.alwaysLabels()) focusName.setVisibility(View.GONE);

        recentLabel = AuraTheme.text(this, 13, "sans-serif-medium", AuraTheme.TEXT_SEC);
        recentLabel.setLetterSpacing(0.05f);
        AuraTheme.shadow(recentLabel);
        bottom.addView(recentLabel, AuraTheme.llp(-2, -2, dp(58), 0, 0, 0));

        recentScroll = new RowScroll(this, dp(200));
        recentRow = row(dp(12), dp(14));
        recentScroll.addView(recentRow, new FrameLayout.LayoutParams(-2, -2));
        bottom.addView(recentScroll, new LinearLayout.LayoutParams(-1, -2));

        appScroll.setOnScrollChangeListener((v, x, y, ox, oy) -> placeExtras(false));
        recentScroll.setOnScrollChangeListener((v, x, y, ox, oy) -> placeExtras(false));
        return root;
    }

    private LinearLayout row(int top, int bottom) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setClipChildren(false);
        r.setClipToPadding(false);
        r.setPadding(dp(56), top, dp(56), bottom);
        return r;
    }

    private View buildHeader() {
        FrameLayout h = new FrameLayout(this);
        h.setClipChildren(false);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        clock = AuraTheme.text(this, 64, "sans-serif-thin", AuraTheme.TEXT);
        clock.setIncludeFontPadding(false);
        dateLine = AuraTheme.text(this, 18, "sans-serif-light", AuraTheme.TEXT);
        greetingTv = AuraTheme.text(this, 13, "sans-serif-medium", AuraTheme.TEXT_SEC);
        greetingTv.setLetterSpacing(0.06f);
        left.addView(clock);
        left.addView(dateLine, AuraTheme.llp(-2, -2, dp(3), dp(4), 0, 0));
        left.addView(greetingTv, AuraTheme.llp(-2, -2, dp(3), dp(6), 0, 0));
        greetingTv.setVisibility(prefs.greeting() ? View.VISIBLE : View.GONE);
        for (TextView t : new TextView[]{clock, dateLine, greetingTv}) AuraTheme.shadow(t);
        h.addView(left, new FrameLayout.LayoutParams(-2, -2, Gravity.START | Gravity.TOP));

        iconBar = new LinearLayout(this);
        iconBar.setGravity(Gravity.CENTER_VERTICAL);
        iconBar.setClipChildren(false);
        // Inputs only where the device actually has TV inputs or a tuner app (not on streaming boxes).
        if (hasInputs()) iconButton(R.drawable.ic_input, getString(R.string.inputs), this::showInputs);
        LinearLayout wifi = iconButton(R.drawable.ic_wifi, getString(R.string.wifi),
                v -> { if (!launch(new Intent(Settings.ACTION_WIFI_SETTINGS))) launch(new Intent(Settings.ACTION_SETTINGS)); });
        wifiIcon = (ImageView) wifi.getChildAt(0);
        wifiLabel = (TextView) wifi.getChildAt(1);
        iconButton(R.drawable.ic_apps, getString(R.string.all_apps), v -> AuraTheme.open(this, AllAppsActivity.class));
        iconButton(R.drawable.ic_settings, getString(R.string.settings), v -> AuraTheme.open(this, SettingsActivity.class));
        // Explicit neighbours: the pills change width as their labels appear, which fools geometric focus search.
        for (int i = 0; i < icons.size(); i++) {
            View v = icons.get(i);
            v.setNextFocusUpId(v.getId());
            v.setNextFocusLeftId(icons.get(Math.max(0, i - 1)).getId());
            v.setNextFocusRightId(icons.get(Math.min(icons.size() - 1, i + 1)).getId());
        }
        h.addView(iconBar, AuraTheme.flp(-2, -2, Gravity.END | Gravity.TOP, 0, dp(6), 0, 0));
        return h;
    }

    /** Round glass icon that grows into a labelled pill while focused. */
    private LinearLayout iconButton(int icon, String label, View.OnClickListener click) {
        LinearLayout p = new LinearLayout(this);
        p.setGravity(Gravity.CENTER_VERTICAL);
        p.setFocusable(true);
        p.setClickable(true);
        p.setId(View.generateViewId());
        p.setPadding(dp(11), 0, dp(11), 0);
        p.setMinimumWidth(dp(40));
        GradientDrawable bg = AuraTheme.rounded(ICON_BG, dp(20));
        bg.setStroke(dp(1), 0x33FFFFFF);
        p.setBackground(bg);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(AuraTheme.TEXT);
        p.addView(iv, new LinearLayout.LayoutParams(dp(18), dp(18)));
        TextView t = AuraTheme.text(this, 13, "sans-serif-medium", AuraTheme.BG);
        t.setText(label);
        t.setSingleLine(true);
        t.setVisibility(View.GONE);
        p.addView(t, AuraTheme.llp(-2, -2, dp(8), 0, dp(2), 0));
        p.setOnClickListener(click);
        String key = "icon:" + label;
        p.setOnFocusChangeListener((v, has) -> {
            int acc = prefs.accent();
            bg.setColor(has ? acc : ICON_BG);
            bg.setStroke(dp(1), has ? acc : 0x33FFFFFF);
            iv.setColorFilter(has ? AuraTheme.BG : AuraTheme.TEXT);
            // No LayoutTransition here: on Android 14 it made the newly focused pill drop focus mid-animation.
            t.setVisibility(has ? View.VISIBLE : View.GONE);
            if (has) { t.setAlpha(0f); t.animate().alpha(1f).setDuration(AuraTheme.FOCUS_IN).start(); }
            v.animate().setStartDelay(0).scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f)
                    .setDuration(has ? AuraTheme.FOCUS_IN : AuraTheme.FOCUS_OUT).setInterpolator(EASE).start();
            if (!has) return;
            focused = v;
            focusKey = key;
            glow.animate().alpha(0f).setDuration(200).start();
            focusName.animate().alpha(0f).setDuration(150).start();
        });
        iconBar.addView(p, AuraTheme.llp(-2, dp(40), iconBar.getChildCount() == 0 ? 0 : dp(10), 0, 0, 0));
        icons.add(p);
        byKey.put(key, p);
        return p;
    }

    // ---------------------------------------------------------------- rows

    private void buildRows() {
        android.content.pm.ChangedPackages ch = getPackageManager().getChangedPackages(packageSeq);
        if (ch != null) packageSeq = ch.getSequenceNumber();
        apps = AppRepo.load(this);
        appRow.removeAllViews();
        appCards.clear();
        byKey.keySet().removeIf(k -> k.startsWith("app:"));

        List<AppRepo.Entry> dock = new ArrayList<>();
        for (String p : AppRepo.effectiveDock(prefs, new ArrayList<>(apps.keySet()))) dock.add(apps.get(p));
        boolean large = prefs.cardSize() == 1;
        int w = dp(large ? 136 : 112), h = dp(large ? 77 : 63);
        for (AppRepo.Entry e : dock) addAppTile(e, w, h);
        addAllAppsTile(w, h);
        edges(appCards);
        if (!appCards.isEmpty()) for (View i : icons) i.setNextFocusDownId(appCards.get(0).getId());
        refreshRecent();
        restoreFocus();
    }

    private void addAppTile(AppRepo.Entry e, int w, int h) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setClipChildren(false);
        FrameLayout card = Cards.app(this, e, prefs.cardStyle(), w, h, prefs.accent());
        card.setId(View.generateViewId());
        card.setFocusable(true);
        card.setClickable(true);
        tile.addView(card, new LinearLayout.LayoutParams(w, h));
        TextView label = null;
        if (prefs.alwaysLabels()) {
            label = AuraTheme.text(this, 12, "sans-serif-medium", AuraTheme.TEXT_SEC);
            label.setText(e.label);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            AuraTheme.shadow(label);
            tile.addView(label, AuraTheme.llp(w, -2, 0, dp(10), 0, 0));
        }
        appRow.addView(tile, AuraTheme.llp(-2, -2, appRow.getChildCount() == 0 ? 0 : dp(18), 0, 0, 0));
        String key = "app:" + e.pkg;
        TextView lbl = label;
        card.setOnClickListener(v -> { prefs.launched(e.pkg); launch(e.intent()); });
        card.setOnFocusChangeListener((v, has) -> {
            Cards.focus(v, has, 1.12f);
            if (lbl != null) lbl.setTextColor(has ? AuraTheme.TEXT : AuraTheme.TEXT_SEC);
            if (!has) return;
            onCardFocused(v, key, e.color, 1.5f, 0.9f);
            for (View i : icons) i.setNextFocusDownId(v.getId());
            if (focusName.getVisibility() == View.VISIBLE) {
                focusName.setText(e.label);
                focusName.animate().alpha(1f).setDuration(180).start();
            }
            placeExtras(true);
        });
        appCards.add(card);
        byKey.put(key, card);
    }

    private void addAllAppsTile(int w, int h) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setClipChildren(false);
        FrameLayout card = Cards.action(this, R.drawable.ic_apps, getString(R.string.all_apps), w, h, prefs.accent());
        card.setId(View.generateViewId());
        card.setFocusable(true);
        card.setClickable(true);
        tile.addView(card, new LinearLayout.LayoutParams(w, h));
        appRow.addView(tile, AuraTheme.llp(-2, -2, appRow.getChildCount() == 0 ? 0 : dp(18), 0, 0, 0));
        card.setOnClickListener(v -> AuraTheme.open(this, AllAppsActivity.class));
        card.setOnFocusChangeListener((v, has) -> {
            Cards.focus(v, has, 1.12f);
            if (!has) return;
            onCardFocused(v, "app:*all", prefs.accent(), 1.5f, 0.9f);
            for (View i : icons) i.setNextFocusDownId(v.getId());
            focusName.animate().alpha(0f).setDuration(150).start();
            placeExtras(true);
        });
        appCards.add(card);
        byKey.put("app:*all", card);
    }

    private void refreshRecent() {
        if (recentRow == null) return;
        boolean hadFocus = focused != null && recentCards.contains(focused);
        recentRow.removeAllViews();
        recentCards.clear();
        byKey.keySet().removeIf(k -> k.startsWith("recent:"));

        List<RecentItem> items = prefs.showRecent() ? usageRecent() : Collections.emptyList();
        recentLabel.setText(R.string.recently_used);
        int vis = items.isEmpty() ? View.GONE : View.VISIBLE;
        recentLabel.setVisibility(vis);
        recentScroll.setVisibility(vis);

        boolean large = prefs.cardSize() == 1;
        int w = dp(large ? 200 : 176), h = dp(large ? 112 : 99);
        for (int i = 0; i < items.size(); i++) {
            RecentItem it = items.get(i);
            FrameLayout card = Cards.recent(this, it, w, h, prefs.accent());
            card.setId(View.generateViewId());
            card.setFocusable(true);
            card.setClickable(true);
            recentRow.addView(card, AuraTheme.llp(w, h, i == 0 ? 0 : dp(18), 0, 0, 0));
            String key = "recent:" + i;
            card.setOnClickListener(v -> {
                prefs.launched(it.app.pkg);
                launch(it.app.intent());
            });
            card.setOnFocusChangeListener((v, has) -> {
                Cards.focus(v, has, 1.07f);
                if (!has) return;
                onCardFocused(v, key, it.app.color, 2.1f, 1.3f);
                focusName.animate().alpha(0f).setDuration(150).start();
                placeExtras(true);
            });
            recentCards.add(card);
            byKey.put(key, card);
        }
        edges(recentCards);
        if (hadFocus) restoreFocus();
    }

    private void edges(List<View> l) {
        if (l.isEmpty()) return;
        l.get(0).setNextFocusLeftId(l.get(0).getId());
        View last = l.get(l.size() - 1);
        last.setNextFocusRightId(last.getId());
    }

    private void restoreFocus() {
        View v = focusKey == null ? null : byKey.get(focusKey);
        if (v == null && !appCards.isEmpty()) v = appCards.get(0);
        if (v == null) return;
        v.requestFocus();
        glowJump = true;
        root.post(() -> placeExtras(false));
    }

    // ---------------------------------------------------------------- focus glow + name

    private void onCardFocused(View v, String key, int color, float sx, float sy) {
        boolean glowShowing = glow.getAlpha() > 0.05f && focused != null && !icons.contains(focused);
        focused = v;
        focusKey = key;
        if (!glowShowing) glowJump = true;
        tintGlow(color);
        glow.animate().setStartDelay(0).alpha(0.6f).scaleX(sx).scaleY(sy)
                .setDuration(260).setInterpolator(EASE).start();
    }

    /** Keeps the glow under the focused card and the app name under it, including while a row scrolls. */
    private void placeExtras(boolean animate) {
        if (focused == null || glow == null || focused.getWidth() == 0 || icons.contains(focused)) return;
        boolean smooth = animate && !glowJump;
        if (appCards.contains(focused) && focusName.getVisibility() == View.VISIBLE) {
            View tile = (View) focused.getParent();
            float tx = tile.getLeft() - appScroll.getScrollX();
            if (nameMove != null) nameMove.cancel();
            if (smooth) {
                nameMove = ObjectAnimator.ofFloat(focusName, View.TRANSLATION_X, tx);
                nameMove.setDuration(200);
                nameMove.setInterpolator(EASE);
                nameMove.start();
            } else {
                focusName.setTranslationX(tx);
            }
        }
        int[] a = new int[2], r = new int[2];
        focused.getLocationInWindow(a);
        root.getLocationInWindow(r);
        float cx = a[0] - r[0] + focused.getWidth() * focused.getScaleX() / 2f;
        float cy = a[1] - r[1] + focused.getHeight() * focused.getScaleY() / 2f;
        float gx = cx - glow.getWidth() / 2f, gy = cy - glow.getHeight() / 2f;
        if (glowMove != null) glowMove.cancel();
        if (smooth) {
            glowMove = ObjectAnimator.ofPropertyValuesHolder(glow,
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_X, gx),
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, gy));
            glowMove.setDuration(220);
            glowMove.setInterpolator(EASE);
            glowMove.start();
        } else {
            glow.setTranslationX(gx);
            glow.setTranslationY(gy);
        }
        glowJump = false;
    }

    private void tintGlow(int to) {
        if (glowAnim != null) glowAnim.cancel();
        glowAnim = ValueAnimator.ofObject(new ArgbEvaluator(), glowColor, to);
        glowAnim.setDuration(350);
        glowAnim.addUpdateListener(a -> setGlowColor((int) a.getAnimatedValue()));
        glowAnim.start();
    }

    private void setGlowColor(int c) {
        glowColor = c;
        glowDrawable.setColors(new int[]{AuraTheme.alpha(c, 0.55f), AuraTheme.alpha(c, 0.18f), 0x00000000});
    }

    // ---------------------------------------------------------------- recent / watch next

    private boolean hasUsageAccess() {
        return usageAccess(this);
    }

    /** Usage access is granted by the user in system settings (or over ADB); without it only AURA's own launches count. */
    @SuppressWarnings("deprecation")
    static boolean usageAccess(Context c) {
        AppOpsManager ops = c.getSystemService(AppOpsManager.class);
        if (ops == null) return false;
        int uid = android.os.Process.myUid();
        int mode = Build.VERSION.SDK_INT >= 29
                ? ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, c.getPackageName())
                : ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, c.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    /** Real history only: Android's usage stats (when granted) merged with launches made from AURA. */
    private List<RecentItem> usageRecent() {
        Map<String, Long> times = new HashMap<>();
        if (hasUsageAccess()) {
            try {
                UsageStatsManager usm = getSystemService(UsageStatsManager.class);
                long now = System.currentTimeMillis();
                for (UsageStats u : usm.queryAndAggregateUsageStats(now - 14L * 86400000L, now).values())
                    if (u.getTotalTimeInForeground() > 0 && u.getLastTimeUsed() > 0)
                        times.put(u.getPackageName(), u.getLastTimeUsed());
            } catch (RuntimeException ignored) { }
        }
        for (Map.Entry<String, Long> r : prefs.recent().entrySet()) {
            Long t = times.get(r.getKey());
            if (t == null || r.getValue() > t) times.put(r.getKey(), r.getValue());
        }
        List<RecentItem> out = new ArrayList<>();
        for (Map.Entry<String, Long> t : times.entrySet()) {
            AppRepo.Entry e = apps.get(t.getKey());
            if (e == null) continue;
            RecentItem it = new RecentItem();
            it.app = e;
            it.time = t.getValue();
            it.title = e.label;
            it.subtitle = ago(t.getValue());
            out.add(it);
        }
        Collections.sort(out, (x, y) -> Long.compare(y.time, x.time));
        return out.size() > 8 ? new ArrayList<>(out.subList(0, 8)) : out;
    }

    private String ago(long t) {
        long d = System.currentTimeMillis() - t;
        if (d < 2 * 60000L) return getString(R.string.just_now);
        if (d < 60 * 60000L) return getString(R.string.minutes_ago, (int) (d / 60000L));
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        Calendar day = Calendar.getInstance();
        String hm = android.text.format.DateFormat.getTimeFormat(this).format(c.getTime());
        if (sameDay(c, day)) return getString(R.string.today_at, hm);
        day.add(Calendar.DAY_OF_YEAR, -1);
        if (sameDay(c, day)) return getString(R.string.yesterday_at, hm);
        Locale l = Locale.getDefault();
        return new SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(l, "dMMMM"), l).format(c.getTime());
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    // ---------------------------------------------------------------- wallpaper

    /** Larger than the screen: a view is clipped to its own bounds, so a screen-sized one would leave a bare edge while panning. */
    private FrameLayout.LayoutParams wallParams() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        return new FrameLayout.LayoutParams(Math.round(dm.widthPixels * PAN_COVER),
                Math.round(dm.heightPixels * PAN_COVER), Gravity.CENTER);
    }

    private ImageView wallLayer() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER);
        return v;
    }

    private void wallpaperTick(boolean advance) {
        handler.removeCallbacks(rotate);
        List<String> ids = Wallpapers.list(this);
        if (ids.isEmpty()) return;
        String sel = prefs.wallpaper();
        long iv = prefs.wallInterval();
        String target;
        if (!"auto".equals(sel) && ids.contains(sel)) {
            target = sel;
        } else {
            int idx = Math.floorMod(prefs.wallIndex(), ids.size());
            long since = SystemClock.elapsedRealtime() - wallChangedAt;
            boolean due = iv > 0 && shownWall != null && since >= iv;
            if (advance || due) {
                idx = (idx + 1) % ids.size();
                prefs.wallIndex(idx);
            }
            target = ids.get(idx);
            if (iv > 0) {
                long left = shownWall == null || advance || due ? iv : iv - since;
                handler.postDelayed(rotate, Math.max(1000, left));
            }
        }
        if (!target.equals(shownWall)) showWallpaper(target);
    }

    private void showWallpaper(String id) {
        shownWall = id;
        wallChangedAt = SystemClock.elapsedRealtime();
        prefs.shownWallpaper(id);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int w = dm.widthPixels, h = dm.heightPixels;
        GradientDrawable[] grade = grade();
        new Thread(() -> {
            Bitmap src = Wallpapers.decode(this, id, w, h, Bitmap.Config.RGB_565);
            Bitmap bm = null;
            if (src != null) {
                // Pre-scaled to cover the screen plus a margin, so the camera move is a pure 1:1 pan;
                // scaling the photo every frame cost this GPU ~14 ms per frame.
                float s = Math.max(w * PAN_COVER / src.getWidth(), h * PAN_COVER / src.getHeight());
                int tw = Math.round(src.getWidth() * s), th = Math.round(src.getHeight() * s);
                try {
                    bm = Bitmap.createBitmap(tw, th, Bitmap.Config.RGB_565);
                    bm.setDensity(Bitmap.DENSITY_NONE);
                    Canvas cv = new Canvas(bm);
                    cv.drawBitmap(src, null, new Rect(0, 0, tw, th), new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG));
                    for (GradientDrawable g : grade) {
                        g.setBounds(0, 0, tw, th);
                        g.draw(cv);
                    }
                } catch (OutOfMemoryError oom) {
                    bm = null;
                }
                src.recycle();
            }
            Bitmap result = bm;
            runOnUiThread(() -> crossfade(result, id));
        }).start();
    }

    /**
     * Cinematic grade: dark at the top for the clock, clear in the middle, deep at the bottom for the rows, plus a
     * left-to-right fade behind the clock. Baked into the photo once, so the GPU fills the screen once per frame.
     */
    private GradientDrawable[] grade() {
        float f = new float[]{0.75f, 1f, 1.25f}[prefs.dim()];
        return new GradientDrawable[]{
                new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{
                        bg(0.55f * f), bg(0.14f * f), bg(0.04f * f), bg(0.32f * f), bg(0.82f * f), bg(0.95f)}),
                new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{
                        bg(0.55f * f), bg(0.22f * f), bg(0f), bg(0f), bg(0.12f * f)})};
    }

    private void crossfade(Bitmap bm, String id) {
        if (bm == null || isDestroyed() || !id.equals(shownWall)) return;
        ImageView old = wallFront;
        wallFront = wallBack;
        wallBack = old;
        root.removeView(wallFront);
        root.addView(wallFront, 1, wallParams());
        wallFront.setImageBitmap(bm);
        wallFront.setTranslationX(0f);
        wallFront.setTranslationY(0f);
        wallFront.setAlpha(0f);
        ImageView back = wallBack;
        wallFront.animate().alpha(1f).setDuration(firstWall ? 700 : 2200).withEndAction(() -> {
            if (back != wallFront) back.setImageDrawable(null);
            root.setBackground(null);
        }).start();
        firstWall = false;
        startKenBurns();
    }

    /** Slow drift across the pre-scaled photo; paused whenever another app is in front. */
    private void startKenBurns() {
        if (kenBurns != null) kenBurns.cancel();
        kenBurns = null;
        if (!prefs.kenBurns()) return;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float px = Math.max(0f, (wallFront.getLayoutParams().width - dm.widthPixels) / 2f - 2f);
        float py = Math.max(0f, (wallFront.getLayoutParams().height - dm.heightPixels) / 2f - 2f) * 0.6f;
        kenBurns = ObjectAnimator.ofPropertyValuesHolder(wallFront,
                PropertyValuesHolder.ofFloat(View.TRANSLATION_X, px, -px),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, py, -py));
        kenBurns.setDuration(50000);
        kenBurns.setInterpolator(new AccelerateDecelerateInterpolator());
        kenBurns.setRepeatMode(ValueAnimator.REVERSE);
        kenBurns.setRepeatCount(ValueAnimator.INFINITE);
        kenBurns.start();
    }

    // ---------------------------------------------------------------- inputs

    private void showInputs(View anchor) {
        int acc = prefs.accent();
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable bg = AuraTheme.rounded(0xF2141B2A, dp(18));
        bg.setStroke(dp(1), AuraTheme.GLASS_EDGE);
        list.setBackground(bg);
        TextView head = AuraTheme.text(this, 11, "sans-serif-medium", AuraTheme.TEXT_DIM);
        head.setText(getString(R.string.inputs).toUpperCase(Locale.getDefault()));
        head.setLetterSpacing(0.14f);
        list.addView(head, AuraTheme.llp(-2, -2, dp(14), dp(6), 0, dp(6)));

        PopupWindow pw = new PopupWindow(list, dp(240), -2, true);
        List<TextView> rows = new ArrayList<>();
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        List<TvInputInfo> inputs = new ArrayList<>();
        if (tim != null) for (TvInputInfo t : tim.getTvInputList())
            if (t.isPassthroughInput() && !t.isHidden(this)) inputs.add(t);
        Collections.sort(inputs, (x, y) -> inputLabel(x).compareToIgnoreCase(inputLabel(y)));
        for (TvInputInfo t : inputs) {
            rows.add(inputRow(inputLabel(t), () -> {
                Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(t.getId()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (!launch(i)) openPackage(inputPickerPkg);
            }, acc));
        }
        if (isInstalled(liveTvPkg)) rows.add(inputRow(getString(R.string.live_tv), () -> openPackage(liveTvPkg), acc));
        if (inputs.isEmpty() && isInstalled(inputPickerPkg))
            rows.add(inputRow(getString(R.string.all_sources), () -> openPackage(inputPickerPkg), acc));
        if (rows.isEmpty())
            rows.add(inputRow(getString(R.string.system_settings), () -> launch(new Intent(Settings.ACTION_SETTINGS)), acc));
        for (TextView r : rows) {
            r.setOnClickListener(v -> { pw.dismiss(); ((Runnable) v.getTag()).run(); });
            list.addView(r, new LinearLayout.LayoutParams(-1, dp(42)));
        }
        for (int i = 0; i < rows.size(); i++) {
            TextView r = rows.get(i);
            r.setNextFocusUpId(rows.get(Math.max(0, i - 1)).getId());
            r.setNextFocusDownId(rows.get(Math.min(rows.size() - 1, i + 1)).getId());
            r.setNextFocusLeftId(r.getId());
            r.setNextFocusRightId(r.getId());
        }
        pw.setOutsideTouchable(true);
        pw.setElevation(dp(16));
        pw.setAnimationStyle(android.R.style.Animation_Dialog);
        pw.setOnDismissListener(() -> { inputsPopup = null; anchor.requestFocus(); });
        inputsPopup = pw;
        pw.showAsDropDown(anchor, 0, dp(10), Gravity.END);
        rows.get(0).requestFocus();
    }

    private String inputLabel(TvInputInfo t) {
        CharSequence c = t.loadCustomLabel(this);
        if (c == null || c.length() == 0) c = t.loadLabel(this);
        return String.valueOf(c);
    }

    private TextView inputRow(String label, Runnable action, int accent) {
        TextView r = AuraTheme.text(this, 15, "sans-serif", AuraTheme.TEXT);
        r.setText(label);
        r.setTag(action);
        r.setId(View.generateViewId());
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(16), 0, dp(16), 0);
        r.setFocusable(true);
        GradientDrawable bg = AuraTheme.rounded(0x00000000, dp(12));
        r.setBackground(bg);
        r.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? accent : 0x00000000);
            r.setTextColor(has ? AuraTheme.BG : AuraTheme.TEXT);
        });
        return r;
    }

    // ---------------------------------------------------------------- status

    private void updateClock() {
        if (clock == null) return;
        Calendar now = Calendar.getInstance();
        int h = now.get(Calendar.HOUR_OF_DAY);
        boolean is24 = android.text.format.DateFormat.is24HourFormat(this);
        Locale l = Locale.getDefault();
        clock.setText(new SimpleDateFormat(is24 ? "HH:mm" : "h:mm", l).format(now.getTime()));
        dateLine.setText(AuraTheme.cap(new SimpleDateFormat(
                android.text.format.DateFormat.getBestDateTimePattern(l, "EEEEdMMMM"), l).format(now.getTime())));
        greetingTv.setText(h < 5 ? R.string.greet_night : h < 10 ? R.string.greet_morning : h < 12 ? R.string.greet_forenoon
                : h < 18 ? R.string.greet_afternoon : R.string.greet_evening);
    }

    private void updateWifi() {
        if (wifiLabel == null) return;
        boolean connected = false, wifi = false;
        String ssid = null;
        try {
            ConnectivityManager cm = getSystemService(ConnectivityManager.class);
            NetworkCapabilities caps = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
            connected = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            wifi = caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            if (wifi) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                String s = wm == null || wm.getConnectionInfo() == null ? null : wm.getConnectionInfo().getSSID();
                if (s != null && !s.contains("unknown ssid")) ssid = s.replace("\"", "");
            }
        } catch (SecurityException ignored) { }
        wifiIcon.setImageResource(connected ? R.drawable.ic_wifi : R.drawable.ic_wifi_off);
        wifiLabel.setText(!connected ? getString(R.string.net_offline) : ssid != null ? ssid
                : getString(wifi ? R.string.net_wifi : R.string.net_wired));
    }

    private String firstInstalled(String[] pkgs) {
        for (String p : pkgs) if (isInstalled(p)) return p;
        return "";
    }

    private boolean hasInputs() {
        if (isInstalled(liveTvPkg)) return true;
        if (!getPackageManager().hasSystemFeature("android.software.live_tv")) return false;
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim == null) return false;
        try {
            for (TvInputInfo t : tim.getTvInputList()) if (t.isPassthroughInput() && !t.isHidden(this)) return true;
        } catch (RuntimeException ignored) { }
        return false;
    }

    // ---------------------------------------------------------------- entrance

    private void playEntrance() {
        header.setAlpha(0f);
        header.setTranslationY(-dp(14));
        header.animate().alpha(1f).translationY(0).setStartDelay(120).setDuration(650).setInterpolator(EASE).start();
        stagger(appRow, 220, 45);
        recentLabel.setAlpha(0f);
        recentLabel.animate().alpha(1f).setStartDelay(420).setDuration(500).start();
        stagger(recentRow, 460, 55);
    }

    private void stagger(ViewGroup g, long delay, long step) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View v = g.getChildAt(i);
            v.setAlpha(0f);
            v.setTranslationY(dp(26));
            v.animate().alpha(1f).translationY(0).setStartDelay(delay + i * step)
                    .setDuration(520).setInterpolator(EASE).start();
        }
    }

    // ---------------------------------------------------------------- helpers

    private boolean launch(Intent i) {
        try {
            if (!i.hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean isInstalled(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void openPackage(String pkg) {
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i != null) launch(i);
    }

    private int dp(float v) { return AuraTheme.dp(this, v); }

    private static FrameLayout.LayoutParams match() { return new FrameLayout.LayoutParams(-1, -1); }

    private static int bg(float a) { return AuraTheme.alpha(AuraTheme.BG, a); }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP && !icons.isEmpty()) {
            showInputs(icons.get(0));
            return true;
        }
        // Up from the app row lands on the icon straight above, not wherever FocusFinder's scoring puts it.
        if (e.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP && e.getAction() == KeyEvent.ACTION_DOWN
                && focused != null && focused.isFocused() && appCards.contains(focused) && !icons.isEmpty()) {
            int[] p = new int[2];
            focused.getLocationOnScreen(p);
            float cx = p[0] + focused.getWidth() * focused.getScaleX() / 2f;
            View best = null;
            float bestD = Float.MAX_VALUE;
            for (View i : icons) {
                i.getLocationOnScreen(p);
                float d = Math.abs(p[0] + i.getWidth() / 2f - cx);
                if (d < bestD) { bestD = d; best = i; }
            }
            best.requestFocus();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    /** Horizontal row that keeps the focused card a comfortable distance from either screen edge. */
    static final class RowScroll extends HorizontalScrollView {
        private final int margin;

        RowScroll(Context c, int margin) {
            super(c);
            this.margin = margin;
            setHorizontalScrollBarEnabled(false);
            setOverScrollMode(OVER_SCROLL_NEVER);
            setClipChildren(false);
            setClipToPadding(false);
            setFocusable(false);
        }

        @Override
        protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) {
            if (getChildCount() == 0) return 0;
            int w = getWidth(), sx = getScrollX();
            int max = Math.max(0, getChildAt(0).getWidth() - w);
            int delta = 0;
            if (r.right > sx + w - margin) delta = r.right - (sx + w - margin);
            else if (r.left < sx + margin) delta = r.left - (sx + margin);
            return Math.max(-sx, Math.min(delta, max - sx));
        }
    }
}
