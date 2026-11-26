# Storm Core — Hybrid Custom Backend Architecture

## Selected configuration

- Backend manager and integration layer: custom Storm Core.
- Primary Linux userspace: GNU Glibc, AArch64.
- Android application ABI: arm64-v8a.
- Target: Infinix Hot 50 4G, MediaTek Helio G100, Mali-G57 MC2, Android 14.
- Compatibility components: upstream Wine/Hangover, Box64 WoW64, DXVK and Vulkan, subject to Android porting and device validation.

## Process boundary

The Android launcher remains a Bionic application. It must not load Glibc or Wine libraries into its JVM process.

```text
Storm UI (Android Activity, Bionic process)
        |
        | Android Messenger IPC
        v
StormRuntimeService (:storm_runtime, separate Android process)
        |
        | future supervised child process
        v
PRoot ARM64 host (Android/Bionic executable)
        |
        v
Debian Bookworm ARM64 Glibc rootfs
  - GNU dynamic loader and libc
  - base Linux utilities and selected shared libraries
  - future Wine/Hangover and Box64 WoW64 payload
        |
        v
Windows PE process (NSUNSR.exe)
```

The service is a process boundary and control plane, not the Wine runtime itself. A separate process avoids loading the Windows compatibility stack into the Activity's JVM process. It does not by itself solve Android seccomp, ptrace, display, or signal-context requirements.

## Implemented

- Versioned private Messenger message identifiers.
- Dedicated Android Service in process `:storm_runtime`.
- Messenger IPC for runtime status and launch-gate requests.
- Runtime bundle manifest validation under app-private storage.
- Debian Bookworm ARM64 Glibc rootfs definition in `runtime/rootfs/Dockerfile`.
- Buildx/QEMU CI workflow to export a rootfs archive, validate AArch64 ELF files and Glibc loader, record package versions, and produce a SHA-256 checksum.
- Explicit launch gate: the app refuses to claim launch capability before a real process host, Android display path, and physical-device PE32 test exist.

## Rootfs artifact

See [STORM_ROOTFS.md](STORM_ROOTFS.md). The rootfs is built separately from the APK and is not yet installed on Android. It is a minimal Linux userspace, not a complete Windows runtime. Its package inventory and provenance manifest are included with the CI artifact.

## Important ABI boundary

Glibc is not a replacement for Android's Bionic libc. The Android UI and Android-side process host use the Android NDK/Bionic ABI. Linux applications that require Glibc run inside the Glibc userspace through a compatible host/PRoot arrangement. Do not mix Glibc and Bionic libraries in one process.

PRoot relies on ptrace/system-call mediation. Android 14 behavior, app process policy, and device-specific restrictions must be tested on the target phone. PRoot is not a VM and does not provide a separate kernel or full security isolation.

## Not implemented yet

- Android-compatible PRoot ARM64 host and syscall validation on Android 14.
- Rootfs installer, integrity verification, and lifecycle management in app-private storage.
- Wine/Hangover runtime and Wine prefix.
- Box64 WoW64 translator build/package matching the selected Hangover ABI.
- Android-compatible Wine display driver and process host.
- DXVK/D3D9 initialization and Vulkan feature validation on Mali-G57.
- Audio/input forwarding.
- PE32 probe execution on the physical device.
- NSUNSR.exe launch or Naruto main-menu verification.

## Acceptance gates

1. CI builds and packages the Android APK with the service in a separate process.
2. Rootfs CI artifact passes AArch64 ELF and loader checks.
3. A Bionic PRoot host runs the rootfs on Android 14 without relying on a guest kernel.
4. Wine/Hangover initializes outside the Activity process.
5. The original PE32 probe runs and returns its expected exit code 37 on the target phone.
6. Only after the PE32 gate passes, integrate D3D9/DXVK, audio, input, then test the game.

A Gradle build, a Glibc sysroot artifact, a rootfs archive, or a manifest that lists runtime files is not evidence of Windows executable support.
