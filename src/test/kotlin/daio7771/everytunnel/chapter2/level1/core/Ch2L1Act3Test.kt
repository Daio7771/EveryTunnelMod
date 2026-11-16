package daio7771.everytunnel.chapter2.level1.core

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Ch2L1Act3Test {

    private val data = Ch2L1DataParser.parse(emptyMap(), Ch2L1Files.readAllBundled()).data
    private val result = Ch2L1Act3Parser.parse(data.document("act3"))
    private val act3 = result.act3

    private fun parse(json: String) = Ch2L1Act3Parser.parse(JsonParser.parseString(json).asJsonObject)

    @Test
    fun `the jar's act3 file reads without a problem and says what the plan says`() {
        assertEquals(emptyList(), result.problems)
        assertEquals("4719", act3.code)
        assertEquals(listOf("chamber_log", "authorization", "medical_file", "contract_14", "basement_note"), act3.allFiles)
        assertEquals("rowan", act3.triggerComputer)
        assertEquals(listOf("medical_file", "contract_14"), act3.computer("rowan")!!.files)
        assertEquals(1200, act3.stuckTicks)
    }

    @Test
    fun `only Rowan's code opens the keypad`() {
        assertTrue(act3.accepts("4719"))
        assertFalse(act3.accepts("4710"))
        assertFalse(act3.accepts("47190"))
        assertFalse(act3.accepts(""))
    }

    @Test
    fun `the objective moves on with what the player has done`() {
        val counters = mutableMapOf<String, Int>()
        val objective = { act3.objective { counters[it] ?: 0 } }
        assertEquals(Ch2L1Act3.EXPLORE_LAB, objective())
        counters[Ch2L1Act3.LAB_OPEN] = 1
        counters[Ch2L1Act3.READ] = 1 shl act3.fileBit("chamber_log")
        assertEquals(Ch2L1Act3.EXPLORE_LAB, objective(), "the chamber's log alone does not give the code")
        counters[Ch2L1Act3.READ] = counters.getValue(Ch2L1Act3.READ) or (1 shl act3.fileBit("authorization"))
        assertEquals(Ch2L1Act3.EXPLORE_OFFICE, objective())
        counters[Ch2L1Act3.IN_OFFICE] = 1
        assertEquals(Ch2L1Act3.SEARCH_OFFICE, objective())
    }

    @Test
    fun `Rowan's computer is read when both its files are`() {
        val rowan = act3.computer("rowan")!!
        var read = 1 shl act3.fileBit("medical_file")
        assertFalse(act3.allRead(rowan) { if (it == Ch2L1Act3.READ) read else 0 })
        read = read or (1 shl act3.fileBit("contract_14"))
        assertTrue(act3.allRead(rowan) { if (it == Ch2L1Act3.READ) read else 0 })
    }

    @Test
    fun `an empty file gives the defaults and a bad one is told`() {
        assertEquals(Ch2L1Act3.DEFAULT, parse("{}").act3)
        val bad = parse("""{ "code": "47a9", "code_file": "nothing", "trigger_computer": "nobody", "guide_after_seconds": -1, "typo": 1 }""")
        assertTrue(bad.problems.any { it.startsWith("code:") })
        assertTrue(bad.problems.any { it.startsWith("code_file:") })
        assertTrue(bad.problems.any { it.startsWith("trigger_computer:") })
        assertTrue(bad.problems.any { it.startsWith("guide_after_seconds") })
        assertTrue(bad.problems.any { it.startsWith("typo") })
        val computers = parse("""{ "computers": [ { "id": "a", "anchor": "x", "files": ["f"] }, { "anchor": "y" }, 3 ], "code_file": "f", "trigger_computer": "a" }""")
        assertEquals(listOf(Ch2L1Computer("a", "x", listOf("f"))), computers.act3.computers)
        assertTrue(computers.problems.any { "computers[1].id" in it })
        assertTrue(computers.problems.any { "computers[2]" in it })
    }
}
