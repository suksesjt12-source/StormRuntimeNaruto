package id.stormruntime.naruto

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * Describes the first MediaTek-oriented runtime profile.
 *
 * These values are launcher metadata until a real runtime host applies them.
 */
data class StormPerformanceProfile(
    val name: String,
    val width: Int,
    val height: Int,
    val frameLimit: Int,
    val box64Preset: String,
    val dxvkVersion: String,
    val dxvkAsync: Boolean,
    val maxRuntimeMemoryMiB: Int
) {
    val summary: String
        get() = "$name • ${width}×${height} • ${frameLimit} FPS cap • Box64 $box64Preset • $dxvkVersion • memory budget $maxRuntimeMemoryMiB MiB"
}

object StormRuntimeConfig {
    const val PRIMARY_BACKEND = "glibc-arm64"
    const val EXPERIMENTAL_BACKEND = "bionic-arm64"
    const val DXVK_DISTRIBUTION = "DXVK-Sarek"
    const val DXVK_VERSION = "1.11.0"
    const val DXVK_GIT_TAG = "v1.11.0"

    val defaultProfile = StormPerformanceProfile(
        name = "Helio G100 Balanced",
        width = 960,
        height = 540,
        frameLimit = 30,
        box64Preset = "Performance",
        dxvkVersion = "Sarek $DXVK_VERSION",
        dxvkAsync = false,
        maxRuntimeMemoryMiB = 2048
    )

    fun deviceSummary(context: Context? = null): String {
        val soc = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else "API < 31"
        val abi = Build.SUPPORTED_ABIS.joinToString()
        val memory = context?.let {
            val manager = it.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            ActivityManager.MemoryInfo().also(manager::getMemoryInfo).totalMem / (1024L * 1024L)
        }
        val memoryText = memory?.let { " • RAM ${it / 1024} GiB" } ?: ""
        return "SoC: $soc • hardware: ${Build.HARDWARE} • ABI: $abi$memoryText"
    }
}
