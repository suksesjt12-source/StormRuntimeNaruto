# Storm Runtime Naruto — Android ARM64 Compatibility Runtime Prototype

This repository is an experimental Android ARM64 project targeting the original Windows x86 (32-bit) executable `NSUNSR.exe` from Naruto Shippuden: Ultimate Ninja Storm Revolution.

## Implemented in source

- Kotlin Android launcher in landscape mode.
- Storage Access Framework game-folder picker.
- Basic PE header inspection to distinguish Windows PE32/i386 from other executable formats.
- JNI native library and Android Vulkan device diagnostics.
- ARM64-only Gradle configuration, JVM 17 target alignment, and pinned Android NDK.
- GitHub Actions APK build, package verification, checksum, and artifact upload.
- Experimental branch: one-frame Vulkan render-pass clear and present test (not a game renderer).
- Custom Storm Core control-plane service hosted in a separate Android process (`:storm_runtime`).
- Messenger IPC between the launcher and Storm Core for runtime status and launch-gate requests.
- Glibc runtime bundle manifest validation for ARM64 ABI, Glibc identity, required payload files, executable permissions, and path traversal.

## Runtime status — NOT GAME-CAPABLE YET

**The app does not currently run Windows executables.**

The selected architecture is Hybrid Custom: Storm Core owns process orchestration, configuration, component validation, and IPC; Wine/Hangover, Box64 WoW64, and DXVK remain upstream compatibility components to be built and integrated.

The Android launcher and Storm Core service use the Android/Bionic ABI. GNU Glibc is intended for a separate AArch64 Linux userspace. Do not load Glibc and Bionic libraries into the same process. The new service is only the Android-side control plane; it does not yet start PRoot, Wine, Hangover, Box64, or a Windows process.

The preferred runtime research candidate is [Hangover](https://github.com/AndreRH/hangover), which combines ARM64 Wine WoW64 with emulator DLLs for i386 and x86_64 Windows applications. Its Linux/Termux build instructions are not a drop-in Android Activity runtime. The Android display-driver and process-host path must be ported and validated deliberately.

See:
- [Compatibility audit](docs/COMPATIBILITY_AUDIT.md)
- [Android Wine/Hangover porting audit](docs/ANDROID_WINE_HANGOVER_PORTING_AUDIT.md)
- [Storm Core architecture](docs/STORM_CORE_ARCHITECTURE.md)
- [Glibc ARM64 sysroot](docs/GLIBC_ARM64_SYSROOT.md)

Box86 is not being treated as a standalone Windows executable translator: upstream Box86 targets x86 Linux userspace and requires a 32-bit ARM host userspace.

Still not integrated:
- Complete Glibc ARM64 rootfs and Android-compatible PRoot host.
- Wine/Hangover runtime and Wine prefix.
- i386 WoW64 translator DLL matching the selected Hangover build.
- Android Wine display driver and actual Windows process supervision.
- D3D9/DXVK with a validated Android Vulkan presentation path.
- Windows audio and input forwarding.
- End-to-end execution of `NSUNSR.exe`.

The experimental Vulkan path is not a game renderer. A Vulkan probe or clear/present test does not prove DXVK or Direct3D 9 compatibility. Required Vulkan features must be checked on the actual Mali-G57 device.

## CI build

Pushes to `main` and `feature/mtk-glibc-runtime`, plus manual workflow dispatch, run the Android debug APK workflow. Changes to the Glibc build script also trigger the experimental Glibc ARM64 sysroot workflow. A successful APK build verifies compilation and packaging only; it does not verify runtime or game execution.

## Target device

- Infinix Hot 50 4G
- MediaTek Helio G100 / Mali-G57 MC2
- Android 14, ARM64, 8 GB RAM
- Initial performance test target: 960×540 at 30 FPS (not a performance guarantee)

## Licensing and game files

Use only game files and runtime components you are authorized to use. This repository does not include Naruto game assets or proprietary runtime binaries. Any future third-party runtime integration must preserve upstream licensing and required notices.
