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
    const val MSG_RUN_GLIBC_SMOKE_TEST = 5
    const val MSG_SMOKE_TEST_RESULT = 6

    const val KEY_STATE = "state"
    const val KEY_DETAIL = "detail"
    const val KEY_MISSING = "missing"

    const val STATE_NO_BUNDLE = "NO_BUNDLE"
    const val STATE_INCOMPLETE = "INCOMPLETE"
    const val STATE_PAYLOAD_READY = "PAYLOAD_READY"
    const val STATE_LAUNCH_BLOCKED = "LAUNCH_BLOCKED"
    const val STATE_PROOT_MISSING = "PROOT_MISSING"
    const val STATE_PROOT_INVALID = "PROOT_INVALID"
    const val STATE_ROOTFS_MISSING = "ROOTFS_MISSING"
    const val STATE_ROOTFS_INVALID = "ROOTFS_INVALID"
    const val STATE_PROOT_TIMEOUT = "PROOT_TIMEOUT"
    const val STATE_PROOT_FAILED = "PROOT_FAILED"
    const val STATE_GLIBC_SMOKE_OK = "GLIBC_SMOKE_OK"
    const val STATE_SMOKE_TEST_BUSY = "SMOKE_TEST_BUSY"
}
