# Foundation Roadmap

Updated: 2026-11-26

| Phase | Scope | Exit evidence | Current state |
|---|---|---|---|
| 0 | Repository and dependency audit | Branch/file inventory, architecture and dependency docs | IN PROGRESS (license audit remains) |
| 1 | Android native host | JNI load, initialization, worker lifecycle, filesystem and lifecycle device test | IN PROGRESS |
| 2 | Wine ARM64 on Android/Bionic | Android-compatible Wine build, linker/dependency audit, prefix on device | NOT STARTED |
| 3 | Hangover WoW64 | Correct translator built and loaded by the selected Wine | NOT STARTED |
| 4 | PE32 process execution | Windows API probe writes log and exits exactly 37 on Android ARM64 | NOT STARTED |
| 5 | Android display backend | Repeated frame render/present and surface recreation on device | NOT STARTED |
| 6 | DXVK/D3D9 | D3D9 test scene rendered and presented | NOT STARTED |
| 7 | Audio | Android audio stream and test-tone validation | NOT STARTED |
| 8 | Input | Android events delivered to a Windows test process | NOT STARTED |
| 9 | Windows app compatibility | Incremental console/GUI/graphics/audio/input reports | NOT STARTED |
| 10 | Naruto Storm Revolution | Original game launch, menu, rendering, audio, input, gameplay | NOT STARTED |

## Execution order

Work one phase at a time. Do not start Wine integration until Phase 1 has passed its Android device acceptance tests. A successful Linux-host native test or APK build does not satisfy the Android device gate.

## Branch policy

- `main`: stable, buildable baseline.
- `experiment/wine-hangover-android`: Wine/Hangover and native host development.
- `experiment/android-vulkan-swapchain`: isolated Vulkan renderer experiments.

Experimental changes are not merged to `main` until tests and review justify the merge.
