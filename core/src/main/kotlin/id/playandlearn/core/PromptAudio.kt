package id.playandlearn.core

/** Metadata aset audio dari server (disalin dari `assets` pada content pack). */
data class AudioMeta(val mime: String, val sha256: String, val sizeBytes: Int)

/** Aturan unduh dan pemutaran audio instruksi. Murni agar dapat diuji tanpa perangkat. */
object PromptAudio {
    private val ext = mapOf(
        "audio/wav" to "wav", "audio/x-wav" to "wav", "audio/mpeg" to "mp3", "audio/mp4" to "m4a",
        "audio/ogg" to "ogg", "audio/webm" to "webm",
    )

    /** Android MediaPlayer memutar semua tipe di atas. */
    fun isPlayable(mime: String): Boolean = mime in ext

    fun fileName(id: String, mime: String): String? = ext[mime]?.let { "$id.$it" }

    /** Id aset yang perlu diunduh: dapat diputar dan belum ada di perangkat. */
    fun toDownload(assets: Map<String, AudioMeta>, existingFiles: Set<String>): List<String> =
        assets.filter { (id, m) -> isPlayable(m.mime) && fileName(id, m.mime) !in existingFiles }.keys.sorted()

    /** Isi berkas harus cocok dengan hash dari server sebelum disimpan. */
    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
