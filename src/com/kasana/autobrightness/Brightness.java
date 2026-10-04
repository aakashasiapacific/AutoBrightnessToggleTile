package com.kasana.autobrightness;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.Settings;

/** Reads and writes Android's standard adaptive-brightness flag (Settings.System). */
final class Brightness {

    private static final String PREFS = "state";
    private static final String KEY_TILE_ADDED = "tile_added";

    private Brightness() {}

    static boolean canWrite(Context c) {
        return Settings.System.canWrite(c);
    }

    static boolean isAuto(Context c) {
        return Settings.System.getInt(c.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
    }

    static boolean setAuto(Context c, boolean auto) {
        if (!canWrite(c)) return false;
        try {
            return Settings.System.putInt(c.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    auto ? Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                         : Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        } catch (RuntimeException e) {
            return false;
        }
    }

    static Uri modeUri() {
        return Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE);
    }

    /** Opens this app's "Modify system settings" toggle. */
    static Intent permissionIntent(Context c) {
        return new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:" + c.getPackageName()));
    }

    static boolean isTileAdded(Context c) {
        return prefs(c).getBoolean(KEY_TILE_ADDED, false);
    }

    static void setTileAdded(Context c, boolean added) {
        prefs(c).edit().putBoolean(KEY_TILE_ADDED, added).apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
