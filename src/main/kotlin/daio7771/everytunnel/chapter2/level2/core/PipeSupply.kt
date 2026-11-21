package daio7771.everytunnel.chapter2.level2.core

import daio7771.everytunnel.common.core.level.Position
import kotlin.random.Random

/**
 * Where and when the next piece of pipe falls in Stone Maw's arena (phase 6.1): always [Ch2L2Maw.piecesAtOnce] pieces, lying on the floor
 * or in a hand. Each tick the platform tells it how many there are ([tick]); once they are fewer for [Ch2L2Maw.respawnSeconds] it picks
 * one of [Ch2L2Maw.pipePoints]' positions to drop from: never the one nearest the creature or the player, and never the same twice in a
 * row. Plain Kotlin, unit tested.
 */
class PipeSupply(private val random: Random = Random.Default) {

    private var missingSince: Long? = null
    private var last = -1

    /** A new fight: nothing is owed. */
    fun reset() {
        missingSince = null
        last = -1
    }

    /** A piece falls now by other means (the first one, in front of the player): the clock starts over. */
    fun dropped() {
        missingSince = null
    }

    /**
     * The index in [points] of the point to drop a piece from now, or null (nothing is missing, or it is not time). [existing]: the pieces
     * there are (lying, falling or held); [creature] and [player] (if any) keep their nearest points out.
     */
    fun tick(now: Long, existing: Int, points: List<Position>, creature: Position?, player: Position?, maw: Ch2L2Maw): Int? {
        if (existing >= maw.piecesAtOnce || points.isEmpty()) {
            missingSince = null
            return null
        }
        val since = missingSince ?: now.also { missingSince = it }
        if (now - since < VibrationCreatureBrain.ticks(maw.respawnSeconds).coerceAtLeast(0)) return null
        val banned = setOfNotNull(creature?.let { nearest(points, it) }, player?.let { nearest(points, it) }, last.takeIf { it >= 0 })
        val choices = points.indices.filter { it !in banned }.ifEmpty { points.indices.filter { it != last } }.ifEmpty { points.indices.toList() }
        val pick = choices[random.nextInt(choices.size)]
        last = pick
        missingSince = null
        return pick
    }

    companion object {
        fun nearest(points: List<Position>, to: Position): Int =
            points.indices.minBy { Math.hypot(points[it].x - to.x, points[it].z - to.z) }
    }
}
