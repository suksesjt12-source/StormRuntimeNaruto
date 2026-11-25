# V11 Implementation Status

Updated: 2026-11-26

## Repository state

- Stable branch: `main`
- Experimental renderer branch: `experiment/android-vulkan-swapchain`
- Latest main implementation commits added during V11 execution:
  - `7d57381c6ce6460b3a3d7dd02cf8d09cc65bd01e` — reproducible Hangover upstream input audit script.
  - `7c62c763184334e1dbb73c2d5a9a41d1777507f7` — Hangover audit GitHub Actions workflow.
- Experimental branch remains unchanged at `9d5eba1fcc5268215340f9a14b44e061b63f31b1`.

## Changes made

### Hangover upstream integrity audit

Added `scripts/audit-hangover-android.sh`. It downloads the Hangover/Wine 11.16 source archive and the upstream Ubuntu 22.04 ARM64 release archive, verifies their pinned SHA-256 checksums, and verifies that both archives can be listed. The script deliberately does not execute or package the Ubuntu runtime.

Added `.github/workflows/hangover-audit.yml`. It runs the checksum/archive audit and inspects the current Termux package recipe for the `enable_wineandroid_drv=no` configuration. Audit evidence is uploaded as a GitHub Actions artifact.

Workflow run:
- [Hangover Android Porting Audit](https://github.com/suksesjt12-source/StormRuntimeNaruto/actions/runs/45707579528)
- Run ID: `45707579528`
- Commit: `7c62c763184334e1dbb73c2d5a9a41d1777507f7`
- Status at time of this document: running; do not treat as passed until GitHub reports success.

## Existing implementation verified by source inspection

- Kotlin launcher and Storage Access Framework folder selection exist.
- The launcher inspects the PE header of `NSUNSR.exe`; it does not execute the file.
- ARM64 JNI library and Vulkan diagnostics exist.
- The experimental renderer branch contains a one-frame Vulkan clear/render-pass/present experiment. It is not a persistent renderer and has not been verified on the target phone.
- The PE32 test program is compiled by an existing cross-build workflow; it has not been executed under Wine/Hangover.

## Runtime status

| Capability | Status |
|---|---|
| Android APK compilation and packaging | Previously passed in GitHub Actions |
| Hangover source/archive checksum audit | Workflow running; pending final result |
| Android/Bionic Wine/Hangover build | Not implemented |
| Android Wine display driver | Not implemented |
| Separate Wine process host / IPC | Not implemented |
| Wine prefix and process supervision | Not implemented |
| PE32 probe execution and exit code 37 | Not implemented |
| Persistent Vulkan renderer | Not implemented |
| DXVK / Direct3D 9 | Not implemented |
| Windows audio forwarding | Not implemented |
| Windows input forwarding | Not implemented |
| Original Naruto game launch | Not implemented |

## Engineering constraints

The upstream Termux recipe is useful as a Bionic-oriented packaging reference, but it explicitly disables Wine's Android display driver and uses the Termux prefix and package environment. The upstream Ubuntu ARM64 release archive is not an Android APK runtime. Neither may be copied into the app and represented as an Android-compatible Wine runtime.

The next runtime milestone remains a real Android/Bionic Wine host with a compatible Android display/process architecture, followed by execution of the existing PE32 probe and verification of its diagnostic log and exact exit code 37.

A successful audit workflow, native APK build, PE32 cross-compilation, or Vulkan clear-frame experiment is not evidence that `NSUNSR.exe` runs.
