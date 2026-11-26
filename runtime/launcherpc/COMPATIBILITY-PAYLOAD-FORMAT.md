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
box64=bin/box64
hangover.wow64_i386=lib/hangover/<actual-relative-path-to-arm64-i386-wow64-bridge>
```

All three referenced files must exist, be non-empty ELF64 little-endian AArch64 binaries or shared objects, and remain inside the package directory. Wine and Box64 are marked executable by the installer.

## Installation checks

LauncherPC verifies the selected archive against a separately selected SHA-256 file before extraction. Extraction is staged, rejects absolute paths, traversal, hard links and special files, and defers symlink creation until regular files have been extracted. The compatibility manifest is checked before the package is activated.

After each package installation, LauncherPC writes a top-level `storm-runtime-bundle-v1` manifest. The bundle is considered incomplete until PRoot, the Glibc rootfs, Wine, Box64 and the Hangover WoW64 bridge are all present.

## Important limitations

- A SHA-256 match detects corruption but does not authenticate who produced a package. Use packages from trusted, pinned source builds.
- This format does not build or bundle Wine, Hangover, Box64 or Glibc. Those native components still need reproducible source builds and licensing/provenance records.
- Detecting the files does not prove that Wine starts, that Hangover translates PE32 correctly, or that a Windows game can launch.
- The game launch gate remains closed until the runtime process host, display integration, input/audio bridges, PE32 smoke tests and device tests are implemented and verified.
