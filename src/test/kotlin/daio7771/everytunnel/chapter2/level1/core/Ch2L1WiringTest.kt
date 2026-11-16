package daio7771.everytunnel.chapter2.level1.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Ch2L1WiringTest {

    private val data = Ch2L1DataParser.parse(emptyMap(), Ch2L1Files.readAllBundled()).data
    private val tasks = Ch2L1TasksParser.parse(data.document("tasks")).tasks
    private val speakers = Ch2L1SpeakersParser.parse(data.document("speakers")).speakers

    @Test
    fun `everything the jar's tasks and speakers files name is in its anchors`() {
        assertEquals(emptyList(), Ch2L1Wiring.problems(data.anchors, tasks, speakers))
    }

    @Test
    fun `everything the jar's act3 file names is in its anchors`() {
        val act3 = Ch2L1Act3Parser.parse(data.document("act3")).act3
        assertEquals(emptyList(), Ch2L1Wiring.problems(data.anchors, tasks, speakers, act3 = act3))
        val bare = Ch2L1Anchors("minecraft:overworld", emptyList(), emptyList())
        val problems = Ch2L1Wiring.problems(bare, tasks, speakers, act3 = act3)
        for (id in listOf("aux_lab_reader", "office_keypad", "rowan_computer", "chamber_02", "\"office\"")) assertTrue(problems.any { id in it }, "nothing about $id")
    }

    @Test
    fun `what is missing is told`() {
        val bare = Ch2L1Anchors("minecraft:overworld", emptyList(), emptyList())
        val problems = Ch2L1Wiring.problems(bare, tasks, speakers)
        assertTrue(problems.any { "task1_tube_1" in it })
        assertTrue(problems.any { "task2_analyzer_block" in it })
        assertTrue(problems.any { "task3_dirt_spots" in it })
        assertTrue(problems.any { "speaker_lab" in it })
        assertTrue(problems.any { "speaker_lab_src" in it })
    }

    @Test
    fun `the lines the speakers say have voices and texts in both languages`() {
        val voices = Ch2L1Voices.load()
        val lines = speakers.fixed.map { it.line } + speakers.pool + speakers.intro!!.line
        for (line in lines) assertTrue(voices.line(line) != null, "no voice line $line")
    }
}
