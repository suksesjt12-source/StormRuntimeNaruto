package id.stormruntime.naruto

import android.content.Context
import android.net.Uri
import android.system.Os
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Installs separately-built runtime payloads into app-private storage.
 *
 * Archives are treated as untrusted input: paths are normalized, writes are
 * confined to a fresh staging directory, symlinks are deferred until all
 * regular files have been extracted, and special/hard-link entries are denied.
 */
internal object StormRuntimePackageInstaller {
    private const val MAX_ENTRIES = 250_000
    private const val MAX_EXPANDED_BYTES = 3L * 1024 * 1024 * 1024

    data class Result(val ok: Boolean, val detail: String)

    fun installProot(context: Context, archiveUri: Uri, checksumUri: Uri): Result =
        install(context, archiveUri, checksumUri, "proot/root", "root", "storm-proot-install")

    fun installRootfs(context: Context, archiveUri: Uri, checksumUri: Uri): Result =
        install(context, archiveUri, checksumUri, "rootfs", "rootfs", "storm-rootfs-install")

    private fun install(
        context: Context,
        uri: Uri,
        checksumUri: Uri,
        destinationName: String,
        archiveRoot: String,
        stagingName: String
    ): Result {
        val bundle = File(context.filesDir, "storm-runtime")
        val staging = File(context.filesDir, stagingName)
        val destination = File(bundle, destinationName)
        val backup = File(context.filesDir, "$stagingName-backup")
        return try {
            if (staging.exists()) staging.deleteRecursively()
            if (backup.exists()) backup.deleteRecursively()
            if (!staging.mkdirs()) error("Tidak dapat membuat direktori staging.")
            val expectedSha256 = readExpectedSha256(context, checksumUri)
            val actualSha256 = calculateSha256(context, uri)
            require(actualSha256.equals(expectedSha256, ignoreCase = true)) {
                "SHA-256 tidak cocok. Arsip mungkin rusak atau checksum bukan pasangannya."
            }
            val source = context.contentResolver.openInputStream(uri)
                ?: error("File arsip tidak dapat dibuka.")
            source.use { input -> extract(input, staging, archiveRoot) }
            validatePayload(staging, destinationName)

            if (!bundle.exists() && !bundle.mkdirs()) error("Tidak dapat membuat direktori runtime.")
            destination.parentFile?.let { if (!it.exists() && !it.mkdirs()) error("Tidak dapat membuat direktori paket.") }
            if (destination.exists() && !destination.renameTo(backup)) {
                error("Tidak dapat mencadangkan instalasi sebelumnya.")
            }
            if (!staging.renameTo(destination)) {
                if (backup.exists()) backup.renameTo(destination)
                error("Tidak dapat mengaktifkan paket baru.")
            }
            backup.deleteRecursively()
            Result(true, "Paket $destinationName berhasil diekstrak dan dipasang di penyimpanan privat aplikasi.")
        } catch (error: Exception) {
            staging.deleteRecursively()
            Result(false, "Instalasi $destinationName gagal: ${error.message ?: error.javaClass.simpleName}")
        }
    }


    private fun readExpectedSha256(context: Context, uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            var total = 0
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                require(total <= 4096) { "File checksum terlalu besar." }
                output.write(buffer, 0, n)
            }
            output.toByteArray()
        } ?: error("File checksum tidak dapat dibuka.")
        val first = String(bytes, Charsets.UTF_8).trim().split(Regex("\\s+")).firstOrNull().orEmpty()
        require(first.matches(Regex("[A-Fa-f0-9]{64}"))) {
            "Format checksum tidak valid. Pilih file .sha256 yang sesuai."
        }
        return first.lowercase(java.util.Locale.ROOT)
    }

    private fun calculateSha256(context: Context, uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        } ?: error("Arsip tidak dapat dibaca untuk verifikasi checksum.")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun extract(input: InputStream, staging: File, expectedRoot: String) {
        val buffered = BufferedInputStream(input)
        val marked = buffered.markSupported().let { buffered }
        marked.mark(8)
        val header = ByteArray(6)
        val count = marked.read(header)
        marked.reset()
        require(count >= 2) { "Arsip kosong atau tidak valid." }

        val decoded: InputStream = when {
            header[0] == 0x1f.toByte() && header[1] == 0x8b.toByte() ->
                GzipCompressorInputStream(marked)
            count >= 6 && header[0] == 0xfd.toByte() &&
                header[1] == 0x37.toByte() && header[2] == 0x7a.toByte() ->
                XZCompressorInputStream(marked, true)
            else -> error("Format arsip harus .tar.gz atau .tar.xz.")
        }

        val links = mutableListOf<Pair<File, String>>()
        var entries = 0
        var expanded = 0L
        TarArchiveInputStream(decoded).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                entries++
                require(entries <= MAX_ENTRIES) { "Jumlah entri arsip melewati batas." }
                val normalized = normalizeEntry(entry, expectedRoot) ?: continue
                val target = File(staging, normalized)
                require(target.canonicalPath.startsWith(staging.canonicalPath + File.separator) ||
                    target.canonicalFile == staging.canonicalFile) { "Path arsip keluar dari direktori staging." }

                when {
                    entry.isDirectory -> {
                        requireNoSymlinkParents(staging, target)
                        if (!target.exists() && !target.mkdirs()) error("Gagal membuat direktori: $normalized")
                    }
                    entry.isSymbolicLink -> {
                        require(entry.linkName.isNotBlank()) { "Symlink tanpa target." }
                        links += target to entry.linkName
                    }
                    entry.isLink || entry.isCharacterDevice || entry.isBlockDevice || entry.isFIFO ->
                        error("Hardlink atau special file tidak diizinkan: $normalized")
                    entry.isFile -> {
                        requireNoSymlinkParents(staging, target.parentFile ?: staging)
                        target.parentFile?.let { if (!it.exists() && !it.mkdirs()) error("Gagal membuat direktori induk.") }
                        require(!target.exists()) { "Entri duplikat: $normalized" }
                        target.outputStream().buffered().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val n = tar.read(buffer)
                                if (n < 0) break
                                expanded += n
                                require(expanded <= MAX_EXPANDED_BYTES) { "Ukuran hasil ekstraksi melewati batas 3 GiB." }
                                output.write(buffer, 0, n)
                            }
                        }
                        try { Os.chmod(target.absolutePath, (entry.mode and 0x1ff)) } catch (_: Exception) { }
                    }
                    else -> error("Jenis entri arsip tidak didukung: $normalized")
                }
            }
        }

        for ((link, rawTarget) in links) {
            requireNoSymlinkParents(staging, link.parentFile ?: staging)
            val targetText = if (rawTarget.startsWith("/")) {
                rawTarget.removePrefix("/")
            } else {
                File(link.parentFile, rawTarget).relativeTo(staging).path
            }
            val resolved = File(staging, targetText).canonicalFile
            require(resolved.path == staging.canonicalPath ||
                resolved.path.startsWith(staging.canonicalPath + File.separator)) {
                "Target symlink keluar dari root paket: $rawTarget"
            }
            link.parentFile?.let { if (!it.exists() && !it.mkdirs()) error("Gagal membuat direktori symlink.") }
            require(!link.exists()) { "Symlink bertabrakan dengan file yang sudah ada." }
            Files.createSymbolicLink(link.toPath(), link.parentFile.toPath().relativize(File(staging, targetText).toPath()))
        }
    }

    private fun normalizeEntry(entry: TarArchiveEntry, expectedRoot: String): String? {
        val raw = entry.name.replace('\\', '/')
        require(!raw.startsWith("/")) { "Path absolut tidak diizinkan: $raw" }
        val parts = raw.split('/').filter { it.isNotEmpty() && it != "." }
        require(parts.none { it == ".." }) { "Path traversal terdeteksi: $raw" }
        if (parts.isEmpty()) return null
        require(parts.first() == expectedRoot) { "Arsip harus memiliki direktori root '$expectedRoot/'." }
        if (parts.size == 1) return null
        return parts.drop(1).joinToString("/")
    }

    private fun requireNoSymlinkParents(root: File, target: File) {
        var current: File? = target
        while (current != null && current != root) {
            require(!Files.isSymbolicLink(current.toPath())) { "Direktori induk merupakan symlink: $current" }
            current = current.parentFile
        }
    }

    private fun validatePayload(staging: File, name: String) {
        if (name.startsWith("proot/")) {
            val proot = File(staging, "bin/proot")
            val loader = File(staging, "libexec/proot/loader")
            require(proot.isFile && proot.length() > 0L) { "bin/proot tidak ditemukan." }
            require(loader.isFile && loader.length() > 0L) { "libexec/proot/loader tidak ditemukan." }
            require(isAarch64Elf(proot) && isAarch64Elf(loader)) { "PRoot dan loader harus ELF64 AArch64." }
            Os.chmod(proot.absolutePath, 0x1ed)
        } else {
            val bash = File(staging, "bin/bash")
            val loader = File(staging, "lib/ld-linux-aarch64.so.1")
            require(bash.exists()) { "bin/bash tidak ditemukan." }
            require(loader.exists()) { "lib/ld-linux-aarch64.so.1 tidak ditemukan." }
        }
    }

    private fun isAarch64Elf(file: File): Boolean = try {
        FileInputStream(file).use { input ->
            val h = ByteArray(20)
            if (input.read(h) != h.size) return false
            h[0] == 0x7f.toByte() && h[1] == 'E'.code.toByte() &&
                h[2] == 'L'.code.toByte() && h[3] == 'F'.code.toByte() &&
                h[4] == 2.toByte() && h[5] == 1.toByte() &&
                (h[18].toInt() and 0xff) == 183 && h[19] == 0.toByte()
        }
    } catch (_: Exception) { false }
}
