# Storm PRoot Host — Stage 3

## Purpose

The Android-side host remains a Bionic process. It supervises a separate ARM64 PRoot executable and asks that process to start `/bin/bash` inside the Debian Bookworm ARM64 Glibc root filesystem.

The current implementation adds a bounded smoke-test path to the `:storm_runtime` service:

- `StormProotHost` checks that the PRoot executable is present and marked executable.
- It validates the PRoot, Bash, and Glibc loader ELF headers as ELF64 little-endian AArch64.
- It starts PRoot with a 30-second timeout and captures a bounded log.
- Success requires the guest shell to print `STORM_GLIBC_OK` and exit successfully.
- The launcher exposes this through **Uji PRoot + Glibc RootFS**.

## Expected app-private layout

    files/storm-runtime/
      bin/
        proot
      rootfs/
        bin/bash
        lib/ld-linux-aarch64.so.1

This layout is a runtime contract, not an installer. The rootfs artifact is still built and distributed separately; automatic download, checksum verification, safe extraction, and installation are not implemented yet.

## Important Android constraints

PRoot must be built as an Android/Bionic ARM64 executable. A normal Debian/Ubuntu Glibc PRoot binary cannot be used as the Android host. The PRoot build must be tested on Android 14 because ptrace policy, syscall behavior, SELinux, executable-file placement, and app storage mount options can affect operation.

The current code only attempts to execute a supplied PRoot binary. It does not bundle PRoot, bypass Android security policy, or claim that ptrace works on the target phone.

## Acceptance criteria

1. The Android APK compiles with minSdk 26.
2. A known-good Bionic ARM64 PRoot binary is installed in the expected path.
3. A Debian ARM64 Glibc rootfs is installed in the expected path.
4. The smoke test returns `GLIBC_SMOKE_OK` on a physical Android 14 device.
5. Repeated start/stop and timeout tests do not leave child processes running.

Until these criteria pass, Wine/Hangover, Box64 WoW64, DXVK, and Naruto launch remain blocked.