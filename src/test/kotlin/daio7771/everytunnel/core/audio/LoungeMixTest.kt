package daio7771.everytunnel.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals

class LoungeMixTest {

    @Test
    fun `each room has its own mix`() {
        assertEquals(LoungeMix(1f, 0f), LoungeMix.of(setOf("ch1_l4_bar"), false))
        assertEquals(LoungeMix(0.25f, 0f), LoungeMix.of(setOf("ch1_l4_suites"), false))
        assertEquals(LoungeMix(0f, 1f), LoungeMix.of(setOf("ch1_l4_calm"), false))
        assertEquals(LoungeMix.SILENT, LoungeMix.of(setOf("ch1_l4_maintenance"), false))
        assertEquals(LoungeMix.SILENT, LoungeMix.of(setOf("ch1_l4_exit"), false))
    }

    @Test
    fun `the film and being outside the level silence it`() {
        assertEquals(LoungeMix.SILENT, LoungeMix.of(setOf("ch1_l4_bar"), true))
        assertEquals(LoungeMix.SILENT, LoungeMix.of(emptySet(), false))
    }

    @Test
    fun `a quiet zone wins over another one overlapping it`() {
        assertEquals(LoungeMix.SILENT, LoungeMix.of(setOf("ch1_l4_past_pool", "ch1_l4_door_maintenance"), false))
    }
}
