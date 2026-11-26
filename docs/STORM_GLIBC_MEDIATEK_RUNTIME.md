# Storm Glibc MediaTek Runtime — implementation boundary

## Decision

The primary runtime target is **GNU Glibc ARM64**. Bionic remains a separate experimental backend and must not be mixed into the same process or treated as an automatic fallback.

## Important distinction

This milestone adds a launcher-side backend declaration and a MediaTek-oriented configuration profile. It does **not** ship a modified Glibc, Wine, Box64, DXVK, Vulkan translation layer, or a Windows process host. The profile is descriptive metadata only until a compatible runtime is built, packaged, and launched in a separate process.

A C library cannot by itself optimize Mali rendering or guarantee higher Helio G100 FPS. CPU scheduling, memory pressure, Wine/Box64 translation, Vulkan driver capabilities, DXVK feature support, frame pacing, and thermal throttling must be measured independently.

## Initial target profile

| Setting | Initial value | Meaning |
|---|---:|---|
| Target SoC | MediaTek Helio G100 | Device target, not a detected guarantee |
| GPU | Mali-G57 MC2 | Vulkan capability must be probed on device |
| ABI | arm64-v8a | Android package ABI |
| Resolution | 960×540 | User-tested starting point |
| Frame cap | 30 FPS | Desired cap, not measured output |
| Box64 preset | Performance | Proposed runtime setting |
| DXVK | 1.10.3 Async | Proposed compatibility setting; verify features |
| Runtime memory budget | 2048 MiB | Initial budget metadata, not a hard allocation |

## Runtime integration gates

1. Select and pin a reproducible Glibc ARM64 build and its license/source provenance.
2. Build an Android-compatible Wine/Hangover process host; do not load Wine into the UI Activity as an unverified shortcut.
3. Build/package the matching Box64 WoW64 translator and verify ABI boundaries.
4. Execute the standalone PE32 probe and verify its expected log and exit code.
5. Validate Vulkan 1.3 and required features on the actual Mali-G57 device before enabling DXVK.
6. Integrate swapchain lifecycle, D3D9, audio, input, process supervision, and game-folder access.
7. Test on the target phone for sustained frame pacing and thermal behavior.

## Acceptance criteria

A backend is not marked available merely because the APK compiles or the launcher displays its name. Mark Glibc runtime available only after the PE32 probe executes successfully under the packaged runtime. Mark the game path available only after the game reaches a usable menu and input/audio/rendering are verified on-device.

## Current milestone

- [x] Launcher explicitly identifies Glibc as the intended primary backend.
- [x] Bionic is identified as a separate experimental path.
- [x] Initial Helio G100 profile is represented in source.
- [ ] Modified/pinned Glibc binary built and packaged.
- [ ] Android Wine process host integrated.
- [ ] Box64 WoW64 translator integrated.
- [ ] PE32 execution verified on-device.
- [ ] DXVK/D3D9 verified on Mali-G57.
- [ ] Naruto game verified end-to-end.
