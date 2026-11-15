package daio7771.everytunnel.core.ch2l1

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Ch2L1SpeakersTest {

    private val data = Ch2L1SpeakersParser.parse(JsonObject()).speakers

    /** Every line takes ten seconds, so that "never two at once" shows. */
    private fun schedule(seed: Long = 1) = Ch2L1SpeakerSchedule(data, { 200 }, Random(seed))

    private fun parse(json: String) = Ch2L1SpeakersParser.parse(JsonParser.parseString(json).asJsonObject)

    @Test
    fun `the jar's speakers file parses without a problem and keeps the plan's rules`() {
        val document = Ch2L1DataParser.parse(emptyMap(), Ch2L1Files.readAllBundled()).data.document("speakers")
        val result = Ch2L1SpeakersParser.parse(document)
        assertEquals(emptyList(), result.problems)
        val speakers = result.speakers
        assertEquals(listOf("rowan_loyal_a1" to 0, "rowan_loyal_a4" to 100), speakers.fixed.map { it.line to it.delayTicks })
        assertEquals(listOf(Ch2L1State.ACT1_TASK2, Ch2L1State.ACT1_TASK3), speakers.fixed.map { it.after })
        assertEquals(20, speakers.afterTerminalTicks)
        assertEquals(listOf("rowan_loyal_a2", "rowan_loyal_a3", "rowan_loyal_a5"), speakers.pool)
        assertTrue("rowan_loyal_a6" !in speakers.pool && speakers.fixed.none { it.line == "rowan_loyal_a6" }, "a6 is kept for the office")
        assertEquals(75 * 20 to 120 * 20, speakers.cooldownMinTicks to speakers.cooldownMaxTicks)
        assertEquals(setOf("speaker_entrance", "speaker_lab", "speaker_main", "speaker_west"), speakers.zones.toSet())
        assertNotNull(speakers.intro)
        assertEquals("rowan_loyal_a1", speakers.intro!!.line)
        assertEquals(1, speakers.intro!!.shots.size)
        assertEquals(Ch2L1IntroScene.PLAYER, speakers.intro!!.shots[0].camera)
    }

    @Test
    fun `with the jar's own file, a1 plays after task 1 and the terminal, then a4, and the pool follows`() {
        val jar = Ch2L1SpeakersParser.parse(Ch2L1DataParser.parse(emptyMap(), Ch2L1Files.readAllBundled()).data.document("speakers")).speakers
        val s = Ch2L1SpeakerSchedule(jar, { 200 }, Random(5))
        s.onState(Ch2L1State.ACT1_CLOCKIN, 0)
        s.onState(Ch2L1State.ACT1_TASK1, 10)
        for (t in 10L..1000L) assertNull(s.tick(t, true), "silent during task 1, at $t")
        s.onState(Ch2L1State.ACT1_TASK2, 1000)
        s.holdUntil(1020)
        assertNull(s.tick(1010, true))
        assertEquals("rowan_loyal_a1", s.tick(1021, true))
        s.onState(Ch2L1State.ACT1_TASK3, 3000)
        assertNull(s.tick(3099, true))
        assertEquals("rowan_loyal_a4", s.tick(3100, true))
    }

    @Test
    fun `the player at the terminal and by the analyzer stands in the lab's speaker zone`() {
        val data = Ch2L1DataParser.parse(emptyMap(), Ch2L1Files.readAllBundled()).data
        val speakers = Ch2L1SpeakersParser.parse(data.document("speakers")).speakers
        fun zoneAt(x: Double, y: Double, z: Double) = speakers.zones.firstOrNull { data.anchors.zone(it)?.box?.contains(x, y, z) == true }
        assertEquals("speaker_lab", zoneAt(1058.5, 64.0, 1050.5), "at the terminal")
        assertEquals("speaker_lab", zoneAt(1061.0, 64.0, 1055.0), "where the log put the player")
        assertEquals("speaker_entrance", zoneAt(1053.5, 64.0, 1029.5), "at the clock")
        assertEquals("speaker_main", zoneAt(1050.0, 64.0, 1040.0), "in the main corridor")
    }

    @Test
    fun `a schedule knows when the player's progress has gone back`() {
        val s = schedule()
        assertTrue(!s.regressed(Ch2L1State.START), "it has seen nothing yet")
        s.onState(Ch2L1State.ACT1_TASK3, 0)
        assertTrue(!s.regressed(Ch2L1State.ACT1_TASK3))
        assertTrue(!s.regressed(Ch2L1State.ACT2_FOLLOW))
        assertTrue(s.regressed(Ch2L1State.START), "a new game puts the progress back at the start")
        assertTrue(s.regressed(Ch2L1State.ACT1_TASK1))
    }

    @Test
    fun `a stale schedule from a finished game says nothing, a new one speaks`() {
        // the player's last game ended in Act 2: the schedule made for it has heard everything and is cancelled
        val stale = schedule()
        stale.restore(listOf("rowan_loyal_a1", "rowan_loyal_a4", "rowan_loyal_a2"))
        stale.onState(Ch2L1State.ACT2_FOLLOW, 0)
        // a new game: the progress is back at the start, the schedule says so, and one made afresh plays the opening after the first task
        assertTrue(stale.regressed(Ch2L1State.START))
        val fresh = schedule()
        fresh.onState(Ch2L1State.ACT1_TASK1, 0)
        fresh.onState(Ch2L1State.ACT1_TASK2, 10)
        assertEquals("rowan_loyal_a1", fresh.tick(10, true))
    }

    @Test
    fun `bad entries are reported and what is usable stays`() {
        val result = parse(
            """{ "fixed": [ { "line": "x", "after": "NOPE" }, 3, { "line": "y", "after": "ACT1_TASK2", "delay_seconds": 2 } ],
                 "cooldown_seconds": [10], "intro": { "shots": [ { "camera": "c" } ] } }""",
        )
        assertEquals(listOf(Ch2L1FixedLine("y", Ch2L1State.ACT1_TASK2, 40)), result.speakers.fixed)
        assertEquals(75 * 20 to 120 * 20, result.speakers.cooldownMinTicks to result.speakers.cooldownMaxTicks)
        assertNull(result.speakers.intro)
        assertTrue(result.problems.size >= 4, result.problems.toString())
    }

    @Test
    fun `Rowan says nothing until the first task is done`() {
        val s = schedule()
        s.onState(Ch2L1State.ACT1_CLOCKIN, 0)
        for (t in 0L..30_000L step 7) assertNull(s.tick(t, true), "nothing before clocking in, at $t")
        s.onState(Ch2L1State.ACT1_TASK1, 30_000)
        for (t in 30_000L..400_000L step 7) assertNull(s.tick(t, true), "nothing during the first task, not even the pool, at $t")
        s.onState(Ch2L1State.ACT1_TASK2, 400_000)
        assertEquals("rowan_loyal_a1", s.tick(400_000, true), "the opening comes as the first task ends")
    }

    @Test
    fun `leaving the terminal holds the opening a beat`() {
        val s = schedule()
        s.onState(Ch2L1State.ACT1_TASK1, 0)
        s.onState(Ch2L1State.ACT1_TASK2, 100)
        // the terminal is open (the caller says no zone), then the player leaves it at tick 160 and a second goes by
        assertNull(s.tick(150, false))
        s.holdUntil(160 + 20)
        assertNull(s.tick(170, true))
        assertNull(s.tick(179, true))
        assertEquals("rowan_loyal_a1", s.tick(180, true))
    }

    @Test
    fun `a line waits for the player to be in a zone`() {
        val s = schedule()
        s.onState(Ch2L1State.ACT1_TASK2, 0)
        assertNull(s.tick(100, false))
        assertNull(s.tick(5000, false))
        assertEquals("rowan_loyal_a1", s.tick(5001, true))
    }

    @Test
    fun `never two at once, and the pool starts only after the first line plus a cooldown`() {
        val s = schedule()
        s.onState(Ch2L1State.ACT1_TASK2, 0)
        assertEquals("rowan_loyal_a1", s.tick(60, true))
        // it takes until 260; the cooldown (75 to 120 s) runs from its end
        for (t in 61..260L) assertNull(s.tick(t, true), "at $t")
        for (t in 261 until 260 + 75 * 20L) assertNull(s.tick(t, true), "at $t, in the cooldown")
        var said: String? = null
        var t = 260 + 75 * 20L
        while (said == null && t <= 260 + 120 * 20L + 1) said = s.tick(t++, true)
        assertNotNull(said)
        assertTrue(said in data.pool, said)
    }

    @Test
    fun `each pool line plays once, in some order, and never a6 nor the fixed ones`() {
        val s = schedule(7)
        s.onState(Ch2L1State.ACT1_TASK2, 0)
        val said = mutableListOf<String>()
        for (t in 0L..400_000L) s.tick(t, true)?.let { said += it }
        assertEquals("rowan_loyal_a1", said.first())
        assertEquals(data.pool.toSet(), said.drop(1).toSet())
        assertEquals(said.size, said.toSet().size, "none twice: $said")
    }

    @Test
    fun `the fourth line plays five seconds after the second task, even over the cooldown`() {
        val s = schedule()
        s.onState(Ch2L1State.ACT1_TASK2, 0)
        s.tick(60, true)
        s.onState(Ch2L1State.ACT1_TASK3, 1000)
        assertNull(s.tick(1099, true))
        assertEquals("rowan_loyal_a4", s.tick(1100, true))
    }

    @Test
    fun `act two cancels everything`() {
        val s = schedule()
        s.onState(Ch2L1State.ACT1_TASK2, 0)
        s.onState(Ch2L1State.ACT1_TASK3, 100)
        s.onState(Ch2L1State.ACT2_FOLLOW, 110)
        for (t in 0L..300_000L step 7) assertNull(s.tick(t, true))
    }

    @Test
    fun `a player put past the opening's state never hears it and the rest still plays`() {
        val s = schedule()
        s.onState(Ch2L1State.ACT1_TASK3, 0)
        assertTrue("rowan_loyal_a1" in s.playedLines)
        val said = mutableListOf<String>()
        for (t in 0L..400_000L) s.tick(t, true)?.let { said += it }
        assertEquals(listOf("rowan_loyal_a4") + said.drop(1), said, "the fourth line first, five seconds in")
        assertEquals((data.pool + "rowan_loyal_a4").toSet(), said.toSet())
    }

    @Test
    fun `what was heard before leaving is not said again`() {
        val s = schedule()
        s.restore(listOf("rowan_loyal_a1", "rowan_loyal_a2"))
        s.onState(Ch2L1State.ACT1_TASK2, 0)
        val said = mutableListOf<String>()
        for (t in 0L..400_000L) s.tick(t, true)?.let { said += it }
        assertEquals(listOf("rowan_loyal_a3", "rowan_loyal_a5").toSet(), said.toSet())
    }
}
