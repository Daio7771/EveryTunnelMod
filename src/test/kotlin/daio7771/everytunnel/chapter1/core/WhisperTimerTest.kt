package daio7771.everytunnel.chapter1.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WhisperTimerTest {

    /** The ticks (1-based) at which a whisper plays over [ticks] ticks of standing still. */
    private fun whispers(timer: WhisperTimer, ticks: Int): List<Int> = (1..ticks).filter { timer.tick(moved = false) }

    @Test
    fun `five seconds of stillness make the first whisper`() {
        val timer = WhisperTimer()
        repeat(WhisperTimer.STILL_TICKS - 1) { assertFalse(timer.tick(false)) }
        assertTrue(timer.tick(false))
    }

    @Test
    fun `moving starts the five seconds over`() {
        val timer = WhisperTimer()
        repeat(WhisperTimer.STILL_TICKS - 1) { timer.tick(false) }
        assertFalse(timer.tick(true))
        repeat(WhisperTimer.STILL_TICKS - 1) { assertFalse(timer.tick(false)) }
        assertTrue(timer.tick(false))
    }

    @Test
    fun `a minute at least between whispers, standing still all the while`() {
        val at = whispers(WhisperTimer(), 4 * WhisperTimer.COOLDOWN_TICKS)
        assertEquals(WhisperTimer.STILL_TICKS, at.first())
        assertTrue(at.size >= 3)
        assertTrue(at.zipWithNext().all { (a, b) -> b - a >= WhisperTimer.COOLDOWN_TICKS })
    }

    @Test
    fun `the cooldown runs while moving too`() {
        val timer = WhisperTimer()
        repeat(WhisperTimer.STILL_TICKS) { timer.tick(false) }
        repeat(WhisperTimer.COOLDOWN_TICKS) { timer.tick(true) }
        repeat(WhisperTimer.STILL_TICKS - 1) { assertFalse(timer.tick(false)) }
        assertTrue(timer.tick(false))
    }

    @Test
    fun `reset forgets everything`() {
        val timer = WhisperTimer()
        repeat(WhisperTimer.STILL_TICKS) { timer.tick(false) }
        timer.reset()
        assertEquals(listOf(WhisperTimer.STILL_TICKS), whispers(timer, WhisperTimer.STILL_TICKS + 10))
    }
}
