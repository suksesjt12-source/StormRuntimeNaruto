# Foundation Architecture

Updated: 2026-11-26

## Current repository baseline

The current application is an Android Kotlin Activity with one ARM64 JNI shared library. It provides a Storage Access Framework folder picker, PE32/i386 header inspection, Vulkan device diagnostics, Android Surface attachment, and a Vulkan swapchain clear/present probe in the current mainline native source.

It does **not** currently contain Wine, Hangover WoW64, a Windows process supervisor, a Wine prefix, DXVK, Windows audio forwarding, or Windows input forwarding. PE header inspection and PE32 cross-compilation are not Windows execution.

## Target process architecture

```
Android Activity
  -> Kotlin Runtime Manager
  -> JNI Bridge
  -> Native Runtime Host
  -> Wine ARM64 (Android/Bionic port)
  -> Hangover WoW64
  -> PE32/i386 process
```

Wine may require a separate host process rather than being loaded into the Activity process. That decision remains open until upstream process, signal, seccomp, linker, and Android-driver requirements are proven. Do not assume JNI shared-library embedding is valid.

Graphics, audio, and input are independent backends. The experimental Vulkan clear/present code is a diagnostic, not a Windows graphics integration.

## Phase 1 host boundary

The native host owns lifecycle state, a worker thread, app-private filesystem setup, logging, and error reporting. It must not claim that a Windows runtime is available. The public internal API is:

- `runtime_initialize(data_directory)`
- `runtime_shutdown()`
- `runtime_get_status()`
- `runtime_get_last_error()`

JNI passes the app-private files directory to the host. SAF document URIs remain managed by Android's ContentResolver; they are not ordinary filesystem paths and must later be bridged through file descriptors or a deliberate staging/copy layer.

## Platform targets

- Android package ABI: `arm64-v8a`
- Android minimum API: 26 (current Gradle configuration)
- Android compile/target API: 35 (current Gradle configuration)
- NDK: 27.2.12479018 (pinned in Gradle)
- CMake: 3.22.1 (pinned in Gradle)
- Native language: C++17

## Validation principles

Compilation validates compilation. Host unit tests validate host logic on the host OS. JNI load and lifecycle on Android require an actual Android ARM64 device or emulator. Wine/Hangover, PE32, Vulkan presentation, DXVK, audio, input, and game claims each require their own runtime evidence. No CI status is promoted to device verification without device logs and device identity.

## Known engineering risks

- Wine Android/Bionic support is not yet integrated.
- Hangover upstream components must be built and loaded against the exact Wine build; WoW64 translators are not interchangeable by filename.
- Android linker namespaces, Bionic symbols, signals, memory mapping, process restrictions, and display-driver architecture require dedicated porting work.
- Vulkan feature support on Mali-G57 must be queried on-device; the current Vulkan diagnostic does not prove DXVK compatibility.
- SAF URIs cannot be treated as POSIX paths.
- Third-party source and binary licensing must be checked before vendoring or packaging.
