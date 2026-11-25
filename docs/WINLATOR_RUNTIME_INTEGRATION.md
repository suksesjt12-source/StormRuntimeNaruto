# Storm Runtime Naruto integration

Storm Runtime's Android launcher is patched into the pinned runtime app at build time. The launcher replaces the Winlator navigation screen with a Storm-branded Naruto screen, uses Android's Storage Access Framework to import the selected game directory into app-specific storage, verifies that `NSUNSR.exe` is a PE32 x86 executable, and creates/reuses a dedicated Wine container.

The initial container profile targets the Helio G100 / Mali-G57 class: Vortek + Gladio graphics backends, DXVK, 960x540 display, Box64 Performance, PulseAudio, and all available CPU cores. The launcher starts the upstream XServer display activity with the selected executable path; the existing runtime supplies Wine, Box64, graphics translation, audio, and touch-input implementation.

Runtime source: https://github.com/brunodev85/winlator-app at pinned commit `f8a211ce7fd776de9a2642b45d166016876eaa86`. Preserve the upstream LGPL-2.1 license and third-party notices. No proprietary Naruto game files are included.

CI verifies source patching and APK packaging. This is not equivalent to an on-device game compatibility test: first-time Wine prefix setup, GPU driver behavior, game launch, audio, and touch controls still require testing on the target Helio G100 device.
