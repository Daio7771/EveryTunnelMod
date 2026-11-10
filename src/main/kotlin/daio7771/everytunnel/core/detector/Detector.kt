package daio7771.everytunnel.core.detector

import daio7771.everytunnel.config.DetectorConfig
import kotlin.math.exp
import kotlin.math.pow

/**
 * What Level 5's radiation detector reads from the distance to the nearest Distortion Cloud it can
 * notice (within its range, nothing solid in between: the client works that out). Plain numbers, no
 * Minecraft types: `client.effects.DetectorEffect` clicks by them and `client.hud.DetectorHud` draws them.
 *
 * It is a Geiger counter: its counts fall at random, more of them a second as the Cloud comes nearer,
 * from a slow tick at the edge of its range to a crackle right next to it.
 */
class Detector(private val config: DetectorConfig) {

    /** How full the radiation icon is: 0 with nothing in range, rising to 1 right next to it. */
    fun level(distance: Double?): Double {
        if (distance == null || distance > config.range) return 0.0
        return (1.0 - distance / config.range).coerceIn(0.0, 1.0)
    }

    /** Close enough for the warning triangle. */
    fun danger(distance: Double?): Boolean = distance != null && distance <= minOf(config.dangerRange, config.range)

    /** Counts a second on average at [level]: none when it is 0 (nothing in range), rising evenly to the ear (each
     *  step nearer multiplies them) from the slowest at the edge to the fastest next to the Cloud. */
    fun countsPerSecond(level: Double): Double {
        if (level <= 0.0) return 0.0
        return config.slowestCountsPerSecond * (config.fastestCountsPerSecond / config.slowestCountsPerSecond).pow(level.coerceAtMost(1.0))
    }

    /** How many counts fall in one tick (a twentieth of a second) at [level], drawn as a real tube's are (a Poisson
     *  draw, Knuth's way); [uniform] gives random numbers in 0 until 1. */
    fun countsThisTick(level: Double, uniform: () -> Double): Int {
        val mean = countsPerSecond(level) / 20.0
        if (mean <= 0.0) return 0
        val limit = exp(-mean)
        var counts = 0
        var product = uniform()
        while (product > limit) {
            counts++
            product *= uniform()
        }
        return counts
    }
}
