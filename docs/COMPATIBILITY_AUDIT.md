# Compatibility Audit — Storm Runtime Naruto

Audit basis: current main branch after JVM/NDK build fixes, expanded Vulkan probing, and PE32 executable inspection.

## Current architecture

The application is a Kotlin Android Activity with an ARM64 JNI C++ shared library. It can request a game folder through Android's Storage Access Framework, inspect the root-level `NSUNSR.exe`, and parse its DOS/PE headers to check whether it is a Windows PE32/i386 executable. The native library queries Android Vulkan for physical devices, graphics queue families, selected device features, and the advertised swapchain extension.

## Current source-level implementation

| Layer | State | Evidence |
|---|---|---|
| Android ARM64 app | Implemented in source | Gradle ABI filter is `arm64-v8a` |
| Java/Kotlin bytecode target | Aligned to JVM 17 | Gradle compile options and Kotlin target |
| NDK | Pinned to 27.2.12479018 | `ndkVersion` |
| JNI native library | Implemented in source | `System.loadLibrary("stormcore")` and native calls |
| Game folder selection | Implemented | SAF tree picker |
| Executable format inspection | Implemented in source | Checks MZ, PE signature, machine `0x014c`, optional header `0x010b` |
| Vulkan discovery | Implemented in source | Creates instance, enumerates physical devices, queue families, device features and `VK_KHR_swapchain` |
| APK CI | Configured | SDK, NDK, CMake, Gradle, package check, artifact upload |

Source-level implementation is not equivalent to a successful Android device test. The executable parser only validates file format; it does not load or run the game.

## Runtime architecture research

### Preferred candidate: Hangover + Wine WoW64

The Hangover project documents running i386 and x86_64 Windows PE applications on ARM64 Wine using emulator DLLs. Its documented 32-bit choices include FEX's `libwow64fex.dll` and Box64's `wowbox64.dll`. This is a more relevant architecture for 32-bit Windows PE applications than treating Box86 as a standalone Windows emulator.

Official project: https://github.com/AndreRH/hangover

The Termux package recipe for Hangover is an important Android ARM64 reference:
https://github.com/termux/termux-packages/tree/master/x11-packages/hangover-wine

However, the current Termux recipe configures Wine with X11, PulseAudio, SDL2 and Vulkan dependencies and explicitly disables `wineandroid_drv`. It is therefore not a drop-in native Android Activity runtime. An integrated display/input/audio bridge or a different Wine Android driver build is still required. The Termux package also has a large dependency graph and must be built and packaged with its corresponding Termux environment conventions.

### Box86 assessment

Box86 is not selected as the primary translator for this Android ARM64 app. Upstream Box86 describes itself as an x86 Linux userspace emulator, requires a 32-bit ARM host userspace and its associated libraries, and is intended to run x86 Linux programs such as x86 Wine. It is not by itself a translator for a Windows PE executable.

References:
- https://github.com/ptitSeb/box86
- https://github.com/ptitSeb/box86/blob/master/docs/README.md
- https://github.com/ptitSeb/box86/blob/master/docs/COMPILE.md

Hangover's ARM64 WoW64 integration with FEX or Box64's WOW64 DLL is the more direct candidate for an i386 Windows PE process.

### DXVK / Mali assessment

DXVK is a Direct3D 8/9/10/11-to-Vulkan translation layer and provides both x32 and x64 DLLs. The upstream driver support page states that legacy DXVK 1.10.3 requires a Vulkan 1.1-capable driver; later DXVK branches require newer Vulkan versions and additional extensions/features. Upstream explicitly does not provide first-party support for Android/mobile graphics drivers.

References:
- https://github.com/doitsujin/dxvk
- https://github.com/doitsujin/dxvk/wiki/Driver-support

The app's Vulkan diagnostic is only an initial capability probe. It does not yet validate every extension, feature, memory requirement, presentation path, or synchronization behavior required by DXVK. Mali-G57 compatibility remains unverified until tested on the actual device.

## Compatibility matrix

| Layer | Current state | Evidence / blocker |
|---|---|---|
| Android ARM64 app | Implemented in source | APK build must pass |
| JNI native loading | Implemented in source | Device test pending |
| PE32/i386 inspection | Implemented in source | Device test and real game-file inspection pending |
| Vulkan device probe | Implemented in source | Device test pending |
| Wine / Win32 API | Not integrated | Hangover identified as candidate; Android packaging/display bridge not implemented |
| x86 32-bit Windows execution | Not integrated | Hangover i386 WoW64 emulator DLL not built or packaged |
| D3D9 / DXVK | Not integrated | Vulkan feature validation and Android presentation path absent |
| Audio | Not integrated | Wine audio backend and Android bridge absent |
| Input | Not integrated | Windows input forwarding absent |
| Game process manager | Not implemented | No real Wine process lifecycle |
| Target game | Not tested | No game execution evidence or device test session |

## Latest known CI failure and fix

The previous run failed at `compileDebugKotlin` with:
`Inconsistent JVM-target compatibility detected for tasks 'compileDebugJavaWithJavac' (1.8) and 'compileDebugKotlin' (17).`

The Gradle configuration has now been updated to set Java source/target compatibility and Kotlin `jvmTarget` to 17, and to pin NDK 27.2.12479018. A new workflow run is required to confirm this fix.

## Honest staged test status

- Test A (APK build): pending confirmation from the latest workflow.
- Test B (install and open on Android): NOT TESTED.
- Test C (native library load on device): source path implemented; device test pending.
- Test D (Vulkan device detection on target phone): NOT TESTED.
- Test E (Wine initialization): BLOCKED; Hangover candidate researched but not integrated.
- Test F (x86-32 PE execution): BLOCKED; translator DLL not built or integrated.
- Test G (Direct3D 9 initialization): BLOCKED; DXVK and Android presentation integration absent.
- Test H (audio and input): BLOCKED; integration absent.
- Test I (launch NSUNSR.exe): BLOCKED.
- Test J (main menu): NOT TESTED.
- Test K (gameplay): NOT TESTED.

A successful APK build must not be reported as successful Windows runtime or game compatibility.
