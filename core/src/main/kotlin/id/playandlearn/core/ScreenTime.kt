package id.playandlearn.core

/** Aturan screen time dari server (server authority). Menit sejak tengah malam untuk jendela waktu. */
data class ScreenTimePolicy(
    val dailyMinutes: Int,
    val sessionMinutes: Int,
    val allowedFromMinute: Int? = null,
    val allowedToMinute: Int? = null,
    val version: Int,
)

enum class PlayDecision { ALLOWED, SESSION_BREAK, DAILY_LIMIT_REACHED, OUTSIDE_ALLOWED_HOURS }

object ScreenTimeGuard {
    /**
     * Menentukan apakah anak boleh bermain sekarang. Dievaluasi sepenuhnya di perangkat
     * agar berfungsi offline; kebijakan terakhir yang diterima dari server dipakai.
     */
    fun decide(
        policy: ScreenTimePolicy,
        minutesOfDay: Int,
        playedTodayMinutes: Int,
        playedThisSessionMinutes: Int,
    ): PlayDecision {
        val from = policy.allowedFromMinute
        val to = policy.allowedToMinute
        if (from != null && to != null) {
            val inside = if (from <= to) minutesOfDay in from until to
            else minutesOfDay >= from || minutesOfDay < to
            if (!inside) return PlayDecision.OUTSIDE_ALLOWED_HOURS
        }
        if (playedTodayMinutes >= policy.dailyMinutes) return PlayDecision.DAILY_LIMIT_REACHED
        if (playedThisSessionMinutes >= policy.sessionMinutes) return PlayDecision.SESSION_BREAK
        return PlayDecision.ALLOWED
    }
}
