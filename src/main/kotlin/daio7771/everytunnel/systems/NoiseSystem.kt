package daio7771.everytunnel.systems

import daio7771.everytunnel.config.NoiseConfig
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** What made a noise. */
enum class NoiseSource(val id: String) {
    SNEAK("sneak"),
    WALK("walk"),
    SPRINT("sprint"),
    JUMP("jump"),
    LAND("land"),
    INTERACT("interact"),
    EQUIPMENT("equipment"),
    BREATHING("breathing"),
}

/**
 * One noise: where it happened, how far it carries ([radius] in blocks) and when
 * ([tick], server ticks). [playerId] is who made it, or null for noises of the world.
 */
data class NoiseEvent(
    val source: NoiseSource,
    val dimension: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val radius: Double,
    val tick: Long,
    val playerId: UUID? = null,
) {
    fun distanceSqTo(x: Double, y: Double, z: Double): Double {
        val dx = this.x - x
        val dy = this.y - y
        val dz = this.z - z
        return dx * dx + dy * dy + dz * dz
    }
}

/**
 * Turns what players do into noises that listeners (the Pale man, later) can hear.
 *
 * - Noises are kept for [MEMORY_TICKS], so a listener can ask at any time what it can
 *   hear from where it stands ([audibleAt]), or register a [Listener] to react the
 *   moment a noise happens.
 * - A noise is heard within its radius times the listener's hearing multiplier. It
 *   travels through walls; whether walls muffle it is up to the listener.
 * - The radii come from [NoiseConfig]; the rules that pick them ([movementRadius],
 *   [landingRadius], [StepCounter]) are pure functions, unit tested without Minecraft.
 *
 * Fed by `platform.NoiseHooks` on the server thread; all access is on that thread.
 */
object NoiseSystem {

    /** How long noises are remembered: 5 seconds. */
    const val MEMORY_TICKS: Long = 100

    fun interface Listener {
        fun onNoise(event: NoiseEvent)
    }

    private val events = ArrayDeque<NoiseEvent>()
    private val listeners = CopyOnWriteArrayList<Listener>()

    fun register(listener: Listener) {
        listeners += listener
    }

    fun unregister(listener: Listener) {
        listeners -= listener
    }

    /** Records a noise and tells the listeners. Silent noises (radius 0) are dropped. */
    fun emit(event: NoiseEvent) {
        if (event.radius <= 0.0) return
        events.addLast(event)
        for (listener in listeners) listener.onNoise(event)
    }

    /** Forgets noises older than [MEMORY_TICKS]. */
    fun prune(nowTick: Long) {
        while (events.isNotEmpty() && nowTick - events.first().tick > MEMORY_TICKS) events.removeFirst()
    }

    /**
     * Noises heard at a position, newest first: same dimension, at most [maxAgeTicks]
     * old, and within their radius times [hearing].
     */
    fun audibleAt(
        dimension: String, x: Double, y: Double, z: Double, nowTick: Long,
        hearing: Double = 1.0, maxAgeTicks: Long = MEMORY_TICKS,
    ): List<NoiseEvent> = events.asReversed().filter { event ->
        val reach = event.radius * hearing
        event.dimension == dimension && nowTick - event.tick <= maxAgeTicks && event.distanceSqTo(x, y, z) <= reach * reach
    }

    /** Every remembered noise, newest first (debug). */
    fun recent(): List<NoiseEvent> = events.asReversed().toList()

    fun clear() {
        events.clear()
    }

    // --- Rules -------------------------------------------------------------------

    /** Footstep noise for the way the player moves. */
    fun movementSource(sneaking: Boolean, sprinting: Boolean): NoiseSource = when {
        sneaking -> NoiseSource.SNEAK
        sprinting -> NoiseSource.SPRINT
        else -> NoiseSource.WALK
    }

    fun radiusOf(source: NoiseSource, config: NoiseConfig): Double = when (source) {
        NoiseSource.SNEAK -> config.sneakRadius
        NoiseSource.WALK -> config.walkRadius
        NoiseSource.SPRINT -> config.sprintRadius
        NoiseSource.JUMP -> config.jumpRadius
        NoiseSource.LAND -> config.landRadius
        NoiseSource.INTERACT -> config.interactRadius
        NoiseSource.EQUIPMENT -> config.equipmentRadius
        NoiseSource.BREATHING -> config.exhaustedBreathingRadius
    }

    fun movementRadius(config: NoiseConfig, sneaking: Boolean, sprinting: Boolean): Double =
        radiusOf(movementSource(sneaking, sprinting), config)

    /**
     * Landing noise: `land_radius` for a 2-block fall, scaled by the fall height between
     * half (a normal jump lands at about 1.25 blocks) and twice (long falls).
     */
    fun landingRadius(config: NoiseConfig, fallDistance: Double): Double =
        config.landRadius * (fallDistance / 2.0).coerceIn(0.5, 2.0)

    /** Falls shorter than this (stepping down a slab) make no landing noise. */
    const val MIN_LANDING_FALL: Double = 0.6

    /** Counts footsteps: one per [STEP_LENGTH] blocks walked on the ground. */
    class StepCounter {
        private var carried = 0.0

        /** Adds the distance walked this tick and returns how many steps it completed. */
        fun add(distance: Double): Int {
            carried += distance
            val steps = (carried / STEP_LENGTH).toInt()
            carried -= steps * STEP_LENGTH
            return steps
        }

        fun reset() {
            carried = 0.0
        }

        companion object {
            const val STEP_LENGTH: Double = 1.0
        }
    }
}
