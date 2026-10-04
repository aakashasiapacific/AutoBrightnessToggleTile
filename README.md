# Auto Brightness Tile

A one-tap **adaptive brightness** button for the Android quick panel, made for Samsung One UI and working on any phone running Android 8.0 or newer.

One UI hides adaptive brightness behind the ⋮ menu next to the brightness slider. This app gives it a proper quick panel button, with **no Shizuku, no ADB and no root**.

<p align="center">
  <img src="docs/preview.png" width="600" alt="The tile when on, when off, and the app icon">
</p>

## Features

- **One-tap toggle.** Tap the tile to turn adaptive brightness on or off.
- **Always in sync.** The tile updates when you change the setting anywhere else, such as Samsung's own brightness menu.
- **Long-press shortcut.** Long-press the tile to jump straight to Display settings.
- **Guided setup screen** (iOS-style, light and dark):
  - grant the permission
  - add the tile to the quick panel in one tap
  - flip adaptive brightness with a live switch
- **Tiny and private.** The APK is about 40 KB, with no internet permission, no ads and no tracking.

## How it works

Adaptive brightness is Android's standard `Settings.System.SCREEN_BRIGHTNESS_MODE` setting. Changing it only needs the **Modify system settings** permission (`WRITE_SETTINGS`), which you grant yourself with a normal toggle in Settings. That's why no Shizuku, ADB or root is involved.

The tile is a regular `TileService`, so it shows up next to the system buttons and follows your quick panel's own style.

## Install

1. Download the latest APK from [Releases](../../releases/latest).
2. Install it. If asked, allow "Install unknown apps" for your browser or file manager. On One UI, temporarily turn off **Auto Blocker** if it blocks the install.
3. Open **Auto Brightness Tile**, tap **Modify system settings**, and turn on **Allow permission**.
4. Tap **Quick panel tile → Add**. You can also add **Auto Brightness** yourself from the quick panel's edit (pencil) screen.

## Requirements

- Android 8.0 (API 26) or newer
- Designed for Samsung One UI 8 (Android 16); targets API 36

## Build from source

No Gradle or Android Studio is needed. `build.sh` uses the standard command-line Android tools:

```bash
sudo apt install aapt apksigner zipalign dalvik-exchange default-jdk-headless curl zip
./build.sh
```

On first run the script:

1. downloads `android.jar` (API 34)
2. compiles resources with `aapt2`
3. compiles Java with `javac`
4. converts to dex with `dx`
5. signs with `apksigner` (v2 + v3 signatures)

The APK lands in `build/`.

A new signing key is created in `keystore/` the first time (git-ignored). An APK signed with your own key can't update one installed from Releases, so uninstall the Releases version first.

Every push is also built by GitHub Actions. Download the APK from the run's artifacts in the **Actions** tab.

## Project layout

```
AndroidManifest.xml
src/com/kasana/autobrightness/
  AutoBrightnessTileService.java   quick panel tile
  MainActivity.java                setup screen
  IosSwitch.java                   iOS-style switch view
  TilePrefsActivity.java           long-press → Display settings
  Brightness.java                  reads/writes the brightness mode
res/                               icons, strings, themes (light + dark)
build.sh                           Gradle-free build script
```

## Credits

The half-sun tile and app icon is an original design, drawn to match the size and stroke weight of One UI's own quick-panel icons. The two small row icons on the setup screen (key and app grid) are from Google's [Material Icons](https://github.com/google/material-design-icons), used under the Apache License 2.0. That license ships inside the APK at `assets/LICENSE-material-symbols.txt`.

Not affiliated with Samsung or Google.

## License

[MIT](LICENSE)
