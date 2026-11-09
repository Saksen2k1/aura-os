# Contributing to AURA OS

Thanks for helping. AURA OS aims to stay fast, small and calm, so the bar for new features is "does it make
the TV better without making it slower?".

## Ways to help

- **Device reports**: install AURA on your TV and open a *Device report* issue. This is the most valuable
  contribution right now (see [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md)).
- **Bug reports**: include model, Android version, AURA version, steps, and `adb logcat` output if it crashed.
- **Translations**: copy `app/res/values/strings.xml` to `app/res/values-<lang>/strings.xml` and translate.
- **Code**: fixes and small, focused improvements.

## Ground rules for code

- Framework-only Java (API 26+). No new libraries without a strong reason.
- No network access, analytics or background services.
- Guard any API above 26 with `Build.VERSION.SDK_INT` and give a sensible fallback.
- Detect device capabilities; do not branch on manufacturer names.
- Keep the idle home screen at **zero frames drawn**, and include before/after numbers from the method in
  [docs/PERFORMANCE.md](docs/PERFORMANCE.md) for anything that touches rendering or focus.
- User-visible text goes in string resources, in English, with a Danish translation in `values-da` if you can.
- D-pad first: every screen must be fully usable with a remote.

## Pull requests

1. Fork, create a branch, keep the change focused.
2. Build with `./build.sh` (or `.\build.ps1`) and test on a TV or the Android TV emulator.
3. Describe what you tested and on which device.

By contributing you agree that your contribution is licensed under the MIT licence of this project.
