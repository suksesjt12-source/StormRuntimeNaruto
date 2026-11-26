package id.stormruntime.naruto

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException

/**
 * Storm Core's Android-side process boundary.
 *
 * Android hosts this service in :storm_runtime, separate from the launcher
 * Activity. It currently exposes status/validation IPC only; it deliberately
 * does not pretend that Wine or a Windows executable can be launched yet.
 */
class StormRuntimeService : Service() {
    private val messenger: Messenger by lazy {
        Messenger(IncomingHandler(this))
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    private class IncomingHandler(service: StormRuntimeService) :
        Handler(Looper.getMainLooper()) {
        private val appContext = service.applicationContext

        override fun handleMessage(message: Message) {
            when (message.what) {
                StormRuntimeProtocol.MSG_QUERY_STATUS -> {
                    val result = StormRuntimeManifest.inspect(appContext)
                    reply(
                        message,
                        StormRuntimeProtocol.MSG_STATUS,
                        result.state,
                        result.detail,
                        result.missing
                    )
                }

                StormRuntimeProtocol.MSG_REQUEST_GAME_LAUNCH -> {
                    // The launch gate remains closed until the native process
                    // host and Android Wine display integration are verified.
                    reply(
                        message,
                        StormRuntimeProtocol.MSG_LAUNCH_RESULT,
                        StormRuntimeProtocol.STATE_LAUNCH_BLOCKED,
                        "Peluncuran ditolak: process host Wine/Hangover belum terintegrasi. Tidak ada executable yang dijalankan.",
                        emptyList()
                    )
                }

                else -> super.handleMessage(message)
            }
        }

        private fun reply(
            request: Message,
            responseCode: Int,
            state: String,
            detail: String,
            missing: List<String>
        ) {
            val target = request.replyTo ?: return
            val response = Message.obtain(null, responseCode).apply {
                data = Bundle().apply {
                    putString(StormRuntimeProtocol.KEY_STATE, state)
                    putString(StormRuntimeProtocol.KEY_DETAIL, detail)
                    putStringArrayList(
                        StormRuntimeProtocol.KEY_MISSING,
                        ArrayList(missing)
                    )
                }
            }
            try {
                target.send(response)
            } catch (_: RemoteException) {
                // The UI process may have disconnected during a lifecycle change.
            }
        }
    }
}
