# Experimental Glibc ARM64 sysroot

This milestone cross-builds upstream GNU Glibc for AArch64 and packages the resulting sysroot as a GitHub Actions artifact. It is deliberately separate from the Android APK.

## Build identity

- Upstream source: bminor/glibc
- Tag: glibc-2.39
- Target: aarch64-linux-gnu
- Minimum Linux kernel ABI: 4.14
- Compiler: Ubuntu AArch64 cross toolchain
- Build flags: conservative upstream defaults; no unsafe Helio-only ISA flags

## Why this is not yet Helio G100 optimized

Glibc is a C runtime, not a GPU driver or CPU scheduler. The initial build keeps a generic AArch64 ABI to preserve compatibility across ARM64 Android devices. MediaTek-specific tuning belongs in measured Box64/Wine settings, CPU scheduling policy, memory limits, Vulkan/DXVK feature selection, and frame pacing. Do not force Cortex-A76-only instructions into a runtime that must also run on other ARM cores.

## Artifact limitations

The output is a Glibc sysroot, not a complete Linux distribution or Android-compatible Wine runtime. It does not contain PRoot, Wine/Hangover, Box64, DXVK, Vulkan userspace/driver components, an Android Wine display driver, or game files. It must not be copied into the APK and described as a working game backend.

## Next integration gates

1. Assemble a complete ARM64 userspace/rootfs with matching ABI and package provenance.
2. Build or select a compatible Android process launcher/PRoot layer and validate its syscall behavior on Android 14.
3. Build Wine/Hangover and Box64 WoW64 against the exact rootfs ABI.
4. Solve Android process isolation and display-driver integration; do not load Wine into the launcher Activity through JNI.
5. Verify a PE32 probe under the packaged runtime on a physical device before enabling game launch.
6. Only then integrate D3D9/DXVK, audio, and input.

## Reproducibility

Run the Build experimental Glibc ARM64 sysroot workflow manually or push changes to its watched paths on feature/mtk-glibc-runtime. The workflow verifies ELF64/AArch64 and uploads a checksummed archive. A successful workflow proves only that the cross-build and package succeeded.
