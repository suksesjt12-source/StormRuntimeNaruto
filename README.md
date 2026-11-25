# Storm Runtime Naruto — Android ARM64 Compatibility Runtime Prototype

This repository is an experimental Android ARM64 project targeting the original Windows x86 (32-bit) executable `NSUNSR.exe` from Naruto Shippuden: Ultimate Ninja Storm Revolution.

## Implemented in source

- Kotlin Android launcher in landscape mode.
- Storage Access Framework game-folder picker.
- Basic PE header inspection to distinguish Windows PE32/i386 from other executable formats.
- JNI native library and Android Vulkan device diagnostics.
- ARM64-only Gradle configuration, JVM 17 target alignment, and pinned Android NDK.
- GitHub Actions APK build, package verification, and artifact upload workflow.

## Runtime status — NOT GAME-CAPABLE YET

**The app does not currently run Windows executables.**

The preferred runtime research candidate is [Hangover](https://github.com/AndreRH/hangover), which combines ARM64 Wine WoW64 with emulator DLLs for i386 and x86_64 Windows applications. The Termux package recipe demonstrates an Android ARM64 packaging route, but currently builds a Wine configuration using X11 and disables Wine's Android display driver. It is not directly embeddable into this native Activity without additional display, input, audio, and filesystem integration.

Box86 is not being treated as a standalone Windows executable translator: upstream Box86 targets x86 Linux userspace and requires a 32-bit ARM host userspace. See [the compatibility audit](docs/COMPATIBILITY_AUDIT.md) for the architecture comparison.

Still not integrated:
- Wine/Hangover runtime and Wine prefix.
- i386 WoW64 translator DLL (FEX or Box64 WOW64).
- D3D9/DXVK with a validated Android Vulkan presentation path.
- Windows audio and input forwarding.
- Actual Windows process management.
- End-to-end execution of `NSUNSR.exe`.

The Vulkan probe does not prove DXVK or Direct3D 9 compatibility. Upstream DXVK does not provide first-party support for Android/mobile graphics drivers.

## CI build

Pushes to `main` and manual workflow dispatch run the Android debug APK workflow. A successful APK build verifies packaging and compilation only; it does not verify runtime or game execution.

## Target device

- Infinix Hot 50 4G
- MediaTek Helio G100 / Mali-G57 MC2
- Android 14, ARM64, 8 GB RAM
- Initial performance test target: 960×540 at 30 FPS (not a performance guarantee)

## Licensing and game files

Use only game files and runtime components you are authorized to use. This repository does not include Naruto game assets or proprietary runtime binaries. Any future third-party runtime integration must preserve upstream licensing and required notices.
