package id.playandlearn.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.playandlearn.core.Answer
import id.playandlearn.core.Feedback
import id.playandlearn.core.LevelPolicy
import id.playandlearn.core.LevelSession
import id.playandlearn.core.Task
import kotlin.random.Random

private val colorOf = mapOf("red" to Color(0xFFE53935), "blue" to Color(0xFF1E88E5), "A" to Color(0xFFE53935),
    "B" to Color(0xFF1E88E5), "C" to Color(0xFF43A047))
private val Touch = 88.dp // target sentuh besar untuk anak

/** Parental gate: soal perkalian yang berubah tiap kali; anak prasekolah tidak dapat menyelesaikannya. */
@Composable
fun ParentalGate(onPassed: () -> Unit, onCancel: () -> Unit) {
    val a = remember { Random.nextInt(6, 10) }
    val b = remember { Random.nextInt(6, 10) }
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Khusus orang tua", fontSize = 24.sp)
        Text("Berapa $a × $b ?", fontSize = 20.sp, modifier = Modifier.padding(vertical = 12.dp))
        OutlinedTextField(input, { input = it.filter(Char::isDigit).take(3); wrong = false }, singleLine = true)
        if (wrong) Text("Jawaban belum tepat")
        Row(Modifier.padding(top = 12.dp)) {
            Button(onClick = { if (input.toIntOrNull() == a * b) onPassed() else wrong = true }) { Text("Lanjut") }
            Button(onClick = onCancel, modifier = Modifier.padding(start = 8.dp)) { Text("Kembali") }
        }
    }
}

@Composable
fun AuthScreen(busy: Boolean, error: String?, onSubmit: (email: String, password: String, register: Boolean) -> Unit) {
    var email by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var register by remember { mutableStateOf(true) }
    var consent by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(if (register) "Daftar orang tua" else "Masuk orang tua", fontSize = 24.sp)
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(pw, { pw = it }, label = { Text("Password (min. 10 karakter)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (register) Row(Modifier.clickable { consent = !consent }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (consent) "☑" else "☐", fontSize = 28.sp)
            Text(" Saya orang tua/wali dan menyetujui pengumpulan data minimum untuk progres belajar anak saya.", modifier = Modifier.padding(start = 8.dp))
        }
        if (error != null) Text(error)
        Button(enabled = !busy && (!register || consent), onClick = { onSubmit(email, pw, register) }) { Text(if (register) "Daftar" else "Masuk") }
        Button(onClick = { register = !register }) { Text(if (register) "Sudah punya akun" else "Buat akun baru") }
    }
}

@Composable
fun ChildPicker(children: List<ChildDef>, onPick: (ChildDef) -> Unit, onAdd: (String, Int) -> Unit, error: String?) {
    var alias by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("2020") }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Pilih anak", fontSize = 24.sp)
        children.forEach { Button(onClick = { onPick(it) }, modifier = Modifier.padding(top = 8.dp)) { Text(it.alias) } }
        Text("Tambah anak (panggilan saja, bukan nama lengkap)", modifier = Modifier.padding(top = 24.dp))
        OutlinedTextField(alias, { alias = it.take(30) }, label = { Text("Panggilan") }, singleLine = true)
        OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, label = { Text("Tahun lahir") }, singleLine = true)
        if (error != null) Text(error)
        Button(enabled = alias.isNotBlank() && year.length == 4, onClick = { onAdd(alias.trim(), year.toInt()) }) { Text("Simpan") }
    }
}

/** Beranda anak: hanya ikon besar, tanpa teks yang harus dibaca. */
@Composable
fun ChildHome(activities: List<ActivityDef>, blockedReason: String?, onPlay: (ActivityDef) -> Unit, onParent: () -> Unit) {
    val icon = mapOf("sort" to "🎨", "pattern" to "🔷", "count" to "🔢")
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (blockedReason != null) {
            Text("😴", fontSize = 96.sp)
            Text(blockedReason, fontSize = 18.sp)
        } else activities.forEach {
            Box(Modifier.padding(12.dp).size(width = 240.dp, height = 120.dp).background(Color(0xFFFFF59D), RoundedCornerShape(24.dp))
                .clickable { onPlay(it) }, contentAlignment = Alignment.Center) { Text(icon[it.kind] ?: "⭐", fontSize = 64.sp) }
        }
        Text("👨‍👩‍👧", fontSize = 28.sp, modifier = Modifier.padding(top = 24.dp).clickable { onParent() })
    }
}

@Composable
fun Shape(token: String, size: Int = 56, highlight: Boolean = false) {
    val col = colorOf[token] ?: Color.Gray
    val m = Modifier.size(size.dp).padding(2.dp).let { if (highlight) it.border(4.dp, Color(0xFFFFB300), CircleShape) else it }
    when (token) {
        "B" -> Box(m.background(col, RoundedCornerShape(8.dp)))
        "C" -> Box(m.background(col, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)))
        else -> Box(m.background(col, CircleShape))
    }
}

/** Satu level. `speak` memutar petunjuk suara; `onEvent` menyimpan event lokal. */
@Composable
fun LevelScreen(
    childId: String, activity: ActivityDef, level: Int, sequence: List<String>, version: Int,
    speak: (String) -> Unit, saveEvent: (id.playandlearn.core.GameEvent) -> Unit,
    onDone: (wrong: Int, revealed: Boolean) -> Unit, onExit: () -> Unit,
) {
    val task = activity.levels[level - 1]
    var revealedAny by remember { mutableStateOf(false) }
    val session = remember { LevelSession(childId, activity.id, version, level, task, emit = saveEvent) }
    var highlight by remember { mutableStateOf<String?>(null) }
    var selectedItem by remember { mutableStateOf<String?>(null) }
    var placed by remember { mutableStateOf(setOf<String>()) }

    fun handle(a: Answer) {
        when (val f = session.answer(a)) {
            Feedback.Correct -> { speak("Bagus!"); if (a is Answer.Place) placed = placed + a.itemId; selectedItem = null }
            Feedback.LevelCompleted -> { speak("Hebat!"); onDone(session.wrongAttempts, revealedAny) }
            is Feedback.TryAgain -> { speak("Coba lagi ya"); if (f.hintLevel >= 2) highlight = (task as? Task.Pattern)?.answer ?: (task as? Task.Count)?.count?.toString() }
            is Feedback.Revealed -> { revealedAny = true; highlight = f.correct; speak("Ini jawabannya"); if (task !is Task.Sort) onDone(session.wrongAttempts, true) }
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("✖", fontSize = 28.sp, modifier = Modifier.align(Alignment.End).clickable { session.abandon(); onExit() })
        when (task) {
            is Task.Count -> {
                Text("⭐".repeat(task.count), fontSize = 36.sp, modifier = Modifier.padding(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    task.options.forEach { n ->
                        Button(onClick = { handle(Answer.Choose(n.toString())) }, modifier = Modifier.size(Touch)
                            .let { if (highlight == n.toString()) it.border(4.dp, Color(0xFFFFB300), RoundedCornerShape(24.dp)) else it }) { Text("$n", fontSize = 28.sp) }
                    }
                }
            }
            is Task.Pattern -> {
                Row(Modifier.padding(16.dp)) { sequence.forEach { Shape(it) }; Text("❓", fontSize = 40.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    task.options.forEach { o -> Box(Modifier.size(Touch).clickable { handle(Answer.Choose(o)) }, contentAlignment = Alignment.Center) { Shape(o, 64, highlight == o) } }
                }
            }
            is Task.Sort -> {
                val groups = task.itemGroups.values.distinct()
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(16.dp)) {
                    task.itemGroups.keys.filter { it !in placed }.forEach { id ->
                        Box(Modifier.clickable { selectedItem = id }.padding(4.dp)) { Shape(task.itemGroups.getValue(id), 64, selectedItem == id) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    groups.forEach { g ->
                        Box(Modifier.size(120.dp).background((colorOf[g] ?: Color.Gray).copy(alpha = 0.25f), RoundedCornerShape(24.dp))
                            .let { if (highlight == g) it.border(4.dp, Color(0xFFFFB300), RoundedCornerShape(24.dp)) else it }
                            .clickable { selectedItem?.let { handle(Answer.Place(it, g)) } })
                    }
                }
            }
        }
    }
}
