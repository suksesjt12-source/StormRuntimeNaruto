package id.stormruntime.naruto

import android.content.Context
import java.io.File
import java.util.Properties

internal data class RuntimeValidation(
    val state: String,
    val detail: String,
    val missing: List<String> = emptyList()
)

/**
 * Validates a future Storm Glibc bundle in app-private storage.
 *
 * This does not execute native code. A valid payload is not considered
 * launch-capable until the Android process host, Wine Android display path,
 * and process supervision implementation have passed their own tests.
 */
internal object StormRuntimeManifest {
    private const val FORMAT = "storm-runtime-bundle-v1"
    private const val MANIFEST = "manifest.properties"

    private val requiredFiles = linkedMapOf(
        "glibc.loader" to "GNU Glibc AArch64 dynamic loader",
        "glibc.libc" to "GNU Glibc shared library",
        "proot" to "PRoot ARM64 executable",
        "wine" to "ARM64 Wine executable",
        "hangover.wow64_i386" to "Hangover Box64 WoW64 ARM64 DLL"
    )

    fun inspect(context: Context): RuntimeValidation {
        val bundle = File(context.filesDir, "storm-runtime")
        val manifest = File(bundle, MANIFEST)
        if (!manifest.isFile) {
            return RuntimeValidation(
                StormRuntimeProtocol.STATE_NO_BUNDLE,
                "Glibc runtime bundle belum dipasang. APK saat ini hanya membawa launcher dan Storm Core IPC."
            )
        }

        val properties = Properties()
        try {
            manifest.inputStream().use { input -> properties.load(input) }
        } catch (error: Exception) {
            return RuntimeValidation(
                StormRuntimeProtocol.STATE_INCOMPLETE,
                "Manifest runtime tidak dapat dibaca: ${error.message ?: "I/O error"}"
            )
        }

        if (properties.getProperty("format") != FORMAT) {
            return RuntimeValidation(
                StormRuntimeProtocol.STATE_INCOMPLETE,
                "Format manifest tidak dikenal; diperlukan $FORMAT."
            )
        }
        if (properties.getProperty("abi") != "arm64-v8a") {
            return RuntimeValidation(
                StormRuntimeProtocol.STATE_INCOMPLETE,
                "ABI runtime harus arm64-v8a."
            )
        }
        if (properties.getProperty("libc") != "glibc") {
            return RuntimeValidation(
                StormRuntimeProtocol.STATE_INCOMPLETE,
                "Userspace utama harus menggunakan GNU Glibc."
            )
        }

        val missing = mutableListOf<String>()
        for ((key, label) in requiredFiles) {
            val relative = properties.getProperty(key)?.trim().orEmpty()
            if (relative.isEmpty()) {
                missing += "$label (manifest key: $key)"
                continue
            }
            val file = resolveInside(bundle, relative)
            if (file == null || !file.isFile || file.length() == 0L) {
                missing += "$label ($relative)"
            } else if (key in setOf("proot", "wine", "box64") && !file.canExecute()) {
                missing += "$label (file belum executable: $relative)"
            }
        }

        val optionalBox64 = properties.getProperty("box64")?.trim().orEmpty()
        if (optionalBox64.isNotEmpty()) {
            val box64File = resolveInside(bundle, optionalBox64)
            if (box64File == null || !box64File.isFile || box64File.length() == 0L ||
                !box64File.canExecute()) {
                missing += "Box64 Linux ARM64 executable ($optionalBox64)"
            }
        }

        if (missing.isNotEmpty()) {
            return RuntimeValidation(
                StormRuntimeProtocol.STATE_INCOMPLETE,
                "Bundle ditemukan, tetapi payload belum lengkap atau belum memiliki permission yang benar.",
                missing
            )
        }

        return RuntimeValidation(
            StormRuntimeProtocol.STATE_LAUNCH_BLOCKED,
            "Payload dasar terdeteksi. Peluncuran tetap dikunci sampai Android process host, display driver, dan uji PE32 pada perangkat nyata selesai."
        )
    }

    private fun resolveInside(root: File, relativePath: String): File? {
        if (relativePath.startsWith("/") || relativePath.split('/').any { it == ".." }) return null
        return try {
            val canonicalRoot = root.canonicalFile
            val candidate = File(root, relativePath).canonicalFile
            if (candidate.path == canonicalRoot.path ||
                candidate.path.startsWith(canonicalRoot.path + File.separator)
            ) candidate else null
        } catch (_: Exception) {
            null
        }
    }
}
