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
| Repository audit | TEST PASSED | Current branch/tree and source review plus architecture/dependency docs | Source audit performed; documentation committed with this phase |
| Android Host | IN PROGRESS | APK build; host tests; JNI load, native init/thread/filesystem, pause/resume and clean shutdown on Android ARM64 | Host implementation and host CI tests being added; device test not yet performed |
| Wine ARM64 | NOT STARTED | Android/Bionic build, no unresolved symbols, device load, prefix and registry | No Wine Android runtime exists |
| Hangover | NOT STARTED | Translator built and loaded by selected Wine | No translator integrated |
| PE32 | NOT STARTED | Probe calls Win32 APIs, writes log, exits exactly 37 on Android ARM64 | Current probe only cross-compiles |
| Vulkan | NOT STARTED | Repeated frames, present, surface recreation on target device | Existing source contains a diagnostic single-frame experiment only |
| DXVK | NOT STARTED | D3D9 test scene rendered through Vulkan | Not integrated |
| Audio | NOT STARTED | Android audio stream and test tone | Not integrated |
| Input | NOT STARTED | Event forwarding to Windows test process | Not integrated |
| Naruto | NOT STARTED | Original game starts and gameplay is verified | Not integrated |

A workflow result may only update BUILD PASSED or TEST PASSED when it actually performs that work. DEVICE VERIFIED requires physical/emulated Android runtime logs and recorded device/API/ABI details. No device test is inferred from GitHub-hosted Ubuntu CI.
