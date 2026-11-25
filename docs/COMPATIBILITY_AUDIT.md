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

## Latest HEAD verification (2026-11-26)

- Current `main` HEAD: `1785d892191afab604269c2d2766c949fb56635f`.
- Android APK workflow run: [45695915175](https://github.com/suksesjt12-source/StormRuntimeNaruto/actions/runs/45695915175), conclusion `success`, built from the exact HEAD above.
- APK artifact: `storm-runtime-naruto-debug`, artifact ID `15061880365`, size 1,121,084 bytes, expires 2027-02-23.
- APK SHA-256 (computed by the workflow): `ca8f2ab769e45e27fc7da3f214916c0806fd41fd8f13893faf0b45af4554ae30`.
- The workflow log confirms `BUILD SUCCESSFUL`, package `id.stormruntime.naruto`, ABI `arm64-v8a`, and `lib/arm64-v8a/libstormcore.so` in the APK.
- Artifact archive digest reported by GitHub: `sha256:10fcfac5dc90d36f1a21f2a69342c244a61718cf9da25377b9919dac2e6dbb04` (this is the ZIP artifact digest, not the APK SHA-256 above).
- APK artifact download: https://github.com/suksesjt12-source/StormRuntimeNaruto/actions/runs/45695915175/artifacts/15061880365

## Evidence-based test matrix

| Test | Status | Evidence / reason |
|---|---|---|
| A. APK build | PASS | Commit `1785d892191afab604269c2d2766c949fb56635f`; run `45695915175`; Ubuntu GitHub Actions; APK SHA-256 recorded above. |
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


---

## Current audit addendum — 2026-11-26

This addendum supersedes older branch-head and Vulkan statements above where they differ.

### Current repository heads and CI

- `main`: `c4f27f4bef670914de8d72a7df9cce13677a6af8` at the start of this audit; APK workflow run [45701032445](https://github.com/suksesjt12-source/StormRuntimeNaruto/actions/runs/45701032445) completed successfully.
- `experiment/android-vulkan-swapchain`: `9d5eba1fcc5268215340f9a14b44e061b63f31b1` at the start of this audit; experimental APK workflow run [45700990064](https://github.com/suksesjt12-source/StormRuntimeNaruto/actions/runs/45700990064) completed successfully.
- Documentation commits made after that snapshot are on `main`; these do not add Wine/Hangover runtime functionality.

### Experimental Vulkan branch clarification

The experimental branch goes beyond the earlier surface-only probe: it creates a swapchain, image views, a render pass, framebuffers, records a clear-only render pass, submits it, and presents a single solid-color frame. It is still only a one-frame clear/present experiment. There is no game draw pipeline, persistent renderer, swapchain recreation implementation verified under lifecycle changes, DXVK, D3D9, or Wine connection. Physical-device execution on the target phone remains unverified.

### Hangover source audit

The pinned upstream reference inspected is Hangover `hangover-11.16` (release commit prefix `56e385b`). Its compile guide documents a Linux-hosted Wine build, llvm-mingw cross tools, submodules, and separate Box64/FEX WoW64 DLL build paths. It does not document a drop-in Android NDK application runtime.

The current Termux package recipe was inspected directly. It pins 11.16, builds `i386,aarch64,arm64ec`, and installs the WoW64 emulator DLLs. Critically, it sets `enable_wineandroid_drv=no` and uses Termux package dependencies and installation paths. Therefore it cannot be treated as an Android Activity display driver or copied into this APK unchanged.

A Wine Android driver patch series (MR10569 v15, posted May 25, 2026) proposes separating the Android UI process from the Wine process, citing seccomp and Wine thread/signal-context concerns. This is a patch proposal, not proof that the pinned Hangover release contains the required Android process/display architecture. See [the dedicated porting audit](ANDROID_WINE_HANGOVER_PORTING_AUDIT.md).

### Current evidence-based status

| Capability | Status |
|---|---|
| Android APK CI build and ARM64 JNI packaging | PASS in GitHub Actions |
| PE32/i386 probe cross-compilation | PASS in existing workflow; compilation only |
| Experimental one-frame Vulkan clear/present | COMPILED in experimental branch; target-device execution unverified |
| Wine/Hangover Bionic runtime in APK | NOT IMPLEMENTED |
| Wine Android display/process integration | NOT IMPLEMENTED |
| Wine prefix and Windows process manager | NOT IMPLEMENTED |
| PE32 probe executed with exit code 37 | NOT IMPLEMENTED |
| DXVK/D3D9, audio/input forwarding | NOT IMPLEMENTED |
| Original `NSUNSR.exe` launch and gameplay | NOT IMPLEMENTED |

Do not report the app as game-capable until the packaged Android runtime executes the PE32 probe on a physical device and verifies its log plus exit code 37.
