package id.stormruntime.naruto

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.*
import androidx.documentfile.provider.DocumentFile
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : Activity(), SurfaceHolder.Callback {
    private lateinit var folderStatus: TextView
    private lateinit var exeStatus: TextView
    private lateinit var runtimeStatus: TextView
    private lateinit var surfaceStatus: TextView
    private var selectedTree: Uri? = null

    companion object { private const val PICK_GAME_FOLDER = 4101 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        val hostInitialized = NativeBridge.initialize(filesDir.absolutePath)
        runtimeStatus.text = if (hostInitialized) {
            NativeBridge.runtimeStatus()
        } else {
            "Native host initialization failed: ${NativeBridge.lastError()}"
        }
        exeStatus.text = "NSUNSR.exe: belum diperiksa"
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 22, 28, 22)
            setBackgroundColor(0xFF10131B.toInt())
        }
        scroll.addView(panel)
        setContentView(scroll)

        fun label(text: String, size: Float = 16f) = TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(0xFFF1F3F8.toInt())
            setPadding(0, 8, 0, 8)
        }
        fun button(text: String, action: () -> Unit) = Button(this).apply {
            this.text = text
            setOnClickListener { action() }
        }

        panel.addView(label("STORM RUNTIME", 26f))
        panel.addView(label("Naruto Shippuden: Ultimate Ninja Storm Revolution"))
        panel.addView(label("Android Native • Surface Diagnostic Prototype 0.2.0"))
        panel.addView(button("Pilih folder instalasi game") { pickFolder() })
        folderStatus = label("Folder game: belum dipilih")
        panel.addView(folderStatus)
        exeStatus = label("NSUNSR.exe: belum diperiksa")
        panel.addView(exeStatus)

        panel.addView(label("Pengaturan awal", 20f))
        panel.addView(label("Resolusi target pengujian: 960 × 540"))
        panel.addView(label("Batas FPS target: 30 FPS"))
        panel.addView(label("Android Vulkan surface diagnostic", 20f))
        surfaceStatus = label("Surface: menunggu SurfaceView dibuat")
        panel.addView(surfaceStatus)

        val surfaceView = SurfaceView(this)
        surfaceView.holder.addCallback(this)
        panel.addView(surfaceView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (220 * resources.displayMetrics.density).toInt()
        ))

        panel.addView(label("Runtime status", 20f))
        runtimeStatus = label(NativeBridge.status())
        panel.addView(runtimeStatus)
        panel.addView(label("Vulkan device diagnostic:"))
        panel.addView(label(NativeBridge.vulkanStatus()))
        panel.addView(label("Catatan: surface probe membuat VkSurfaceKHR dan memeriksa present support. Belum membuat swapchain atau menjalankan Wine/EXE."))
        panel.addView(button("Periksa ulang file game") { inspectGameFolder() })
        panel.addView(button("Periksa ulang Android surface") {
            surfaceStatus.text = NativeBridge.androidSurfaceStatus()
        })
        panel.addView(button("Lihat status runtime") {
            runtimeStatus.text = NativeBridge.runtimeStatus()
        })
    }

    override fun onDestroy() {
        NativeBridge.shutdown()
        super.onDestroy()
    }

    private fun pickFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, PICK_GAME_FOLDER)
    }

    @Deprecated("Use Activity Result API in a later iteration")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_GAME_FOLDER && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            contentResolver.takePersistableUriPermission(uri, flags)
            selectedTree = uri
            folderStatus.text = "Folder game: dipilih"
            inspectGameFolder()
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceStatus.text = NativeBridge.setAndroidSurface(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        surfaceStatus.text = NativeBridge.androidSurfaceStatus()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        NativeBridge.clearAndroidSurface()
        surfaceStatus.text = "Surface: dilepas (lifecycle destroy)"
    }

    private fun inspectGameFolder() {
        val uri = selectedTree ?: run {
            exeStatus.text = "Pilih folder game terlebih dahulu."
            return
        }
        val tree = DocumentFile.fromTreeUri(this, uri)
        val exe = tree?.findFile("NSUNSR.exe")
        if (exe?.isFile != true) {
            exeStatus.text = "NSUNSR.exe: tidak ditemukan di folder utama."
            return
        }

        try {
            val bytes = contentResolver.openInputStream(exe.uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (total < 1024 * 1024) {
                    val read = input.read(buffer, 0, minOf(buffer.size, 1024 * 1024 - total))
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    total += read
                }
                output.toByteArray()
            } ?: throw IllegalStateException("File tidak dapat dibaca.")

            val peType = inspectPe32X86(bytes)
            val fileSize = exe.length()
            exeStatus.text = when (peType) {
                true -> "NSUNSR.exe: PE32 x86 (i386) terdeteksi • ukuran ${fileSize} byte. Ini hanya validasi file, bukan bukti runtime siap."
                false -> "NSUNSR.exe ditemukan, tetapi formatnya bukan PE32 x86 yang diharapkan."
                null -> "NSUNSR.exe ditemukan, tetapi header PE tidak lengkap atau tidak valid."
            }
        } catch (e: Exception) {
            exeStatus.text = "Gagal membaca NSUNSR.exe: ${e.message ?: "kesalahan I/O"}"
        }
    }

    private fun inspectPe32X86(data: ByteArray): Boolean? {
        if (data.size < 64 || data[0] != 0x4D.toByte() || data[1] != 0x5A.toByte()) return null
        val header = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val peOffset = header.getInt(0x3C).toLong() and 0xFFFFFFFFL
        if (peOffset > (data.size - 26).toLong()) return null
        val pe = peOffset.toInt()
        if (data[pe] != 0x50.toByte() || data[pe + 1] != 0x45.toByte() ||
            data[pe + 2] != 0.toByte() || data[pe + 3] != 0.toByte()) return null
        val machine = header.getShort(pe + 4).toInt() and 0xFFFF
        val optionalMagic = header.getShort(pe + 24).toInt() and 0xFFFF
        return machine == 0x014C && optionalMagic == 0x010B
    }
}

object NativeBridge {
    init { System.loadLibrary("stormcore") }
    external fun initialize(appDataDirectory: String): Boolean
    external fun shutdown()
    external fun runtimeStatus(): String
    external fun lastError(): String
    external fun status(): String
    external fun vulkanStatus(): String
    external fun setAndroidSurface(surface: Surface): String
    external fun androidSurfaceStatus(): String
    external fun clearAndroidSurface()
}
