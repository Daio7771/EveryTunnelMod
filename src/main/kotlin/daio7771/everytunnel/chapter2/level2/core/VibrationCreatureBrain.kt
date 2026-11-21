package daio7771.everytunnel.chapter2.level2.core

import daio7771.everytunnel.common.core.level.Position
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * What the vibration creature of Chapter 2's Level 2 decides, tick by tick, from the vibrations it feels (phases 4 and 4.1). It cannot
 * see or hear: it only knows where something shook the floor, and how hard ([Kind]).
 *
 * - [State.REST]: still, at home. A [Kind.SOFT] vibration (sneaking) makes it [State.STALK]; any other makes it [State.ORIENT].
 * - [State.STALK]: walks slowly to the latest soft step (each new one moves it on, nothing is locked); there it stops and listens
 *   `stalk_listen_seconds`; then it searches round that point. A harder vibration: [State.ORIENT].
 * - [State.ORIENT]: for `orient_seconds` it turns to the point (the click of its teeth at the end). A new vibration moves the point, a
 *   [Kind.LOUD] one (running, jumping, landing, the pipe) makes the charge faster. Then the point is **locked**: [State.CHARGE].
 * - [State.CHARGE]: runs to the locked point; vibrations no longer move it. There, at the nearest place it can reach, or touching a player
 *   on the way, it starts a blow: [State.WINDUP].
 * - [State.WINDUP]: still for `windup_seconds`, fists raised (the escape: a player who gets beyond `contact_range` is missed). Its last
 *   tick is the impact: it hits the player within `contact_range` of it, never twice within `hit_interval_seconds`. Then [State.RECOVER].
 * - [State.RECOVER]: `recover_seconds` still; the latest vibration of that time is the next one it follows when it ends; with none it
 *   searches round where it struck.
 * - [State.SEARCH]: walks to `search_points` points round the centre, listening at each; any vibration is followed as at rest. Then
 *   [State.RETURN]: walks home (a vibration turns it round), and rests there, or where it gets stuck.
 *
 * A vibration the pump masks ([Vibration.masked]) is not felt at all, and the creature never reacts to its own steps or to the pump (the
 * caller never feeds those). While [Senses.introPending] (the first silence, until the pipe is struck) it feels only the pipe. Each
 * vibration is used once, the tick after it was made. It never takes an idle pathfinder for "there": it is there when close enough, or
 * when the caller says the way is [Nav.BLOCKED] (it then acts where it stands, the nearest it could get). Plain Kotlin;
 * `platform.VibrationCreatureEntity` feeds it and acts on its [Decision].
 */
class VibrationCreatureBrain(private val random: Random = Random.Default) {

    enum class State { REST, STALK, ORIENT, CHARGE, WINDUP, RECOVER, SEARCH, RETURN }

    /** What shook the floor. Only the player and the pipe do; the pump and the creature's own moves are never passed in. */
    enum class Source { PLAYER, PIPE }

    /** How hard: sneaking; walking or touching something; running, jumping, landing or the pipe. */
    enum class Kind { SOFT, NORMAL, LOUD }

    /** Its way to the target, as the caller sees it: still going, or it cannot get closer (no path, or stuck). */
    enum class Nav { MOVING, BLOCKED }

    /** A vibration at [position], made at server tick [tick]; [masked]: the pump was running (or just stopped) at that tick. */
    data class Vibration(
        val position: Position,
        val tick: Long,
        val source: Source,
        val kind: Kind = Kind.NORMAL,
        val masked: Boolean = false,
        val playerId: UUID? = null,
    )

    data class NearbyPlayer(val id: UUID, val distance: Double)

    /** What it senses this tick. [navigation]: about the target of its last decision. */
    data class Senses(
        val now: Long,
        val position: Position,
        val home: Position,
        val vibrations: List<Vibration> = emptyList(),
        val nearestPlayer: NearbyPlayer? = null,
        val navigation: Nav = Nav.MOVING,
        val introPending: Boolean = false,
    )

    /**
     * What to do this tick. [target]: where to go at [speed] blocks a second, or what to face when [speed] is 0 (null: nothing).
     * [oriented]: it has just finished turning (the click of its teeth); [windup]: a blow begins (its fists rise); [struck]: the blow
     * lands this tick (the thud); [hit]: the player it hurts, if it connects; [struckPipe]: the blow was at the pipe's point.
     */
    data class Decision(
        val state: State,
        val target: Position?,
        val speed: Double,
        val oriented: Boolean = false,
        val windup: Boolean = false,
        val struck: Boolean = false,
        val hit: UUID? = null,
        val struckPipe: Boolean = false,
    )

    var state: State = State.REST
        private set
    var target: Position? = null
        private set

    private var stateTicks = 0
    private var stateLength = 0
    private var moveTicks = 0
    private var listening = false
    private var loud = false
    private var atPipe = false
    private var lastVibrationTick = Long.MIN_VALUE
    private var hitCooldown = 0
    private var remembered: Vibration? = null
    private var struckAt: Position? = null
    private val searchPoints = ArrayDeque<Position>()
    private var config = Ch2L2Creature.DEFAULT
    private var home = Position(0.0, 0.0, 0.0)

    fun tick(senses: Senses, config: Ch2L2Creature): Decision {
        this.config = config
        home = senses.home
        // Vibrations of this very tick wait for the next one, so none made later this tick is skipped.
        val fresh = senses.vibrations.filter { it.tick > lastVibrationTick && it.tick < senses.now }
        fresh.maxOfOrNull { it.tick }?.let { lastVibrationTick = it }
        val felt = fresh
            .filter { !it.masked && (!senses.introPending || it.source == Source.PIPE) }
            .maxWithOrNull(compareBy<Vibration>({ it.tick }, { it.kind.ordinal }))

        stateTicks++
        moveTicks++
        if (hitCooldown > 0) hitCooldown--
        val timeUp = stateTicks >= stateLength
        val there = target?.let { horizontalDistance(senses.position, it) <= ARRIVE_DISTANCE } == true ||
            (senses.navigation == Nav.BLOCKED && moveTicks > NAV_TRUST_TICKS)
        var oriented = false
        var windup = false
        var struck = false
        var hit: UUID? = null
        var struckPipe = false

        when (state) {
            State.REST -> felt?.let(::follow)
            State.STALK -> when {
                felt != null && felt.kind == Kind.SOFT -> stalk(felt.position)
                felt != null -> orient(felt)
                listening -> if (timeUp) search(target ?: senses.position)
                there -> listen(config.stalkListenSeconds)
            }
            State.ORIENT -> {
                if (felt != null) {
                    target = felt.position
                    if (felt.kind == Kind.LOUD) loud = true
                    atPipe = felt.source == Source.PIPE
                }
                if (timeUp) {
                    oriented = true
                    enter(State.CHARGE, 0)
                    moveTicks = 0
                }
            }
            State.CHARGE -> {
                val touching = senses.nearestPlayer?.let { it.distance <= config.touchRange } == true
                if (there || touching) {
                    windup = true
                    enter(State.WINDUP, ticks(config.windupSeconds))
                }
            }
            State.WINDUP -> if (timeUp) {
                struck = true
                struckPipe = atPipe
                val victim = senses.nearestPlayer?.takeIf { it.distance <= config.contactRange && hitCooldown == 0 }
                if (victim != null) {
                    hit = victim.id
                    hitCooldown = ticks(config.hitIntervalSeconds)
                }
                struckAt = senses.position
                remembered = null
                atPipe = false
                enter(State.RECOVER, ticks(config.recoverSeconds))
            }
            State.RECOVER -> {
                if (felt != null) remembered = felt
                if (timeUp) {
                    val next = remembered
                    remembered = null
                    if (next != null) follow(next) else search(struckAt ?: senses.position)
                }
            }
            State.SEARCH -> when {
                felt != null -> follow(felt)
                listening -> if (timeUp) nextSearchPoint()
                there -> listen(config.searchListenSeconds)
            }
            State.RETURN -> {
                if (target != senses.home) setTarget(senses.home)
                when {
                    felt != null -> follow(felt)
                    there -> {
                        enter(State.REST, 0)
                        target = null
                    }
                }
            }
        }

        val speed = when {
            listening -> 0.0
            state == State.STALK -> config.stalkSpeed
            state == State.CHARGE -> if (loud) config.chargeSpeedLoud else config.chargeSpeed
            state == State.SEARCH -> config.searchSpeed
            state == State.RETURN -> config.walkSpeed
            else -> 0.0
        }
        val goal = if (state == State.REST || state == State.RECOVER) null else target
        return Decision(state, goal, speed, oriented, windup, struck, hit, struckPipe)
    }

    /** Follows [v]: a soft step is stalked, anything harder is turned to. */
    private fun follow(v: Vibration) {
        if (v.kind == Kind.SOFT && v.source == Source.PLAYER) stalk(v.position) else orient(v)
    }

    private fun stalk(point: Position) {
        if (state != State.STALK) enter(State.STALK, 0)
        listening = false
        setTarget(point)
    }

    private fun orient(v: Vibration) {
        enter(State.ORIENT, ticks(config.orientSeconds))
        target = v.position
        loud = v.kind == Kind.LOUD
        atPipe = v.source == Source.PIPE
    }

    private fun listen(seconds: Double) {
        listening = true
        stateTicks = 0
        stateLength = ticks(seconds)
    }

    private fun search(centre: Position) {
        searchPoints.clear()
        val n = config.searchPoints
        val start = random.nextDouble(0.0, 2 * PI)
        for (i in 0 until n) {
            val angle = start + i * 2 * PI / n + random.nextDouble(-0.3, 0.3)
            searchPoints += Position(centre.x + config.searchRadius * cos(angle), centre.y, centre.z + config.searchRadius * sin(angle))
        }
        enter(State.SEARCH, 0)
        nextSearchPoint()
    }

    private fun nextSearchPoint() {
        listening = false
        val next = searchPoints.removeFirstOrNull()
        if (next == null) {
            enter(State.RETURN, 0)
            setTarget(home)
        } else {
            stateTicks = 0
            stateLength = 0
            setTarget(next)
        }
    }

    private fun setTarget(point: Position) {
        target = point
        moveTicks = 0
    }

    /** Starts a search round [centre] now, whatever it was doing (the fight of phase 6.1 hands it back after a stun): it walks to a few points, listens, then goes home. */
    fun searchAround(centre: Position) {
        search(centre)
    }

    /** Back at rest as if just spawned (the player died and restarts the encounter); what it felt is forgotten. */
    fun reset() {
        state = State.REST
        target = null
        stateTicks = 0
        stateLength = 0
        moveTicks = 0
        listening = false
        loud = false
        atPipe = false
        hitCooldown = 0
        remembered = null
        struckAt = null
        searchPoints.clear()
        // lastVibrationTick is kept: vibrations it already used stay used.
    }

    private fun enter(newState: State, length: Int) {
        state = newState
        stateTicks = 0
        stateLength = length
        listening = false
    }

    companion object {
        /** Close enough to a point (horizontally) to count as there. */
        const val ARRIVE_DISTANCE: Double = 1.2

        /** Ticks after a new target before [Nav.BLOCKED] is believed (the caller's path is still the old one). */
        const val NAV_TRUST_TICKS: Int = 3

        fun ticks(seconds: Double): Int = (seconds * 20).roundToInt().coerceAtLeast(1)

        fun horizontalDistance(a: Position, b: Position): Double {
            val dx = a.x - b.x
            val dz = a.z - b.z
            return sqrt(dx * dx + dz * dz)
        }
    }
}
