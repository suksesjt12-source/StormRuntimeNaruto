package id.stormruntime.naruto

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.util.Properties
import java.util.concurrent.TimeUnit

/**
 * Android/Bionic-side supervisor for a separately built ARM64 PRoot executable.
 * It runs bounded diagnostics inside a Debian ARM64 Glibc guest.
 */
internal object StormProotHost {
    private const val MAX_LOG_BYTES = 16 * 1024
    private const val TIMEOUT_SECONDS = 30L

    fun runGlibcSmokeTest(context: Context): RuntimeValidation {
        val bundle = File(context.filesDir, "storm-runtime")
        val proot = File(bundle, "proot/root/bin/proot")
        val prootLoader = File(bundle, "proot/root/libexec/proot/loader")
        val rootfs = File(bundle, "rootfs")
        val bash = File(rootfs, "bin/bash")
        val loader = File(rootfs, "lib/ld-linux-aarch64.so.1")

        val preflight = validateBaseRuntime(proot, prootLoader, rootfs, bash, loader)
        if (preflight != null) return preflight

        val result = runGuestCommand(
            bundle, proot, prootLoader, rootfs, null,
            listOf(
                "/bin/bash", "--noprofile", "--norc", "-c",
                "printf 'STORM_GLIBC_OK\\n'; /lib/ld-linux-aarch64.so.1 --version"
            ),
            "storm-proot-smoke.log"
        )
        if (result.timedOut) {
            return RuntimeValidation(StormRuntimeProtocol.STATE_PROOT_TIMEOUT, "Smoke test PRoot melebihi batas 30 detik dan dihentikan.")
        }
        return if (result.exitCode == 0 && result.output.contains("STORM_GLIBC_OK")) {
            RuntimeValidation(
                StormRuntimeProtocol.STATE_GLIBC_SMOKE_OK,
                "PRoot berhasil menjalankan Bash dari Glibc RootFS. Output: ${result.output.trim().take(500)}"
            )
        } else {
            RuntimeValidation(
                StormRuntimeProtocol.STATE_PROOT_FAILED,
                "PRoot/Glibc smoke test gagal (exit ${result.exitCode}). Output: ${result.output.trim().take(700)}"
            )
        }
    }

    fun runCompatibilitySmokeTest(context: Context): RuntimeValidation {
        val bundle = File(context.filesDir, "storm-runtime")
        val proot = File(bundle, "proot/root/bin/proot")
        val prootLoader = File(bundle, "proot/root/libexec/proot/loader")
        val rootfs = File(bundle, "rootfs")
        val compatRoot = File(bundle, "compat")
        val properties = Properties()
        try {
            File(bundle, "manifest.properties").inputStream().use { properties.load(it) }
        } catch (error: Exception) {
            return RuntimeValidation(
                StormRuntimeProtocol.STATE_COMPAT_MISSING,
                "Manifest runtime belum tersedia atau tidak dapat dibaca: ${error.message ?: "I/O error"}"
            )
        }

        val preflight = validateBaseRuntime(
            proot, prootLoader, rootfs, File(rootfs, "bin/bash"),
            File(rootfs, "lib/ld-linux-aarch64.so.1")
        )
        if (preflight != null) return preflight

        fun resolveCompat(key: String): File? {
            val relative = properties.getProperty(key)?.trim().orEmpty()
            if (relative.isEmpty() || relative.startsWith("/") || relative.split('/').any { it == ".." }) return null
            val candidate = File(bundle, relative).canonicalFile
            val compatCanonical = compatRoot.canonicalFile
            return if (candidate.path.startsWith(compatCanonical.path + File.separator)) candidate else null
        }

        val wine = resolveCompat("wine")
        val box64 = resolveCompat("box64")
        val hangover = resolveCompat("hangover.wow64_i386")
        val absent = listOf(
            "wine" to wine, "box64" to box64, "hangover.wow64_i386" to hangover
        ).filter { it.second == null || !it.second.isFile || it.second.length() == 0L }
            .map { "Komponen ${it.first} belum tersedia atau path manifest tidak valid." }
        if (absent.isNotEmpty()) {
            return RuntimeValidation(StormRuntimeProtocol.STATE_COMPAT_MISSING, "Paket kompatibilitas belum lengkap.", absent)
        }
        if (!isAarch64Elf(wine!!) || !isAarch64Elf(box64!!) || !isAarch64Elf(hangover!!)) {
            return RuntimeValidation(
                StormRuntimeProtocol.STATE_COMPAT_INVALID,
                "Wine, Box64, dan Hangover bridge harus ELF64 little-endian AArch64."
            )
        }

        val guestWine = "/opt/storm/compat/" + wine.relativeTo(compatRoot).invariantSeparatorsPath
        val guestBox64 = "/opt/storm/compat/" + box64.relativeTo(compatRoot).invariantSeparatorsPath
        val wineResult = runGuestCommand(
            bundle, proot, prootLoader, rootfs, compatRoot,
            listOf(guestWine, "--version"),
            "storm-wine-version.log"
        )
        if (wineResult.timedOut) {
            return RuntimeValidation(StormRuntimeProtocol.STATE_COMPAT_SMOKE_TIMEOUT, "Wine --version melebihi batas waktu 30 detik.")
        }
        if (wineResult.exitCode != 0 || wineResult.output.isBlank()) {
            return RuntimeValidation(
                StormRuntimeProtocol.STATE_COMPAT_SMOKE_FAILED,
                "Wine ditemukan tetapi gagal memulai di Glibc/PRoot (exit ${wineResult.exitCode}). Output: ${wineResult.output.trim().take(700)}"
            )
        }

        val box64Result = runGuestCommand(
            bundle, proot, prootLoader, rootfs, compatRoot,
            listOf(guestBox64, "--version"),
            "storm-box64-version.log"
        )
        if (box64Result.timedOut) {
            return RuntimeValidation(StormRuntimeProtocol.STATE_COMPAT_SMOKE_TIMEOUT, "Box64 --version melebihi batas waktu 30 detik.")
        }
        if (box64Result.exitCode != 0 || box64Result.output.isBlank()) {
            return RuntimeValidation(
                StormRuntimeProtocol.STATE_COMPAT_SMOKE_FAILED,
                "Wine berhasil merespons, tetapi Box64 gagal memulai (exit ${box64Result.exitCode}). Output: ${box64Result.output.trim().take(700)}"
            )
        }

        return RuntimeValidation(
            StormRuntimeProtocol.STATE_WINE_SMOKE_OK,
            "Wine dan Box64 berhasil merespons --version di dalam PRoot/Glibc. Hangover bridge terdeteksi: ${hangover.relativeTo(bundle).invariantSeparatorsPath}. Ini belum menguji PE32/WoW64 atau game.\nWine: ${wineResult.output.trim().take(250)}\nBox64: ${box64Result.output.trim().take(250)}"
        )
    }

    private data class CommandResult(val exitCode: Int, val output: String, val timedOut: Boolean)

    private fun runGuestCommand(
        bundle: File,
        proot: File,
        prootLoader: File,
        rootfs: File,
        compatRoot: File?,
        guestCommand: List<String>,
        logName: String
    ): CommandResult {
        val log = File(bundle.parentFile, logName)
        return try {
            val command = mutableListOf(
                proot.absolutePath,
                "-0",
                "-r", rootfs.absolutePath,
                "-b", "/dev",
                "-b", "/proc"
            )
            if (compatRoot != null) {
                command += listOf("-b", "${compatRoot.absolutePath}:/opt/storm/compat")
            }
            command += listOf("-w", "/root")
            command.addAll(guestCommand)

            val process = ProcessBuilder(command)
                .directory(bundle)
                .redirectErrorStream(true)
                .redirectOutput(log)
                .apply {
                    environment()["PROOT_LOADER"] = prootLoader.absolutePath
                    environment()["HOME"] = "/root"
                    environment()["TMPDIR"] = "/tmp"
                    environment()["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/opt/storm/compat/bin"
                    environment()["LD_LIBRARY_PATH"] = "/opt/storm/compat/lib:/opt/storm/compat/lib64:/lib/aarch64-linux-gnu:/usr/lib/aarch64-linux-gnu"
                    environment()["LANG"] = "C"
                    environment()["WINEDEBUG"] = "-all"
                }
                .start()

            val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
                CommandResult(-1, readBounded(log), true)
            } else {
                CommandResult(process.exitValue(), readBounded(log), false)
            }
        } catch (error: Exception) {
            CommandResult(-1, "${error.javaClass.simpleName}: ${error.message ?: "I/O error"}", false)
        } finally {
            log.delete()
        }
    }

    private fun validateBaseRuntime(
        proot: File,
        prootLoader: File,
        rootfs: File,
        bash: File,
        glibcLoader: File
    ): RuntimeValidation? {
        if (!proot.isFile || proot.length() == 0L || !proot.canExecute() || !isAarch64Elf(proot)) {
            return RuntimeValidation(
                StormRuntimeProtocol.STATE_PROOT_INVALID,
                "PRoot ARM64 belum tersedia, tidak executable, atau format ELF-nya tidak valid."
            )
        }
        if (!prootLoader.isFile || !isAarch64Elf(prootLoader)) {
            return RuntimeValidation(StormRuntimeProtocol.STATE_PROOT_INVALID, "PRoot companion loader tidak ditemukan atau bukan ELF64 AArch64.")
        }
        if (!bash.isFile || !glibcLoader.isFile) {
            return RuntimeValidation(
                StormRuntimeProtocol.STATE_ROOTFS_MISSING,
                "RootFS Glibc belum lengkap; diperlukan /bin/bash dan /lib/ld-linux-aarch64.so.1."
            )
        }
        if (!isAarch64Elf(bash) || !isAarch64Elf(glibcLoader)) {
            return RuntimeValidation(StormRuntimeProtocol.STATE_ROOTFS_INVALID, "Bash dan dynamic loader harus ELF64 AArch64.")
        }
        return null
    }

    private fun isAarch64Elf(file: File): Boolean = try {
        RandomAccessFile(file, "r").use { input ->
            if (input.length() < 20) return false
            val ident = ByteArray(20)
            input.readFully(ident)
            ident[0] == 0x7f.toByte() &&
                ident[1] == 'E'.code.toByte() &&
                ident[2] == 'L'.code.toByte() &&
                ident[3] == 'F'.code.toByte() &&
                ident[4] == 2.toByte() &&
                ident[5] == 1.toByte() &&
                (ident[18].toInt() and 0xff) == 183 &&
                ident[19] == 0.toByte()
        }
    } catch (_: Exception) {
        false
    }

    private fun readBounded(file: File): String {
        if (!file.isFile) return ""
        return try {
            file.inputStream().use { input ->
                val buffer = ByteArray(MAX_LOG_BYTES)
                var count = 0
                while (count < buffer.size) {
                    val read = input.read(buffer, count, buffer.size - count)
                    if (read <= 0) break
                    count += read
                }
                String(buffer, 0, count, Charsets.UTF_8)
            }
        } catch (_: Exception) {
            ""
        }
    }
}
