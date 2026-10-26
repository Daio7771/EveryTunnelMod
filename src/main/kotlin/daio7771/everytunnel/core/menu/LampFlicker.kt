package daio7771.everytunnel.core.menu

import kotlin.random.Random

/**
 * When the lamps of the main menu's scene flicker (`client.menu.MenuScene`).
 *
 * Every lamp is lit, and now and then (every [Timing.minGapTicks] to [Timing.maxGapTicks],
 * counted from the end of its last burst) it goes through a burst of rapid off/on switching,
 * the rhythm of a flickering light in the levels (`platform.FluorescentLightBlock`): it goes
 * out, then every 1 to 4 ticks switches, and each time it comes back on the burst ends with
 * [Timing.endChance]. A burst always ends with the lamp lit. The first bursts are spread over
 * the first [Timing.maxGapTicks], so the lamps never flicker all together.
 *
 * Plain Kotlin, one [tick] per game tick (20 per second), unit tested.
 */
class LampFlicker(lamps: Int, private val random: Random, private val timing: Timing = Timing()) {

    data class Timing(
        val minGapTicks: Int = 20 * 20,
        val maxGapTicks: Int = 40 * 20,
        val endChance: Double = 0.2,
    )

    private val lit = BooleanArray(lamps) { true }
    private val bursting = BooleanArray(lamps)
    /** Ticks until each lamp's next switch (or the start of its next burst). */
    private val untilNext = IntArray(lamps) { FIRST_BURST_MIN_TICKS + random.nextInt(timing.maxGapTicks) }

    val size: Int get() = lit.size

    fun isLit(lamp: Int): Boolean = lit[lamp]

    /** One tick; true if any lamp switched on or off. */
    fun tick(): Boolean {
        var changed = false
        for (lamp in lit.indices) {
            if (--untilNext[lamp] > 0) continue
            changed = true
            if (!bursting[lamp]) {
                bursting[lamp] = true
                lit[lamp] = false
                untilNext[lamp] = 1 + random.nextInt(3)
                continue
            }
            lit[lamp] = !lit[lamp]
            if (lit[lamp] && random.nextDouble() < timing.endChance) {
                bursting[lamp] = false
                untilNext[lamp] = timing.minGapTicks + random.nextInt(timing.maxGapTicks - timing.minGapTicks + 1)
            } else {
                untilNext[lamp] = 1 + random.nextInt(4)
            }
        }
        return changed
    }

    private companion object {
        /** No lamp flickers in the menu's first two seconds. */
        const val FIRST_BURST_MIN_TICKS = 40
    }
}
