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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Storm Core's Android-side process boundary.
 * The service serializes bounded runtime diagnostics outside the UI process.
 */
class StormRuntimeService : Service() {
    private val smokeExecutor = Executors.newSingleThreadExecutor()
    private val smokeTestRunning = AtomicBoolean(false)

    private val messenger: Messenger by lazy {
        Messenger(IncomingHandler(this))
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        smokeExecutor.shutdownNow()
        super.onDestroy()
    }

    private class IncomingHandler(service: StormRuntimeService) :
        Handler(Looper.getMainLooper()) {
        private val runtimeService = service
        private val appContext = service.applicationContext

        override fun handleMessage(message: Message) {
            when (message.what) {
                StormRuntimeProtocol.MSG_QUERY_STATUS -> {
                    val result = StormRuntimeManifest.inspect(appContext)
                    reply(message, StormRuntimeProtocol.MSG_STATUS, result.state, result.detail, result.missing)
                }

                StormRuntimeProtocol.MSG_REQUEST_GAME_LAUNCH -> {
                    reply(
                        message,
                        StormRuntimeProtocol.MSG_LAUNCH_RESULT,
                        StormRuntimeProtocol.STATE_LAUNCH_BLOCKED,
                        "Peluncuran ditolak: process host Wine/Hangover dan display integration belum lolos pengujian. Tidak ada executable game yang dijalankan.",
                        emptyList()
                    )
                }

                StormRuntimeProtocol.MSG_RUN_GLIBC_SMOKE_TEST -> runSerialized(
                    message,
                    StormRuntimeProtocol.MSG_SMOKE_TEST_RESULT
                ) { StormProotHost.runGlibcSmokeTest(appContext) }

                StormRuntimeProtocol.MSG_RUN_COMPATIBILITY_SMOKE_TEST -> runSerialized(
                    message,
                    StormRuntimeProtocol.MSG_COMPATIBILITY_SMOKE_TEST_RESULT
                ) { StormProotHost.runCompatibilitySmokeTest(appContext) }

                else -> super.handleMessage(message)
            }
        }

        private fun runSerialized(
            request: Message,
            responseCode: Int,
            operation: () -> RuntimeValidation
        ) {
            if (!runtimeService.smokeTestRunning.compareAndSet(false, true)) {
                reply(
                    request,
                    responseCode,
                    StormRuntimeProtocol.STATE_SMOKE_TEST_BUSY,
                    "Pengujian runtime lain masih berjalan. Tunggu hingga selesai.",
                    emptyList()
                )
                return
            }
            runtimeService.smokeExecutor.execute {
                try {
                    val result = operation()
                    reply(request, responseCode, result.state, result.detail, result.missing)
                } catch (error: Exception) {
                    reply(
                        request,
                        responseCode,
                        StormRuntimeProtocol.STATE_PROOT_FAILED,
                        "Runtime diagnostic gagal: ${error.javaClass.simpleName}: ${error.message ?: "I/O error"}",
                        emptyList()
                    )
                } finally {
                    runtimeService.smokeTestRunning.set(false)
                }
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
                    putStringArrayList(StormRuntimeProtocol.KEY_MISSING, ArrayList(missing))
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
