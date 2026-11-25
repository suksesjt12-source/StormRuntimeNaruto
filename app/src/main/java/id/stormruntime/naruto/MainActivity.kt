package id.stormruntime.naruto

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.widget.*
import androidx.documentfile.provider.DocumentFile
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : Activity() {
    private lateinit var folderStatus: TextView
    private lateinit var exeStatus: TextView
    private lateinit var runtimeStatus: TextView
    private var selectedTree: Uri? = null

    companion object { private const val PICK_GAME_FOLDER = 4101 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        runtimeStatus.text = "Runtime: belum terintegrasi"
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
        panel.addView(label("Android Native • Prototype 0.1.0"))
        panel.addView(button("Pilih folder instalasi game") { pickFolder() })
        folderStatus = label("Folder game: belum dipilih")
        panel.addView(folderStatus)
        exeStatus = label("NSUNSR.exe: belum diperiksa")
        panel.addView(exeStatus)

        panel.addView(label("Pengaturan awal", 20f))
        panel.addView(label("Resolusi target pengujian"))
        val resolution = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("960 × 540", "854 × 480", "1280 × 720"))
        }
        panel.addView(resolution)
        panel.addView(label("Batas FPS target"))
        val fps = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("30 FPS", "45 FPS", "60 FPS"))
        }
        panel.addView(fps)
        panel.addView(label("Diagnostik", 20f))
        runtimeStatus = label("")
        panel.addView(runtimeStatus)
        panel.addView(label("Status native: ${NativeBridge.status()}"))
        panel.addView(label("Diagnostik Vulkan: ${NativeBridge.vulkanStatus()}"))
        panel.addView(label("Catatan: aplikasi ini saat ini adalah kerangka launcher/diagnostik. Belum dapat menjalankan EXE Windows."))
        panel.addView(button("Periksa ulang file game") { inspectGameFolder() })
        panel.addView(button("Lihat informasi kompatibilitas") {
            runtimeStatus.text = "Game memerlukan Windows API, CPU x86 32-bit, dan DirectX 9. Runtime Wine + translator + graphics backend belum dipaketkan."
        })
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

    // true = Windows PE32/i386; false = another PE architecture;
    // null = incomplete or invalid PE header.
    private fun inspectPe32X86(data: ByteArray): Boolean? {
        if (data.size < 64 || data[0] != 0x4D.toByte() || data[1] != 0x5A.toByte()) {
            return null
        }
        val header = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val peOffset = header.getInt(0x3C).toLong() and 0xFFFFFFFFL
        if (peOffset > (data.size - 26).toLong()) return null
        val pe = peOffset.toInt()
        if (data[pe] != 0x50.toByte() || data[pe + 1] != 0x45.toByte() ||
            data[pe + 2] != 0.toByte() || data[pe + 3] != 0.toByte()) {
            return null
        }
        val machine = header.getShort(pe + 4).toInt() and 0xFFFF
        val optionalMagic = header.getShort(pe + 24).toInt() and 0xFFFF
        return machine == 0x014C && optionalMagic == 0x010B
    }
}

object NativeBridge {
    init { System.loadLibrary("stormcore") }
    external fun status(): String
    external fun vulkanStatus(): String
}
