# Compatibility Audit — Storm Runtime Naruto

Audit basis: repository source on the current main branch after the CI and Vulkan diagnostic changes.

## Current architecture

The application is a Kotlin Android Activity with a JNI C++ shared library. It can request a game folder through Android's Storage Access Framework and check for `NSUNSR.exe` at the selected directory root. The native library loads into the Android process and queries the Android Vulkan loader for a Vulkan instance and physical-device properties.

## Compatibility matrix

| Layer | Current state | Evidence / blocker |
|---|---|---|
| Android ARM64 app | Implemented in source | Gradle ABI filter is `arm64-v8a`; CI build must pass |
| JNI native loading | Implemented in source | `System.loadLibrary("stormcore")` and native JNI calls |
| Game folder selection | Implemented | SAF tree picker and root-level EXE lookup |
| Vulkan device probe | Implemented in source | Calls `vkCreateInstance`, enumerates physical devices and reads properties |
| Wine / Win32 API | Not integrated | No Wine source, build, prefix, loader, or process integration |
| x86 32-bit execution | Not integrated | No Box86 or alternative translator integrated with Wine |
| D3D9 / DXVK | Not integrated | No D3D9 frontend, DXVK build, Vulkan feature validation, or swapchain bridge |
| Audio | Not integrated | No Wine audio driver or Android audio bridge |
| Input | Not integrated | No Windows input injection or gamepad/touch forwarding |
| Game process manager | Not implemented | No Windows process launch, wait, stop, or exit-code reporting |
| Target game | Not tested | No game execution evidence or device test session |

## Technical blockers and required validation

1. Wine Android builds require a supported Android/ARM64 configuration and compatible 32-bit Windows execution strategy. Merely linking Wine source is not sufficient.
2. A 32-bit x86 Windows process needs a complete compatible userspace and CPU translation path. Box64 alone is not evidence of x86-32 support.
3. D3D9 translation requires validating Vulkan device features, extensions, synchronization, memory behavior, and Android surface/swapchain integration on the target Mali driver.
4. The target game's redistributables, codecs, DirectX components, and runtime dependencies have not been inspected. Their compatibility cannot be assumed.
5. Tests requiring the physical Infinix device and the user's legitimately obtained game directory cannot be completed in hosted CI.

## Honest test status

- Test A (APK build): pending successful CI run.
- Test B (install and open on Android): not tested.
- Test C (native library load on device): source path implemented; device test pending.
- Test D (Wine initialization): blocked; Wine not integrated.
- Test E (x86-32 execution): blocked; translator not integrated.
- Test F (D3D9 initialization): blocked; graphics runtime not integrated.
- Test G (audio and input): blocked; integration absent.
- Test H (launch NSUNSR.exe): not possible yet.
- Test I (main menu): not tested.
- Test J (gameplay): not tested.

A successful APK build must not be reported as successful Windows runtime or game compatibility.
