# Installing AURA OS

AURA OS is an ordinary app. Installing it changes nothing on your TV until **you** choose to make it the
home screen, and every step below can be undone.

## 1. Get the APK

Download `AURA-OS-v<version>.apk` and `AURA-OS-v<version>.apk.sha256` from the
[releases page](https://github.com/Saksen2k1/aura-os/releases). Check the file:

```bash
sha256sum -c AURA-OS-v1.1.0-beta.1.apk.sha256           # Linux
shasum -a 256 AURA-OS-v1.1.0-beta.1.apk                 # macOS, compare by eye
```

```powershell
Get-FileHash AURA-OS-v1.1.0-beta.1.apk -Algorithm SHA256   # Windows, compare by eye
```

Release APKs are signed with the AURA OS release key; its certificate SHA-256 is
`af99d1a0c4710225cadf670450c6fd5b07e5c32fecfccf58e03255be1baa7def`
(check with `apksigner verify --print-certs`).

## 2. Install it

**On the TV**: copy the APK to a USB stick or use an app such as a file manager or "Send files to TV",
open it, and allow that app to install unknown apps when Android asks.

**From a computer over ADB**:

1. On the TV: Settings → Device Preferences (or System) → About → press OK on **Build** seven times.
2. Back in Device Preferences → Developer options → turn on **USB debugging** (and **Network debugging** if
   offered).
3. Find the TV's IP address under Network & Internet, then on the computer:

```bash
adb connect <tv-ip>:5555        # accept "Allow USB debugging?" on the TV
adb install AURA-OS-v1.1.0-beta.1.apk
```

AURA OS now appears in the TV's app list. Open it to try it out.

## 3. Optional: make it the home screen

Press **Home**. If the TV asks which app to use, pick **AURA OS** and choose "Always".
Some TVs also have the setting under Settings → Apps → Default apps → Home app; AURA's own
**Settings → Default home app** opens that screen where the TV provides it.

### TVs that keep their own launcher (Google TV and many Android TV builds)

On these, the built-in launcher always wins the Home button. The only way to change that without root is
to **disable** (not uninstall) the built-in launcher over ADB. This is fully reversible.

Find the launcher's package:

```bash
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

Common ones: `com.google.android.apps.tv.launcherx` (Google TV) and `com.google.android.tvlauncher`
(Android TV). Then:

```bash
adb shell pm disable-user --user 0 <launcher-package>
adb shell cmd package set-home-activity io.github.saksen2k1.aura/.MainActivity
# Google TV only, if the package exists: its setup app can also take over the Home button
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
```

Press Home. Tested on the Android TV 8.0 and Google TV 14 emulators.

> Disabling the built-in launcher also hides what it provides, such as the Google TV recommendations
> screen. Your apps, accounts and settings are untouched.

## Going back to the built-in launcher

```bash
adb shell pm enable <launcher-package>
adb shell pm enable com.google.android.tungsten.setupwraith      # if you disabled it
adb shell cmd package set-home-activity <launcher-package>/<its HOME activity>
adb shell am force-stop com.android.tv.settings
adb shell input keyevent KEYCODE_HOME
```

`<its HOME activity>` is what `resolve-activity` printed earlier, for example
`com.google.android.apps.tv.launcherx/.home.HomeActivity`. If you just uninstall AURA OS
(`adb uninstall io.github.saksen2k1.aura`), the TV falls back to any enabled launcher.

The `force-stop` line fixes a known Android quirk where Home keeps showing the Settings app after a
launcher switch.

## Optional: usage access for "Recently used"

Without it, the row lists only apps you opened from AURA. To include apps opened anywhere, open
**Settings → Usage access** in AURA and allow AURA OS. If your TV has no such screen:

```bash
adb shell appops set io.github.saksen2k1.aura GET_USAGE_STATS allow
```

The history stays on the TV; AURA has no network permission.
