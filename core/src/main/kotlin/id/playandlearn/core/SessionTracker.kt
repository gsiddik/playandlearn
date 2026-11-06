package id.playandlearn.core

/**
 * Melacak durasi sesi bermain. Sesi berakhir (reset) setelah anak istirahat cukup lama,
 * sehingga menutup lalu membuka ulang aplikasi tidak melewati jeda sesi.
 * State dipersistenkan oleh pemanggil (agar bertahan saat aplikasi dimatikan).
 */
object SessionTracker {
    const val DEFAULT_RESET_MS = 5 * 60_000L

    data class State(val sessionSec: Int = 0, val lastEndMs: Long = 0L)

    fun sessionMinutes(s: State, nowMs: Long, resetAfterMs: Long = DEFAULT_RESET_MS): Int =
        if (nowMs - s.lastEndMs >= resetAfterMs) 0 else s.sessionSec / 60

    fun record(s: State, startMs: Long, endMs: Long, resetAfterMs: Long = DEFAULT_RESET_MS): State {
        val base = if (startMs - s.lastEndMs >= resetAfterMs) 0 else s.sessionSec
        return State(base + ((endMs - startMs) / 1000).toInt().coerceAtLeast(0), endMs)
    }
}
