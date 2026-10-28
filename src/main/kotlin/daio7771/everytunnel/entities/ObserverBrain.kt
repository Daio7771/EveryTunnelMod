package daio7771.everytunnel.entities

import daio7771.everytunnel.core.level.Position
import java.util.UUID
import kotlin.math.PI
import kotlin.math.sin

/**
 * What the Observer (Level 3's observant guard) decides, tick by tick. Unlike the Pale man he
 * does not hear footsteps: he sees, and he is told when a box goes on Storage's shelf.
 *
 * - [State.PATROL]: walks between points of his home (the library), and at each one
 *   [State.LOOK]s round: stands, his head sweeping slowly from side to side ([lookOffset]).
 * - [State.ALERTED]: a box was put on the shelf: he runs there, very fast, from wherever he is,
 *   then [State.INSPECT]s (stands and looks round) and [State.RETURN]s, walking, to his home.
 * - [State.CHASE]: he sees a player: first he walks towards them; only once they have stayed in
 *   his sight for [ObserverSettings.spotSeconds] (a moment's gap does not count), or are within
 *   [ObserverSettings.spotCloseRange], does he run at them. Close enough, he catches them
 *   ([Decision.catch]). Out of his sight for [ObserverSettings.lostSeconds], he goes to where he
 *   saw them last ([State.SEARCH]; running if he was), looks round there, and goes home. So a
 *   player he has just spotted has a moment to sneak out of his range, duck behind a shelf or
 *   get into a locker unseen.
 * - [State.GRAB]: he saw a player get into a locker: he goes to it and pulls them out
 *   ([Decision.grab]). A player who hid unseen is not seen at all.
 *
 * Seeing a player beats everything but a grab; a box beats patrolling, looking, inspecting
 * and going home. What he sees (his cone of sight, walls) is worked out by the caller
 * (`platform.ObserverEntity`); this decides what to do about it.
 */
class ObserverBrain(private val settings: ObserverSettings = ObserverSettings()) {

    enum class State { PATROL, LOOK, ALERTED, INSPECT, CHASE, SEARCH, RETURN, GRAB }

    enum class Pace { STAND, WALK, RUN, ALERT_RUN }

    /** A player he sees this tick, and how far. */
    data class Seen(val id: UUID, val position: Position, val distance: Double)

    /** A player he saw get into a locker, the locker's position, and how far it is. */
    data class Hidden(val id: UUID, val locker: Position, val distance: Double)

    /**
     * What he knows this tick. [atHome]: he stands inside his home (the library). [patrolPoint]:
     * a fresh random point of his home, taken when he wants a new one. [alert]: where a box was
     * just put. [navigationIdle]: his pathfinding has stopped (there, or no way on).
     */
    data class Senses(
        val now: Long,
        val position: Position,
        val atHome: Boolean,
        val patrolPoint: Position?,
        val seen: Seen? = null,
        val hidden: Hidden? = null,
        val alert: Position? = null,
        val navigationIdle: Boolean = false,
    )

    /**
     * What to do this tick: go to [target] at [pace] (or stand facing it); [lookOffset]: degrees
     * to turn his head from his body (looking round), or null; [catch] / [grab]: the player he
     * catches now, out in the open or out of a locker.
     */
    data class Decision(
        val state: State,
        val target: Position?,
        val pace: Pace,
        val lookOffset: Double? = null,
        val catch: UUID? = null,
        val grab: UUID? = null,
    )

    var state: State = State.PATROL
        private set
    var target: Position? = null
        private set

    private var stateTicks = 0
    private var stateLength = 0
    private var lastSeenTick = Long.MIN_VALUE
    /** Since when he has had the chased player in sight without a real gap, and whether he runs yet. */
    private var inSightSince = Long.MIN_VALUE
    private var running = false
    private var chased: UUID? = null
    private var hiddenTarget: Hidden? = null

    fun reset() {
        enter(State.PATROL, 0)
        target = null
        lastSeenTick = Long.MIN_VALUE
        inSightSince = Long.MIN_VALUE
        running = false
        chased = null
        hiddenTarget = null
    }

    fun tick(senses: Senses): Decision {
        stateTicks++
        val arrived = target?.let { PaleManBrain.horizontalDistance(senses.position, it) <= ARRIVE_DISTANCE } == true ||
            (senses.navigationIdle && stateTicks > NAVIGATION_GRACE_TICKS)
        val timeUp = stateTicks >= stateLength

        // What beats whatever he is doing.
        senses.hidden?.let { hidden ->
            if (state != State.GRAB || hiddenTarget?.id != hidden.id) {
                enter(State.GRAB, ticks(settings.grabTimeoutSeconds))
                hiddenTarget = hidden
                target = hidden.locker
            }
        }
        val seen = senses.seen
        if (state != State.GRAB && seen != null) {
            if (state != State.CHASE && state != State.SEARCH) running = false
            if (state != State.CHASE) enter(State.CHASE, 0)
            if (senses.now - lastSeenTick > SIGHT_GAP_TICKS || chased != seen.id) inSightSince = senses.now
            target = seen.position
            chased = seen.id
            lastSeenTick = senses.now
            if (senses.now - inSightSince >= ticks(settings.spotSeconds) || seen.distance <= settings.spotCloseRange) running = true
        }
        if (senses.alert != null && state in INTERRUPTIBLE) {
            enter(State.ALERTED, ticks(settings.alertTimeoutSeconds))
            target = senses.alert
        }

        return when (state) {
            State.PATROL -> {
                if (target == null || arrived || (timeUp && stateLength > 0)) {
                    if (target != null && arrived) {
                        enter(State.LOOK, ticks(settings.lookSeconds))
                        return Decision(state, target, Pace.STAND, lookOffset(0))
                    }
                    target = senses.patrolPoint
                    stateTicks = 0
                    stateLength = ticks(settings.patrolLegSeconds)
                }
                Decision(state, target, Pace.WALK)
            }
            State.LOOK -> {
                if (timeUp) {
                    enter(State.PATROL, ticks(settings.patrolLegSeconds))
                    target = senses.patrolPoint
                    Decision(state, target, Pace.WALK)
                } else {
                    Decision(state, target, Pace.STAND, lookOffset(stateTicks))
                }
            }
            State.ALERTED -> {
                if (arrived || timeUp) {
                    enter(State.INSPECT, ticks(settings.inspectSeconds))
                    Decision(state, target, Pace.STAND, lookOffset(0))
                } else {
                    Decision(state, target, Pace.ALERT_RUN)
                }
            }
            State.INSPECT -> {
                if (timeUp) {
                    goHome(senses)
                } else {
                    Decision(state, target, Pace.STAND, lookOffset(stateTicks))
                }
            }
            State.CHASE -> {
                val catchNow = seen != null && seen.distance <= settings.catchRange
                when {
                    catchNow -> {
                        val caught = seen!!.id
                        enter(State.RETURN, ticks(settings.returnTimeoutSeconds))
                        target = senses.patrolPoint
                        Decision(State.CHASE, seen.position, Pace.STAND, catch = caught)
                    }
                    seen == null && senses.now - lastSeenTick >= ticks(settings.lostSeconds) -> {
                        enter(State.SEARCH, ticks(settings.searchTimeoutSeconds))
                        Decision(state, target, chasePace())
                    }
                    else -> Decision(state, target, chasePace())
                }
            }
            State.SEARCH -> {
                if (arrived || timeUp) {
                    enter(State.INSPECT, ticks(settings.inspectSeconds))
                    running = false
                    Decision(state, target, Pace.STAND, lookOffset(0))
                } else {
                    Decision(state, target, chasePace())
                }
            }
            State.RETURN -> {
                when {
                    senses.atHome -> {
                        enter(State.PATROL, ticks(settings.patrolLegSeconds))
                        target = senses.patrolPoint
                        Decision(state, target, Pace.WALK)
                    }
                    target == null || arrived || timeUp -> goHome(senses)
                    else -> Decision(state, target, Pace.WALK)
                }
            }
            State.GRAB -> {
                val hidden = hiddenTarget
                when {
                    hidden == null || timeUp -> goHome(senses)
                    (senses.hidden?.distance ?: Double.MAX_VALUE) <= settings.grabRange -> {
                        hiddenTarget = null
                        enter(State.RETURN, ticks(settings.returnTimeoutSeconds))
                        target = senses.patrolPoint
                        Decision(State.GRAB, hidden.locker, Pace.STAND, grab = hidden.id)
                    }
                    senses.hidden == null -> {
                        // The player left the locker (or it is gone): it is a chase again if he sees them.
                        hiddenTarget = null
                        goHome(senses)
                    }
                    else -> Decision(state, target, Pace.RUN)
                }
            }
        }
    }

    private fun chasePace(): Pace = if (running) Pace.RUN else Pace.WALK

    private fun goHome(senses: Senses): Decision {
        enter(State.RETURN, ticks(settings.returnTimeoutSeconds))
        target = senses.patrolPoint
        return Decision(state, target, Pace.WALK)
    }

    /** How far his head is turned, looking round: a slow sweep from side to side. */
    private fun lookOffset(ticks: Int): Double =
        settings.lookSweepDegrees * sin(2 * PI * ticks / (settings.lookSweepSeconds * 20.0))

    private fun enter(next: State, length: Int) {
        state = next
        stateTicks = 0
        stateLength = length
    }

    private fun ticks(seconds: Double): Int = (seconds * 20).toInt()

    companion object {
        /** Close enough to a target to count as there (blocks, horizontally). */
        const val ARRIVE_DISTANCE = 1.5
        /** Pathfinding that stops this soon after a new target is not trusted yet. */
        const val NAVIGATION_GRACE_TICKS = 10
        /** Out of sight this briefly (a pillar, a shelf's end) still counts as in sight. */
        private const val SIGHT_GAP_TICKS = 5L
        /** What a box on the shelf takes him away from. */
        private val INTERRUPTIBLE = setOf(State.PATROL, State.LOOK, State.INSPECT, State.RETURN, State.ALERTED)
    }
}

/**
 * The Observer's numbers (see [ObserverBrain]). Speeds are pathfinding multipliers on his base
 * speed 0.3; how fast he really runs grows with their square: about 3.5 blocks/s x speed², turns
 * and all (measured in game: 2.3 ran some 17 blocks/s, 1.2 ran 5.4, 1.1 ran 4.2). His run is
 * meant a little faster than the player's sprint (about 5.6 blocks/s), his run to the shelf a
 * little faster still. The log says how fast he really ran ("ran ... blocks/s"); tune from that.
 */
data class ObserverSettings(
    val walkSpeed: Double = 0.6,
    val runSpeed: Double = 1.4,
    val alertSpeed: Double = 1.45,
    /** Sight: the cone's full width, how far he sees, sneaking, and with the player's flashlight on. */
    val fieldOfView: Double = 90.0,
    val sightRange: Double = 24.0,
    val sneakSightRange: Double = 12.0,
    val flashlightSightRange: Double = 32.0,
    /** Closer than this he notices a player whatever way he faces. */
    val senseRange: Double = 2.0,
    val catchRange: Double = 1.6,
    /** A player newly seen: he walks towards them, and runs once they stay in sight this long or come this close. */
    val spotSeconds: Double = 1.5,
    val spotCloseRange: Double = 6.0,
    val grabRange: Double = 2.2,
    val lookSeconds: Double = 5.0,
    val lookSweepDegrees: Double = 70.0,
    val lookSweepSeconds: Double = 5.0,
    val inspectSeconds: Double = 5.0,
    val lostSeconds: Double = 1.5,
    val patrolLegSeconds: Double = 25.0,
    val alertTimeoutSeconds: Double = 25.0,
    val searchTimeoutSeconds: Double = 12.0,
    val returnTimeoutSeconds: Double = 40.0,
    val grabTimeoutSeconds: Double = 15.0,
)
