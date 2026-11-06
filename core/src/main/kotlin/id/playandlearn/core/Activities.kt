package id.playandlearn.core

import java.util.UUID

/** Tugas dalam satu level aktivitas. Tidak ada teks: konten disajikan lewat gambar dan suara. */
sealed interface Task {
    /** Memilah: setiap item punya satu kelompok benar. */
    data class Sort(val itemGroups: Map<String, String>) : Task
    /** Melanjutkan pola: pilih satu opsi. */
    data class Pattern(val options: List<String>, val answer: String) : Task
    /** Berhitung: pilih angka yang sesuai jumlah objek. */
    data class Count(val count: Int, val options: List<Int>) : Task
}

sealed interface Answer {
    data class Place(val itemId: String, val groupId: String) : Answer
    data class Choose(val value: String) : Answer
}

/** Umpan balik selalu suportif; kesalahan tidak mengurangi skor atau membuka layar "gagal". */
sealed interface Feedback {
    data object Correct : Feedback
    data object LevelCompleted : Feedback
    /** hintLevel 1 = ulang instruksi suara, 2 = sorot area benar, 3 = tunjukkan jawaban (anak tetap selesai). */
    data class TryAgain(val hintLevel: Int) : Feedback
    data class Revealed(val correct: String) : Feedback
}

enum class EventType(val wire: String) { STARTED("started"), ATTEMPT("attempt"), HINT("hint_shown"), COMPLETED("completed"), ABANDONED("abandoned") }

data class GameEvent(
    val id: String,
    val childId: String,
    val activityId: String,
    val contentVersion: Int,
    val type: EventType,
    val payload: Map<String, Any>,
    val deviceTsMillis: Long,
)

/** Satu permainan satu level. Mengeluarkan event yang akan disimpan lokal lalu disinkronkan. */
class LevelSession(
    private val childId: String,
    private val activityId: String,
    private val contentVersion: Int,
    private val level: Int,
    private val task: Task,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val emit: (GameEvent) -> Unit,
) {
    var wrongAttempts = 0; private set
    var hintsShown = 0; private set
    var finished = false; private set
    private var consecutiveWrong = 0
    private val placed = mutableSetOf<String>()

    init { event(EventType.STARTED, mapOf("level" to level)) }

    fun answer(a: Answer): Feedback {
        check(!finished) { "level sudah selesai" }
        val correct = when (val t = task) {
            is Task.Sort -> a is Answer.Place && t.itemGroups[a.itemId] == a.groupId
            is Task.Pattern -> a is Answer.Choose && a.value == t.answer
            is Task.Count -> a is Answer.Choose && a.value == t.count.toString()
        }
        event(EventType.ATTEMPT, mapOf("level" to level, "correct" to correct))
        if (correct) {
            consecutiveWrong = 0
            if (task is Task.Sort && a is Answer.Place) {
                placed += a.itemId
                if (placed.size < task.itemGroups.size) return Feedback.Correct
            }
            return complete()
        }
        wrongAttempts++
        consecutiveWrong++
        val hint = minOf(consecutiveWrong, 3)
        hintsShown++
        event(EventType.HINT, mapOf("level" to level, "hintLevel" to hint))
        if (hint == 3) {
            val reveal = when (val t = task) {
                is Task.Sort -> (a as? Answer.Place)?.let { t.itemGroups[it.itemId] } ?: ""
                is Task.Pattern -> t.answer
                is Task.Count -> t.count.toString()
            }
            if (task !is Task.Sort) { complete(revealed = true); return Feedback.Revealed(reveal) }
            consecutiveWrong = 0
            return Feedback.Revealed(reveal) // untuk memilah: tunjukkan kelompok benar, anak tetap melanjutkan
        }
        return Feedback.TryAgain(hint)
    }

    fun abandon() { if (!finished) { finished = true; event(EventType.ABANDONED, mapOf("level" to level)) } }

    private fun complete(revealed: Boolean = false): Feedback {
        finished = true
        event(EventType.COMPLETED, mapOf("level" to level, "wrongAttempts" to wrongAttempts, "hints" to hintsShown, "revealed" to revealed))
        return Feedback.LevelCompleted
    }

    private fun event(t: EventType, p: Map<String, Any>) =
        emit(GameEvent(newId(), childId, activityId, contentVersion, t, p, now()))
}

/**
 * Aturan adaptif sederhana v1 (tetap, bukan machine learning): naik level setelah selesai
 * dengan paling banyak 1 salah dan tanpa tampilan jawaban; selain itu tetap di level yang sama.
 * Tidak pernah menurunkan level atau menampilkan "gagal".
 */
object LevelPolicy {
    fun nextLevel(current: Int, maxLevel: Int, wrongAttempts: Int, revealed: Boolean): Int =
        if (!revealed && wrongAttempts <= 1) minOf(current + 1, maxLevel) else current
}

/** Antrean sinkronisasi: urutan kronologis, batch ≤ 500 sesuai kontrak API. */
object SyncBatcher {
    const val MAX_BATCH = 500
    fun nextBatch(pending: List<GameEvent>): List<GameEvent> =
        pending.sortedBy { it.deviceTsMillis }.take(MAX_BATCH)
}
