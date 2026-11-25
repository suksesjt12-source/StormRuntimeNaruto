# Compatibility Audit — Storm Runtime Naruto

Audit scope: repository state at commit `685e111249a48719dbe23b4d21d8877b6d49820e`, plus the subsequent CI hardening commit that verifies the ARM64 JNI library inside the APK.

## Verified Android build

- Commit tested by the successful baseline build: `685e111249a48719dbe23b4d21d8877b6d49820e`.
- Workflow run: [45693296903](https://github.com/suksesjt12-source/StormRuntimeNaruto/actions/runs/45693296903).
- Conclusion: `success`.
- Job steps: Java 17, Android SDK packages, NDK/CMake install, Gradle build, APK verification and artifact upload all completed successfully.
- Artifact: `storm-runtime-naruto-debug`, artifact ID `15059108725`, size 1,110,636 bytes, SHA-256 `0a411e9fcb5bd24622cd6e57e62df1f576c966718560e3da1e2e9114e0b72253`.
- Artifact expiry: 2027-02-23.
- Package configured by Gradle: `id.stormruntime.naruto`.
- ABI configured by Gradle: `arm64-v8a`.

The baseline workflow checked that an APK existed and that its package ID matched. A subsequent workflow change additionally checks the APK's advertised native ABI and the presence of `lib/arm64-v8a/libstormcore.so`, and records the APK SHA-256. That strengthened workflow must itself pass on its resulting commit before those new checks are considered verified.

## Current application architecture

The application is a Kotlin Android Activity with an ARM64 JNI C++ shared library. It can request a game folder through Android's Storage Access Framework, inspect the root-level `NSUNSR.exe`, and parse its DOS/PE headers to check whether it is a Windows PE32/i386 executable. The native library queries Android Vulkan for physical devices, graphics queue families, selected device features, and the advertised swapchain extension.

Source implementation is not equivalent to a successful Android device test. The PE parser validates a header; it does not load or execute the executable. Vulkan instance/device discovery does not create an Android surface or swapchain and does not prove D3D9 compatibility.

## Hangover/Wine ARM64 audit

Hangover is the strongest candidate identified for this target because its upstream project describes running Win32 and Win64 Windows applications on ARM64 Linux using Wine and architecture-translation components.

References:
- Hangover upstream: https://github.com/AndreRH/hangover
- Termux package recipe: https://github.com/termux/termux-packages/tree/master/x11-packages/hangover-wine
- Recipe source: https://raw.githubusercontent.com/termux/termux-packages/master/x11-packages/hangover-wine/build.sh

The inspected Termux recipe currently packages Hangover Wine 11.16 and configures `--enable-archs=i386,aarch64,arm64ec`. It installs the Hangover translator DLL packages `wowbox64`, `libwow64fex`, and `libarm64ecfex`. This confirms that Box64 WOW64 and FEX-based WoW64 are actual Hangover integration paths, rather than assuming Box86 is a Windows PE loader.

Important Android integration blocker: the recipe sets `enable_wineandroid_drv=no`. It uses Termux-specific dependencies and a Linux/X11-oriented stack including XCB/XRandR, SDL2, PulseAudio and Vulkan. It is not a ready-made Android Activity runtime. Simply copying its libraries into an APK would not supply the missing Android window/display driver, Android surface lifecycle, input forwarding, audio integration, prefix setup, dynamic-library search paths, or process lifecycle management.

The recipe is also tied to Termux's Bionic environment and package layout. Its packages cannot be assumed binary-compatible with arbitrary Android NDK packaging. A proper integration requires a reproducible build against the intended Android ABI, a validated Android display path, and licensing/notice handling. No Wine/Hangover binary has been built, packaged, loaded, or initialized in this repository.

## Windows PE32/i386 execution

- The target executable is a 32-bit Windows PE, not an x86 Linux ELF.
- Box86 is an x86 Linux userspace emulator and is not, by itself, a Windows PE compatibility runtime.
- Hangover's WoW64 emulator DLL route (Box64 WOW64 or FEX) is the candidate to test after Wine ARM64 is built and initialized.
- A valid test must execute a small Windows PE32 program under the packaged Wine/Hangover runtime and capture its Windows API calls and exit status. A Linux x86 test binary is not sufficient evidence.

No Windows PE32 program has yet been executed by this app.

## Direct3D 9 / Vulkan / Mali

DXVK translates Direct3D 8/9/10/11 to Vulkan. It still requires a supported Vulkan driver and a working presentation path. The current JNI probe does not test:
- Android `VkSurfaceKHR` creation;
- presentation support for the selected graphics queue;
- swapchain creation/acquisition/presentation;
- all DXVK-required Vulkan extensions/features;
- shader compilation through the actual DXVK device;
- D3D9 device creation.

The target Mali-G57 MC2 has not been tested by this app. Upstream DXVK does not provide a blanket guarantee for Android/mobile GPU drivers. DXVK and D3D9 remain unintegrated and unverified.

Reference: https://github.com/doitsujin/dxvk and https://github.com/doitsujin/dxvk/wiki/Driver-support

## Current source-level components

| Component | State | Evidence / limitation |
|---|---|---|
| Android ARM64 launcher | Implemented in source | Kotlin Activity, Gradle ABI filter |
| JVM target | Configured | Java and Kotlin target 17 |
| Android NDK | Pinned | 27.2.12479018 |
| CMake native library | Implemented in source | `stormcore` shared library |
| JNI bridge | Implemented in source | `System.loadLibrary("stormcore")` and JNI methods |
| Game folder selection | Implemented in source | SAF tree picker |
| PE32/i386 inspection | Implemented in source | MZ, PE signature, machine `0x014c`, optional magic `0x010b` |
| Vulkan capability probe | Implemented in source | Instance, physical devices, queue family, selected features/extensions |
| Wine/Hangover | Not integrated | Android-native display/audio/input path absent |
| WoW64 translator | Not integrated | No translator DLL packaged or loaded |
| D3D9/DXVK | Not integrated | No D3D9 device or swapchain test |
| Audio/input forwarding | Not integrated | No Windows-to-Android bridge |
| Windows process manager | Not implemented | No Wine process lifecycle |
| Original game | Not tested | No launch/menu/gameplay evidence |

## Test matrix

Statuses below are deliberately evidence-based. The CI result is for the exact commit listed; physical-device and Windows-runtime tests have not been performed.

| Test | Status | Commit / environment | Evidence or blocker |
|---|---|---|---|
| A. APK build | PASS | `685e111`, GitHub Actions Ubuntu runner | Run 45693296903 completed successfully |
| B. APK install/open | NOT TESTED | Infinix Hot 50 4G / Android 14 | No device installation session or logcat |
| C. JNI library load | NOT TESTED | Target Android device | JNI source is present; no device runtime evidence |
| D. Vulkan device detection | NOT TESTED | Mali-G57 MC2 | Probe is compiled in source; no target-device output |
| E. Vulkan surface/swapchain | BLOCKED | Android surface + Vulkan | No ANativeWindow surface/swapchain implementation |
| F. Wine initialization | BLOCKED | ARM64 Android | Hangover not built or packaged; Termux recipe disables wineandroid driver |
| G. Windows PE32 x86 execution | BLOCKED | ARM64 WoW64 | No Wine runtime or WoW64 emulator DLL integrated |
| H. Direct3D 9 initialization | BLOCKED | Vulkan/Mali | DXVK and Android presentation path absent |
| I. Audio/input forwarding | BLOCKED | Android audio/input | No Windows API bridge |
| J. NSUNSR.exe launch | BLOCKED | Original game folder | No Windows process runtime |
| K. Main menu | NOT TESTED | Target Android device | Executable cannot currently be launched |
| L. Gameplay | NOT TESTED | Target Android device | No end-to-end execution |

## Required next engineering milestone

Do not add a Start Game action that implies capability. The next meaningful milestone is a separate, reproducible Hangover/Wine ARM64 build-and-load experiment, with a minimal Android-native display/input/audio strategy chosen before packaging. Only after Wine initializes should the project add and test the i386 WoW64 DLL, then a legal minimal Windows PE32 test program, then D3D9/DXVK surface presentation. The commercial game remains the final compatibility test, not the first test.
