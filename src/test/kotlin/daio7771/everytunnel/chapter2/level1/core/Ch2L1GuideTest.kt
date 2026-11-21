package daio7771.everytunnel.chapter2.level1.core

import daio7771.everytunnel.common.core.level.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Ch2L1GuideTest {

    private val data = Ch2L1DataParser.parse(emptyMap(), Ch2L1Files.readAllBundled()).data
    private val anchors = data.anchors
    private val tasks = Ch2L1TasksParser.parse(data.document("tasks")).tasks
    private val height = tasks.guide.height

    private fun targets(state: Ch2L1State, counters: Map<String, Int> = emptyMap()) =
        Ch2L1Guide.targets(state, tasks, anchors) { counters[it] ?: 0 }

    private fun over(id: String) = anchors.point(id)!!.position.let { Position(it.x, it.y + height, it.z) }

    @Test
    fun `the jar's guide is on, over the object, and hides near`() {
        assertTrue(tasks.guide.enabled)
        assertEquals(1.1, tasks.guide.height)
        assertEquals(1.5, tasks.guide.hideWithin)
        assertEquals(Ch2L1GuideSpec(true, 1.1, 1.5), Ch2L1TasksParser.parse(JsonObjectOf()).tasks.guide)
    }

    private fun JsonObjectOf() = com.google.gson.JsonObject()

    @Test
    fun `before clocking in it points at the clock`() {
        assertEquals(listOf(over("time_clock")), targets(Ch2L1State.ACT1_CLOCKIN))
    }

    @Test
    fun `in the first task it points at the tubes not taken, then at the terminal`() {
        assertEquals(tasks.tubes.map { over(it.anchor) }, targets(Ch2L1State.ACT1_TASK1))
        val one = Ch2L1Bits.with(0, 1)
        assertEquals(listOf(over("task1_tube_1"), over("task1_tube_3")), targets(Ch2L1State.ACT1_TASK1, mapOf(Ch2L1Tasks.TUBES to one)))
        val all = Ch2L1Bits.first(3)
        assertEquals(listOf(over("task1_terminal_block")), targets(Ch2L1State.ACT1_TASK1, mapOf(Ch2L1Tasks.TUBES to all)))
    }

    @Test
    fun `the second task points at the analyzer`() {
        assertEquals(listOf(over("task2_analyzer_block")), targets(Ch2L1State.ACT1_TASK2))
    }

    @Test
    fun `the third points at the broom, then at the dirt not swept`() {
        assertEquals(listOf(over("task3_broom_block")), targets(Ch2L1State.ACT1_TASK3))
        val dirt = anchors.path(tasks.dirtPath)!!.points
        val withBroom = mapOf(Ch2L1Tasks.BROOM to 1)
        assertEquals(dirt.map { Position(it.x, it.y + height, it.z) }, targets(Ch2L1State.ACT1_TASK3, withBroom))
        val swept = Ch2L1Bits.with(Ch2L1Bits.with(0, 0), 4)
        val left = targets(Ch2L1State.ACT1_TASK3, withBroom + (Ch2L1Tasks.SWEPT to swept))
        assertEquals(dirt.size - 2, left.size)
        assertFalse(Position(dirt[4].x, dirt[4].y + height, dirt[4].z) in left)
    }

    @Test
    fun `later states have no target yet`() {
        for (state in listOf(Ch2L1State.ACT2_FOLLOW, Ch2L1State.ACT2_MEETING, Ch2L1State.ACT3_CUTSCENE, Ch2L1State.DONE)) {
            assertEquals(emptyList(), targets(state), state.name)
        }
    }

    @Test
    fun `in Act 3 it points at the next thing to do`() {
        val act3 = Ch2L1Act3.DEFAULT
        fun at(counters: Map<String, Int>) = Ch2L1Guide.targets(Ch2L1State.ACT3_EXPLORE, tasks, anchors, act3) { counters[it] ?: 0 }
        assertEquals(listOf(over("aux_lab_reader")), at(emptyMap()))
        assertEquals(listOf(over("aux_lab_computer")), at(mapOf(Ch2L1Act3.LAB_OPEN to 1)))
        val authorization = 1 shl act3.fileBit("authorization")
        assertEquals(listOf(over("office_keypad")), at(mapOf(Ch2L1Act3.LAB_OPEN to 1, Ch2L1Act3.READ to authorization)))
        assertEquals(listOf(over("door_anchor")), at(mapOf(Ch2L1Act3.READ to authorization, Ch2L1Act3.OFFICE_OPEN to 1)))
        assertEquals(listOf(over("rowan_computer")), at(mapOf(Ch2L1Act3.READ to authorization, Ch2L1Act3.IN_OFFICE to 1)))
        assertFalse(Ch2L1Guide.stuck(act3.stuckTicks - 1L, act3))
        assertTrue(Ch2L1Guide.stuck(act3.stuckTicks.toLong(), act3))
    }

    @Test
    fun `locked in, it points at the manhole once the door has been tried`() {
        val act3 = Ch2L1Act3.DEFAULT
        fun at(counters: Map<String, Int>) = Ch2L1Guide.targets(Ch2L1State.ACT3_LOCKED_IN, tasks, anchors, act3) { counters[it] ?: 0 }
        assertEquals(emptyList(), at(emptyMap()))
        assertEquals(listOf(over("manhole")), at(mapOf(Ch2L1Overtime.TRIED_DOOR to 1)))
        assertEquals(Ch2L1Overtime.GO_HOME, act3.overtime.objective { 0 })
        assertEquals(Ch2L1Overtime.WAY_OUT, act3.overtime.objective { if (it == Ch2L1Overtime.TRIED_DOOR) 1 else 0 })
    }

    @Test
    fun `the arrow shows the nearest and hides when the player is near`() {
        val list = listOf(Position(10.0, 2.0, 10.0), Position(0.0, 2.0, 0.0), Position(-8.0, 2.0, 3.0))
        assertEquals(Position(0.0, 2.0, 0.0), Ch2L1Guide.nearest(list, Position(1.0, 1.6, 2.0)))
        assertNull(Ch2L1Guide.nearest(emptyList(), Position(0.0, 0.0, 0.0)))
        val spec = Ch2L1GuideSpec(true, 1.1, 1.5)
        assertTrue(Ch2L1Guide.hidden(Position(0.0, 2.0, 0.0), Position(1.0, 1.6, 1.0), spec))
        assertFalse(Ch2L1Guide.hidden(Position(0.0, 2.0, 0.0), Position(1.2, 1.6, 1.2), spec), "1.7 blocks along the floor is far enough")
        assertFalse(Ch2L1Guide.hidden(Position(0.0, 9.0, 0.0), Position(5.0, 1.6, 0.0), spec))
    }
}
