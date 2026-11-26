# Storm Core — Hybrid Custom Backend Architecture

## Selected configuration

- Backend manager and integration layer: custom Storm Core.
- Primary Linux userspace: GNU Glibc, AArch64.
- Android application ABI: arm64-v8a.
- Target: Infinix Hot 50 4G, MediaTek Helio G100, Mali-G57 MC2, Android 14.
- Compatibility components: upstream Wine/Hangover, Box64 WoW64, DXVK and Vulkan, subject to Android porting and device validation.

## Process boundary

The Android launcher remains a Bionic application. It must not load Glibc or Wine libraries into its JVM process.

The intended process topology is:

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
Glibc ARM64 rootfs
  - GNU dynamic loader and libc
  - Wine/Hangover
  - Box64 WoW64 translator
  - DXVK userspace libraries
        |
        v
Windows PE process (NSUNSR.exe)
```

The service is a process boundary and control plane, not the Wine runtime itself. A separate process avoids loading the Windows compatibility stack into the Activity's JVM process. It does not by itself solve Android seccomp, ptrace, display, or signal-context requirements.

## Implemented in this milestone

- Versioned private Messenger message identifiers.
- A dedicated Android Service declared in process `:storm_runtime`.
- Runtime bundle manifest validation under app-private storage at `filesDir/storm-runtime/manifest.properties`.
- Validation of ABI, libc identity, path traversal, required payload files, and executable permissions.
- Explicit launch gate: a valid-looking bundle is not launch-capable until a real process host, Android display path, and physical-device PE32 test exist.

## Bundle manifest contract

The future manifest uses Java Properties syntax:

```properties
format=storm-runtime-bundle-v1
abi=arm64-v8a
libc=glibc
glibc.loader=rootfs/lib/ld-linux-aarch64.so.1
glibc.libc=rootfs/lib/aarch64-linux-gnu/libc.so.6
proot=bin/proot
wine=rootfs/usr/bin/wine
box64=bin/box64
hangover.wow64_i386=rootfs/usr/lib/wine/aarch64-windows/wowbox64.dll
```

Paths are relative to the bundle root and must remain inside it. Actual paths must match the selected, pinned Wine/Hangover build; the sample paths are a contract example, not proof that those files have been built.

## Important ABI boundary

Glibc is not a replacement for Android's Bionic libc. The Android UI and Android-side process host use the Android NDK/Bionic ABI. Linux applications that require Glibc run inside the Glibc userspace through a compatible host/PRoot arrangement. Do not mix Glibc and Bionic libraries in one process.

## Not implemented yet

- PRoot Android ARM64 build and syscall validation on Android 14.
- Complete, reproducible Glibc rootfs package and installer.
- Android-compatible Wine/Hangover display driver and process host.
- Box64 WoW64 translator build/package matching the pinned Hangover ABI.
- Wine prefix initialization.
- DXVK/D3D9 initialization and Vulkan feature validation on Mali-G57.
- Audio/input forwarding.
- PE32 probe execution on the physical device.
- NSUNSR.exe launch or Naruto main-menu verification.

## Acceptance gates

1. CI builds and packages the Android APK with the service in a separate process.
2. Runtime manifest validator rejects wrong ABI/libc and path traversal.
3. The packaged rootfs starts through the Android-compatible PRoot host.
4. Wine/Hangover initializes outside the Activity process.
5. The original PE32 probe runs and returns its expected exit code 37 on the target phone.
6. Only after the PE32 gate passes, integrate DXVK/D3D9, audio, input, then test the game.

A Gradle build, a Glibc sysroot artifact, or a manifest that lists runtime files is not evidence of Windows executable support.
