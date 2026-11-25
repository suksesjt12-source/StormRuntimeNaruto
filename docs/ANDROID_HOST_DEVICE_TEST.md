# Android Native Host Device Test

Status: required; not yet performed.

Target test device: Infinix Hot 50 4G (X6882), Helio G100, Android 14, ARM64.

## Install and launch

1. Download the APK from the Android APK Build workflow artifact for the exact commit under test.
2. Install it on the target Android ARM64 device.
3. Launch Storm Runtime and inspect the Runtime status panel.
4. Expected host-only status: `ANDROID NATIVE HOST READY (Wine/Hangover unavailable)`.
5. Confirm the app-private directory `files/storm-runtime` is created. The transient filesystem probe is removed after read-back validation.

## Lifecycle test

1. Launch the app and record the native host status.
2. Press Home to move the Activity to the background.
3. Return to the app repeatedly; verify no crash and that the host status remains READY.
4. Rotate is locked by the current manifest configuration; do not treat that as resize/rotation validation.
5. Close the Activity/process and relaunch; verify initialization succeeds again.
6. Capture `adb logcat -s StormRuntime` output and record Android version, device model, APK SHA-256, and tested commit SHA.

## Pass criteria

- Native library loads without `UnsatisfiedLinkError`.
- JNI initialization returns true.
- Status reports native host READY.
- Worker starts and shuts down without crash.
- Filesystem initialization succeeds.
- Pause/background/resume does not crash.
- Process restart can initialize a fresh host.

This test is not yet recorded as passed. GitHub-hosted Ubuntu jobs do not substitute for this device test. It validates only the Android host, not Wine, Hangover, PE32 execution, Vulkan game rendering, or Naruto compatibility.
