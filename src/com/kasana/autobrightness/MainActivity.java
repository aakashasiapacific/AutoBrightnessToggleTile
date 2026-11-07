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

import java.util.function.Consumer;

/** One UI-style setup screen: big collapsing title, a live switch, permission and add-tile rows. */
public class MainActivity extends Activity {

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    /** Shared grid (dp): left edge inside cards, icon size, icon-to-text gap. */
    private static final int INSET = 20;
    private static final int ICON = 40;
    private static final int GAP = 16;

    private boolean dark;
    private int cBg, cCell, cLabel, cSecondary, cTertiary, cSeparator, cHighlight;
    private int cBlue, cBlueTint, cOrange, cGray, cSwitchOff;

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
    private OneUiSwitch autoSwitch;
    private TextView autoStatus;
    private GradientDrawable autoBubble;
    private View setupHeader;
    private LinearLayout setupCard;
    private View setupDivider;
    private int navBottom;
    private ContentObserver observer;

    private static final class Row {
        LinearLayout view;
        GradientDrawable bubble;
        TextView sub;
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
        // Setup rows only show while a step is still needed; the section goes away once both are done
        boolean needPerm = !Brightness.canWrite(this);
        boolean needTile = !Brightness.isTileAdded(this);
        permRow.view.setVisibility(needPerm ? View.VISIBLE : View.GONE);
        tileRow.view.setVisibility(needTile ? View.VISIBLE : View.GONE);
        setupDivider.setVisibility(needPerm && needTile ? View.VISIBLE : View.GONE);
        int setup = needPerm || needTile ? View.VISIBLE : View.GONE;
        setupHeader.setVisibility(setup);
        setupCard.setVisibility(setup);

        boolean auto = Brightness.isAuto(this);
        autoSwitch.setChecked(auto, animate);
        autoStatus.setText(auto ? R.string.tile_on : R.string.tile_off);
        autoBubble.setColor(auto ? cOrange : cGray);
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
            cCell = 0xFF1F1F1F;
            cLabel = 0xFFFFFFFF;
            cSecondary = 0xFFA8A8A8;
            cTertiary = 0x66FFFFFF;
            cSeparator = 0x24FFFFFF;
            cHighlight = 0xFF333333;
            cBlue = 0xFF4C9BFF;
            cBlueTint = 0x294C9BFF;
            cOrange = 0xFFFF9F0A;
            cGray = 0xFF6F6F73;
            cSwitchOff = 0xFF9A9A9A;
        } else {
            cBg = 0xFFF5F5F5;
            cCell = 0xFFFFFFFF;
            cLabel = 0xFF000000;
            cSecondary = 0xFF6E6E6E;
            cTertiary = 0x4D000000;
            cSeparator = 0x1F000000;
            cHighlight = 0xFFEAEAEA;
            cBlue = 0xFF3E91FF;
            cBlueTint = 0x1F3E91FF;
            cOrange = 0xFFFF9500;
            cGray = 0xFF9A9AA0;
            cSwitchOff = 0xFF8E8E8E;
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

        // Everything shares one left edge: INSET from the card edge (title, icons, headers, footer)
        hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.BOTTOM);
        hero.setMinimumHeight(dp(150));
        hero.setPadding(dp(INSET), 0, dp(INSET), dp(24));
        heroTitle = text(getString(R.string.tile_label), 32, cLabel, 500);
        hero.addView(heroTitle, new LinearLayout.LayoutParams(MATCH, WRAP));
        content.addView(hero, new LinearLayout.LayoutParams(MATCH, WRAP));

        // Main card: the live switch
        LinearLayout control = card();
        Row autoRow = row(R.drawable.ic_tile, cGray, R.string.row_auto);
        autoBubble = autoRow.bubble;
        autoStatus = autoRow.sub;
        autoRow.view.setPadding(dp(INSET), dp(12), dp(10), dp(12));
        autoSwitch = new OneUiSwitch(this, cBlue, cSwitchOff);
        autoSwitch.setContentDescription(getString(R.string.row_auto));
        autoSwitch.setListener(new OneUiSwitch.Listener() {
            @Override
            public void onToggle(OneUiSwitch view, boolean checked) {
                onAutoToggled(checked);
            }
        });
        autoRow.view.addView(autoSwitch, new LinearLayout.LayoutParams(WRAP, WRAP));
        control.addView(autoRow.view, new LinearLayout.LayoutParams(MATCH, WRAP));
        content.addView(control, new LinearLayout.LayoutParams(MATCH, WRAP));

        // Setup card: only the steps that are still left
        setupHeader = sectionHeader(R.string.section_setup);
        content.addView(setupHeader);
        setupCard = card();
        permRow = row(R.drawable.ic_key, cGray, R.string.row_permission);
        addPill(permRow, R.string.allow);
        tappable(permRow.view, new Runnable() {
            @Override
            public void run() {
                openPermission();
            }
        });
        setupCard.addView(permRow.view, new LinearLayout.LayoutParams(MATCH, WRAP));
        setupDivider = separator(dp(INSET + ICON + GAP));
        setupCard.addView(setupDivider);
        tileRow = row(R.drawable.ic_apps, cBlue, R.string.row_tile);
        addPill(tileRow, R.string.add);
        tappable(tileRow.view, new Runnable() {
            @Override
            public void run() {
                requestAddTile();
            }
        });
        setupCard.addView(tileRow.view, new LinearLayout.LayoutParams(MATCH, WRAP));
        content.addView(setupCard, new LinearLayout.LayoutParams(MATCH, WRAP));

        content.addView(footer(R.string.footer_control));

        TextView version = text(getString(R.string.version_line), 12, cTertiary, 400);
        version.setGravity(Gravity.CENTER);
        content.addView(version, margins(MATCH, WRAP, 0, dp(40), 0, 0));

        // Top bar: fades in while scrolling and shows the collapsed title
        nav = new FrameLayout(this);
        navBg = new View(this);
        navBg.setBackgroundColor((cBg & 0x00FFFFFF) | 0xF0000000);
        navBg.setAlpha(0f);
        nav.addView(navBg, new FrameLayout.LayoutParams(MATCH, MATCH));
        navTitle = text(getString(R.string.tile_label), 17, cLabel, 600);
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
        g.setCornerRadius(dp(26));
        c.setBackground(g);
        c.setClipToOutline(true);
        return c;
    }

    /** Round icon bubble, then a title with an optional status line underneath. */
    private Row row(int glyph, int tint, int title) {
        Row r = new Row();
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setMinimumHeight(dp(68));
        v.setPadding(dp(INSET), dp(10), dp(INSET), dp(10));

        ImageView icon = new ImageView(this);
        r.bubble = new GradientDrawable();
        r.bubble.setShape(GradientDrawable.OVAL);
        r.bubble.setColor(tint);
        icon.setBackground(r.bubble);
        icon.setImageResource(glyph);
        icon.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        icon.setPadding(dp(9), dp(9), dp(9), dp(9));
        v.addView(icon, new LinearLayout.LayoutParams(dp(ICON), dp(ICON)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView t = text(getString(title), 17, cLabel, 400);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(t, new LinearLayout.LayoutParams(MATCH, WRAP));
        r.sub = text("", 14, cSecondary, 400);
        labels.addView(r.sub, margins(MATCH, WRAP, 0, dp(3), 0, 0));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, WRAP, 1f);
        tl.leftMargin = dp(GAP);
        tl.rightMargin = dp(8);
        v.addView(labels, tl);

        r.view = v;
        return r;
    }

    /** Tonal blue action pill at the end of a setup row; the row has no status line. */
    private void addPill(Row r, int label) {
        r.sub.setVisibility(View.GONE);
        TextView pill = text(getString(label), 15, cBlue, 500);
        pill.setSingleLine(true);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(16), 0, dp(16), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        bg.setColor(cBlueTint);
        pill.setBackground(bg);
        r.view.addView(pill, new LinearLayout.LayoutParams(WRAP, dp(36)));
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
        TextView t = text(getString(res), 14, cBlue, 500);
        t.setLayoutParams(margins(MATCH, WRAP, dp(INSET), dp(28), dp(INSET), dp(10)));
        return t;
    }

    private TextView footer(int res) {
        TextView t = text(getString(res), 13, cSecondary, 400);
        t.setLineSpacing(dp(2), 1f);
        t.setLayoutParams(margins(MATCH, WRAP, dp(INSET), dp(16), dp(INSET), 0));
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
