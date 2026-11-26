# LauncherPC — Winlator Mali Backend Integration

## Product decision

LauncherPC owns the Android UI, game library, settings, container controls, and process supervision. Winlator Mali is an upstream runtime/component reference; its launcher UI is not part of the LauncherPC shell.

Target: ARM64 Android 14, MediaTek Helio G99/G100, Mali-G57 MC2, 8 GB RAM.

## Runtime boundaries

1. Android UI and LauncherPC Core are Android/Bionic processes.
2. Linux guest: Debian ARM64 with glibc, isolated from Bionic by PRoot. Never load glibc into the Android process.
3. Windows compatibility: Wine/Hangover plus Box64 WoW64 and matching i386 guest components.
4. Graphics routes are alternatives: Mali Graphics Wrapper, DXVK-Sarek/Vulkan, or WineD3D fallback. Do not blindly chain Wrapper and DXVK.
5. Audio/input require explicit Android-to-guest bridges; merely packaging libraries is not enough.

## Graphics component decision

| Component | Selected baseline | Current state |
|---|---|---|
| Mali Wrapper | Winlator Mali Bionic 1.2.1 source reference | Not imported; source/license audit and device validation required |
| DXVK | **DXVK-Sarek v1.11.0 “Red River”** | Version pinned in manifest; source not yet imported or built |
| DXVK upstream | No upstream 1.11.0 release | Do not label Sarek as official upstream DXVK |
| Fallback | Mali Wrapper, then WineD3D | Selection remains capability- and per-game-gated |

DXVK-Sarek is an unofficial continuation of the DXVK 1.10.x line. Its project describes a Vulkan 1.1/1.2 baseline and makes a number of extensions optional to support older or incomplete drivers. It is therefore the selected candidate for Mali-G57 testing, not a guarantee of compatibility or performance.

Pinned source reference:
- Repository: https://github.com/pythonlover02/DXVK-Sarek
- Tag: `v1.11.0`
- Release commit prefix: `7c0e3b2`
- Release: https://github.com/pythonlover02/DXVK-Sarek/releases/tag/v1.11.0

The Android native probe now checks the Vulkan 1.1 API baseline, VK_KHR_swapchain, a graphics queue, and Android surface instance extensions. The existing Android Surface probe separately checks actual surface/swapchain presentation. Both are diagnostics; neither proves that DXVK DLLs load or that D3D9 works.

## Renderer enable policy

DXVK-Sarek must remain disabled unless all checks pass:
1. Vulkan physical device reports API 1.1 or newer.
2. VK_KHR_swapchain is available.
3. A graphics queue is available.
4. VK_KHR_surface and VK_KHR_android_surface are available.
5. The Android surface/swapchain presentation smoke test passes.
6. DXVK-Sarek x32/x64 DLLs are built/imported for the guest ABI and pass Wine D3D9 smoke tests.

If the DXVK route fails, select the Mali Wrapper route as a separate backend. WineD3D remains a compatibility fallback. Renderer selection is stored per game/container.

## Performance policy

- Initial test profile: 960×540, 30 FPS cap.
- Do not force CPU/GPU clocks, thermal limits, or privileged sysfs writes.
- Track frame time, RSS, battery temperature, and sustained FPS.
- Use app-private versioned shader caches with bounded size.
- Do not claim stable FPS until physical Helio G99 and G100 testing is completed.

## Implementation status

Completed in this iteration:
- Pinned DXVK-Sarek v1.11.0 metadata and provenance in the component manifest.
- Added a native Vulkan capability probe for the Sarek Vulkan 1.1 baseline.
- Added a separate CMake source entry and launcher diagnostic hook (see source files).

Not completed:
- Import/build of DXVK-Sarek binaries and Wine/Hangover/Box64.
- Runtime DLL installation and Wine overrides.
- Android guest Vulkan loader/WSI bridge.
- D3D9 smoke test, PE32 WoW64 execution, Naruto launch, or sustained gameplay test.

An APK build alone is not evidence that a Windows game can run.
