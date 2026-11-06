package id.playandlearn.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenTimeGuardTest {
    private val policy = ScreenTimePolicy(
        dailyMinutes = 30, sessionMinutes = 10,
        allowedFromMinute = 7 * 60, allowedToMinute = 19 * 60, version = 1,
    )

    @Test fun allowedWithinLimits() =
        assertEquals(PlayDecision.ALLOWED, ScreenTimeGuard.decide(policy, 9 * 60, 5, 3))

    @Test fun sessionBreakWhenSessionExhausted() =
        assertEquals(PlayDecision.SESSION_BREAK, ScreenTimeGuard.decide(policy, 9 * 60, 12, 10))

    @Test fun dailyLimitWinsOverSession() =
        assertEquals(PlayDecision.DAILY_LIMIT_REACHED, ScreenTimeGuard.decide(policy, 9 * 60, 30, 10))

    @Test fun blockedOutsideAllowedHours() =
        assertEquals(PlayDecision.OUTSIDE_ALLOWED_HOURS, ScreenTimeGuard.decide(policy, 20 * 60, 0, 0))

    @Test fun windowCrossingMidnight() {
        val night = policy.copy(allowedFromMinute = 22 * 60, allowedToMinute = 6 * 60)
        assertEquals(PlayDecision.ALLOWED, ScreenTimeGuard.decide(night, 23 * 60, 0, 0))
        assertEquals(PlayDecision.OUTSIDE_ALLOWED_HOURS, ScreenTimeGuard.decide(night, 12 * 60, 0, 0))
    }
}
