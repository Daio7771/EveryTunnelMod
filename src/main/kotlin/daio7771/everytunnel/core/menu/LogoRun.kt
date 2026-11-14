package daio7771.everytunnel.core.menu

import kotlin.math.sin
import kotlin.random.Random

/**
 * The main menu logo's easter egg (`client.screen.MainMenuScreen`): now and then the words go out, flickering
 * like the lamps behind them, and only the R of EVERY and the U and N of TUNNEL stay lit, which read RUN. They
 * glow for a couple of seconds, and then the words flicker back on.
 *
 * The logo is drawn in two layers (`tools/gui/logo.py`): the words and the emblem, which this puts out
 * ([wordsLevel]), and the three letters of RUN, which are always lit and brighter in the middle ([glow]).
 *
 * A run is [OUT_TICKS] of the words going out, [HOLD_TICKS] with only RUN, and [BACK_TICKS] of them coming
 * back. The first is [Timing.firstMinTicks] to [Timing.firstMaxTicks] after the menu opens, and each next one
 * [Timing.minGapTicks] to [Timing.maxGapTicks] after the last one ends. Plain Kotlin, one [tick] per game tick
 * (20 per second), unit tested.
 */
class LogoRun(private val random: Random, private val timing: Timing = Timing()) {

    data class Timing(
        val firstMinTicks: Int = 25 * 20,
        val firstMaxTicks: Int = 60 * 20,
        val minGapTicks: Int = 45 * 20,
        val maxGapTicks: Int = 120 * 20,
    )

    private var untilStart = between(timing.firstMinTicks, timing.firstMaxTicks)

    /** Ticks since the run began, or -1 while there is none. */
    private var age = -1

    val running: Boolean get() = age >= 0

    /** How lit the words and the emblem are, 0 (out) to 1: always 1 outside a run. */
    val wordsLevel: Float
        get() = when {
            age < 0 -> 1f
            age < OUT_TICKS -> OUT[age]
            age < OUT_TICKS + HOLD_TICKS -> 0f
            else -> BACK[age - OUT_TICKS - HOLD_TICKS]
        }

    /** How much brighter RUN shines, 0 to 1: it swells and fades over the time it is alone, and is 0 outside a run. */
    val glow: Float
        get() {
            val into = age - OUT_TICKS
            if (age < 0 || into < 0 || into >= HOLD_TICKS) return 0f
            return sin(Math.PI * into / HOLD_TICKS).let { (it * it).toFloat() }
        }

    fun tick() {
        if (age < 0) {
            if (--untilStart <= 0) age = 0
        } else if (++age >= OUT_TICKS + HOLD_TICKS + BACK_TICKS) {
            age = -1
            untilStart = between(timing.minGapTicks, timing.maxGapTicks)
        }
    }

    private fun between(min: Int, max: Int): Int = min + random.nextInt(max - min + 1)

    companion object {
        /** The words going out, a tick each: they stutter, and the last is out. */
        private val OUT = floatArrayOf(
            0.35f, 0.35f, 1f, 1f, 1f, 0.15f, 0.15f, 0.8f, 0.8f, 0.8f, 0f,
            0f, 0.5f, 0f, 0f, 0.3f, 0f, 0f, 0f, 0f, 0f, 0f,
        )

        /** The words coming back: they stutter too, and the last is lit. */
        private val BACK = floatArrayOf(
            0f, 0f, 0.2f, 0f, 0f, 0.6f, 0.6f, 0.1f, 0.1f, 1f, 1f,
            0.3f, 1f, 1f, 0.2f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
        )

        val OUT_TICKS = OUT.size
        const val HOLD_TICKS = 52
        val BACK_TICKS = BACK.size
    }
}
