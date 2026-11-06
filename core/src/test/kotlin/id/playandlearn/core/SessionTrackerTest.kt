package id.playandlearn.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SessionTrackerTest {
    private val min = 60_000L

    @Test fun accumulatesWithinSession() {
        var s = SessionTracker.State()
        s = SessionTracker.record(s, 10 * min, 14 * min)
        s = SessionTracker.record(s, 15 * min, 21 * min) // jeda 1 menit < reset
        assertEquals(10, SessionTracker.sessionMinutes(s, 22 * min))
    }

    @Test fun resetsAfterLongBreak() {
        var s = SessionTracker.record(SessionTracker.State(), 0, 10 * min)
        assertEquals(10, SessionTracker.sessionMinutes(s, 11 * min))
        assertEquals(0, SessionTracker.sessionMinutes(s, 16 * min))
        s = SessionTracker.record(s, 20 * min, 23 * min)
        assertEquals(3, SessionTracker.sessionMinutes(s, 24 * min))
    }

    @Test fun killingTheAppDoesNotResetSession() {
        val s = SessionTracker.record(SessionTracker.State(), 0, 10 * min)
        assertEquals(10, SessionTracker.sessionMinutes(s, 10 * min + 30_000))
    }
}
