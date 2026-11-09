# Compatibility

**Minimum:** Android 8.0 (API 26), the first Android TV release with adaptive icons and the TV app APIs
AURA uses. **Target:** API 30. Android 9 and newer also get colour-tinted card shadows; Android 8 shows the
standard grey shadow.

"Verified" means installed and exercised by the maintainer: start, home screen, D-pad navigation through
the icons and app row, Settings, Wallpaper, App order and All apps, no crashes in the log.

## Matrix

| Device | Android | AURA version | Status | Notes |
|---|---|---|---|---|
| Android TV emulator (`android-tv` x86 image) | 8.0 / API 26 | 1.1.0-beta.1 | ✅ Verified | Release APK installs and runs; home switch and undo verified |
| Google TV emulator (`google-tv` x86 image) | 14 / API 34 | 1.1.0-beta.1 | ✅ Verified | Release APK installs and runs; home switch and undo verified. The emulator's Play Protect check had to be turned off for ADB installs |
| Android TV set, MediaTek m7322 SoC | 11 / API 30 | 1.0 (private build) | ✅ Daily use | One week without crashes on 1.0; 1.1.0-beta.1 not yet installed on hardware |
| TCL Android TV / Google TV | 9–12 | – | ❓ Unverified | Community testing needed |
| Sony Bravia (Android TV / Google TV) | 9–12 | – | ❓ Unverified | Community testing needed |
| Philips Android TV | 9–11 | – | ❓ Unverified | Community testing needed |
| Xiaomi TV / Mi Box | 9–11 | – | ❓ Unverified | Community testing needed |
| Hisense Android TV | 9–11 | – | ❓ Unverified | Community testing needed |
| Nvidia Shield TV | 11 | – | ❓ Unverified | Community testing needed |
| Chromecast with Google TV | 12–14 | – | ❓ Unverified | Community testing needed; expect to need the ADB home steps |

Not supported: Samsung (Tizen), LG (webOS), Amazon Fire TV (no Android TV home intent), phones and tablets.

## How AURA adapts to the device

AURA checks what the device can do instead of assuming a brand:

| Feature | Detection | When unavailable |
|---|---|---|
| Inputs button | The device declares `android.software.live_tv` and lists visible passthrough inputs, or a known tuner app is installed | The button is not shown |
| Live TV entry | First installed of a short list of tuner apps (`com.google.android.tv`, `com.android.tv`, `com.mediatek.wwtv.tvcenter`, `com.sony.dtv.tvx`, `com.tcl.tv`) | Not listed |
| Switching to an input | Standard `TvContract` passthrough intent; a known source-picker app as fallback | The TV's own input menu |
| Recently used | Usage-stats permission granted | Only apps opened from AURA |
| Wi-Fi name | Android reveals the SSID | "Wi-Fi connected" / "Wired connection" |
| Coloured shadows | API 28+ | Grey shadows |
| Default-home screen | `Settings.ACTION_HOME_SETTINGS` exists | Opens the main Settings app |

## Reporting a device

Open a **Device report** issue with the model, Android version, whether AURA installed, whether it could
become the home screen, and what did or did not work. Reports move devices from "unverified" to
"community verified".
