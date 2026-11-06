package daio7771.everytunnel.entities

import daio7771.everytunnel.config.HunterConfig
import daio7771.everytunnel.core.level.Position
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.roundToLong

/**
 * What a Hunter (Level 5's little ones, in groups of three) decides, tick by tick. They see; they
 * do not hear, not even the wrench.
 *
 * - [State.WANDER]: they keep near their spawn point, standing [HunterConfig.wanderPauseSeconds]
 *   between short walks to random points of it.
 * - [State.CHASE]: they know where a player is: they see them, one of their group does, or a
 *   Distortion Junior marks them ([Senses.told]). They run there; a player they see within
 *   [HunterConfig.attackRange] gets a hit, at most every [HunterConfig.attackSeconds]. The first
 *   tick of a chase is [Decision.charged] (the shriek). Knowing nothing new for
 *   [HunterConfig.lostSeconds], or reaching the last place without finding anyone, they
 *   [State.SEARCH]: stand there [HunterConfig.searchSeconds], then [State.RETURN], walking.
 *
 * Knowing where a player is beats everything else. What they see (their cone, walls) and what
 * they are told are worked out by the caller (`platform.HunterEntity`); this decides what to do.
 */
class HunterBrain(private val config: HunterConfig = HunterConfig()) {

    enum class State { WANDER, CHASE, SEARCH, RETURN }

    enum class Pace { STAND, WALK, RUN }

    /** A player whose place they know this tick, and how far. */
    data class Contact(val id: UUID, val position: Position, val distance: Double)

    /**
     * What they know this tick. [home]: their spawn point. [wanderPoint]: a fresh random point near
     * it, taken when they want one. [seen]: the nearest player they see; [told]: one their group or
     * a Junior gives away. [navigationIdle]: their pathfinding has stopped (there, or no way on).
     */
    data class Senses(
        val now: Long,
        val position: Position,
        val home: Position,
        val wanderPoint: Position?,
        val seen: Contact? = null,
        val told: Contact? = null,
        val navigationIdle: Boolean = false,
    )

    /** Go to [target] at [pace] (or stand); hit [attack] now; [charged]: a chase starts this tick. */
    data class Decision(
        val state: State,
        val target: Position?,
        val pace: Pace,
        val attack: UUID? = null,
        val charged: Boolean = false,
    )

    var state: State = State.WANDER
        private set
    var target: Position? = null
        private set

    private var stateStart = 0L
    private var lastContact = NEVER
    private var lastAttack = NEVER
    private var started = false

    private val attackTicks = ticks(config.attackSeconds)
    private val lostTicks = ticks(config.lostSeconds)
    private val searchTicks = ticks(config.searchSeconds)
    private val pauseTicks = ticks(config.wanderPauseSeconds)

    fun tick(senses: Senses): Decision {
        if (!started) {
            started = true
            stateStart = senses.now
        }
        val contact = senses.seen ?: senses.told
        if (contact != null) {
            val charged = state != State.CHASE
            enter(State.CHASE, senses.now)
            lastContact = senses.now
            target = contact.position
            val seen = senses.seen
            val attack = if (seen != null && seen.distance <= config.attackRange && senses.now - lastAttack >= attackTicks) {
                lastAttack = senses.now
                seen.id
            } else {
                null
            }
            return Decision(state, target, Pace.RUN, attack, charged)
        }
        return when (state) {
            State.CHASE -> {
                val there = target?.let { near(senses.position, it) } ?: true
                if (senses.now - lastContact >= lostTicks || (there && senses.navigationIdle)) {
                    enter(State.SEARCH, senses.now)
                    Decision(state, null, Pace.STAND)
                } else {
                    Decision(state, target, Pace.RUN)
                }
            }
            State.SEARCH -> {
                if (senses.now - stateStart >= searchTicks) {
                    enter(State.RETURN, senses.now)
                    target = senses.home
                    Decision(state, target, Pace.WALK)
                } else {
                    Decision(state, null, Pace.STAND)
                }
            }
            State.RETURN -> {
                target = senses.home
                if (near(senses.position, senses.home) || senses.navigationIdle && senses.now - stateStart > RETURN_SETTLE_TICKS) {
                    enter(State.WANDER, senses.now)
                    target = null
                    Decision(state, null, Pace.STAND)
                } else {
                    Decision(state, target, Pace.WALK)
                }
            }
            State.WANDER -> wander(senses)
        }
    }

    /** Stand a while, walk to a new point, stand again. */
    private fun wander(senses: Senses): Decision {
        val walkingTo = target
        if (walkingTo != null) {
            if (near(senses.position, walkingTo) || senses.navigationIdle) {
                target = null
                stateStart = senses.now
                return Decision(state, null, Pace.STAND)
            }
            return Decision(state, walkingTo, Pace.WALK)
        }
        if (senses.now - stateStart >= pauseTicks && senses.wanderPoint != null) {
            target = senses.wanderPoint
            return Decision(state, target, Pace.WALK)
        }
        return Decision(state, null, Pace.STAND)
    }

    /** Back to wandering, as if just spawned (the player died, or a new game). */
    fun reset() {
        state = State.WANDER
        target = null
        started = false
        lastContact = NEVER
        lastAttack = NEVER
    }

    private fun enter(next: State, now: Long) {
        if (state != next) stateStart = now
        state = next
    }

    private fun near(a: Position, b: Position): Boolean = hypot(a.x - b.x, a.z - b.z) <= ARRIVED && kotlin.math.abs(a.y - b.y) < 2.0

    private fun ticks(seconds: Double): Long = (seconds * 20).roundToLong()

    private companion object {
        /** Blocks from a target that count as there. */
        const val ARRIVED = 1.5
        /** Walking home and stuck this long: home is wherever they are. */
        const val RETURN_SETTLE_TICKS = 40L
        /** Long ago (not Long.MIN_VALUE: now minus it must not overflow). */
        const val NEVER = Long.MIN_VALUE / 2
    }
}
