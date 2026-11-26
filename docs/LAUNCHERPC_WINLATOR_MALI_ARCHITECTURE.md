# LauncherPC — Winlator Mali Backend Integration

## Product decision

The Android application is named **LauncherPC**. Its UI, navigation, game library, settings, and container controls remain LauncherPC-owned. Winlator Mali is an upstream runtime/component reference and source of compatible backend components; its UI must not be exposed as the LauncherPC shell.

Target devices: MediaTek Helio G99 / G100, ARM64 Android 14, Mali-G57 MC2, 8 GB RAM.

## Runtime boundaries

1. Android UI process: Android/Bionic only. It owns LauncherPC UI, permissions, SAF access, preferences, and user-facing lifecycle.
2. LauncherPC Core process: Android/Bionic host and IPC controller. It validates manifests, stages assets, starts/stops runtime processes, collects logs, and reports capability probes.
3. Linux guest userspace: Debian ARM64 with glibc, isolated from Bionic. PRoot is the initial userspace boundary; do not load glibc libraries into the Android process.
4. Windows compatibility stack: Wine/Hangover plus Box64 WoW64 and matching i386 guest components. Launch only after ABI and dependency checks pass.
5. Graphics stack: selectable Mali Wrapper path and DXVK/Vulkan path. They are alternative rendering routes, not blindly chained layers.
6. Audio/input: explicit bridge interfaces from Android to the guest process; bundling a library alone does not implement forwarding.

## Component baseline and policy

| Component | Baseline / candidate | Policy |
|---|---|---|
| Winlator Mali | Bionic 1.2.1 | Final upstream release; source reference, not a live update channel |
| Box64 | 0.4.4 | Baseline reported in Winlator 11.2 release notes; verify ABI/build against guest |
| DXVK | 3.1.1 | Latest upstream candidate; enable only after Vulkan capability checks |
| DXVK fallback | 2.7.1, 1.10.3 | Test per device/game; newer does not automatically mean faster or compatible |
| Mali Graphics Wrapper | Winlator Mali Wrapper implementation | Primary Mali-specific route, subject to source/license audit and device validation |
| BCn emulation | ASTC / ETC2 | Capability-gated; persistent cache must be versioned and invalidated safely |
| Userspace | Debian ARM64 + glibc | Separate from Android/Bionic |
| Process supervision | LauncherPC Core + PRoot | Bounded startup, timeout, exit status, logs, graceful stop |

Version references are not proof that binaries have been imported or that the combination runs on Mali-G57. Pin immutable upstream tags/commits and checksums before packaging.

## Renderer selection

LauncherPC must probe Vulkan API version, extensions, formats, queue families, memory budget, and presentation support at runtime.

- mali_wrapper: preferred first test route for Mali devices, with BCn emulation set to ASTC or ETC2 only when required formats are available.
- dxvk_vulkan: permitted only when the device driver satisfies the selected DXVK build's Vulkan and extension requirements and the Android presentation bridge passes smoke tests.
- wined3d_fallback: compatibility fallback for games that do not work with the Vulkan route.
- Never silently select DXVK 3.x on a device that lacks its required Vulkan capabilities.
- Store renderer choice per game/container, not as a global forced setting.

The upstream Winlator Mali Bionic instructions recommend the Wrapper route and ASTC/ETC2 BCn emulation for Mali; Wrapper is therefore the first Mali candidate. DXVK remains a separate optional route and must pass validation.

## Helio G99/G100 performance policy

- Initial resolution profile: 960x540.
- Initial frame cap: 30 FPS.
- Box64 preset: start with a balanced/performance candidate, then compare per game.
- Keep CPU affinity at Android scheduler defaults unless measurements prove a safe benefit.
- Avoid forcing GPU clocks, thermal overrides, or privileged sysfs writes.
- Keep shader and texture caches on app-private storage with size limits and version keys.
- Compile/cache shaders incrementally; avoid synchronous full-cache work during game launch.
- Monitor frame time, process RSS, battery temperature, and sustained FPS. Reduce resolution/effects before increasing memory budgets.
- Do not promise stable FPS until tested on physical Helio G99 and G100 devices.

## Required integration stages

### Stage A — branding and component contract
- Rename user-facing product strings to LauncherPC.
- Keep package/application identity migration explicit; do not change applicationId without a migration decision.
- Add component manifest, provenance, licenses, hashes, and ABI checks.

### Stage B — source integration
- Audit Winlator Mali source and all transitive licenses.
- Import only runtime/backend modules needed by LauncherPC; do not embed its launcher UI.
- Build Box64, Wine/Hangover, DXVK and Mali Wrapper for compatible ARM64/guest ABIs.
- Preserve upstream notices and source-offer obligations.

### Stage C — Android integration
- Implement package staging, runtime version checks, process lifecycle, log collection, and IPC.
- Validate Bionic host / glibc guest boundary.
- Implement Android Vulkan surface/presentation bridge before enabling DXVK.

### Stage D — validation
- Unit tests for manifest, ABI, hashes, and renderer selection.
- PRoot/rootfs smoke test on Android 14.
- Vulkan capability report on Mali-G57.
- D3D9/DXVK smoke test, then PE32 WoW64 execution test.
- Naruto launch through LauncherPC UI and physical-device play test.
- Sustained 30-minute and 60-minute tests with frame-time and thermal logs.

## Current truth / non-goals

This document defines the integration contract. It does not claim that Winlator Mali, Box64 0.4.4, DXVK 3.1.1, or Mali Wrapper binaries have already been built, packaged, or tested in this repository. A successful Android APK build alone is not proof that Windows games can run.