# V13 Execution Status

Updated: 2026-11-26

## Repository state

- Repository: `suksesjt12-source/StormRuntimeNaruto`
- Runtime branch: `experiment/wine-hangover-android`
- Stable branch `main` was not modified by this status update.
- Runtime branch head known from the latest completed APK workflow: `bab21a79a44348c163af37eb76ab026a4bc6c594`.

## Verified CI

### Android APK build

- Workflow run: https://github.com/suksesjt12-source/StormRuntimeNaruto/actions/runs/45710540192
- Result: completed / success.
- Commit: `bab21a79a44348c163af37eb76ab026a4bc6c594`
- Jobs: Android Gradle build, APK package/ABI/JNI verification, artifact upload all succeeded.
- Verified package: `id.stormruntime.naruto`
- Verified ABI: `arm64-v8a`
- Verified JNI library: `lib/arm64-v8a/libstormcore.so`
- Artifact: https://github.com/suksesjt12-source/StormRuntimeNaruto/actions/runs/45710540192/artifacts/15066864340
- Artifact SHA-256 digest: `31e6dd199dc1e3752ad0e39dcca738b9b58880a8d79a92a5231a19bec54b5e10`

This proves APK compilation and package structure only. It does not prove that JNI loads on a physical device, nor that Wine, Hangover, PE32, DXVK, audio, input, or the game works.

### Native host test

- Workflow run: https://github.com/suksesjt12-source/StormRuntimeNaruto/actions/runs/45709304132
- Result: CMake build and CTest passed 1/1 on Ubuntu 24.04.
- This is a host-side test, not an Android device test.

## Implementation inventory

| Subsystem | Status | Evidence / blocker |
|---|---|---|
| Android Kotlin host | BUILD PASSED | APK workflow succeeds; physical-device lifecycle test is still required |
| JNI native host | BUILD PASSED | JNI library is packaged; physical-device JNI load is not verified |
| Wine ARM64 Android/Bionic | NOT STARTED | No Android/Bionic Wine runtime is present in the app |
| Hangover WoW64 | NOT STARTED | No translator DLL or WoW64 runtime is integrated |
| PE32 execution | NOT STARTED | PE32 probe is cross-compiled only; no Android execution result |
| Vulkan | NOT STARTED | Existing native code is diagnostic/probe code; no verified continuous renderer |
| DXVK / D3D9 | NOT STARTED | No DXVK integration or D3D9 test rendering |
| Audio | NOT STARTED | No Windows-to-Android audio path |
| Input | NOT STARTED | No Windows input API forwarding |
| Naruto executable | NOT STARTED | `NSUNSR.exe` has not been launched by this runtime |

## Required next gates

1. Install the current APK on the target Android device and record JNI initialization, surface lifecycle, pause/resume, and shutdown logs.
2. Select and pin a reproducible Android/Bionic Wine build strategy. A Linux/glibc or Termux package must not be treated as a drop-in APK runtime.
3. Build and load the Wine runtime on Android before attempting Hangover integration.
4. Integrate one Hangover WoW64 translator and execute the PE32 probe on-device.
5. Validate the graphics path independently: Vulkan swapchain and repeated presentation, then D3D9 through DXVK.
6. Implement and test audio and input bridges against a running Windows test process.
7. Attempt `NSUNSR.exe` only after the independent tests pass.

## Integrity rule

Do not mark a subsystem as passed based on source files, successful cross-compilation, APK packaging, header inspection, or a Linux-only test. Device and integration status require corresponding Android runtime evidence.
