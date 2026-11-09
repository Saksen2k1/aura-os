# Troubleshooting

### Home still opens the TV's own launcher
The built-in launcher has priority on Google TV and many Android TV builds. Either open AURA from the app
list, or follow "TVs that keep their own launcher" in [INSTALL.md](INSTALL.md).

### After switching launchers, Home shows the Settings app
A known Android quirk from a stale home task. Run
`adb shell am force-stop com.android.tv.settings`, then press Home.

### `adb install` hangs (seen on the Google TV 14 emulator)
Play Protect is waiting for a confirmation on the TV screen. Look at the TV and accept, or on a test device
turn off "Verify apps over USB" in Developer options.

### "App not installed" / update fails
An APK signed with a different key is already installed (for example a build you made yourself). Uninstall
it first: `adb uninstall io.github.saksen2k1.aura`. AURA's settings are reset by this.

### The Recently used row is empty
It fills as you use apps. To include apps opened outside AURA, allow usage access
(**Settings → Usage access**, or `adb shell appops set io.github.saksen2k1.aura GET_USAGE_STATS allow`).

### An app has a white square instead of a logo
Some apps ship only a square icon with a coloured background. AURA then shows that icon on the dark card
rather than altering its colours. Plain white or black backgrounds are cut out automatically; coloured ones
are kept because they are part of the brand.

### A new app does not appear
AURA rescans when it comes back to the front. Press Home, or open **All apps**. Only apps that declare a
TV launcher entry (`LEANBACK_LAUNCHER`) are listed, which is what the TV's own launcher shows too.

### No Inputs button
The device reports no TV inputs or tuner (normal for streaming boxes such as Chromecast or Shield).

### Navigation feels slow
Turn **Camera motion** off (it is off by default). On a TV measured during development, continuous
animation was limited to about 43 frames per second by the TV itself; with nothing moving, AURA draws no
frames at all.

### My own wallpapers do not show up
Copy JPG, PNG or WebP files into the folder shown at the bottom of **Settings → Wallpaper**, then reopen that
screen. Very large images are scaled down when loaded.
