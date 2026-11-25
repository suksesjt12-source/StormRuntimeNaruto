# Runtime source integration

Storm Runtime Naruto now pins the Android runtime source from:

- Repository: https://github.com/brunodev85/winlator-app
- Pinned commit: `f8a211ce7fd776de9a2642b45d166016876eaa86`
- Upstream license: GNU Lesser General Public License v2.1 (see the submodule's `LICENSE` file).

The upstream application supplies the runtime stack used as the integration base, including its Android Wine root filesystem, Box64 runtime, DXVK packages, graphics backends, PulseAudio components, and input/control implementation. Storm's build workflow checks out this exact submodule commit, applies a small reproducible package-name/app-label patch, and builds the runtime application as the Storm APK.

This is a source integration milestone, not proof of game compatibility. The upstream UI and container model still need a Naruto-specific launcher workflow; the actual game must be tested on the target Helio G100/Mali-G57 device. No proprietary Naruto game files are included.

When redistributing builds, retain upstream notices and comply with LGPL-2.1 and all licenses of bundled components. The pinned source's own license and third-party notices remain in the submodule.
