# Storm Runtime Naruto — Android Native Prototype

This repository is a starter Android project for a Naruto Storm Revolution compatibility-runtime experiment.

## Current contents
- Kotlin Android launcher in landscape mode.
- Android Storage Access Framework folder picker.
- Basic `NSUNSR.exe` detection in the selected folder.
- Initial resolution/FPS selectors.
- JNI/C++ native-core health/status probe.
- GitHub Actions workflow for a debug APK build.

## Compatibility status
**This prototype does not run Windows EXE files yet.** The native core is a diagnostic stub. A working runtime still requires compatible, correctly licensed components for Windows API compatibility (such as Wine), 32-bit x86 execution on ARM64, Direct3D 9 translation to a supported Android graphics API, plus audio, input, filesystem, and process integration.

Do not describe this prototype as a working Winlator replacement until those components are integrated and the target game is tested.

## Build
Pushes to `main` and manual workflow dispatch trigger the Android APK build. The resulting debug APK is uploaded as a GitHub Actions artifact.

## Initial device target
Android ARM64, Android 14+, 8 GB RAM, and a Mali-G57-class GPU. Initial performance testing target: 960×540 at 30 FPS; this is a target, not a guarantee.

## Legal note
Use only game files and runtime components you are authorized to use. This repository does not include Naruto game assets or proprietary runtime binaries.
