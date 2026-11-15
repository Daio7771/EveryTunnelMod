package daio7771.everytunnel.core.ch2l1

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Ch2L1TasksTest {

    private fun parse(json: String) = Ch2L1TasksParser.parse(JsonParser.parseString(json).asJsonObject)

    @Test
    fun `an empty file is the defaults, three tubes and no problems`() {
        val result = Ch2L1TasksParser.parse(JsonObject())
        assertEquals(emptyList(), result.problems)
        assertEquals(listOf("S-0412", "S-0413", "S-0414"), result.tasks.tubes.map { it.code })
        assertEquals(listOf("task1_tube_1", "task1_tube_2", "task1_tube_3"), result.tasks.tubes.map { it.anchor })
        assertEquals(80, result.tasks.calibrateTicks)
        assertEquals(30, result.tasks.sweepTicks)
        assertEquals(24, result.tasks.registerTicks)
    }

    @Test
    fun `the jar's tasks file parses without a problem and says what the defaults say`() {
        val document = Ch2L1DataParser.parse(emptyMap(), Ch2L1Files.readAllBundled()).data.document("tasks")
        val result = Ch2L1TasksParser.parse(document)
        assertEquals(emptyList(), result.problems)
        assertEquals(Ch2L1Tasks.DEFAULT, result.tasks)
    }

    @Test
    fun `a file can change the times, the anchors and the tubes`() {
        val result = parse(
            """{ "task1": { "tubes": [ { "anchor": "a", "code": "X-1" }, { "anchor": "b" } ], "terminal": "t", "register_seconds": 2 },
                 "task2": { "analyzer": "z", "calibrate_seconds": 10 },
                 "task3": { "broom": "br", "dirt": "spots", "sweep_seconds": 0.5 } }""",
        )
        assertEquals(emptyList(), result.problems)
        val tasks = result.tasks
        assertEquals(listOf(Ch2L1TubeSpec("a", "X-1"), Ch2L1TubeSpec("b", "S-0413")), tasks.tubes)
        assertEquals("t", tasks.terminalAnchor)
        assertEquals(40, tasks.registerTicks)
        assertEquals("z", tasks.analyzerAnchor)
        assertEquals(200, tasks.calibrateTicks)
        assertEquals("br", tasks.broomAnchor)
        assertEquals("spots", tasks.dirtPath)
        assertEquals(10, tasks.sweepTicks)
    }

    @Test
    fun `bad entries are reported and the defaults stand in`() {
        val result = parse("""{ "task1": { "tubes": [ 5, { "code": "no anchor" } ] }, "task2": { "calibrate_seconds": -3 }, "oops": 1 }""")
        assertEquals(Ch2L1Tasks.DEFAULT.tubes, result.tasks.tubes)
        assertEquals(80, result.tasks.calibrateTicks)
        assertTrue(result.problems.any { "tubes[0]" in it }, result.problems.toString())
        assertTrue(result.problems.any { "no usable tube" in it }, result.problems.toString())
        assertTrue(result.problems.any { "calibrate_seconds" in it }, result.problems.toString())
        assertTrue(result.problems.any { "oops" in it }, result.problems.toString())
    }

    @Test
    fun `more tubes than bits are cut`() {
        val tubes = (1..12).joinToString { """{ "anchor": "t$it" }""" }
        val result = parse("""{ "task1": { "tubes": [ $tubes ] } }""")
        assertEquals(Ch2L1Tasks.MAX_TUBES, result.tasks.tubes.size)
        assertTrue(result.problems.any { "at most" in it })
    }

    @Test
    fun `bits hold a set of things`() {
        var mask = 0
        mask = Ch2L1Bits.with(mask, 0)
        mask = Ch2L1Bits.with(mask, 4)
        assertTrue(Ch2L1Bits.has(mask, 4))
        assertFalse(Ch2L1Bits.has(mask, 1))
        assertEquals(2, Ch2L1Bits.count(mask))
        assertEquals(0b111, Ch2L1Bits.first(3))
        assertEquals(10, Ch2L1Bits.count(Ch2L1Bits.first(10)))
    }

    @Test
    fun `a held use goes on while it comes in time and on the same target`() {
        val hold = HoldTracker(maxGapTicks = 8)
        assertEquals(0, hold.press("a", 100))
        assertEquals(4, hold.press("a", 104))
        assertEquals(8, hold.press("a", 108))
        assertEquals(0, hold.press("b", 112), "another target starts over")
        assertEquals(4, hold.press("b", 116))
        assertEquals(0, hold.press("b", 130), "a long gap starts over")
        hold.press("b", 134)
        hold.reset()
        assertEquals(0, hold.press("b", 138))
    }
}
