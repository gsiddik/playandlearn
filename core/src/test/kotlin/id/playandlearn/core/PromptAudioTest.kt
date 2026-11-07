package id.playandlearn.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PromptAudioTest {
    private val a = "11111111-1111-1111-1111-111111111111"
    private val b = "22222222-2222-2222-2222-222222222222"

    @Test fun fileNameFromMime() {
        assertEquals("$a.wav", PromptAudio.fileName(a, "audio/wav"))
        assertEquals("$a.mp3", PromptAudio.fileName(a, "audio/mpeg"))
        assertNull(PromptAudio.fileName(a, "image/png"))
        assertTrue(PromptAudio.isPlayable("audio/webm"))
        assertFalse(PromptAudio.isPlayable("image/png"))
    }

    @Test fun downloadsOnlyMissingPlayable() {
        val assets = mapOf(a to AudioMeta("audio/wav", "x", 10), b to AudioMeta("audio/mpeg", "y", 10))
        assertEquals(listOf(a, b), PromptAudio.toDownload(assets, emptySet()))
        assertEquals(listOf(b), PromptAudio.toDownload(assets, setOf("$a.wav")))
        assertEquals(emptyList(), PromptAudio.toDownload(mapOf(a to AudioMeta("image/png", "x", 1)), emptySet()))
    }

    @Test fun hexEncoding() { assertEquals("00ff10", PromptAudio.hex(byteArrayOf(0, -1, 16))) }
}
