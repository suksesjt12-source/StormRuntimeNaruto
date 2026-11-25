# Foundation Validation

Updated: 2026-11-26

Status vocabulary:

- NOT STARTED
- IN PROGRESS
- BUILD PASSED
- TEST PASSED
- DEVICE VERIFIED
- BLOCKED

| Milestone | Status | Evidence required | Current evidence |
|---|---|---|---|
| Repository audit | IN PROGRESS | Current branch/tree, native/JNI/build/workflow and license review | Branch and source inventory plus architecture/dependency docs completed. Full third-party license audit remains open. |
| Android Host | IN PROGRESS | APK build; host tests; JNI load, native init/thread/filesystem, pause/resume and clean shutdown on Android ARM64 | Linux native host test passed (run 45709304132); Android ARM64 APK build/package verification passed (run 45709339406). Physical Android JNI/lifecycle test remains pending. |
| Wine ARM64 | NOT STARTED | Android/Bionic build, no unresolved symbols, device load, prefix and registry | No Wine Android runtime exists |
| Hangover | NOT STARTED | Translator built and loaded by selected Wine | No translator integrated |
| PE32 | NOT STARTED | Probe calls Win32 APIs, writes log, exits exactly 37 on Android ARM64 | Current probe only cross-compiles |
| Vulkan | NOT STARTED | Repeated frames, present, surface recreation on target device | Existing source contains a diagnostic single-frame experiment only |
| DXVK | NOT STARTED | D3D9 test scene rendered through Vulkan | Not integrated |
| Audio | NOT STARTED | Android audio stream and test tone | Not integrated |
| Input | NOT STARTED | Event forwarding to Windows test process | Not integrated |
| Naruto | NOT STARTED | Original game starts and gameplay is verified | Not integrated |

## Phase 1 CI evidence

- Native host test workflow: https://github.com/suksesjt12-source/StormRuntimeNaruto/actions/runs/45709304132
  - Commit: `efe5194145f2a0d80a9f2575c01e27394da64269`
  - CMake build and CTest: 1/1 passed on Ubuntu 24.04.
  - Test executable SHA-256: `9f68f18bfd2474808747b039ce2fd67d9467c7678a853a8d08e1ec6f76453dfa`
  - Evidence artifact: https://github.com/suksesjt12-source/StormRuntimeNaruto/actions/runs/45709304132/artifacts/15067450199
  - This is a host-side unit test, not an Android device test.
- Android APK build: https://github.com/suksesjt12-source/StormRuntimeNaruto/actions/runs/45709339406
  - Commit: `851db513fe190918b379e2b95c80af12746a4abf`
  - Gradle build, package ID, `arm64-v8a` ABI and `libstormcore.so` package verification: passed.
  - APK SHA-256: `2d08bdd081bcd0ce95ebf18e1230a19746eed4485d956753032d7aa7262beac9`
  - Artifact: https://github.com/suksesjt12-source/StormRuntimeNaruto/actions/runs/45709339406/artifacts/15066557164
  - APK compilation does not prove JNI load or device lifecycle behavior.

A workflow result may only update BUILD PASSED or TEST PASSED when it actually performs that work. DEVICE VERIFIED requires Android runtime logs and recorded device/API/ABI details. No device test is inferred from GitHub-hosted Ubuntu CI.
