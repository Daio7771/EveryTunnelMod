package daio7771.everytunnel.entities

import daio7771.everytunnel.config.CloudConfig

/**
 * The game of Level 5's entry corridor, played as Halt plays it: one Cloud for the whole corridor. It keeps behind
 * the player, creeping after them, so they must walk on; now and then it jumps in front of them, and they must turn
 * and walk back, until it jumps behind them again; and so on to the end. The legs forward are longer than the legs
 * back, both a random length, and everything quickens towards the end: the legs get shorter by up to
 * [CloudConfig.gameHurry], the creeping faster from [CloudConfig.gameCreepStart] to [CloudConfig.gameCreepEnd].
 *
 * Plain arithmetic along the corridor's line: a position is a distance from its start line, [length] its end,
 * [nearest] the lowest a Cloud may stand (a little behind the start line). It never jumps in front of a player too
 * near the end for its place ([CloudConfig.gameAheadBlocks] on): the last stretch is a walk to the end, the Cloud behind.
 * Killing (by touch, by a look) is the Cloud's own business ([CloudClock]).
 */
class CloudGame(
    private val config: CloudConfig,
    private val length: Double,
    private val nearest: Double,
    private val random: () -> Double,
) {

    /** Where the Cloud is: on the start's side of the player, or on the end's. */
    enum class Side { BEHIND, AHEAD }

    /** One tick: where the Cloud stands, whether it has just jumped, and how far along the player is (0 to 1). */
    data class Step(val cloud: Double, val jumped: Boolean, val progress: Double)

    var side = Side.BEHIND
        private set

    /** How many times it has jumped. */
    var jumps = 0
        private set

    private var cloud = 0.0
    private var left = 0.0

    /** The game starts with the player at [player]: the Cloud behind them. */
    fun start(player: Double): Double {
        side = Side.BEHIND
        cloud = maxOf(nearest, player - config.gameBehindBlocks)
        left = leg(progress(player))
        return cloud
    }

    /** [seconds] later, the player at [player]. */
    fun tick(player: Double, seconds: Double): Step {
        val progress = progress(player)
        left -= seconds
        val ahead = side == Side.BEHIND
        if (left <= 0.0 && (!ahead || player + config.gameAheadBlocks < length)) {
            side = if (ahead) Side.AHEAD else Side.BEHIND
            cloud = if (ahead) player + config.gameAheadBlocks else maxOf(nearest, player - config.gameBehindBlocks)
            left = leg(progress)
            jumps++
            return Step(cloud, true, progress)
        }
        val speed = config.gameCreepStart + (config.gameCreepEnd - config.gameCreepStart) * progress
        val step = speed * seconds
        cloud = if (cloud < player) minOf(player, cloud + step) else maxOf(player, cloud - step)
        return Step(cloud, false, progress)
    }

    private fun progress(player: Double): Double = (player / length).coerceIn(0.0, 1.0)

    /** A leg's seconds, from now: forward with the Cloud behind, back with it in front. */
    private fun leg(progress: Double): Double {
        val (min, max) = if (side == Side.BEHIND) {
            config.gameForwardMinSeconds to config.gameForwardMaxSeconds
        } else {
            config.gameBackMinSeconds to config.gameBackMaxSeconds
        }
        return (min + (maxOf(min, max) - min) * random()) * (1.0 - config.gameHurry * progress)
    }
}
