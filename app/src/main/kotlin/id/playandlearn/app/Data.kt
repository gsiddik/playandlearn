package id.playandlearn.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import id.playandlearn.core.EventType
import id.playandlearn.core.GameEvent
import id.playandlearn.core.ScreenTimePolicy
import id.playandlearn.core.SessionTracker
import id.playandlearn.core.SyncBatcher
import id.playandlearn.core.Task
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/** Aktivitas dari content pack. Konten diunduh sekali lalu dimainkan offline. */
data class ActivityDef(val id: String, val kind: String, val levels: List<Task>)
data class ChildDef(val id: String, val alias: String, val birthYear: Int)

/** Penyimpanan lokal (SQLite + SharedPreferences privat). Token disimpan tanpa enkripsi tambahan: TODO Keystore. */
class Store(ctx: Context) : SQLiteOpenHelper(ctx, "pal.db", null, 1) {
    private val prefs = ctx.getSharedPreferences("pal", Context.MODE_PRIVATE)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE events(id TEXT PRIMARY KEY, child TEXT, activity TEXT, version INT, type TEXT, payload TEXT, ts INT, sent INT DEFAULT 0)")
    }
    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}

    var accessToken: String? get() = prefs.getString("access", null); set(v) = prefs.edit().putString("access", v).apply()
    var packJson: String? get() = prefs.getString("pack", null); set(v) = prefs.edit().putString("pack", v).apply()
    var childrenJson: String? get() = prefs.getString("children", null); set(v) = prefs.edit().putString("children", v).apply()
    var role: String? get() = prefs.getString("role", null); set(v) = prefs.edit().putString("role", v).apply()
    /** ID aktivitas yang ditugaskan guru (hanya mode murid), dipisah koma. */
    var assigned: Set<String> get() = prefs.getString("assigned", "")!!.split(",").filter { it.isNotBlank() }.toSet()
        set(v) = prefs.edit().putString("assigned", v.joinToString(",")).apply()
    fun logout() { prefs.edit().clear().apply() }
    var policyJson: String? get() = prefs.getString("policy", null); set(v) = prefs.edit().putString("policy", v).apply()
    fun levelOf(child: String, activity: String) = prefs.getInt("lvl:$child:$activity", 1)
    fun setLevel(child: String, activity: String, l: Int) = prefs.edit().putInt("lvl:$child:$activity", l).apply()

    var sessionState: SessionTracker.State
        get() = SessionTracker.State(prefs.getInt("sess_sec", 0), prefs.getLong("sess_end", 0L))
        set(v) = prefs.edit().putInt("sess_sec", v.sessionSec).putLong("sess_end", v.lastEndMs).apply()

    fun playedTodaySeconds(): Int = prefs.getInt("played:${LocalDate.now()}", 0)
    fun addPlayed(sec: Int) = prefs.edit().putInt("played:${LocalDate.now()}", playedTodaySeconds() + sec).apply()

    fun save(e: GameEvent) {
        writableDatabase.insertWithOnConflict("events", null, ContentValues().apply {
            put("id", e.id); put("child", e.childId); put("activity", e.activityId); put("version", e.contentVersion)
            put("type", e.type.wire); put("payload", JSONObject(e.payload).toString()); put("ts", e.deviceTsMillis)
        }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    /** Event yang belum terkirim beserta payload JSON-nya. */
    fun pending(): List<Pair<GameEvent, String>> = readableDatabase.rawQuery(
        "SELECT id,child,activity,version,type,payload,ts FROM events WHERE sent=0 ORDER BY ts LIMIT ${SyncBatcher.MAX_BATCH}", null).use { c ->
        buildList {
            while (c.moveToNext()) add(GameEvent(c.getString(0), c.getString(1), c.getString(2), c.getInt(3),
                EventType.entries.first { it.wire == c.getString(4) }, emptyMap(), c.getLong(6)) to c.getString(5))
        }
    }
    fun markSent(ids: List<String>) = ids.forEach { writableDatabase.execSQL("UPDATE events SET sent=1 WHERE id=?", arrayOf(it)) }
}

fun parseActivities(json: String): List<ActivityDef> {
    val acts = JSONObject(json).getJSONArray("activities")
    return (0 until acts.length()).map { i ->
        val a = acts.getJSONObject(i)
        val kind = a.getString("kind")
        val levels = a.getJSONArray("levels")
        ActivityDef(a.getString("id"), kind, (0 until levels.length()).map { j ->
            val l = levels.getJSONObject(j)
            when (kind) {
                "sort" -> Task.Sort(l.getJSONArray("items").let { arr ->
                    (0 until arr.length()).associate { arr.getJSONObject(it).getString("id") to arr.getJSONObject(it).getString("group") }
                })
                "pattern" -> Task.Pattern(l.getJSONArray("options").strings(), l.getString("answer"))
                "count" -> Task.Count(l.getInt("count"), l.getJSONArray("options").let { o -> (0 until o.length()).map { o.getInt(it) } })
                else -> error("jenis aktivitas tidak dikenal: $kind")
            }
        })
    }
}
fun JSONArray.strings() = (0 until length()).map { getString(it) }
fun patternSequence(json: String, activityId: String, level: Int): List<String> {
    val acts = JSONObject(json).getJSONArray("activities")
    for (i in 0 until acts.length()) {
        val a = acts.getJSONObject(i)
        if (a.getString("id") == activityId) return a.getJSONArray("levels").getJSONObject(level - 1).getJSONArray("sequence").strings()
    }
    return emptyList()
}

fun parsePolicy(json: String?): ScreenTimePolicy {
    if (json == null) return ScreenTimePolicy(30, 10, version = 0)
    val o = JSONObject(json)
    fun min(k: String): Int? = o.optString(k, "").takeIf { it.length == 5 }?.let { it.substring(0, 2).toInt() * 60 + it.substring(3).toInt() }
    return ScreenTimePolicy(o.getInt("dailyMinutes"), o.getInt("sessionMinutes"), min("allowedFrom"), min("allowedTo"), o.getInt("version"))
}

/** Klien HTTP minimal. Dipanggil dari thread IO. */
class Api(private val store: Store) {
    class HttpError(val code: Int, msg: String) : Exception(msg)

    private fun call(method: String, path: String, body: JSONObject? = null): Pair<Int, String> {
        val c = URL(BuildConfig.API_BASE + path).openConnection() as HttpURLConnection
        c.requestMethod = method; c.connectTimeout = 8000; c.readTimeout = 15000
        c.setRequestProperty("Content-Type", "application/json")
        store.accessToken?.let { c.setRequestProperty("Authorization", "Bearer $it") }
        if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toString().toByteArray()) } }
        val code = c.responseCode
        val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
        return code to text
    }

    private fun auth(path: String, body: JSONObject) {
        val (code, text) = call("POST", path, body)
        if (code !in 200..299) throw HttpError(code, text)
        store.accessToken = JSONObject(text).getString("accessToken")
    }
    fun register(email: String, password: String) = auth("/auth/parents/register",
        JSONObject().put("email", email).put("password", password).put("consentPolicyVersion", "2026-11-draft").put("consentGranted", true))
    fun login(email: String, password: String) = auth("/auth/login", JSONObject().put("email", email).put("password", password))

    /** Login murid dari kartu: kode QR + 3 indeks gambar PIN (0..8). */
    fun studentLogin(code: String, pin: List<Int>) {
        val (c, text) = call("POST", "/auth/students/login", JSONObject().put("loginCode", code).put("pin", JSONArray(pin)))
        if (c !in 200..299) throw HttpError(c, text)
        store.accessToken = JSONObject(text).getString("accessToken")
        store.role = "student"
    }
    /** Muat profil murid, misi tugas, konten, dan kebijakan; setelah ini murid bisa bermain offline. */
    fun loadStudent(): ChildDef {
        val (c, text) = call("GET", "/me/profile")
        if (c !in 200..299) throw HttpError(c, text)
        val o = JSONObject(text)
        val child = ChildDef(o.getString("id"), o.getString("alias"), o.getInt("birthYear"))
        store.childrenJson = JSONArray().put(JSONObject().put("id", child.id).put("alias", child.alias).put("birthYear", child.birthYear)).toString()
        val a = o.getJSONArray("assignments")
        store.assigned = (0 until a.length()).map { a.getJSONObject(it).getString("activityId") }.toSet()
        refreshPack(java.time.Year.now().value - child.birthYear)
        refreshPolicy(child.id)
        return child
    }

    fun refreshChildren(): List<ChildDef> {
        val (code, text) = call("GET", "/children")
        if (code !in 200..299) throw HttpError(code, text)
        store.childrenJson = text
        return childrenFromJson(text)
    }
    fun createChild(alias: String, birthYear: Int) {
        val (code, text) = call("POST", "/children", JSONObject().put("alias", alias).put("birthYear", birthYear))
        if (code !in 200..299) throw HttpError(code, text)
    }
    fun refreshPack(age: Int) {
        val have = store.packJson?.let { JSONObject(it).optInt("version", 0) } ?: 0
        val (code, text) = call("GET", "/content/packs/latest?age=$age&haveVersion=$have")
        if (code == 200) store.packJson = JSONObject(text).getJSONObject("manifest").put("version", JSONObject(text).getInt("version")).toString()
        else if (code != 304) throw HttpError(code, text)
    }
    fun refreshPolicy(childId: String) {
        val (code, text) = call("GET", "/children/$childId/policy")
        if (code == 200) store.policyJson = text
    }

    /** Kirim event tertunda; server authority atas kebijakan. Aman dipanggil berulang (idempoten). */
    fun sync() {
        val pending = store.pending()
        if (pending.isEmpty()) return
        val events = JSONArray(pending.map { (e, payload) ->
            JSONObject().put("id", e.id).put("childId", e.childId).put("activityId", e.activityId).put("contentVersion", e.contentVersion)
                .put("type", e.type.wire).put("payload", JSONObject(payload))
                .put("deviceTs", java.time.Instant.ofEpochMilli(e.deviceTsMillis).toString())
        })
        val (code, text) = call("POST", "/sync/events", JSONObject().put("events", events))
        if (code !in 200..299) throw HttpError(code, text)
        store.markSent(pending.map { it.first.id })
        JSONObject(text).optJSONObject("policy")?.let { store.policyJson = it.toString() }
    }
}

fun childrenFromJson(text: String): List<ChildDef> {
    val a = JSONArray(text)
    return (0 until a.length()).map { a.getJSONObject(it).let { o -> ChildDef(o.getString("id"), o.getString("alias"), o.getInt("birthYear")) } }
}
