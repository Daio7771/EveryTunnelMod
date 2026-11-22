package daio7771.everytunnel.chapter2.level2.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Ch2L2SupplyWatchTest {

    private val rules = Ch2L2NoSupplies("zone", "anchor", listOf("bar_", "water_"), listOf(Ch2L2HunterSpot(1.0, 0.0, 0.0, 0f)))
    private val ids = listOf("bar_1", "bar_2", "water_1", "other")

    private fun watch() = Ch2L2SupplyWatch<String>(rules, ids)

    @Test
    fun `leaving the room with nothing taken fires once`() {
        val w = watch()
        assertFalse(w.update("p", true) { false })
        assertTrue(w.update("p", false) { false })
        assertFalse(w.update("p", false) { false })
        assertFalse(w.update("p", true) { false })
        assertFalse(w.update("p", false) { false }, "fires once until reset")
    }

    @Test
    fun `either a bar or water missing is enough, both taken is not`() {
        for ((taken, fires) in listOf(setOf("bar_1") to true, setOf("water_1") to true, setOf("bar_2", "water_1") to false)) {
            val w = watch()
            w.update("p", true) { false }
            assertTrue(w.update("p", false) { it in taken } == fires, taken.toString())
        }
    }

    @Test
    fun `a player never seen in the room does not fire, and a reset lets it fire again`() {
        val w = watch()
        assertFalse(w.update("p", false) { false })
        w.update("p", true) { false }
        assertTrue(w.update("p", false) { false })
        w.reset("p")
        assertFalse(w.update("p", false) { false })
        w.update("p", true) { false }
        assertTrue(w.update("p", false) { false })
    }

    @Test
    fun `players are kept apart`() {
        val w = watch()
        w.update("a", true) { false }
        assertFalse(w.update("b", false) { false })
        assertTrue(w.update("a", false) { false })
    }
}
