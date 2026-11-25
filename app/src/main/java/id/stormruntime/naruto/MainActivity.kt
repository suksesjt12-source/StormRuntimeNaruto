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
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : Activity(), SurfaceHolder.Callback {
    private lateinit var folderStatus: TextView
    private lateinit var exeStatus: TextView
    private lateinit var runtimeStatus: TextView
    private lateinit var surfaceStatus: TextView
    private lateinit var importStatus: TextView
    private lateinit var importButton: Button
    private var selectedTree: Uri? = null
    @Volatile private var importInProgress = false

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
        val prepared = File(filesDir, "game/NSUNSR.exe")
        if (prepared.isFile) {
            importStatus.text = "Salinan lokal tersedia: ${File(filesDir, "game").absolutePath}"
        }
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
        panel.addView(label("Android ARM64 • Runtime integration prototype"))
        panel.addView(button("Pilih folder instalasi game") { pickFolder() })
        folderStatus = label("Folder game: belum dipilih")
        panel.addView(folderStatus)
        exeStatus = label("NSUNSR.exe: belum diperiksa")
        panel.addView(exeStatus)

        importButton = button("Salin folder game ke penyimpanan aplikasi") { prepareGameFolder() }
        importButton.isEnabled = false
        panel.addView(importButton)
        importStatus = label("Folder lokal: belum disiapkan")
        panel.addView(importStatus)

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
        panel.addView(label("Status: Wine/Box64/DXVK belum terintegrasi. APK ini belum dapat menjalankan NSUNSR.exe."))
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
            importButton.isEnabled = false
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

    private fun inspectGameFolder(): Boolean {
        val uri = selectedTree ?: run {
            exeStatus.text = "Pilih folder game terlebih dahulu."
            return false
        }
        val tree = DocumentFile.fromTreeUri(this, uri)
        val exe = tree?.findFile("NSUNSR.exe")
        if (exe?.isFile != true) {
            exeStatus.text = "NSUNSR.exe: tidak ditemukan di folder utama."
            importButton.isEnabled = false
            return false
        }

        return try {
            val bytes = contentResolver.openInputStream(exe.uri)?.use { input ->
                val output = ByteArray(8192)
                var total = 0
                val header = ByteArray(1024 * 1024)
                while (total < header.size) {
                    val read = input.read(header, total, minOf(output.size, header.size - total))
                    if (read <= 0) break
                    total += read
                }
                header.copyOf(total)
            } ?: throw IllegalStateException("File tidak dapat dibaca.")

            val peType = inspectPe32X86(bytes)
            val fileSize = exe.length()
            if (peType == true) {
                exeStatus.text = "NSUNSR.exe: PE32 x86 (i386) terdeteksi • ukuran ${fileSize} byte."
                importButton.isEnabled = !importInProgress
                true
            } else {
                exeStatus.text = if (peType == false)
                    "NSUNSR.exe ditemukan, tetapi bukan PE32 x86."
                else "NSUNSR.exe ditemukan, tetapi header PE tidak lengkap atau tidak valid."
                importButton.isEnabled = false
                false
            }
        } catch (e: Exception) {
            exeStatus.text = "Gagal membaca NSUNSR.exe: ${e.message ?: "kesalahan I/O"}"
            importButton.isEnabled = false
            false
        }
    }

    private fun prepareGameFolder() {
        if (importInProgress || !inspectGameFolder()) return
        val uri = selectedTree ?: return
        importInProgress = true
        importButton.isEnabled = false
        importStatus.text = "Menyalin file game. Jangan tutup aplikasi..."
        Thread {
            try {
                val source = DocumentFile.fromTreeUri(this, uri)
                    ?: throw IllegalStateException("Folder sumber tidak dapat dibuka.")
                val staging = File(filesDir, "game-import.tmp")
                val destination = File(filesDir, "game")
                val backup = File(filesDir, "game-import.backup")
                deleteRecursively(staging)
                deleteRecursively(backup)
                if (!staging.mkdirs()) throw IllegalStateException("Tidak dapat membuat folder staging.")

                var copiedFiles = 0
                var copiedBytes = 0L
                copyDocumentTree(source, staging) { name, bytes ->
                    copiedFiles++
                    copiedBytes += bytes
                    runOnUiThread {
                        importStatus.text = "Menyalin: $name\nFile: $copiedFiles • ${copiedBytes / (1024 * 1024)} MB"
                    }
                }

                val stagedExe = File(staging, "NSUNSR.exe")
                if (!stagedExe.isFile || !isPe32X86(stagedExe)) {
                    throw IllegalStateException("Salinan NSUNSR.exe tidak lolos validasi PE32 x86.")
                }

                if (destination.exists() && !destination.renameTo(backup)) {
                    throw IllegalStateException("Tidak dapat mencadangkan salinan game lama.")
                }
                if (!staging.renameTo(destination)) {
                    if (backup.exists()) backup.renameTo(destination)
                    throw IllegalStateException("Tidak dapat memasang salinan game baru.")
                }
                deleteRecursively(backup)
                runOnUiThread {
                    importStatus.text = "Folder game siap di filesystem lokal:\n${destination.absolutePath}\n$copiedFiles file • ${copiedBytes / (1024 * 1024)} MB".replace("$c opiedFiles", "$copiedFiles")
                }
            } catch (error: Exception) {
                runOnUiThread {
                    importStatus.text = "Penyiapan gagal: ${error.message ?: "kesalahan tidak diketahui"}"
                }
            } finally {
                importInProgress = false
                runOnUiThread { importButton.isEnabled = selectedTree != null }
            }
        }.start()
    }

    private fun copyDocumentTree(
        source: DocumentFile,
        destination: File,
        onFileCopied: (String, Long) -> Unit
    ) {
        for (child in source.listFiles()) {
            val name = child.name ?: throw IllegalStateException("Ada item tanpa nama.")
            if (name == "." || name == ".." || name.contains('/') || name.contains('\\')) {
                throw IllegalStateException("Nama file/folder tidak aman: $name")
            }
            val target = File(destination, name)
            if (child.isDirectory) {
                if (!target.exists() && !target.mkdirs()) {
                    throw IllegalStateException("Gagal membuat folder: $name")
                }
                copyDocumentTree(child, target, onFileCopied)
            } else if (child.isFile) {
                contentResolver.openInputStream(child.uri)?.use { input ->
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                } ?: throw IllegalStateException("Tidak dapat membaca file: $name")
                onFileCopied(name, target.length())
            }
        }
    }

    private fun isPe32X86(file: File): Boolean {
        val header = ByteArray(1024 * 1024)
        val count = file.inputStream().use { it.read(header) }
        return count > 0 && inspectPe32X86(header.copyOf(count)) == true
    }

    private fun deleteRecursively(file: File) {
        if (file.isDirectory) file.listFiles()?.forEach { deleteRecursively(it) }
        if (file.exists() && !file.delete()) {
            throw IllegalStateException("Tidak dapat menghapus: ${file.name}")
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
