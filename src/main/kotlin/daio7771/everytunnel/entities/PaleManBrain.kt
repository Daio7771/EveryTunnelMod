package daio7771.everytunnel.entities

import daio7771.everytunnel.config.PaleManConfig
import daio7771.everytunnel.core.level.Position
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * What the Pale man decides, tick by tick, from what he hears. He is blind: he only
 * knows where noises came from, never where a player is, until he touches one.
 *
 * - [State.ROAM]: wanders. A noise makes him stop and [State.LISTEN]; a loud one
 *   (loudness at least `chase_loudness`) makes him [State.CHASE] at once.
 * - [State.LISTEN]: stands still, head turned towards the noise, for `listen_seconds`.
 *   A loud noise or a second one in that time: chase. Otherwise he walks there
 *   ([State.INVESTIGATE]).
 * - [State.INVESTIGATE]: walks to the last noise heard; new noises move the target, a
 *   loud one starts a chase. Once there (or after `investigate_seconds`): search.
 * - [State.CHASE]: runs to the last noise heard, each new noise moving the target. If
 *   he gets there and hears nothing more for half a second, he has lost you: search.
 * - [State.SEARCH]: stands still, alert, for `search_seconds`. Any noise: chase.
 *   Then back to roaming.
 *
 * He hits a player within `attack_range` while chasing or searching, and anyone closer
 * than `touch_range` whatever he is doing (bumping into him).
 *
 * Noise loudness (1 at the source, 0 at the edge of what he can hear) and walls are
 * worked out by the caller (`platform.PaleManEntity`, with [loudness]); each noise is
 * used once, the tick after it was made.
 */
class PaleManBrain {

    enum class State { ROAM, LISTEN, INVESTIGATE, CHASE, SEARCH }

    /** A noise as he hears it from where he stands. */
    data class Heard(val position: Position, val loudness: Double, val tick: Long, val playerId: UUID? = null)

    data class NearbyPlayer(val id: UUID, val distance: Double)

    /**
     * What he senses this tick. [navigationIdle]: his pathfinding has stopped (arrived,
     * or no path), only trusted after he has been moving for a moment.
     */
    data class Senses(
        val now: Long,
        val position: Position,
        val heard: List<Heard> = emptyList(),
        val nearestPlayer: NearbyPlayer? = null,
        val navigationIdle: Boolean = false,
    )

    /**
     * What to do this tick. [target]: where to go, or face when [speed] is 0; null while
     * roaming (wander freely). [attack]: the player to hit now. [alerted]: whose noise just
     * started a chase (for the "he heard you" stinger), if a player's.
     */
    data class Decision(
        val state: State,
        val target: Position?,
        val speed: Double,
        val attack: UUID? = null,
        val alerted: UUID? = null,
    )

    var state: State = State.ROAM
        private set
    var target: Position? = null
        private set

    private var stateTicks = 0
    private var stateLength = 0
    private var noisesThisListen = 0
    private var lastNoiseTick = Long.MIN_VALUE
    private var attackCooldown = 0

    fun tick(senses: Senses, config: PaleManConfig): Decision {
        // Noises of this very tick wait for the next one, so none made later this tick is skipped.
        val fresh = senses.heard.filter { it.tick > lastNoiseTick && it.tick < senses.now }
        fresh.maxOfOrNull { it.tick }?.let { lastNoiseTick = it }
        val noise = fresh.maxByOrNull { it.loudness }
        val loud = noise != null && noise.loudness >= config.chaseLoudness
        var alerted: UUID? = null

        fun chase(by: Heard) {
            enter(State.CHASE, 0)
            target = by.position
            alerted = by.playerId
        }

        stateTicks++
        if (attackCooldown > 0) attackCooldown--
        val timeUp = stateTicks >= stateLength
        val arrived = target?.let { horizontalDistance(senses.position, it) <= ARRIVE_DISTANCE } == true ||
            (senses.navigationIdle && stateTicks > NAVIGATION_GRACE_TICKS)

        when (state) {
            State.ROAM -> when {
                noise == null -> Unit
                loud -> chase(noise)
                else -> {
                    enter(State.LISTEN, ticks(config.listenSeconds))
                    target = noise.position
                    noisesThisListen = 1
                }
            }
            State.LISTEN -> when {
                noise != null && (loud || ++noisesThisListen >= 2) -> chase(noise)
                noise != null -> target = noise.position
                timeUp -> enter(State.INVESTIGATE, ticks(config.investigateSeconds))
            }
            State.INVESTIGATE -> when {
                noise != null && loud -> chase(noise)
                noise != null -> target = noise.position
                arrived || timeUp -> enter(State.SEARCH, ticks(config.searchSeconds))
            }
            State.CHASE -> when {
                noise != null -> {
                    target = noise.position
                    stateTicks = 0
                }
                // There and nothing heard for a moment: lost (he runs over your last step every tick).
                (arrived && stateTicks > NAVIGATION_GRACE_TICKS) || stateTicks >= ticks(config.investigateSeconds) ->
                    enter(State.SEARCH, ticks(config.searchSeconds))
            }
            State.SEARCH -> when {
                noise != null -> chase(noise)
                timeUp -> {
                    enter(State.ROAM, 0)
                    target = null
                }
            }
        }

        val reach = if (state == State.CHASE || state == State.SEARCH) config.attackRange else config.touchRange
        val victim = senses.nearestPlayer?.takeIf { it.distance <= reach && attackCooldown == 0 }
        if (victim != null) {
            attackCooldown = ticks(config.attackCooldownSeconds)
            if (state != State.CHASE) enter(State.CHASE, 0)
            target = senses.position
        }

        val speed = when (state) {
            State.ROAM -> config.roamSpeed
            State.INVESTIGATE -> config.investigateSpeed
            State.CHASE -> config.chaseSpeed
            State.LISTEN, State.SEARCH -> 0.0
        }
        return Decision(state, target, speed, victim?.id, alerted)
    }

    /** Back to wandering, as if he had just been spawned (the player died and restarts the level). */
    fun reset() {
        state = State.ROAM
        target = null
        stateTicks = 0
        stateLength = 0
        noisesThisListen = 0
        attackCooldown = 0
        // lastNoiseTick is kept: noises he already used stay used.
    }

    private fun enter(newState: State, length: Int) {
        state = newState
        stateTicks = 0
        stateLength = length
    }

    companion object {
        /** Close enough to where a noise came from (horizontally) to count as there. */
        const val ARRIVE_DISTANCE: Double = 1.5

        /** Ticks after a change of state before an idle pathfinder means "arrived". */
        const val NAVIGATION_GRACE_TICKS: Int = 10

        fun ticks(seconds: Double): Int = (seconds * 20).roundToInt().coerceAtLeast(1)

        fun horizontalDistance(a: Position, b: Position): Double {
            val dx = a.x - b.x
            val dz = a.z - b.z
            return sqrt(dx * dx + dz * dz)
        }

        /**
         * How loud a noise of [radius] is [distance] blocks away: 1 at the source, 0 at
         * the edge of its reach, which [hearing] stretches and each of [walls] solid
         * blocks in between shrinks by [wallMuffle]. 0 means he cannot hear it.
         */
        fun loudness(distance: Double, radius: Double, hearing: Double, walls: Int, wallMuffle: Double): Double {
            var reach = radius * hearing
            repeat(walls) { reach *= wallMuffle }
            if (reach <= 0.0 || distance >= reach) return 0.0
            return 1.0 - distance / reach
        }
    }
}
