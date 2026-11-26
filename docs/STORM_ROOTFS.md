# Storm Runtime — ARM64 Glibc rootfs

## Purpose

This stage assembles a minimal Debian Bookworm ARM64 filesystem using the distribution's ARM64 packages. It is the guest userspace that later Wine/Hangover and Box64 components may use. It is separate from the Android APK and separate from the Glibc cross-compilation sysroot.

## Build

The GitHub Actions workflow `Build Storm ARM64 Glibc rootfs` uses Docker Buildx with ARM64 emulation to build the Debian image, exports its filesystem, validates ELF architecture and the expected Glibc loader, and uploads a compressed rootfs plus SHA-256 checksum.

The local script is:

```bash
bash scripts/build-rootfs-arm64.sh
```

It requires Docker with Buildx and QEMU/binfmt support, plus `readelf`, `tar`, and `sha256sum`.

## Rootfs contract

- Distribution: Debian Bookworm.
- Architecture: AArch64 / ARM64.
- C library: Debian GNU Glibc (`libc6`).
- Dynamic loader: `/lib/ld-linux-aarch64.so.1`.
- Shell: `/bin/bash`.
- Storage: app-private files directory after a future verified installer is implemented.

The package set is intentionally minimal and includes base shell utilities plus selected shared libraries useful for later runtime integration. It is not a complete desktop distribution.

## Android integration boundary

The Android Activity and Android-side service remain Bionic processes. Do not load the rootfs Glibc into either Android process. A future Android-compatible ARM64 PRoot host must run as a Bionic executable and launch guest Glibc binaries through the guest loader. PRoot relies on ptrace/system-call mediation; Android 14 behavior and device policy must be tested on the target device. PRoot is not a VM and does not provide a separate kernel or full security isolation.

## Not included / not proven

- PRoot binary or Android process host.
- Wine, Hangover, Box64 WoW64, DXVK, or game assets.
- Android display, Vulkan, audio, or input bridges.
- Execution on Android 14 or Helio G100.
- Windows PE32 compatibility or Naruto game execution.

A successful CI job proves that a rootfs archive was assembled and its ELF ABI inspected on the CI runner. It does not prove the archive can execute on the phone.
