# Storm Runtime Naruto — Compatibility Audit

Audit updated after adding the Android native surface probe and a standalone Windows PE32 test-program build. Target device: Infinix Hot 50 4G, Helio G100, Mali-G57 MC2, Android 14, ARM64.

## Repository and CI

- Branch: `main`.
- APK build workflow: [Android APK Build](https://github.com/suksesjt12-source/StormRuntimeNaruto/actions/workflows/android-build.yml).
- PE32 cross-build workflow: [Windows PE32 Probe Build](https://github.com/suksesjt12-source/StormRuntimeNaruto/actions/workflows/windows-pe32-probe.yml).
- Last known fully verified APK artifact before the CMake variable-name hardening: commit `c61a59d5f5f2feb8b009cad48b96300c80090e04`, run [45695581298](https://github.com/suksesjt12-source/StormRuntimeNaruto/actions/runs/45695581298), artifact `storm-runtime-naruto-debug`, ID `15061101613`, size 1,121,069 bytes, artifact SHA-256 `b91086c479ebff6c021ae8fd9839911e1044d54d7dabce188711f128de3e0a36`.
- PE32 probe cross-build: commit `c61a59d5f5f2feb8b009cad48b96300c80090e04`, run [45695581343](https://github.com/suksesjt12-source/StormRuntimeNaruto/actions/runs/45695581343), artifact `storm-windows-pe32-probe`, ID `15059459837`, size 103,636 bytes, ZIP SHA-256 `0c63069ed7dcb410551e6690ca1c2e19c8727d46596d1fb4c06ee20f52ec3a1c`.
- The APK workflow verifies package ID `id.stormruntime.naruto`, ABI `arm64-v8a`, `lib/arm64-v8a/libstormcore.so`, records the APK SHA-256, and uploads the APK.
- The PE32 workflow compiles the test source using i686 MinGW-w64, verifies PE32/i386 format and uploads the executable plus inspection output. Compilation is not execution.

## Android application and JNI

The Android app is a Kotlin Activity with an ARM64 JNI library. Existing functionality includes Android Storage Access Framework folder selection and PE header inspection for the root-level `NSUNSR.exe`. PE header inspection checks MZ, PE signature, machine `0x014c`, and optional-header magic `0x010b`; it does not execute the file.

The native library currently provides:
- JNI library loading and explicit capability/status messages.
- Vulkan instance and physical-device discovery.
- Android `SurfaceView` lifecycle callbacks.
- JNI conversion from Java `Surface` to `ANativeWindow`.
- Temporary `VkSurfaceKHR` creation using `VK_KHR_android_surface`.
- Graphics-queue presentation-support and surface-capabilities queries.
- Window reference management across surface attach/detach.

The surface probe destroys its temporary Vulkan surface and instance after inspection. It does **not** create a swapchain, acquire/present frames, create a D3D9 device, or render game content. No physical-device run on the Infinix/Mali target has been recorded yet.

## Hangover/Wine audit

Primary source: https://github.com/AndreRH/hangover

The upstream Hangover README describes running x86_32 and x86_64 Windows applications on AArch64 Wine using emulator DLLs at the WoW64 boundary. Its documented i386 options include:
- `wowbox64.dll` for Box64-based i386 emulation.
- `libwow64fex.dll` for FEX-based i386 emulation, selected with `HODLL=libwow64fex.dll`.

For ARM64EC/x86_64 emulation it documents `libarm64ecfex.dll`. This makes Hangover's WoW64 integration a credible candidate for PE32 execution; Box86 alone is not a Windows PE loader.

Termux recipe inspected: https://github.com/termux/termux-packages/blob/master/x11-packages/hangover-wine/build.sh

The recipe pins a Wine/Hangover 11.16 source/archive pair with SHA-256 values and builds for `i386,aarch64,arm64ec`. It installs `wowbox64`, `libwow64fex`, and `libarm64ecfex`. However, the recipe explicitly sets `enable_wineandroid_drv=no` and depends on Termux-specific libraries and a Linux desktop-oriented stack, including XCB/XRandR, SDL2, PulseAudio and Vulkan. It is not an Android Activity display driver or an NDK-ready APK dependency bundle. It cannot simply be copied into this app.

The current repository has **not** built or packaged Wine/Hangover, loaded a Wine library, created a Wine prefix, or initialized a Windows process. No Android-native Wine integration claim is made.

## Windows PE32 test program

Source: `tests/windows-pe32/pe32_probe.c`.

The program is an original, small test utility that calls Win32 APIs including `GetTempPathA`, `CreateFileA`, `GetCurrentProcessId`, `GetTickCount`, `WriteFile`, and `OutputDebugStringA`. It writes a diagnostic log in the Windows temporary directory and is designed to return exit code `37` on success (20–23 on its own error paths).

The dedicated GitHub Actions workflow successfully compiled it as PE32/i386 and verified its executable format. It has **not** been run under Wine/Hangover. Therefore Windows API execution, log capture from an actual Windows process, and exit-code observation remain unverified.

## DXVK / Direct3D 9

Hangover's upstream README notes that DXVK requires a Vulkan 1.3 driver with required features. A Vulkan instance or physical-device enumeration alone does not establish this. The repository does not yet package DXVK, create a Vulkan swapchain, initialize a D3D9 device, or validate required device extensions/features on Mali-G57 MC2.

## Audio, input, and process management

No Wine process manager is integrated. There is no actual Windows process launcher, process handle, wait/status/exit-code bridge, Wine prefix manager, or Wine log capture in the Android app. No Windows audio API forwarding to AAudio/OpenSL ES is implemented. No keyboard, mouse, touchscreen, or gamepad events are forwarded to a Windows process. The launcher must continue to report these capabilities as unavailable.

## Evidence-based test matrix

| Test | Status | Evidence / reason |
|---|---|---|
| A. APK build | PASS on commit `c61a59d`; newer CMake hardening build pending | Android run 45695581298 uploaded an APK artifact. Confirm the final run for the current HEAD before treating it as current-head evidence. |
| B. APK install/open | NOT TESTED | No physical Android installation session or logcat. |
| C. JNI loaded on device | NOT TESTED | CI packages the ARM64 library; no device runtime log. |
| D. Vulkan device detected on Mali | NOT TESTED | Probe is implemented, but no target-device output. |
| E. Android surface/present support | NOT TESTED on device | `ANativeWindow` and `VkSurfaceKHR` probe are implemented; no physical-device result and no swapchain. |
| F. Wine/Hangover initialized | BLOCKED | No Android-compatible Wine build, packaging, prefix, or display driver. |
| G. Windows PE32 executed | BLOCKED | Test EXE is compiled, but no Wine/Hangover runtime is integrated. |
| H. Windows API called by PE32 | BLOCKED | Calls exist in test source; runtime execution has not occurred. |
| I. Exit code/error captured from PE32 | BLOCKED | Test program defines known codes; no Windows process host exists to observe them. |
| J. D3D9 initialized | BLOCKED | No Wine + DXVK + swapchain integration. |
| K. Audio/input forwarded | BLOCKED | No Windows-to-Android bridge. |
| L. NSUNSR.exe launched | BLOCKED | No Windows PE runtime/process launcher. |
| M. Main menu shown | NOT TESTED | Game cannot currently be launched by this app. |
| N. Gameplay | NOT TESTED | No end-to-end game run. |

## Next engineering sequence

1. Verify the latest APK build on the exact current HEAD and retain its artifact/SHA-256.
2. Install the APK on the target phone; capture JNI, Vulkan device, Android surface, and present-support diagnostics.
3. Choose and prototype the Android-native Wine display driver path. The inspected Termux package disables Wine's Android driver, so a direct package transplant is not a solution.
4. Build Wine/Hangover and WoW64 components for the intended Android/Bionic environment with pinned source/toolchain checksums and license notices.
5. Load the runtime from the APK and initialize a prefix before attempting the PE32 probe.
6. Execute `storm-pe32-probe.exe` under the packaged runtime and verify its log plus exit code 37.
7. Only then integrate DXVK, swapchain presentation, D3D9, audio/input forwarding, and finally the user's legally obtained game files.

Do not mark any later stage PASS based solely on source code or a successful APK build.
