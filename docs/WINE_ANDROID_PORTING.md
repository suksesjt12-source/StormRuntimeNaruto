# Wine Android Porting Notes

Status: research only — Phase 2 has not started.

The current application is a normal Android Activity process using Bionic and the Android linker. A Linux ARM64 Wine build is not automatically compatible with this process or with an APK. Termux packages are also not drop-in APK dependencies: their prefix, package paths, libraries, display integration, and runtime assumptions must be audited.

Before selecting an integration model, evaluate:

1. Wine Android/Bionic support and required upstream patches.
2. ELF interpreter and dynamic linker behavior.
3. Every DT_NEEDED dependency and symbol version.
4. Android linker namespaces and permitted library search paths.
5. Threading, signal handling, memory mapping, process creation, and seccomp.
6. Wine prefix creation, registry initialization, and filesystem mapping.
7. Whether Wine must run in a separate process with an IPC protocol to the Activity.
8. Android display-driver availability and lifecycle model.
9. License obligations for Wine and Hangover components.

The Termux Hangover recipe previously inspected disables `wineandroid_drv`; therefore it is a packaging reference, not proof of an Android display implementation. Do not package its Linux/Termux output into this app.

Phase 2 acceptance is blocked until a reproducible Android/Bionic build can be loaded on an Android ARM64 device and can create a prefix. Until then, PE32 execution is not claimed.
