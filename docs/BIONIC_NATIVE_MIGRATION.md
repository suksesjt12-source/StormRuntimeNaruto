# Storm Runtime — Bionic-Native Runtime Migration

Status: architecture decision and implementation gate
Target: Android 14, ARM64, Infinix Hot 50 4G (Helio G100 / Mali-G57 MC2)
Base: `experiment/wine-hangover-android`

## Objective

Replace the current Winlator application/runtime dependency with a Storm-owned Android runtime package. The resulting APK must launch the original, user-supplied Windows PE32 game executable. This is not a game port or remake.

"Bionic-native" means that native runtime binaries and their dependency closure are built for Android's Bionic ABI and are launched in an Android-supported way. It does not mean that Bionic itself executes Windows programs.

## Architecture decision

Keep the Storm Android UI process separate from the Windows compatibility process.

- **Storm UI:** Kotlin/Android Activity, game-folder permission and import, settings, diagnostics, lifecycle, and user-facing status.
- **Runtime supervisor:** Android-native ARM64 process started by the app; owns runtime files, prefix lifecycle, logs, process state, and IPC.
- **Wine/Hangover:** Windows API and PE loader compatibility layer, built for the selected Android/Bionic target.
- **x86 translation:** Hangover WoW64 emulator integration (FEX or Box64 WoW64) for the game's i386 code. Bionic is not an x86 translator.
- **Display:** a validated Android display driver or a separately validated Android display bridge. A Vulkan probe in the UI is not a Wine display backend.
- **Graphics:** D3D9-to-Vulkan path (candidate DXVK) only after the device Vulkan feature/extension requirements and surface presentation are validated.
- **Audio/input:** explicit bridge from Windows audio/input APIs to Android audio and input facilities.
- **Game:** user's own game files; no copyrighted game data is bundled.

## Important technical constraints

1. Android uses Bionic, while ordinary Linux Wine/Hangover builds commonly target glibc. A glibc ELF cannot be treated as a Bionic executable merely by copying it into an APK.
2. Android app execution policy and linker behavior must be respected. The runtime process launch mechanism must be validated on the target Android version; do not assume app-private executables can be launched with ordinary `execve()`.
3. Do not load the full Wine runtime into the Kotlin/JVM Activity process as a shortcut. Wine's signal/thread/register assumptions and Android seccomp behavior make process separation an explicit design requirement.
4. A successful native/Gradle build proves compilation and packaging only. It does not prove Wine startup, PE execution, graphics, audio, input, or gameplay.
5. No existing source project or binary bundle is accepted as a drop-in solution until its revision, license, ABI, dependency closure, launch model, and device behavior are checked.

## External technical references reviewed

- Wine Android driver MR10569 v15 (May 25, 2026) proposes splitting the Android Activity/UI process from the Wine process. It is a patch series/proposal; verify its current upstream status and exact revision before adopting it:
  https://gitlab.winehq.org/wine/wine/-/merge_requests/10569
- Hangover upstream documents WoW64 emulator DLLs for x86 Windows applications on AArch64 Wine:
  https://github.com/AndreRH/hangover
- Piperella's public Hangover-Bionic repository describes a Bionic-targeted Hangover build workflow. Its repository metadata reports no declared license and very limited project activity, so treat it as a research reference only; do not copy or redistribute its code/artifacts until licensing, source provenance, build reproducibility, and runtime behavior are independently verified:
  https://github.com/Piperella/Piperella-Hangover-Bionic

## Implementation milestones and acceptance gates

### M0 — Freeze current baseline
- Preserve the current working branch and its APK artifact.
- Work only on `experiment/bionic-native-runtime`; do not modify `main`.
- Remove no current source/runtime until a replacement passes its gate.

### M1 — Bionic toolchain and ABI proof
- Pin Android NDK, compiler, source revisions, patches, and checksums.
- Build a minimal ARM64 Bionic executable and all required shared libraries.
- Inspect ELF interpreter, DT_NEEDED closure, symbol versions, and ABI.
- Verify the app can launch the helper process on Android 14 without root.
- Capture stdout, stderr, exit status, and crash diagnostics.

**Pass condition:** a packaged Bionic helper launches on the target Android device and returns a known exit code.

### M2 — Wine/Hangover process proof
- Select a reproducible Wine/Hangover Android/Bionic source revision and patch set.
- Build ARM64 Wine plus the i386 WoW64 translator integration.
- Implement the separate runtime process and a versioned IPC protocol.
- Initialize a clean prefix and collect Wine logs.

**Pass condition:** the packaged runtime initializes a prefix and reports a clean shutdown on the target device.

### M3 — Windows PE32 proof
- Use the repository's existing original PE32 test program.
- Run it under the packaged Wine/Hangover runtime.
- Verify its expected diagnostic log and exact exit code 37.

**Pass condition:** both log contents and exit code 37 are observed on a physical Android device.

### M4 — Display and graphics proof
- Implement or integrate the Android display path.
- Validate Android surface lifecycle and swapchain recreation.
- Check actual Mali Vulkan version, extensions, and required features.
- Initialize a basic Windows window, then D3D9, then DXVK.

**Pass condition:** a controlled Direct3D test renders and presents repeatedly on the target device without lifecycle or device-loss errors.

### M5 — Audio and input proof
- Add audio output and input-event bridges.
- Verify latency, lifecycle, focus, touch mapping, and controller mapping independently.

**Pass condition:** a Windows test application can produce audio and receive input reliably.

### M6 — Naruto integration
- Add game-specific launch configuration and Mali-G57 defaults.
- Validate file paths, dependencies, startup, menu, gameplay, and sustained frame pacing.
- Compare against the user's known Winlator baseline (960x540, target 30 FPS); do not promise equal or better performance without measurements.

**Pass condition:** the original game reaches its menu and completes a sustained gameplay test on the target phone.

## Current status

All M1–M6 items are pending. The existing repository audits explicitly state that Wine/Hangover, a Windows process host, Android Wine display integration, DXVK/D3D9, audio/input forwarding, and actual `NSUNSR.exe` execution are not yet implemented. The previous successful APK build is not evidence that these runtime milestones pass.

## Rollback policy

Keep the previous experiment branch intact. Do not delete the Winlator-derived baseline or replace its workflow until the Bionic-native runtime passes M1 and M2. Do not claim a standalone Windows runtime until M3 passes on physical hardware.
