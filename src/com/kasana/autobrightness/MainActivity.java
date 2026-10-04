package com.kasana.autobrightness;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.StatusBarManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.function.Consumer;

/** iOS-style setup screen: permission, add-tile shortcut and a live switch. */
public class MainActivity extends Activity {

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private boolean dark;
    private int cBg, cCell, cLabel, cSecondary, cTertiary, cSeparator, cHighlight;
    private int cBlue, cGreen, cOrange, cGray, cSwitchOff;

    private FrameLayout nav;
    private View navBg;
    private View navLine;
    private TextView navTitle;
    private ScrollView scroll;
    private LinearLayout content;
    private LinearLayout hero;
    private TextView heroTitle;
    private Row permRow;
    private Row tileRow;
    private IosSwitch autoSwitch;
    private int navBottom;
    private ContentObserver observer;

    private static final class Row {
        LinearLayout view;
        TextView value;
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        loadPalette();
        setContentView(buildUi());
        setupWindow();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (observer == null) {
            observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange) {
                    refresh(true);
                }
            };
            getContentResolver().registerContentObserver(Brightness.modeUri(), false, observer);
        }
        refresh(false);
    }

    @Override
    protected void onPause() {
        if (observer != null) {
            getContentResolver().unregisterContentObserver(observer);
            observer = null;
        }
        super.onPause();
    }

    // ---------------------------------------------------------------- state

    private void refresh(boolean animate) {
        boolean canWrite = Brightness.canWrite(this);
        setValue(permRow, canWrite ? R.string.allowed : R.string.allow, !canWrite);
        boolean added = Brightness.isTileAdded(this);
        setValue(tileRow, added ? R.string.added : R.string.add, !added);
        autoSwitch.setChecked(Brightness.isAuto(this), animate);
    }

    private void setValue(Row row, int text, boolean isAction) {
        row.value.setText(text);
        row.value.setTextColor(isAction ? cBlue : cSecondary);
    }

    private void onAutoToggled(boolean on) {
        if (!Brightness.canWrite(this)) {
            autoSwitch.setChecked(!on, true);
            Toast.makeText(this, R.string.toast_allow_first, Toast.LENGTH_SHORT).show();
            openPermission();
            return;
        }
        if (!Brightness.setAuto(this, on)) {
            autoSwitch.setChecked(!on, true);
            Toast.makeText(this, R.string.toast_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void openPermission() {
        try {
            startActivity(Brightness.permissionIntent(this));
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (ActivityNotFoundException ignored) {
                // nothing else to open
            }
        }
    }

    private void requestAddTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            StatusBarManager sbm = getSystemService(StatusBarManager.class);
            if (sbm != null) {
                try {
                    sbm.requestAddTileService(
                            new ComponentName(this, AutoBrightnessTileService.class),
                            getString(R.string.tile_label),
                            Icon.createWithResource(this, R.drawable.ic_tile),
                            getMainExecutor(),
                            new Consumer<Integer>() {
                                @Override
                                public void accept(Integer result) {
                                    onAddTileResult(result);
                                }
                            });
                    return;
                } catch (RuntimeException ignored) {
                    // fall back to manual instructions
                }
            }
        }
        showAddTileHelp();
    }

    private void onAddTileResult(int result) {
        if (isFinishing() || isDestroyed()) return;
        if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED
                || result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
            Brightness.setTileAdded(this, true);
            refresh(false);
            Toast.makeText(this,
                    result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED
                            ? R.string.toast_tile_added : R.string.toast_tile_already,
                    Toast.LENGTH_SHORT).show();
        } else if (result != StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED) {
            showAddTileHelp();
        }
    }

    private void showAddTileHelp() {
        new AlertDialog.Builder(this, dark
                ? android.R.style.Theme_DeviceDefault_Dialog_Alert
                : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle(R.string.help_title)
                .setMessage(R.string.help_body)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    // ---------------------------------------------------------------- UI

    private void loadPalette() {
        if (dark) {
            cBg = 0xFF000000;
            cCell = 0xFF1C1C1E;
            cLabel = 0xFFFFFFFF;
            cSecondary = 0x99EBEBF5;
            cTertiary = 0x4DEBEBF5;
            cSeparator = 0xA6545458;
            cHighlight = 0xFF3A3A3C;
            cBlue = 0xFF0A84FF;
            cGreen = 0xFF30D158;
            cOrange = 0xFFFF9F0A;
            cGray = 0xFF8E8E93;
            cSwitchOff = 0xFF39393D;
        } else {
            cBg = 0xFFF2F2F7;
            cCell = 0xFFFFFFFF;
            cLabel = 0xFF000000;
            cSecondary = 0x993C3C43;
            cTertiary = 0x4D3C3C43;
            cSeparator = 0x4A3C3C43;
            cHighlight = 0xFFD1D1D6;
            cBlue = 0xFF007AFF;
            cGreen = 0xFF34C759;
            cOrange = 0xFFFF9500;
            cGray = 0xFF8E8E93;
            cSwitchOff = 0xFFE9E9EA;
        }
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(cBg);

        scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new FrameLayout.LayoutParams(MATCH, WRAP));
        root.addView(scroll, new FrameLayout.LayoutParams(MATCH, MATCH));

        // Hero card
        hero = card();
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(dp(24), dp(28), dp(24), dp(26));
        ImageView icon = new ImageView(this);
        GradientDrawable iconBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xFFFFC94D, 0xFFFF8A00});
        iconBg.setCornerRadius(dp(17));
        icon.setBackground(iconBg);
        icon.setImageResource(R.drawable.ic_tile);
        icon.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        icon.setPadding(dp(12), dp(12), dp(12), dp(12));
        hero.addView(icon, new LinearLayout.LayoutParams(dp(76), dp(76)));

        heroTitle = text(getString(R.string.app_name), 24, cLabel, 700);
        heroTitle.setGravity(Gravity.CENTER);
        hero.addView(heroTitle, margins(MATCH, WRAP, 0, dp(14), 0, 0));

        TextView desc = text(getString(R.string.hero_desc), 15, cSecondary, 400);
        desc.setGravity(Gravity.CENTER);
        desc.setLineSpacing(dp(2), 1f);
        hero.addView(desc, margins(MATCH, WRAP, 0, dp(6), 0, 0));
        content.addView(hero, new LinearLayout.LayoutParams(MATCH, WRAP));

        // Setup section
        content.addView(sectionHeader(R.string.section_setup));
        LinearLayout setup = card();
        permRow = row(R.drawable.ic_key, cGray, R.string.row_permission);
        addValueAndChevron(permRow);
        tappable(permRow.view, new Runnable() {
            @Override
            public void run() {
                openPermission();
            }
        });
        setup.addView(permRow.view, new LinearLayout.LayoutParams(MATCH, WRAP));
        setup.addView(separator(dp(16 + 30 + 14)));
        tileRow = row(R.drawable.ic_apps, cBlue, R.string.row_tile);
        addValueAndChevron(tileRow);
        tappable(tileRow.view, new Runnable() {
            @Override
            public void run() {
                requestAddTile();
            }
        });
        setup.addView(tileRow.view, new LinearLayout.LayoutParams(MATCH, WRAP));
        content.addView(setup, new LinearLayout.LayoutParams(MATCH, WRAP));
        content.addView(footer(R.string.footer_setup));

        // Brightness section
        content.addView(sectionHeader(R.string.section_control));
        LinearLayout control = card();
        Row autoRow = row(R.drawable.ic_tile, cOrange, R.string.row_auto);
        autoRow.view.setPadding(dp(16), 0, dp(12), 0);
        autoSwitch = new IosSwitch(this, cGreen, cSwitchOff);
        autoSwitch.setContentDescription(getString(R.string.row_auto));
        autoSwitch.setListener(new IosSwitch.Listener() {
            @Override
            public void onToggle(IosSwitch view, boolean checked) {
                onAutoToggled(checked);
            }
        });
        autoRow.view.addView(autoSwitch, new LinearLayout.LayoutParams(WRAP, WRAP));
        control.addView(autoRow.view, new LinearLayout.LayoutParams(MATCH, WRAP));
        content.addView(control, new LinearLayout.LayoutParams(MATCH, WRAP));
        content.addView(footer(R.string.footer_control));

        TextView version = text(getString(R.string.version_line), 12, cTertiary, 400);
        version.setGravity(Gravity.CENTER);
        content.addView(version, margins(MATCH, WRAP, 0, dp(32), 0, 0));

        // Translucent nav bar that fades in while scrolling (iOS large-title behaviour)
        nav = new FrameLayout(this);
        navBg = new View(this);
        navBg.setBackgroundColor((cBg & 0x00FFFFFF) | 0xF0000000);
        navBg.setAlpha(0f);
        nav.addView(navBg, new FrameLayout.LayoutParams(MATCH, MATCH));
        navTitle = text(getString(R.string.app_name), 17, cLabel, 600);
        navTitle.setGravity(Gravity.CENTER);
        navTitle.setSingleLine(true);
        navTitle.setAlpha(0f);
        nav.addView(navTitle, new FrameLayout.LayoutParams(MATCH, dp(44), Gravity.BOTTOM));
        navLine = new View(this);
        navLine.setBackgroundColor(cSeparator);
        navLine.setAlpha(0f);
        nav.addView(navLine, new FrameLayout.LayoutParams(MATCH, 1, Gravity.BOTTOM));
        root.addView(nav, new FrameLayout.LayoutParams(MATCH, dp(44), Gravity.TOP));

        scroll.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int x, int y, int oldX, int oldY) {
                updateNav();
            }
        });
        content.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b,
                                       int oldL, int oldT, int oldR, int oldB) {
                updateNav();
            }
        });
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                applyInsets(insets);
                return insets;
            }
        });
        return root;
    }

    @SuppressWarnings("deprecation")
    private void setupWindow() {
        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            w.setStatusBarContrastEnforced(false);
            w.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = w.getInsetsController();
            if (controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(dark ? 0 : mask, mask);
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if (!dark) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            w.getDecorView().setSystemUiVisibility(flags);
        }
    }

    @SuppressWarnings("deprecation")
    private void applyInsets(WindowInsets in) {
        int top;
        int bottom;
        int left;
        int right;
        if (Build.VERSION.SDK_INT >= 30) {
            Insets s = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            top = s.top;
            bottom = s.bottom;
            left = s.left;
            right = s.right;
        } else {
            top = in.getSystemWindowInsetTop();
            bottom = in.getSystemWindowInsetBottom();
            left = in.getSystemWindowInsetLeft();
            right = in.getSystemWindowInsetRight();
        }
        navBottom = top + dp(44);
        ViewGroup.LayoutParams lp = nav.getLayoutParams();
        if (lp.height != navBottom) {
            lp.height = navBottom;
            nav.setLayoutParams(lp);
        }
        content.setPadding(dp(16) + left, navBottom + dp(4), dp(16) + right, bottom + dp(36));
        updateNav();
    }

    private void updateNav() {
        if (navBottom == 0 || heroTitle.getHeight() == 0) return;
        int y = scroll.getScrollY();
        float bg = clamp(y / (float) dp(8));
        navBg.setAlpha(bg);
        navLine.setAlpha(bg);
        int titleTop = hero.getTop() + heroTitle.getTop();
        float p = clamp((y + navBottom - titleTop) / (float) heroTitle.getHeight());
        navTitle.setAlpha(p);
        navTitle.setTranslationY((1f - p) * dp(4));
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(cCell);
        g.setCornerRadius(dp(24));
        c.setBackground(g);
        c.setClipToOutline(true);
        return c;
    }

    private Row row(int glyph, int tint, int title) {
        Row r = new Row();
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setMinimumHeight(dp(52));
        v.setPadding(dp(16), 0, dp(16), 0);

        ImageView icon = new ImageView(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(tint);
        bg.setCornerRadius(dp(7));
        icon.setBackground(bg);
        icon.setImageResource(glyph);
        icon.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        icon.setPadding(dp(5), dp(5), dp(5), dp(5));
        v.addView(icon, new LinearLayout.LayoutParams(dp(30), dp(30)));

        TextView t = text(getString(title), 17, cLabel, 400);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, WRAP, 1f);
        tl.leftMargin = dp(14);
        tl.rightMargin = dp(8);
        v.addView(t, tl);

        r.view = v;
        return r;
    }

    private void addValueAndChevron(Row r) {
        r.value = text("", 17, cSecondary, 400);
        r.value.setSingleLine(true);
        r.view.addView(r.value, new LinearLayout.LayoutParams(WRAP, WRAP));
        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron);
        chevron.setImageTintList(ColorStateList.valueOf(cTertiary));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(dp(8), dp(14));
        cl.leftMargin = dp(10);
        r.view.addView(chevron, cl);
    }

    private void tappable(View v, final Runnable action) {
        StateListDrawable states = new StateListDrawable();
        states.setExitFadeDuration(220);
        states.addState(new int[] {android.R.attr.state_pressed}, new ColorDrawable(cHighlight));
        states.addState(new int[0], new ColorDrawable(Color.TRANSPARENT));
        v.setBackground(states);
        v.setClickable(true);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                action.run();
            }
        });
    }

    private View separator(int insetLeft) {
        View s = new View(this);
        s.setBackgroundColor(cSeparator);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, 1);
        lp.leftMargin = insetLeft;
        s.setLayoutParams(lp);
        return s;
    }

    private TextView sectionHeader(int res) {
        TextView t = text(getString(res).toUpperCase(Locale.ROOT), 13, cSecondary, 400);
        t.setLetterSpacing(0.03f);
        t.setLayoutParams(margins(MATCH, WRAP, dp(16), dp(28), dp(16), dp(8)));
        return t;
    }

    private TextView footer(int res) {
        TextView t = text(getString(res), 13, cSecondary, 400);
        t.setLineSpacing(dp(2), 1f);
        t.setLayoutParams(margins(MATCH, WRAP, dp(16), dp(8), dp(16), 0));
        return t;
    }

    private TextView text(String s, float sp, int color, int weight) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        if (weight != 400) {
            if (Build.VERSION.SDK_INT >= 28) {
                t.setTypeface(Typeface.create(Typeface.DEFAULT, weight, false));
            } else {
                t.setTypeface(Typeface.DEFAULT_BOLD);
            }
        }
        return t;
    }

    private static LinearLayout.LayoutParams margins(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.setMargins(l, t, r, b);
        return lp;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static float clamp(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
