package daio7771.everytunnel.chapter2.level2.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PumpCycleTest {

    private val config = Ch2L2PumpConfig("pump_beacon", beacons = listOf("pump_beacon"), runSeconds = 6.0, silentSeconds = 6.0, slowSeconds = 1.0)
    private val cycle = PumpCycle(config, graceAfterStopSeconds = 0.25)

    @Test
    fun `it runs six seconds, slows in the last one and is silent six seconds`() {
        assertEquals(PumpPhase.RUNNING, cycle.phaseAt(0))
        assertEquals(PumpPhase.RUNNING, cycle.phaseAt(99))
        assertEquals(PumpPhase.SLOWING, cycle.phaseAt(100))
        assertEquals(PumpPhase.SLOWING, cycle.phaseAt(119))
        assertEquals(PumpPhase.SILENT, cycle.phaseAt(120))
        assertEquals(PumpPhase.SILENT, cycle.phaseAt(239))
        assertEquals(PumpPhase.RUNNING, cycle.phaseAt(240))
        assertEquals(240L, cycle.cycleLengthTicks)
    }

    @Test
    fun `it masks while running and slowing, and for the grace after it stops`() {
        assertTrue(cycle.masking(0))
        assertTrue(cycle.masking(110))
        assertTrue(cycle.masking(119))
        assertTrue(cycle.masking(120))
        assertTrue(cycle.masking(124))
        assertFalse(cycle.masking(125))
        assertFalse(cycle.masking(200))
        assertFalse(cycle.masking(239))
        assertTrue(cycle.masking(240))
    }

    @Test
    fun `the change of phase is told once`() {
        assertEquals((0L until 480L).filter { cycle.silenceBegins(it) }, listOf(120L, 360L))
        assertEquals((0L until 480L).filter { cycle.runBegins(it) }, listOf(0L, 240L))
        assertEquals(120L, cycle.silenceStartsAt)
    }
}
