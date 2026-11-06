package daio7771.everytunnel.core.detector

import daio7771.everytunnel.config.DetectorConfig
import kotlin.math.roundToInt

/**
 * What Level 5's radiation detector reads from the distance to the nearest Distortion Cloud it can
 * notice (within its range, nothing solid in between: the client works that out). Plain numbers, no
 * Minecraft types: `client.effects.DetectorEffect` beeps by them and `client.hud.DetectorHud` draws them.
 */
class Detector(private val config: DetectorConfig) {

    /** How full the radiation icon is: 0 with nothing in range, rising to 1 right next to it. */
    fun level(distance: Double?): Double {
        if (distance == null || distance > config.range) return 0.0
        return (1.0 - distance / config.range).coerceIn(0.0, 1.0)
    }

    /** Close enough for the warning triangle. */
    fun danger(distance: Double?): Boolean = distance != null && distance <= minOf(config.dangerRange, config.range)

    /** Ticks from one beep to the next at [level], or null when it is silent (nothing in range). */
    fun beepTicks(level: Double): Int? {
        if (level <= 0.0) return null
        val seconds = config.slowestBeepSeconds + (config.fastestBeepSeconds - config.slowestBeepSeconds) * level.coerceAtMost(1.0)
        return (seconds * 20.0).roundToInt().coerceAtLeast(1)
    }
}
