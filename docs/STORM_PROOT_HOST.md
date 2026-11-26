# Storm PRoot Host — Stage 3

## Android host and guest split

The Android launcher and supervisor use Android Bionic. The Linux guest uses the separate Debian ARM64 Glibc root filesystem. PRoot itself must be built for Android/Bionic; a normal Glibc Linux PRoot binary is not an Android host executable.

## PRoot ARM64 build

The `Build Android PRoot ARM64` workflow builds the Android package from the `green-green-avk/build-proot-android` build scripts using Android NDK 23.2.8568313. It restricts the build to `aarch64`, checks that the PRoot executable and companion loader are ELF64 AArch64, and uploads `storm-proot-android-arm64` as a workflow artifact.

Upstream build scripts and license: https://github.com/green-green-avk/build-proot-android

## Expected app-private layout

    files/storm-runtime/
      proot/
        root/
          bin/proot
          libexec/proot/loader
      rootfs/
        bin/bash
        lib/ld-linux-aarch64.so.1

The APK does not yet install the PRoot artifact or RootFS automatically. The artifact must be unpacked into this layout by a future installer before the launcher smoke test can pass.

## Smoke test

The `Uji PRoot + Glibc RootFS` action checks the executable and loader ELF headers, checks the guest Bash and Glibc dynamic loader, then runs a bounded PRoot command that prints `STORM_GLIBC_OK`. The process is limited to 30 seconds and its output is bounded.

## Android constraints and acceptance gate

PRoot relies on ptrace-based process instrumentation. A successful cross-compile does not prove that ptrace, syscall translation, SELinux policy, app-private executable placement, or the device kernel will permit the runtime to work. The final acceptance test must run on a physical Android 14 device, including the target Helio G100 phone.

Stage 3 is not complete until the PRoot package and Glibc RootFS are installed on-device and the smoke test returns `GLIBC_SMOKE_OK`. Wine/Hangover, Box64 WoW64, DXVK, and game launch remain blocked until then.