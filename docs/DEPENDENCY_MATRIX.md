# Dependency Matrix

Updated: 2026-11-26

| Component | Current dependency / version | Purpose | State / constraint |
|---|---|---|---|
| Android Gradle Plugin | Defined in root Gradle plugin management | Android packaging | Existing |
| Gradle | CI pins 8.9 | Build orchestration | Existing |
| Java | 17 | Kotlin/Gradle toolchain | Existing |
| Android SDK | compile/target 35 | Platform APIs | Existing |
| Android NDK | 27.2.12479018 | ARM64 native build | Existing |
| CMake | 3.22.1 | Native build | Existing |
| AndroidX DocumentFile | 1.0.1 | SAF tree access | Existing |
| Android NDK APIs | android, log, Vulkan loader | Surface/JNI/logging/graphics diagnostics | Existing or Phase 1 host linkage |
| Wine | Upstream Wine source as carried by Hangover project | Windows API and process runtime | Not integrated; Android/Bionic port unresolved |
| Hangover | https://github.com/AndreRH/hangover | WoW64 architecture translation | Not integrated; pin and license audit required |
| Box64 WOW64 / FEX WoW64 | Hangover-selected translator | i386 execution boundary | Not selected; must match Wine/Hangover build |
| DXVK | https://github.com/doitsujin/dxvk | D3D9-to-Vulkan | Not integrated; Vulkan 1.3/features and Mali support need device audit |
| Vulkan | Android Vulkan loader / device driver | Native rendering | Existing diagnostics; device verification pending |
| Audio backend | To be selected after Wine backend audit | Windows audio to Android | Not selected |
| Input backend | Android input APIs plus Wine event integration | Touch/gamepad/keyboard forwarding | Not integrated |

## Licensing audit policy

Before adding source or binaries, record upstream URL, exact commit/tag, license identifier, redistribution obligations, patches, and build provenance. Do not copy Ubuntu or Termux runtime binaries into the APK as if they were Android/Bionic builds. Do not bundle proprietary game files.

## Reproducibility

Pin upstream commits or immutable release archives and SHA-256 checksums. Record compiler/toolchain versions and generated artifact hashes in CI. Floating branch heads are research references, not reproducible build inputs.
