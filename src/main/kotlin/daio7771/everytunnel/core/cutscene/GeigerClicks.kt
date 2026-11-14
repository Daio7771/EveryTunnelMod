package daio7771.everytunnel.core.cutscene

import java.util.SplittableRandom
import kotlin.math.exp

/**
 * The counts of a Geiger counter, tick by tick: a Poisson process, so that the clicks fall at random but at a
 * rate that can rise (the prologue's counter keeps climbing). The same [seed], tick and rate always give the same
 * number, so a scene sounds the same each time it plays. Plain Kotlin, unit tested.
 */
class GeigerClicks(private val seed: Long) {

    /** How many counts fall in game tick [tick], at [ratePerSecond] counts a second on average (Knuth's method). */
    fun counts(tick: Int, ratePerSecond: Double): Int {
        if (ratePerSecond <= 0.0) return 0
        val limit = exp(-ratePerSecond / TICKS_PER_SECOND)
        // Not java.util.Random: its first draws for consecutive seeds are correlated, which skews the rate.
        val random = SplittableRandom(seed * 1_000_003L + tick)
        var k = 0
        var p = 1.0
        do {
            k++
            p *= random.nextDouble()
        } while (p > limit && k < MAX_COUNTS)
        return k - 1
    }

    companion object {
        private const val TICKS_PER_SECOND = 20.0

        /** A tick never counts more than this many, however high the rate. */
        private const val MAX_COUNTS = 12
    }
}
