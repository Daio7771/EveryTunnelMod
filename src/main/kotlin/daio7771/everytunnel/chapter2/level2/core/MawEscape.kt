package daio7771.everytunnel.chapter2.level2.core

import kotlin.math.hypot
import kotlin.random.Random

/**
 * The escape after Stone Maw's death (phase 9), as data: the arena cave in for `maw.escape.seconds` and the player must be through the far door
 * by then. [plan] says which cells of the arena a piece of ceiling falls on and when (deterministic from a seed, `maw.escape.debris_per_second`
 * of them a second, fewer within `door_clear_radius` of the far door so that the way out is not walled by what falls), [endTick] when the time is
 * up and [isLost] whether whoever is where they are then dies. Plain Kotlin, unit tested; `platform.Ch2L2Maw` runs it.
 */
object MawEscape {

    /** A piece of ceiling falling on the cell ([x], [z]) (world block coordinates) at [tick] ticks from the escape's start. */
    data class Debris(val tick: Int, val x: Int, val z: Int)

    /** Where the arena is: its cells (both ends in) and the cell the far door opens on. */
    data class Arena(val minX: Int, val minZ: Int, val maxX: Int, val maxZ: Int, val doorX: Int, val doorZ: Int)

    /** Share of the candidates near the door that are kept: the door's approach stays clearer than the rest of the arena. */
    const val NEAR_DOOR_KEEP = 0.2

    fun endTick(maw: Ch2L2Maw): Int = Math.round(maw.escape.seconds * 20.0).toInt()

    fun plan(maw: Ch2L2Maw, arena: Arena, seed: Int): List<Debris> {
        val random = Random(seed)
        val total = Math.round(maw.escape.seconds * maw.escape.debrisPerSecond).toInt()
        val end = endTick(maw)
        val out = ArrayList<Debris>(total)
        var tries = 0
        while (out.size < total && tries++ < total * 40) {
            val x = random.nextInt(arena.minX, arena.maxX + 1)
            val z = random.nextInt(arena.minZ, arena.maxZ + 1)
            val near = hypot((x - arena.doorX).toDouble(), (z - arena.doorZ).toDouble()) <= maw.escape.doorClearRadius
            if (near && random.nextDouble() > NEAR_DOOR_KEEP) continue
            // Spread over the first part of the time (a piece must have time to fall and be announced before the end).
            out += Debris(0, x, z)
        }
        // Even spacing over the time, in the order drawn.
        val lastStart = (end - maw.debris.warnTicks).coerceAtLeast(1)
        return out.mapIndexed { i, d -> d.copy(tick = (i * lastStart) / out.size.coerceAtLeast(1)) }.sortedBy { it.tick }
    }

    /** Whoever is in the arena ([inArena]) when [tick] reaches the end is lost. */
    fun isLost(maw: Ch2L2Maw, tick: Int, inArena: Boolean): Boolean = inArena && tick >= endTick(maw)
}
