package id.stormruntime.naruto

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

/**
 * Android/Bionic-side supervisor for a separately built ARM64 PRoot executable.
 *
 * This class deliberately does not bundle or emulate PRoot. It verifies the
 * host binary and Glibc rootfs before attempting a bounded smoke test.
 */
internal object StormProotHost {
    private const val MAX_LOG_BYTES = 16 * 1024
    private const val TIMEOUT_SECONDS = 30L

    fun runGlibcSmokeTest(context: Context): RuntimeValidation {
        val bundle = File(context.filesDir, "storm-runtime")
        val prootPackage = File(bundle, "proot/root")
        val proot = File(prootPackage, "bin/proot")
        val prootLoader = File(prootPackage, "libexec/proot/loader")
        val rootfs = File(bundle, "rootfs")
        val bash = File(rootfs, "bin/bash")
        val loader = File(rootfs, "lib/ld-linux-aarch64.so.1")

        if (!proot.isFile || proot.length() == 0L) {
            return RuntimeValidation(
                StormRuntimeProtocol.STATE_PROOT_MISSING,
                "PRoot ARM64 belum dipasang di app-private storage: ${proot.relativeTo(context.filesDir)}"
            )
        }
        if (!prootLoader.isFile || prootLoader.length() == 0L || !isAarch64Elf(prootLoader)) {
            return RuntimeValidation(
                StormRuntimeProtocol.STATE_PROOT_INVALID,
                "PRoot companion loader tidak ditemukan atau bukan ELF64 AArch64: ${prootLoader.relativeTo(context.filesDir)}"
            )
        }
        if (!proot.canExecute()) {
            return RuntimeValidation(
                StormRuntimeProtocol.STATE_PROOT_INVALID,
                "File PRoot ada tetapi tidak executable. Android juga dapat membatasi eksekusi dari direktori app-private; ini perlu diuji pada perangkat."
            )
        }
        if (!isAarch64Elf(proot)) {
            return RuntimeValidation(
                StormRuntimeProtocol.STATE_PROOT_INVALID,
                "PRoot bukan ELF64 little-endian AArch64 yang valid."
            )
        }
        if (!bash.isFile || !loader.isFile) {
            return RuntimeValidation(
                StormRuntimeProtocol.STATE_ROOTFS_MISSING,
                "RootFS Glibc belum dipasang lengkap. Diperlukan rootfs/bin/bash dan rootfs/lib/ld-linux-aarch64.so.1."
            )
        }
        if (!isAarch64Elf(bash) || !isAarch64Elf(loader)) {
            return RuntimeValidation(
                StormRuntimeProtocol.STATE_ROOTFS_INVALID,
                "Bash dan dynamic loader harus berupa ELF64 AArch64."
            )
        }

        val log = File(context.cacheDir, "storm-proot-smoke.log")
        return try {
            val command = listOf(
                proot.absolutePath,
                "-0",
                "-r", rootfs.absolutePath,
                "-w", "/root",
                "/bin/bash",
                "--noprofile",
                "--norc",
                "-c",
                "printf 'STORM_GLIBC_OK\\n'; /lib/ld-linux-aarch64.so.1 --version"
            )
            val process = ProcessBuilder(command)
                .directory(bundle)
                .redirectErrorStream(true)
                .redirectOutput(log)
                .apply {
                    environment()["HOME"] = "/root"
                    environment()["TMPDIR"] = "/tmp"
                    environment()["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
                    environment()["LANG"] = "C"
                }
                .start()

            val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
                return RuntimeValidation(
                    StormRuntimeProtocol.STATE_PROOT_TIMEOUT,
                    "Smoke test PRoot melebihi batas 30 detik dan dihentikan."
                )
            }

            val output = readBounded(log)
            if (process.exitValue() == 0 && output.contains("STORM_GLIBC_OK")) {
                RuntimeValidation(
                    StormRuntimeProtocol.STATE_GLIBC_SMOKE_OK,
                    "PRoot berhasil menjalankan /bin/bash dari Glibc RootFS. Output: ${output.trim().take(500)}"
                )
            } else {
                RuntimeValidation(
                    StormRuntimeProtocol.STATE_PROOT_FAILED,
                    "PRoot berjalan tetapi smoke test gagal (exit ${process.exitValue()}). Output: ${output.trim().take(700)}"
                )
            }
        } catch (error: Exception) {
            RuntimeValidation(
                StormRuntimeProtocol.STATE_PROOT_FAILED,
                "Tidak dapat memulai PRoot: ${error.javaClass.simpleName}: ${error.message ?: "I/O error"}"
            )
        } finally {
            log.delete()
        }
    }

    private fun isAarch64Elf(file: File): Boolean {
        return try {
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
