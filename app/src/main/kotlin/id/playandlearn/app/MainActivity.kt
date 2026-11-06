package id.playandlearn.app

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import id.playandlearn.core.LevelPolicy
import id.playandlearn.core.PlayDecision
import id.playandlearn.core.ScreenTimeGuard
import id.playandlearn.core.SessionTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.util.Locale

private sealed interface Screen {
    data object Gate : Screen
    data object Auth : Screen
    data object Children : Screen
    data object Home : Screen
    data class Level(val activity: ActivityDef, val level: Int) : Screen
}

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { if (it == TextToSpeech.SUCCESS) tts?.language = Locale("id", "ID") } // suara sintetis = placeholder
        val store = Store(this)
        val api = Api(store)
        setContent { App(store, api) { tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, null) } }
    }

    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }
}

@Composable
private fun App(store: Store, api: Api, speak: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf<Screen>(if (store.accessToken == null) Screen.Gate else Screen.Home) }
    var child by remember { mutableStateOf(store.childrenJson?.let { childrenFromJson(it).firstOrNull() }) }
    var children by remember { mutableStateOf(store.childrenJson?.let { childrenFromJson(it) } ?: emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var startedAt by remember { mutableStateOf(0L) }

    fun io(block: () -> Unit, ok: () -> Unit = {}) = scope.launch {
        busy = true; error = null
        runCatching { withContext(Dispatchers.IO) { block() } }.onSuccess { ok() }
            .onFailure { error = (it as? Api.HttpError)?.let { e -> "Gagal (${e.code})" } ?: "Tidak ada koneksi" }
        busy = false
    }
    // Sinkronisasi senyap saat dibuka; gagal = tetap main offline.
    LaunchedEffect(screen) { if (screen == Screen.Home && store.accessToken != null) runCatching { withContext(Dispatchers.IO) { api.sync() } } }

    when (val s = screen) {
        Screen.Gate -> ParentalGate(onPassed = { screen = if (store.accessToken == null) Screen.Auth else Screen.Children }, onCancel = {
            screen = if (store.accessToken == null) Screen.Gate else Screen.Home })
        Screen.Auth -> AuthScreen(busy, error) { email, pw, reg ->
            io({ if (reg) api.register(email, pw) else api.login(email, pw); children = api.refreshChildren() }, { screen = Screen.Children })
        }
        Screen.Children -> ChildPicker(children, onPick = { c ->
            child = c
            io({ api.refreshPack(java.time.Year.now().value - c.birthYear.coerceAtMost(java.time.Year.now().value)); api.refreshPolicy(c.id) }, { screen = Screen.Home })
        }, onAdd = { a, y -> io({ api.createChild(a, y); children = api.refreshChildren() }) }, error = error)
        Screen.Home -> {
            val pack = store.packJson
            val c = child
            if (pack == null || c == null) { Screen.Gate.also { screen = it }; return }
            val policy = parsePolicy(store.policyJson)
            val now = LocalTime.now()
            val decision = ScreenTimeGuard.decide(policy, now.hour * 60 + now.minute, store.playedTodaySeconds() / 60,
                SessionTracker.sessionMinutes(store.sessionState, System.currentTimeMillis()))
            val blocked = when (decision) {
                PlayDecision.ALLOWED -> null
                PlayDecision.SESSION_BREAK -> "Istirahat dulu ya"
                PlayDecision.DAILY_LIMIT_REACHED -> "Besok main lagi ya!"
                PlayDecision.OUTSIDE_ALLOWED_HOURS -> "Waktunya istirahat"
            }
            ChildHome(parseActivities(pack), blocked, onPlay = { a ->
                startedAt = System.currentTimeMillis(); screen = Screen.Level(a, store.levelOf(c.id, a.id).coerceAtMost(a.levels.size))
            }, onParent = { screen = Screen.Gate })
        }
        is Screen.Level -> {
            val c = child!!
            val pack = store.packJson!!
            val version = org.json.JSONObject(pack).optInt("version", 1)
            fun finish() {
                val end = System.currentTimeMillis()
                store.addPlayed(((end - startedAt) / 1000).toInt())
                store.sessionState = SessionTracker.record(store.sessionState, startedAt, end)
                screen = Screen.Home
            }
            LevelScreen(c.id, s.activity, s.level, if (s.activity.kind == "pattern") patternSequence(pack, s.activity.id, s.level) else emptyList(),
                version, speak, store::save,
                onDone = { wrong, revealed ->
                    store.setLevel(c.id, s.activity.id, LevelPolicy.nextLevel(s.level, s.activity.levels.size, wrong, revealed)); finish()
                }, onExit = { finish() })
        }
    }
}
