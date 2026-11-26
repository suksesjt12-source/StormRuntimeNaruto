# Storm Runtime Naruto — Android ARM64 Compatibility Runtime Prototype

This repository is an experimental Android ARM64 project targeting the original Windows x86 (32-bit) executable `NSUNSR.exe` from Naruto Shippuden: Ultimate Ninja Storm Revolution.

## Implemented in source

- Kotlin Android launcher in landscape mode.
- Storage Access Framework game-folder picker and basic PE32/i386 header inspection.
- JNI native library and Android Vulkan device diagnostics.
- ARM64-only Gradle configuration, JVM 17 target alignment, and pinned Android NDK.
- GitHub Actions APK build, package verification, checksum, and artifact upload.
- Experimental Vulkan clear/present test (not a game renderer).
- Custom Storm Core control-plane service hosted in a separate Android process (`:storm_runtime`).
- Messenger IPC between launcher and Storm Core.
- Glibc runtime bundle manifest validation.
- PRoot smoke-test supervisor with AArch64 ELF checks, bounded execution, timeout, and launcher control; requires a separately supplied Bionic PRoot binary and installed rootfs.
- Debian Bookworm ARM64 Glibc rootfs definition, Buildx/QEMU CI workflow, ELF ABI validation, package inventory, and checksummed rootfs artifact workflow.

## Runtime status — NOT GAME-CAPABLE YET

**The app does not currently run Windows executables.**

The selected architecture is Hybrid Custom: Storm Core owns process orchestration, configuration, component validation, and IPC; Wine/Hangover, Box64 WoW64, and DXVK remain upstream compatibility components to be built and integrated.

The Android launcher and Android-side host use Bionic. GNU Glibc is intended for a separate AArch64 Linux userspace. Do not load Glibc and Bionic libraries into the same process. The Storm Core service is currently a control plane; it does not yet start PRoot, Wine, Hangover, Box64, or a Windows process.

## Runtime architecture and status

- [Storm Core architecture](docs/STORM_CORE_ARCHITECTURE.md)
- [Glibc ARM64 sysroot](docs/GLIBC_ARM64_SYSROOT.md)
- [Debian ARM64 Glibc rootfs](docs/STORM_ROOTFS.md)
- [Storm PRoot host — Stage 3](docs/STORM_PROOT_HOST.md)
- [Compatibility audit](docs/COMPATIBILITY_AUDIT.md)
- [Android Wine/Hangover porting audit](docs/ANDROID_WINE_HANGOVER_PORTING_AUDIT.md)

The rootfs workflow builds a minimal Debian Bookworm ARM64 userspace and uploads it as a separate artifact. It is not included in the APK and is not yet installed on the phone. A successful rootfs workflow proves archive assembly and ELF architecture checks on CI only.

The rootfs does not contain PRoot, Wine, Hangover, Box64 WoW64, DXVK, an Android Vulkan/display bridge, or game files. PRoot must be built or selected as an Android-compatible Bionic executable and validated against Android 14 ptrace/system-call behavior before it can host the Glibc userspace.

Box86 is not treated as a standalone Windows executable translator: upstream Box86 targets x86 Linux userspace and requires a 32-bit ARM host userspace.

## Still not integrated

- Build and package an Android/Bionic-compatible PRoot ARM64 host, then validate ptrace/syscall behavior on Android 14.
- Rootfs installer and lifecycle management in app-private storage.
- Wine/Hangover runtime and Wine prefix.
- Matching i386 WoW64 translator.
- Android Wine display driver and actual Windows process supervision.
- D3D9/DXVK with validated Android Vulkan presentation.
- Windows audio and input forwarding.
- End-to-end execution of `NSUNSR.exe`.

The experimental Vulkan path is not a game renderer. A Vulkan probe or clear/present test does not prove DXVK or Direct3D 9 compatibility. Required Vulkan features must be checked on the actual Mali-G57 device.

## CI workflows

- Android debug APK workflow: launcher compilation and packaging only.
- Experimental Glibc sysroot workflow: cross-built Glibc sysroot artifact.
- Storm ARM64 rootfs workflow: Debian Bookworm ARM64 rootfs archive and package inventory.

A successful Gradle build, Glibc sysroot build, or rootfs build is not evidence of Windows executable support.

## Target device

- Infinix Hot 50 4G
- MediaTek Helio G100 / Mali-G57 MC2
- Android 14, ARM64, 8 GB RAM
- Initial performance test target: 960×540 at 30 FPS (not a performance guarantee)

## Licensing and game files

Use only game files and runtime components you are authorized to use. This repository does not include Naruto game assets or proprietary runtime binaries. Any future third-party runtime integration must preserve upstream licensing and required notices.
