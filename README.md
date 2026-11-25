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

## Runtime status — NOT GAME-CAPABLE YET

**The app does not currently run Windows executables.**

The preferred runtime research candidate is [Hangover](https://github.com/AndreRH/hangover), which combines ARM64 Wine WoW64 with emulator DLLs for i386 and x86_64 Windows applications. Hangover documents `wowbox64.dll` and `libwow64fex.dll` for i386 translation. However, the current Termux package recipe explicitly disables Wine's Android display driver and depends on Termux libraries and package paths. It is not directly embeddable into this native Activity.

The current Android app also runs its UI in a JVM Activity process. A Wine Android driver patch series proposes separating the Android UI and Wine processes due to Android seccomp and Wine thread/signal-context concerns. That proposal must be verified and integrated deliberately; simply loading Wine through JNI in the Activity is not an acceptable shortcut.

See:
- [Compatibility audit](docs/COMPATIBILITY_AUDIT.md)
- [Android Wine/Hangover porting audit](docs/ANDROID_WINE_HANGOVER_PORTING_AUDIT.md)

Box86 is not being treated as a standalone Windows executable translator: upstream Box86 targets x86 Linux userspace and requires a 32-bit ARM host userspace.

Still not integrated:
- Wine/Hangover runtime and Wine prefix.
- i386 WoW64 translator DLL (FEX or Box64 WOW64).
- Android/Bionic Wine process host and display driver.
- D3D9/DXVK with a validated Android Vulkan presentation path.
- Windows audio and input forwarding.
- Actual Windows process management.
- End-to-end execution of `NSUNSR.exe`.

The experimental Vulkan branch can compile a one-frame clear/present path, but it does not contain a game draw pipeline or a Wine/DXVK connection. It has not been verified on the target phone. A Vulkan probe does not prove DXVK or Direct3D 9 compatibility. Upstream DXVK requires Vulkan 1.3 and specific features, which must be checked on the actual device.

## CI build

Pushes to `main` and manual workflow dispatch run the Android debug APK workflow. A successful APK build verifies packaging and compilation only; it does not verify runtime or game execution.

## Target device

- Infinix Hot 50 4G
- MediaTek Helio G100 / Mali-G57 MC2
- Android 14, ARM64, 8 GB RAM
- Initial performance test target: 960×540 at 30 FPS (not a performance guarantee)

## Licensing and game files

Use only game files and runtime components you are authorized to use. This repository does not include Naruto game assets or proprietary runtime binaries. Any future third-party runtime integration must preserve upstream licensing and required notices.
