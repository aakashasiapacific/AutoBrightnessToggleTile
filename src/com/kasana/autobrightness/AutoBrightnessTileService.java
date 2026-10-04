package com.kasana.autobrightness;

import android.app.PendingIntent;
import android.content.Intent;
import android.database.ContentObserver;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick panel tile: one tap flips adaptive brightness. */
public class AutoBrightnessTileService extends TileService {

    private ContentObserver observer;

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        Brightness.setTileAdded(this, true);
        render();
    }

    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
        Brightness.setTileAdded(this, false);
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        Brightness.setTileAdded(this, true);
        if (observer == null) {
            // Keeps the tile in sync when the setting is changed elsewhere (e.g. Samsung's slider menu)
            observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange) {
                    render();
                }
            };
            getContentResolver().registerContentObserver(Brightness.modeUri(), false, observer);
        }
        render();
    }

    @Override
    public void onStopListening() {
        unregister();
        super.onStopListening();
    }

    @Override
    public void onDestroy() {
        unregister();
        super.onDestroy();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (!Brightness.canWrite(this)) {
            if (isLocked()) {
                unlockAndRun(new Runnable() {
                    @Override
                    public void run() {
                        openPermission();
                    }
                });
            } else {
                openPermission();
            }
            return;
        }
        boolean target = !Brightness.isAuto(this);
        if (Brightness.setAuto(this, target)) {
            render(true, target);
        } else {
            render();
        }
    }

    @SuppressWarnings("deprecation")
    private void openPermission() {
        Intent intent = Brightness.permissionIntent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        } else {
            startActivityAndCollapse(intent);
        }
    }

    private void unregister() {
        if (observer != null) {
            getContentResolver().unregisterContentObserver(observer);
            observer = null;
        }
    }

    private void render() {
        render(Brightness.canWrite(this), Brightness.isAuto(this));
    }

    private void render(boolean canWrite, boolean auto) {
        Tile tile = getQsTile();
        if (tile == null) return;

        String status = !canWrite ? getString(R.string.tile_setup)
                : getString(auto ? R.string.tile_on : R.string.tile_off);

        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_tile));
        tile.setLabel(getString(R.string.tile_label));
        tile.setState(canWrite && auto ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        if (Build.VERSION.SDK_INT >= 29) tile.setSubtitle(status);
        if (Build.VERSION.SDK_INT >= 30) tile.setStateDescription(status);
        tile.updateTile();
    }
}
