# Performance

Speed is a feature of AURA OS. This page lists what has been measured, how, and what has not.

## Design rules

- Nothing moves when you are not using the remote: the home screen draws **zero frames** at idle.
- The wallpaper's darkening grade is drawn into the photo once when it loads, so each frame fills the
  screen once instead of stacking full-screen overlays.
- Photos are decoded off the main thread at screen size in RGB_565; app logos are extracted once and cached
  as small PNGs.
- No network access, no background services, no libraries.
- Camera motion (continuous animation) is off by default.

## Baseline: reference TV

Measured on 2026-11-09 with **AURA OS 1.0**, the private build that 1.1 grew from (same rendering pipeline;
1.1 removes code but adds no rendering work).

| | |
|---|---|
| Device | Android TV set with a MediaTek m7322 SoC, Android 11, 1920×1080 at 60 Hz, ~1.3 GB RAM visible to Android |

| Measurement | Camera motion off (default) | Camera motion on |
|---|---|---|
| Cold start (`am start -W`, TotalTime) | **1 189 ms** | 1 315 ms |
| Navigation, median frame time | **8 ms** | 23 ms |
| Navigation, 90th percentile | 27 ms | 28 ms |
| Navigation, slow UI-thread events | 14 | 7 |
| Idle 10 s, frames drawn | **0** | 615 |

With camera motion on, this TV caps continuous animation at about 43 fps whatever the app draws; that is
why it is off by default. Before the wallpaper-grade optimisation the same test gave a 38 ms median, 100 %
janky frames and 420 frames drawn at idle.

## Emulator checks for 1.1.0-beta.1

Cold start on the Android TV 8.0 emulator (x86, software rendering): 972 ms (debug) and 945 ms (release).
Emulator timings depend on the host computer and are **not** comparable with TV hardware.

## Not measured yet

- 1.1.0-beta.1 frame times on real TV hardware.
- Any device other than the reference TV.
- Memory over days of use, and power draw.

## Method

1. `adb shell am force-stop io.github.saksen2k1.aura`, then
   `adb shell am start -W -n io.github.saksen2k1.aura/.MainActivity` and read `TotalTime`.
2. Wait 5 s, run `adb shell dumpsys gfxinfo io.github.saksen2k1.aura reset`.
3. Send 20 key presses 350 ms apart: `→ → → → → → → ← ← ← ↓ → ↑ ← ← ← ← ↑ → ↓`
   (`adb shell input keyevent KEYCODE_DPAD_RIGHT` and so on).
4. `adb shell dumpsys gfxinfo io.github.saksen2k1.aura` gives the navigation numbers.
5. `reset` again, wait 10 s without input, read again for idle.

Please include these numbers in device reports when you can.
