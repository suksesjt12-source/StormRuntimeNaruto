# LauncherPC compatibility payload format

This package is a separately built ARM64 Linux userspace payload. It is installed into the app-private directory `files/storm-runtime/compat`.

## Archive layout

The tar archive must contain a single top-level `compat/` directory:

```text
compat/
  manifest.properties
  bin/
    wine
    box64
  lib/
    ... Hangover WoW64 bridge files ...
```

The exact Hangover library location is selected by the package manifest. Do not rename upstream libraries merely to match this example; point the manifest to the real file emitted by the pinned Hangover build.

## Required manifest

`compat/manifest.properties` must use Java Properties syntax and contain:

```properties
format=storm-compatibility-v1
wine=bin/wine
# Optional standalone Box64 Linux executable:
box64=bin/box64
# Required Hangover WoW64 emulator DLL built from Box64:
hangover.wow64_i386=lib/wine/aarch64-windows/wowbox64.dll
```

Wine must be a non-empty ELF64 little-endian AArch64 executable. The Hangover `wowbox64.dll` must be a PE32+ ARM64 DLL (machine `0xAA64`, optional-header magic `0x020b`). A standalone Box64 Linux executable is optional; if supplied, it must be ELF64 AArch64. All files must remain inside the package directory. Linux executables are marked executable by the installer.

## Installation checks

LauncherPC verifies the selected archive against a separately selected SHA-256 file before extraction. Extraction is staged, rejects absolute paths, traversal, hard links and special files, and defers symlink creation until regular files have been extracted. The compatibility manifest is checked before the package is activated.

After each package installation, LauncherPC writes a top-level `storm-runtime-bundle-v1` manifest. The bundle is considered incomplete until PRoot, the Glibc rootfs, Wine and the Hangover Box64 WoW64 bridge are all present.


## DXVK-Sarek package

The DXVK-Sarek CI archive is installed separately from the ARM64 compatibility package. Select the `launcherpc-dxvk-sarek-1.11.0-windows-dlls.tar.gz` artifact and its matching SHA-256 file in the launcher. The archive root is `runtime-artifact/`; it must contain:

- `x32/d3d9.dll` and `x32/dxgi.dll` as PE32 x86 DLLs (machine `0x014c`, optional-header magic `0x010b`).
- `x64/d3d9.dll` and `x64/dxgi.dll` as PE32+ x86-64 DLLs (machine `0x8664`, optional-header magic `0x020b`).
- Build provenance and license files from the pinned DXVK-Sarek source build.

LauncherPC validates the PE machine type and optional-header magic before installing the files into `files/storm-runtime/dxvk/`. It records their paths in the runtime manifest. The files are not copied into a Wine prefix and are not loaded by a game process yet; renderer selection and DLL loading remain a later integration stage.

## Important limitations

- A SHA-256 match detects corruption but does not authenticate who produced a package. Use packages from trusted, pinned source builds.
- This format does not build or bundle Wine, Hangover, Box64 or Glibc. Those components still need reproducible source builds and licensing/provenance records.
- Detecting the files does not prove that Wine starts, that Hangover translates PE32 correctly, or that a Windows game can launch.
- The game launch gate remains closed until the runtime process host, display integration, input/audio bridges, PE32 smoke tests and device tests are implemented and verified.
