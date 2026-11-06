package id.playandlearn.core

import kotlin.test.*

class ActivitiesTest {
    private val events = mutableListOf<GameEvent>()
    private var n = 0
    private fun session(task: Task, level: Int = 1) = LevelSession(
        "child", "act", 1, level, task, now = { 1000L + n }, newId = { "id-${n++}" }, emit = { events += it })

    @Test fun countCorrectFirstTryCompletes() {
        val s = session(Task.Count(3, listOf(2, 3, 4)))
        assertEquals(Feedback.LevelCompleted, s.answer(Answer.Choose("3")))
        assertEquals(listOf(EventType.STARTED, EventType.ATTEMPT, EventType.COMPLETED), events.map { it.type })
        assertTrue(s.finished)
    }

    @Test fun progressiveHintsThenRevealWithoutPenalty() {
        val s = session(Task.Pattern(listOf("A", "B", "C"), "A"))
        assertEquals(Feedback.TryAgain(1), s.answer(Answer.Choose("B")))
        assertEquals(Feedback.TryAgain(2), s.answer(Answer.Choose("C")))
        assertEquals(Feedback.Revealed("A"), s.answer(Answer.Choose("B")))
        assertTrue(s.finished, "anak tetap menyelesaikan level setelah jawaban ditunjukkan")
        val done = events.last { it.type == EventType.COMPLETED }
        assertEquals(true, done.payload["revealed"])
    }

    @Test fun sortNeedsAllItemsAndWrongPlacementIsHinted() {
        val s = session(Task.Sort(mapOf("a" to "red", "b" to "blue")))
        assertEquals(Feedback.TryAgain(1), s.answer(Answer.Place("a", "blue")))
        assertEquals(Feedback.Correct, s.answer(Answer.Place("a", "red")))
        assertFalse(s.finished)
        assertEquals(Feedback.LevelCompleted, s.answer(Answer.Place("b", "blue")))
    }

    @Test fun abandonEmitsOnce() {
        val s = session(Task.Count(1, listOf(1, 2)))
        s.abandon(); s.abandon()
        assertEquals(1, events.count { it.type == EventType.ABANDONED })
    }

    @Test fun levelPolicyNeverDemotes() {
        assertEquals(2, LevelPolicy.nextLevel(1, 3, 0, false))
        assertEquals(2, LevelPolicy.nextLevel(1, 3, 1, false))
        assertEquals(1, LevelPolicy.nextLevel(1, 3, 2, false))
        assertEquals(1, LevelPolicy.nextLevel(1, 3, 0, true))
        assertEquals(3, LevelPolicy.nextLevel(3, 3, 0, false))
    }

    @Test fun batchIsChronologicalAndCapped() {
        val many = (1..600).map { GameEvent("e$it", "c", "a", 1, EventType.ATTEMPT, emptyMap(), (600 - it).toLong()) }
        val b = SyncBatcher.nextBatch(many)
        assertEquals(500, b.size)
        assertTrue(b.zipWithNext().all { (x, y) -> x.deviceTsMillis <= y.deviceTsMillis })
    }
}
