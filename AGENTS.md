# Project guidance

## Current milestone

`v0.1.0` is a read-only HyperOS 3 navigation diagnostics module. Do not alter
settings, overlays, hook return values, navbar visibility, or gesture input in this
milestone.

## Architecture

- Keep the static LSPosed scope limited to `com.android.systemui` until device logs
  prove that `system_server` is required.
- Preserve the user's independently installed MiuiBackGestureHook 0.4.0 behavior.
- Keep diagnostics in LSPosed module logs through `XposedModule.log`; avoid direct
  `android.util.Log` writes.
- Use modern libxposed API 102.
- Prefer small Java hooks with graceful class/method absence handling.
- Do not use `KEYCODE_HOME` as the final Home implementation; the intended path is
  SystemUI → WM Shell → RecentsAnimation/Overview.

## Verification

Build with `./gradlew.bat assembleDebug` on Windows and inspect the APK for
`META-INF/xposed/java_init.list`, `module.prop`, and `scope.list`.
