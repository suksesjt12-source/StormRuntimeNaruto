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
import java.io.RandomAccessFile
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

    fun installCompatibility(context: Context, archiveUri: Uri, checksumUri: Uri): Result =
        install(context, archiveUri, checksumUri, "compat", "compat", "storm-compat-install")

    fun installDxvk(context: Context, archiveUri: Uri, checksumUri: Uri): Result =
        install(context, archiveUri, checksumUri, "dxvk", "runtime-artifact", "storm-dxvk-install")

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
            try {
                writeBundleManifest(bundle)
            } catch (manifestError: Exception) {
                destination.deleteRecursively()
                if (backup.exists()) backup.renameTo(destination)
                throw manifestError
            }
            backup.deleteRecursively()
            Result(true, "Paket $destinationName berhasil diekstrak dan dipasang di penyimpanan privat aplikasi.")
        } catch (error: Exception) {
            staging.deleteRecursively()
            Result(false, "Instalasi $destinationName gagal: ${error.message ?: error.javaClass.simpleName}")
        }
    }


    private fun writeBundleManifest(bundle: File) {
        val rootfs = File(bundle, "rootfs")
        val compat = File(bundle, "compat")
        val compatManifest = File(compat, "manifest.properties")
        val props = java.util.Properties()
        props.setProperty("format", "storm-runtime-bundle-v1")
        props.setProperty("abi", "arm64-v8a")
        props.setProperty("libc", "glibc")
        props.setProperty("glibc.loader", "rootfs/lib/ld-linux-aarch64.so.1")
        val libcCandidates = listOf(
            "lib/aarch64-linux-gnu/libc.so.6",
            "lib/libc.so.6",
            "usr/lib/aarch64-linux-gnu/libc.so.6"
        )
        val libcPath = libcCandidates.firstOrNull { File(rootfs, it).isFile }
            ?: libcCandidates.first()
        props.setProperty("glibc.libc", "rootfs/$libcPath")
        props.setProperty("proot", "proot/root/bin/proot")
        if (compatManifest.isFile) {
            val compatProps = java.util.Properties()
            compatManifest.inputStream().use { compatProps.load(it) }
            for (key in listOf("wine", "box64", "hangover.wow64_i386")) {
                val relative = compatProps.getProperty(key)?.trim().orEmpty()
                if (relative.isNotEmpty()) props.setProperty(key, "compat/$relative")
            }
        }
        val dxvkDlls = listOf(
            "dxvk.d3d9.x86" to "dxvk/x32/d3d9.dll",
            "dxvk.dxgi.x86" to "dxvk/x32/dxgi.dll",
            "dxvk.d3d9.x64" to "dxvk/x64/d3d9.dll",
            "dxvk.dxgi.x64" to "dxvk/x64/dxgi.dll"
        )
        for ((key, relative) in dxvkDlls) {
            if (File(bundle, relative).isFile) props.setProperty(key, relative)
        }
        val temporary = File(bundle, "manifest.properties.tmp")
        temporary.outputStream().use { props.store(it, "LauncherPC runtime bundle manifest") }
        val destination = File(bundle, "manifest.properties")
        if (destination.exists() && !destination.delete()) {
            temporary.delete()
            error("Tidak dapat mengganti manifest runtime.")
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete()
            error("Tidak dapat mengaktifkan manifest runtime.")
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
        } else if (name == "rootfs") {
            val bash = File(staging, "bin/bash")
            val loader = File(staging, "lib/ld-linux-aarch64.so.1")
            require(bash.exists()) { "bin/bash tidak ditemukan." }
            require(loader.exists()) { "lib/ld-linux-aarch64.so.1 tidak ditemukan." }
            require(isAarch64Elf(bash) && isAarch64Elf(loader)) {
                "Bash dan dynamic loader Glibc harus ELF64 AArch64."
            }
        } else if (name == "dxvk") {
            val dlls = listOf(
                Triple("x32/d3d9.dll", 0x014c, 0x010b),
                Triple("x32/dxgi.dll", 0x014c, 0x010b),
                Triple("x64/d3d9.dll", 0x8664, 0x020b),
                Triple("x64/dxgi.dll", 0x8664, 0x020b)
            )
            for ((relative, machine, magic) in dlls) {
                val dll = File(staging, relative)
                require(dll.isFile && dll.length() > 0L && isExpectedPeDll(dll, machine, magic)) {
                    "DLL DXVK $relative tidak ditemukan atau arsitektur PE-nya salah."
                }
            }
        } else if (name == "compat") {
            val manifest = File(staging, "manifest.properties")
            require(manifest.isFile) { "compat/manifest.properties tidak ditemukan." }
            val properties = java.util.Properties()
            manifest.inputStream().use { properties.load(it) }
            require(properties.getProperty("format") == "storm-compatibility-v1") {
                "Format manifest compatibility harus storm-compatibility-v1."
            }
            for (key in listOf("wine", "box64", "hangover.wow64_i386")) {
                val relative = properties.getProperty(key)?.trim().orEmpty()
                require(relative.isNotEmpty() && !relative.startsWith("/") &&
                    relative.split('/').none { it == ".." }) {
                    "Path $key kosong atau tidak aman di manifest compatibility."
                }
                val component = File(staging, relative).canonicalFile
                require(component.path.startsWith(staging.canonicalPath + File.separator) &&
                    component.isFile && component.length() > 0L) {
                    "Komponen $key tidak ditemukan atau path keluar dari paket."
                }
                require(isAarch64Elf(component)) {
                    "Komponen $key harus berupa ELF64 AArch64."
                }
                if (key == "wine" || key == "box64") {
                    Os.chmod(component.absolutePath, 0x1ed)
                }
            }
        }
    }

    private fun isExpectedPeDll(file: File, expectedMachine: Int, expectedMagic: Int): Boolean = try {
        RandomAccessFile(file, "r").use { input ->
            if (input.length() < 0x40) return false
            val dos = ByteArray(0x40)
            input.readFully(dos)
            if (dos[0] != 'M'.code.toByte() || dos[1] != 'Z'.code.toByte()) return false
            val peOffset = java.nio.ByteBuffer.wrap(dos)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt(0x3c).toLong() and 0xffffffffL
            if (peOffset < 0x40 || peOffset + 26 > input.length()) return false
            input.seek(peOffset)
            val header = ByteArray(26)
            input.readFully(header)
            if (header[0] != 'P'.code.toByte() || header[1] != 'E'.code.toByte() ||
                header[2] != 0.toByte() || header[3] != 0.toByte()) return false
            val fields = java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val machine = fields.getShort(4).toInt() and 0xffff
            val optionalMagic = fields.getShort(24).toInt() and 0xffff
            machine == expectedMachine && optionalMagic == expectedMagic
        }
    } catch (_: Exception) { false }

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
