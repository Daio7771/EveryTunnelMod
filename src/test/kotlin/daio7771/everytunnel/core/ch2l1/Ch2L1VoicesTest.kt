package daio7771.everytunnel.core.ch2l1

import com.google.gson.JsonParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Ch2L1VoicesTest {

    private val assets = File("src/main/resources/assets/every_tunnel")

    private fun lang(name: String): Map<String, String> =
        JsonParser.parseString(File(assets, "lang/$name.json").readText()).asJsonObject.entrySet()
            .associate { it.key to it.value.asString }

    private val text = "Enough. Both of you. One is shaking like a leaf. This is a secret meeting, not a circus."

    private val line = Ch2L1VoiceLine(
        "meeting_05", "k.meeting_05", "k.speaker", 8200,
        listOf(Ch2L1Cue(0, 0, 2), Ch2L1Cue(3000, 2, 3), Ch2L1Cue(6000, 3, null)),
    )

    @Test
    fun `sentences end at a full stop, a question or an exclamation mark followed by a space`() {
        assertEquals(listOf("Enough.", "Both of you."), Ch2L1Sentences.split("Enough. Both of you."))
        assertEquals(listOf("Why?", "Because!", "Ha ha."), Ch2L1Sentences.split("Why? Because! Ha ha."))
        assertEquals(listOf("Those who don't...", "well.", "Time is rarely kind."), Ch2L1Sentences.split("Those who don't... well. Time is rarely kind."))
        assertEquals(listOf("¿Quién te dijo que estaría abierta?", "Nadie."), Ch2L1Sentences.split("¿Quién te dijo que estaría abierta? Nadie."))
        assertEquals(listOf("One only"), Ch2L1Sentences.split("  One only  "))
        assertEquals(emptyList(), Ch2L1Sentences.split("   "))
    }

    @Test
    fun `a cue shows the sentences of its range from its time until the next cue`() {
        assertEquals("Enough. Both of you.", line.textAt(0, text))
        assertEquals("Enough. Both of you.", line.textAt(2999, text))
        assertEquals("One is shaking like a leaf.", line.textAt(3000, text))
        assertEquals("This is a secret meeting, not a circus.", line.textAt(8000, text))
    }

    @Test
    fun `a line with no cues, or not begun, shows nothing`() {
        assertNull(Ch2L1VoiceLine("x", "k", "s", 1000, emptyList()).textAt(500, text))
        assertNull(Ch2L1VoiceLine("x", "k", "s", 1000, listOf(Ch2L1Cue(300, 0, null))).textAt(100, text))
    }

    @Test
    fun `a translation with fewer sentences than the cues expect never fails`() {
        assertEquals("Basta.", line.textAt(0, "Basta."))
        assertNull(line.textAt(3000, "Basta."))
        assertEquals("Basta. Los dos. Uno tiembla. Esto no es un circo. Y otra más.", Ch2L1VoiceLine("x", "k", "s", 1, listOf(Ch2L1Cue(0, 0, null)))
            .textAt(0, "Basta. Los dos. Uno tiembla. Esto no es un circo. Y otra más."))
    }

    @Test
    fun `a line is over a moment after its voice ends`() {
        assertEquals(false, line.isOver(8200, 600))
        assertEquals(false, line.isOver(8800, 600))
        assertEquals(true, line.isOver(8801, 600))
    }

    @Test
    fun `a sound is matched to its line by its path`() {
        val voices = Ch2L1Voices(listOf(line))
        assertEquals(line, voices.ofSound("voice.meeting_05"))
        assertNull(voices.ofSound("voice.meeting_06"))
        assertNull(voices.ofSound("voice.director.boss_01"))
        assertNull(voices.ofSound("meeting_05"))
        assertNull(voices.ofSound("item.flashlight.on"))
    }

    @Test
    fun `parse reads the text, the speaker, the length and the cues`() {
        val voices = Ch2L1Voices.parse(
            """{ "lines": [ { "id": "meeting_03_a", "text": "k.meeting_03", "speaker": "k.worker_a", "ms": 4830,
                "cues": [ { "at": 0, "from": 0, "to": 1 }, { "at": 2040, "from": 1 } ] } ] }""",
        )
        val a = voices.line("meeting_03_a")!!
        assertEquals("k.meeting_03", a.textKey)
        assertEquals("k.worker_a", a.speakerKey)
        assertEquals(4830, a.ms)
        assertEquals(listOf(Ch2L1Cue(0, 0, 1), Ch2L1Cue(2040, 1, null)), a.cues)
    }

    // The files in the jar -----------------------------------------------------------------------------------------

    private val ids = listOf("follow_01", "follow_02", "follow_03") +
        (1..15).map { n ->
            "meeting_%02d".format(n) + mapOf(3 to "_a", 6 to "_a", 10 to "_a", 4 to "_b", 8 to "_b", 14 to "_b").getOrDefault(n, "")
        } +
        (1..6).map { "rowan_loyal_a$it" } + (1..8).map { "rowan_office_c$it" }

    @Test
    fun `the jar has all 32 voice lines of the level`() {
        assertEquals(ids.sorted(), Ch2L1Voices.load().lines.map { it.id }.sorted())
    }

    @Test
    fun `every line has its sound event and its Ogg`() {
        val events = JsonParser.parseString(File(assets, "sounds.json").readText()).asJsonObject
        for (id in ids) {
            assertTrue(events.has("voice.$id"), "no sound event voice.$id (run tools/sounds/export.py)")
            assertTrue(File(assets, "sounds/voice/ch2l1/$id.ogg").isFile, "no Ogg for $id")
        }
    }

    @Test
    fun `every line has its text and its speaker in both languages, with as many sentences in each`() {
        val english = lang("en_us")
        val spanish = lang("es_es")
        for (line in Ch2L1Voices.load().lines) {
            for (key in listOf(line.textKey, line.speakerKey)) {
                assertNotNull(english[key], "$key is missing from en_us.json")
                assertNotNull(spanish[key], "$key is missing from es_es.json")
            }
            assertEquals(
                Ch2L1Sentences.split(english.getValue(line.textKey)).size,
                Ch2L1Sentences.split(spanish.getValue(line.textKey)).size,
                "${line.textKey} has not the same number of sentences in English and in Spanish: its cues would show different things",
            )
        }
    }

    @Test
    fun `every line's cues are in order, cover all its sentences and start before its voice ends`() {
        val english = lang("en_us")
        for (line in Ch2L1Voices.load().lines) {
            val count = Ch2L1Sentences.split(english.getValue(line.textKey)).size
            assertTrue(line.ms > 0, "${line.id} has no length")
            assertTrue(line.cues.isNotEmpty(), "${line.id} has no subtitles")
            assertEquals(Ch2L1Cue(0, 0, line.cues.first().endSentence), line.cues.first(), "${line.id}: the first cue must start at 0 with the first sentence")
            line.cues.zipWithNext { a, b ->
                assertTrue(a.atMs < b.atMs, "${line.id}'s cues are out of order")
                assertEquals(b.firstSentence, a.endSentence, "${line.id}: a cue does not go on where the one before ends")
            }
            assertNull(line.cues.last().endSentence, "${line.id}: the last cue must go on to the end of the text")
            assertTrue(line.cues.last().firstSentence < count, "${line.id}: the last cue starts after the last sentence")
            assertTrue(line.cues.last().atMs < line.ms, "${line.id}'s last cue starts after the voice ends")
        }
    }

    @Test
    fun `the level's lang keys are all under one prefix and none is empty`() {
        val english = lang("en_us").filterKeys { it.startsWith("every_tunnel.ch2.l1.") }
        assertTrue(english.isNotEmpty())
        assertTrue(english.values.none { it.isBlank() })
    }
}
