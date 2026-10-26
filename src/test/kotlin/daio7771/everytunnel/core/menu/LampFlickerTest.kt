package daio7771.everytunnel.core.menu

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class LampFlickerTest {

    /** Runs [ticks] ticks; per lamp, the ticks at which a burst started (lit -> dark after a lit stretch). */
    private fun run(flicker: LampFlicker, ticks: Int): List<MutableList<Int>> {
        val starts = List(flicker.size) { mutableListOf<Int>() }
        val litFor = IntArray(flicker.size)
        for (t in 1..ticks) {
            flicker.tick()
            for (lamp in 0 until flicker.size) {
                if (flicker.isLit(lamp)) {
                    litFor[lamp]++
                } else {
                    if (litFor[lamp] > 10) starts[lamp] += t
                    litFor[lamp] = 0
                }
            }
        }
        return starts
    }

    @Test
    fun `lamps start lit and none flickers in the first two seconds`() {
        val flicker = LampFlicker(30, Random(1))
        repeat(39) {
            flicker.tick()
            assertTrue((0 until 30).all { flicker.isLit(it) })
        }
    }

    @Test
    fun `each lamp flickers every 20 to 40 seconds`() {
        val flicker = LampFlicker(20, Random(7))
        val starts = run(flicker, 20 * 60 * 5)
        for (lamp in starts) {
            assertTrue(lamp.size in 6..16, "bursts in five minutes: ${lamp.size}")
            assertTrue(lamp.first() <= 40 * 20 + 40, "the first burst comes within the first 40 s")
            for ((a, b) in lamp.zipWithNext()) assertTrue(b - a >= 20 * 20, "at least 20 s between bursts: ${b - a}")
        }
    }

    @Test
    fun `bursts are short and end lit`() {
        val flicker = LampFlicker(1, Random(3))
        var darkStretch = 0
        var longest = 0
        repeat(20 * 60 * 10) {
            flicker.tick()
            if (flicker.isLit(0)) darkStretch = 0 else darkStretch++
            longest = maxOf(longest, darkStretch)
        }
        assertTrue(longest in 1..4, "a lamp goes dark, but never for long: $longest ticks")
        // One burst that ends at the first switch back on, then a long wait: lit.
        val quiet = LampFlicker(1, Random(5), LampFlicker.Timing(minGapTicks = 1000, maxGapTicks = 1000, endChance = 1.0))
        repeat(1100) { quiet.tick() }
        assertTrue(quiet.isLit(0))
    }
}
