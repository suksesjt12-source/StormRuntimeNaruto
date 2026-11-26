# Storm Runtime Package Installer — Stage 3C

## Scope

Stage 3C adds an app-private package extraction layer for the independently built Android/Bionic PRoot package and Debian ARM64 Glibc rootfs. It does not install Wine, Hangover, Box64, DXVK, or game files.

## Expected package inputs

- PRoot: `proot-android-aarch64.tar.gz`, with the archive root directory `root/`.
- RootFS: `storm-rootfs-bookworm-arm64.tar.xz`, with the archive root directory `rootfs/`.

The rootfs build script packages only the `rootfs/` tree. Its manifest and package lock remain separate CI outputs.

## Destination layout

```
files/storm-runtime/
  proot/root/
    bin/proot
    libexec/proot/loader
  rootfs/
    bin/bash
    lib/ld-linux-aarch64.so.1
```

This matches the paths consumed by `StormProotHost`.

## Installer safeguards

- Extracts into a fresh staging directory before switching the destination.
- Rejects absolute archive entry paths, parent traversal, duplicate writes, hardlinks, device nodes, FIFOs, and unsupported entry types.
- Defers symlink creation until regular files have been extracted.
- Checks ELF64 little-endian AArch64 headers for PRoot and its companion loader.
- Limits archive entries to 250,000 and expanded regular-file data to 3 GiB.
- Keeps the previous installation as a temporary backup until the staged directory is activated.

## Current limitations / acceptance gates

- The Kotlin installer implementation exists, but the launcher UI has not yet been wired to Android's document picker. It is not yet user-invokable.
- The installer currently checks archive structure and key files; it does not yet verify a trusted SHA-256 manifest. Do not install archives from untrusted sources.
- The APK dependency resolution and Kotlin compilation have not yet been verified by a successful CI run.
- No PRoot execution test on Android has been completed. Android ptrace restrictions and executable-memory policy still need real-device validation.
- A successful package installation would only prepare PRoot and Glibc. It would not make Windows PE32 execution or Naruto launch-capable.

## Next acceptance criteria

1. Wire separate SAF file pickers for the PRoot and RootFS archives.
2. Run extraction on a worker thread and report progress/results to the UI.
3. Add trusted SHA-256 verification before extraction.
4. Build the APK in GitHub Actions and fix all compile errors.
5. Install on an ARM64 Android device and pass the PRoot + Glibc smoke test.
