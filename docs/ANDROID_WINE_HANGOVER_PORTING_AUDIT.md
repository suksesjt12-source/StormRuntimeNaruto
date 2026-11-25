# Android Wine/Hangover Porting Audit

Audit date: 2026-11-26

## Decision

Do not embed a conventional Linux Hangover build or a Termux package directly into the current Android Activity. The repository currently has no Wine runtime, no Android Wine driver, no process host, and no Bionic runtime dependency bundle. A successful Android APK build does not change that status.

The immediate engineering blocker is not the PE32 probe: it is producing a Wine/Hangover runtime that can be launched and hosted safely by an ordinary Android application, with a working Android display/input/audio integration.

## Pinned upstream reference

The upstream Hangover release reference used for this audit is `hangover-11.16`. Its release notes identify commit prefix `56e385b`. The upstream README documents:
- ARM64 Wine with WoW64 emulator DLLs.
- i386 execution using `wowbox64.dll` or `libwow64fex.dll`.
- The `HODLL` selection variable for the FEX i386 translator.
- DXVK installation into the Wine prefix.
- A DXVK requirement for Vulkan 1.3 and required device features.

Official references:
- https://github.com/AndreRH/hangover/tree/hangover-11.16
- https://github.com/AndreRH/hangover/blob/hangover-11.16/docs/COMPILE.md
- https://github.com/AndreRH/hangover/releases/tag/hangover-11.16

The upstream compile guide describes a Linux-hosted Wine build with host build dependencies, llvm-mingw, submodules, and separate build instructions for FEX or Box64 WoW64 DLLs. Those instructions are not an Android NDK/Bionic application integration recipe.

## Termux recipe findings

The current Termux package recipe was inspected directly:
https://github.com/termux/termux-packages/blob/master/x11-packages/hangover-wine/build.sh

It currently pins Hangover/Wine 11.16 and includes ARM64/i386/ARM64EC targets. It also:
- explicitly sets `enable_wineandroid_drv=no`;
- depends on Termux package libraries, including X11/XCB, SDL2, PulseAudio, Vulkan and Android-specific Termux packages;
- installs the runtime under the Termux prefix;
- builds Wine host tools separately;
- obtains emulator DLL packages separately.

Therefore, this recipe is useful evidence for a Bionic-based package build, but it does not supply the Android Activity display driver or a self-contained APK runtime. Copying its package files into this APK would be an unsupported integration and is explicitly not treated as a solution.

## Android process and driver architecture risk

A Wine Android driver patch series (MR10569, v15 posted May 25, 2026) proposes separating the Android Activity/UI process from the Wine process. Its rationale describes problems with running Wine inside the JVM process on modern Android, including seccomp restrictions and conflicts involving Wine thread context, signals, and register state.

Reference:
https://list.winehq.org/hyperkitty/list/wine-gitlab%40list.winehq.org/thread/QWXUN6THR3HKLYU352UG67WRFSA2UWD/

This is a patch proposal, not evidence that the required Android driver is already integrated into the pinned Hangover release. The project must verify the patch's current upstream/branch status and licensing before adopting it. Do not implement a JNI call into Wine inside the current Activity as a shortcut.

## Required integration sequence

1. Select a reproducible Wine Android driver source/patch revision and verify its status against the pinned Wine/Hangover source.
2. Design a separate Wine process and a narrow IPC protocol between the Kotlin UI process and Wine process. Account for Android app sandboxing, process lifecycle, storage URI permissions, and supported executable launch paths.
3. Build the runtime and all native dependencies against a compatible Android/Bionic toolchain. Record every source revision, patch, compiler, and checksum.
4. Provide a real Android display driver or a documented compatible display-server bridge. A Vulkan surface probe in the UI process is not a Wine display backend.
5. Add process supervision, prefix initialization, logs, exit-code capture, and graceful shutdown.
6. Execute the existing PE32 probe in that runtime and verify its log file plus exit code 37.
7. Only after PE32 execution works, integrate D3D9/DXVK, then audio and input forwarding, and finally test the original game.

## Current repository evidence

### main

- HEAD at audit time: `c4f27f4bef670914de8d72a7df9cce13677a6af8`.
- APK workflow run `45701032445`: success.
- The workflow verifies package ID, ARM64 ABI, JNI library packaging, and checksum artifact.
- The native library reports that Wine/Hangover and Windows PE execution are not integrated.
- The Android Activity only inspects the PE header and runs Vulkan diagnostics.

### experiment/android-vulkan-swapchain

- HEAD at audit time: `9d5eba1fcc5268215340f9a14b44e061b63f31b1`.
- Experimental APK workflow run `45700990064`: success.
- The experimental native code creates a swapchain, image views, render pass, framebuffers, records a clear-only render pass, submits it, and presents one solid-color frame.
- This is a one-frame Vulkan clear/present experiment, not a persistent game renderer. It has no game draw pipeline, no DXVK, no D3D9 device, and no Wine connection.
- No physical-device verification on the target Infinix device has been recorded.

## Evidence-based status

| Capability | Status |
|---|---|
| Android ARM64 APK build | PASS in GitHub Actions |
| JNI library packaged in APK | PASS in GitHub Actions; device load not tested |
| PE32/i386 test executable compiled | PASS in existing cross-build workflow |
| PE32/i386 executed under Wine/Hangover | NOT IMPLEMENTED |
| Wine prefix and Windows process manager | NOT IMPLEMENTED |
| Android/Bionic Wine runtime packaged | NOT IMPLEMENTED |
| Android Wine display driver | NOT IMPLEMENTED |
| Experimental Vulkan clear-frame present | COMPILED; target-device execution unverified |
| DXVK/D3D9 | NOT IMPLEMENTED |
| Audio/input forwarding | NOT IMPLEMENTED |
| Original game launch | NOT IMPLEMENTED |

## Non-negotiable validation

Do not mark runtime integration complete based on a successful CMake/Gradle build, a PE32 cross-compile, a library being present in the APK, or a Vulkan clear-frame workflow. The first runtime milestone is the PE32 probe returning exactly 37 under the packaged Android runtime on a physical device, with its expected log file available for inspection.
