package id.stormruntime.naruto

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException

/**
 * Lifecycle-aware client for the :storm_runtime process.
 * All UI callbacks are delivered on the main thread.
 */
internal class StormRuntimeClient(
    context: Context,
    private val onStatus: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private var service: Messenger? = null
    private var bound = false

    private val replies = Messenger(
        Handler(Looper.getMainLooper()) { message ->
            val data = message.data
            val state = data.getString(StormRuntimeProtocol.KEY_STATE) ?: "UNKNOWN"
            val detail = data.getString(StormRuntimeProtocol.KEY_DETAIL) ?: "Tidak ada detail."
            val missing = data.getStringArrayList(StormRuntimeProtocol.KEY_MISSING).orEmpty()
            val suffix = if (missing.isEmpty()) "" else "\nKomponen belum lengkap:\n- " + missing.joinToString("\n- ")
            onStatus("$state\n$detail$suffix")
            true
        }
    )

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = binder?.let(::Messenger)
            queryStatus()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            onStatus("DISCONNECTED\nProses Storm Core terputus.")
        }

        override fun onBindingDied(name: ComponentName?) {
            service = null
            onStatus("DISCONNECTED\nBinding Storm Core dihentikan Android.")
        }

        override fun onNullBinding(name: ComponentName?) {
            service = null
            onStatus("UNAVAILABLE\nStorm Core tidak menyediakan binding.")
        }
    }

    fun bind() {
        if (bound) return
        bound = appContext.bindService(
            Intent(appContext, StormRuntimeService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
        if (!bound) onStatus("UNAVAILABLE\nGagal menghubungkan proses Storm Core.")
    }

    fun unbind() {
        if (!bound) return
        try {
            appContext.unbindService(connection)
        } catch (_: IllegalArgumentException) {
            // The framework may already have removed this binding.
        }
        bound = false
        service = null
    }

    fun queryStatus() = send(StormRuntimeProtocol.MSG_QUERY_STATUS)

    fun requestGameLaunch() = send(StormRuntimeProtocol.MSG_REQUEST_GAME_LAUNCH)

    private fun send(command: Int) {
        val target = service
        if (target == null) {
            onStatus("CONNECTING\nProses Storm Core belum tersambung.")
            return
        }
        val message = Message.obtain(null, command).apply {
            replyTo = replies
        }
        try {
            target.send(message)
        } catch (_: RemoteException) {
            onStatus("ERROR\nKomunikasi dengan proses Storm Core gagal.")
        }
    }
}
