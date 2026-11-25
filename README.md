# Storm Runtime Naruto — Android ARM64 Compatibility Runtime Prototype

This repository is an experimental Android ARM64 project targeting the original Windows x86 (32-bit) executable `NSUNSR.exe` from Naruto Shippuden: Ultimate Ninja Storm Revolution.

## Implemented and verifiable in source

- Kotlin Android launcher in landscape mode.
- Storage Access Framework game-folder picker and root-level `NSUNSR.exe` detection.
- Initial resolution and FPS target selectors (settings only; they do not yet control a game renderer).
- JNI native library loaded by the Android app.
- Native Vulkan loader / physical-device diagnostic using the Android Vulkan API.
- GitHub Actions workflow for ARM64 debug APK builds, APK package verification, and artifact upload.

## Runtime status — NOT GAME-CAPABLE YET

**The app does not currently run Windows executables.** The following core components are not integrated:

- Wine for Android / Win32 API and Windows process environment.
- A supported x86 32-bit-on-ARM64 execution path integrated with Wine (Box86 or another proven solution).
- D3D9 translation and a working graphics path for Mali-G57.
- Windows audio integration and Android audio backend.
- Game input forwarding, touch controls, and game process lifecycle.
- End-to-end execution and testing of `NSUNSR.exe`.

The Vulkan probe only reports whether Android can create a Vulkan instance and enumerate physical devices. It does **not** establish DXVK compatibility, D3D9 support, or game compatibility.

The UI must not be interpreted as a working Winlator replacement. No success state is emitted for Wine, CPU translation, DXVK, or game execution.

## CI build

Pushes to `main` and manual workflow dispatch run the Android debug APK workflow. A successful APK build verifies packaging and compilation only; it does not verify runtime or game execution.

## Target device

- Infinix Hot 50 4G
- MediaTek Helio G100 / Mali-G57 MC2
- Android 14, ARM64, 8 GB RAM
- Initial performance test target: 960×540 at 30 FPS (not a performance guarantee)

## Licensing and game files

Use only game files and runtime components you are authorized to use. This repository does not include Naruto game assets or proprietary runtime binaries. Any future third-party runtime integration must preserve upstream licensing and required notices.
