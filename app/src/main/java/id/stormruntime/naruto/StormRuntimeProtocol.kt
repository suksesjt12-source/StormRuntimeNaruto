package id.stormruntime.naruto

/**
 * Private Messenger protocol between the Android UI process and the
 * dedicated :storm_runtime process. Keep this protocol versioned and small.
 */
object StormRuntimeProtocol {
    const val MSG_QUERY_STATUS = 1
    const val MSG_STATUS = 2
    const val MSG_REQUEST_GAME_LAUNCH = 3
    const val MSG_LAUNCH_RESULT = 4

    const val KEY_STATE = "state"
    const val KEY_DETAIL = "detail"
    const val KEY_MISSING = "missing"

    const val STATE_NO_BUNDLE = "NO_BUNDLE"
    const val STATE_INCOMPLETE = "INCOMPLETE"
    const val STATE_PAYLOAD_READY = "PAYLOAD_READY"
    const val STATE_LAUNCH_BLOCKED = "LAUNCH_BLOCKED"
}
