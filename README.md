# LauncherPC — Android ARM64 Windows Compatibility Runtime Prototype

LauncherPC is the product name for this Android ARM64 project. The project targets the original Windows x86 (32-bit) executable NSUNSR.exe from Naruto Shippuden: Ultimate Ninja Storm Revolution. LauncherPC owns the Android UI and control plane; upstream runtime components are integrated separately.

## Target devices

- MediaTek Helio G99 / G100
- Mali-G57 MC2
- Android 14, ARM64, 8 GB RAM
- Initial performance test target: 960x540 at 30 FPS (not a performance guarantee)

## Implemented in source

- Kotlin Android launcher in landscape mode.
- Storage Access Framework game-folder picker and basic PE32/i386 header inspection.
- JNI native library and Android Vulkan device diagnostics.
- ARM64-only Gradle configuration, JVM 17 target alignment, and pinned Android NDK.
- GitHub Actions APK build, package verification, checksum, and artifact upload.
- Experimental Vulkan clear/present test (not a game renderer).
- Custom runtime control-plane service hosted in a separate Android process.
- Messenger IPC between launcher and runtime core.
- Glibc runtime bundle manifest validation.
- PRoot smoke-test supervisor with AArch64 ELF checks, bounded execution, timeout, and launcher control.
- Debian Bookworm ARM64 Glibc rootfs definition and CI artifact workflow.

## Runtime status — NOT GAME-CAPABLE YET

**The current APK does not yet run Windows executables.**

The selected architecture is hybrid: LauncherPC UI and host processes use Android/Bionic; the Linux guest uses a separate ARM64 glibc userspace. Do not load glibc and Bionic libraries into the same process. PRoot/rootfs smoke-test foundations exist, but the current APK does not yet bundle and install a complete PRoot + rootfs + Wine/Hangover + Box64 + graphics runtime.

The current source does not yet demonstrate:
- Wine/Hangover startup in the guest.
- A complete i386 WoW64 execution path for PE32.
- A working Android Wine display and Vulkan presentation bridge.
- DXVK/D3D9 execution on Mali-G57.
- Windows audio and input forwarding.
- End-to-end execution of NSUNSR.exe.

A Vulkan probe or clear/present test is not proof of DXVK or Direct3D compatibility. A successful Android APK build is not proof of Windows game support.

## Winlator Mali integration direction

The LauncherPC-specific integration contract is documented in [LauncherPC Winlator Mali architecture](docs/LAUNCHERPC_WINLATOR_MALI_ARCHITECTURE.md). A version candidate manifest is stored at runtime/launcherpc/components.json.

The plan uses Winlator Mali Bionic 1.2.1 as a source reference for the Mali Wrapper and BCn emulation approach, and Box64 0.4.4 as a candidate baseline. The selected DXVK component is **DXVK-Sarek v1.11.0 (Red River)**, pinned to tag `v1.11.0` and source commit prefix `7c0e3b2`. This is an unofficial continuation of the DXVK 1.10.x line, not an official upstream DXVK 1.11 release. The pinned source is not yet part of the Android APK.

A dedicated GitHub Actions workflow now builds the Sarek 1.11.0 Win32 and Win64 DLL sets from the pinned source tag and uploads them as a separate component artifact. The build workflow has been added but has not yet been observed completing successfully. For Mali, the Wrapper + ASTC/ETC2 route remains the separate fallback path. DXVK-Sarek is selected only after the Vulkan 1.1, swapchain, graphics queue, Android surface, and presentation checks pass.

## Runtime architecture and status

- [LauncherPC Winlator Mali integration contract](docs/LAUNCHERPC_WINLATOR_MALI_ARCHITECTURE.md)
- [Compatibility audit](docs/COMPATIBILITY_AUDIT.md)
- [Android Wine/Hangover porting audit](docs/ANDROID_WINE_HANGOVER_PORTING_AUDIT.md)
- [Glibc ARM64 sysroot](docs/GLIBC_ARM64_SYSROOT.md)
- [Debian ARM64 Glibc rootfs](docs/STORM_ROOTFS.md)

The rootfs workflow builds a minimal Debian Bookworm ARM64 userspace as a separate artifact. It is not evidence that the rootfs, PRoot, Wine, Hangover, Box64 WoW64, DXVK, Android Vulkan bridge, or game files are included in the APK.

## Performance policy

- Start at 960x540 and 30 FPS.
- Use per-game renderer selection based on device capability probes.
- Keep CPU affinity at Android scheduler defaults until device measurements justify a change.
- Use app-private, bounded, versioned shader/texture caches.
- Monitor frame time, memory use, sustained FPS, and battery temperature.
- Do not use privileged clock forcing or thermal overrides.
- Do not claim stable performance until tested on physical Helio G99 and G100 devices.

## Licensing and game files

Use only game files and runtime components you are authorized to use. This repository does not include Naruto game assets or proprietary runtime binaries. Any third-party runtime integration must preserve upstream licenses, notices, and source-offer obligations.
