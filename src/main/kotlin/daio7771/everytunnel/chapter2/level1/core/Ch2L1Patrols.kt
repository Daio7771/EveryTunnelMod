package daio7771.everytunnel.chapter2.level1.core

import com.google.gson.JsonObject
import daio7771.everytunnel.core.level.Position
import daio7771.everytunnel.util.JsonFields
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/** A patrolling guard (`patrols.json`'s `patrols`): his skin, the path he walks (round, or there and back) and where he stops a while. */
data class Ch2L1PatrolSpec(
    val id: String,
    val skin: String,
    val path: String,
    /** Where he appears; null: the path's first point. */
    val spawn: String? = null,
    /** How fast he walks, a multiple of the workers' stroll. */
    val pace: Double = 1.0,
    /** Point anchors he stops at (at the path's point nearest each), and for how long. */
    val pauses: List<Pause> = emptyList(),
    /** He is there only from this state on (null: from the start); patrol C, the dark LAB's, from the overtime. */
    val from: Ch2L1State? = null,
) {
    data class Pause(val at: String, val ticks: Int)
}

/** A forbidden place (a zone) and where a player caught in it is put back (a point anchor); only from [from] on, if set (the dark LAB). */
data class Ch2L1Forbidden(val zone: String, val back: String, val from: Ch2L1State? = null) {
    fun applies(state: Ch2L1State): Boolean = from == null || state >= from
}

/**
 * How the guards watch (`patrols.json`'s `watch`). A guard **sees** the player within [range] blocks ([crouchRange] while they crouch) and
 * [fovDegrees] of where he looks, with nothing solid between, or within [proximity] whatever the way he looks. The player is
 * **suspicious** while seen inside a [forbidden] zone, crouching within [crouchNear] of him, or staring at the auxiliary lab ([stareZone],
 * the crosshair on it, not the corner of the eye) for more than [stareTicks]. Then his "?" fills over [noticeTicks] (and empties over
 * [decayTicks] when they stop); full, he warns them ([warnLine]) and gives them [graceTicks] to stop; if they do not, he runs at them
 * ([runPace]) and they are caught after [chaseTicks] or when he reaches them ([catchReach]); unseen for [giveUpTicks] he gives up.
 * In the [darkZones] from [darkFrom] on (the LAB with its lights off) he sees only [darkRange] far, unless the player's flashlight is on:
 * then [flashlightRange] ([sightRange]).
 */
data class Ch2L1WatchSpec(
    val range: Double = 8.0,
    val crouchRange: Double = 4.0,
    val fovDegrees: Double = 70.0,
    val proximity: Double = 2.0,
    val crouchNear: Double = 3.0,
    val stareZone: String = "aux_lab",
    val stareTicks: Int = 200,
    val stareReach: Double = 16.0,
    val noticeTicks: Int = 30,
    val decayTicks: Int = 40,
    val graceTicks: Int = 60,
    val chaseTicks: Int = 50,
    val giveUpTicks: Int = 60,
    val catchReach: Double = 1.6,
    val runPace: Double = 3.0,
    val warnLine: String = "guard_warn",
    val caughtLine: String = "guard_caught",
    val forbidden: List<Ch2L1Forbidden> = DEFAULT_FORBIDDEN,
    /** Where a player caught anywhere else (staring, crouching) is put back. */
    val backElsewhere: String = "return_ring",
    val darkZones: List<String> = listOf("lab"),
    val darkFrom: Ch2L1State = Ch2L1State.ACT3_OVERTIME,
    val darkRange: Double = 3.0,
    val flashlightRange: Double = 12.0,
) {
    /** How far a guard sees the player: crouching or not, in the dark or not, their flashlight on or not. */
    fun sightRange(crouching: Boolean, inDark: Boolean, flashlightOn: Boolean): Double = when {
        inDark && flashlightOn -> flashlightRange
        inDark -> if (crouching) minOf(darkRange, crouchRange) else darkRange
        crouching -> crouchRange
        else -> range
    }

    companion object {
        val DEFAULT_FORBIDDEN = listOf(
            Ch2L1Forbidden("aux_lab", "return_aux_lab"), Ch2L1Forbidden("aux_chamber", "return_aux_lab"),
            Ch2L1Forbidden("authorized_corridor", "return_authorized"), Ch2L1Forbidden("security_room", "return_authorized"),
            Ch2L1Forbidden("office", "return_authorized"), Ch2L1Forbidden("wc_east", "return_authorized"),
            Ch2L1Forbidden("lab", "return_lab", Ch2L1State.ACT3_OVERTIME),
        )
    }
}

/** Act 3's patrols of Chapter 2's Level 1, from `patrols.json`. Plain Kotlin. */
data class Ch2L1Patrols(val watch: Ch2L1WatchSpec = Ch2L1WatchSpec(), val patrols: List<Ch2L1PatrolSpec> = DEFAULT_PATROLS) {
    companion object {
        val DEFAULT_PATROLS = listOf(
            Ch2L1PatrolSpec("patrol_a", "guard_1", "patrol_a_waypoints", pauses = listOf(Ch2L1PatrolSpec.Pause("patrol_a_pause", 100))),
            Ch2L1PatrolSpec("patrol_b", "guard_2", "patrol_b_waypoints", spawn = "patrol_b_spawn", pauses = listOf(Ch2L1PatrolSpec.Pause("patrol_b_spawn", 120))),
            Ch2L1PatrolSpec("patrol_c", "guard_1", "patrol_c_waypoints", pauses = listOf(Ch2L1PatrolSpec.Pause("patrol_c_pause", 80)), from = Ch2L1State.ACT3_OVERTIME),
        )
        val DEFAULT = Ch2L1Patrols()
    }
}

/**
 * Reads `patrols.json` (every key optional; seconds where the spec has ticks):
 *
 * ```json
 * { "watch": { "range": 8, "crouch_range": 4, "fov_degrees": 70, "proximity": 2, "crouch_near": 3,
 *              "stare_zone": "aux_lab", "stare_seconds": 10, "stare_reach": 16,
 *              "notice_seconds": 1.5, "decay_seconds": 2, "grace_seconds": 3, "chase_seconds": 2.5, "give_up_seconds": 3,
 *              "catch_reach": 1.6, "run_pace": 3, "warn_line": "guard_warn", "caught_line": "guard_caught",
 *              "forbidden": [ { "zone": "aux_lab", "back": "return_aux_lab" }, { "zone": "lab", "back": "return_lab", "from": "ACT3_OVERTIME" } ],
 *              "back_elsewhere": "return_ring", "dark_zones": ["lab"], "dark_from": "ACT3_OVERTIME", "dark_range": 3, "flashlight_range": 12 },
 *   "patrols": [ { "id": "patrol_a", "skin": "guard_1", "path": "patrol_a_waypoints", "pace": 1,
 *                  "pauses": [ { "at": "patrol_a_pause", "seconds": 5 } ], "from": "ACT3_OVERTIME" } ] }
 * ```
 */
object Ch2L1PatrolsParser {

    data class Result(val patrols: Ch2L1Patrols, val problems: List<String>)

    fun parse(root: JsonObject): Result {
        val f = JsonFields(root, "")
        val w = f.child("watch")
        val d = Ch2L1WatchSpec()
        fun seconds(key: String, default: Int, max: Double = 600.0) = (w.double(key, default / 20.0, min = 0.0, max = max) * 20).toInt()
        val forbidden = if (!w.has("forbidden")) d.forbidden else (w.array("forbidden")?.toList() ?: emptyList()).mapIndexedNotNull { i, e ->
            if (!e.isJsonObject) {
                f.problems += "watch.forbidden[$i]: expected an object"
                return@mapIndexedNotNull null
            }
            val o = JsonFields(e.asJsonObject, "watch.forbidden[$i]", f.problems)
            val zone = o.requiredString("zone")
            val back = o.requiredString("back")
            val from = state(o, "from")
            o.reportUnknownKeys()
            if (zone == null || back == null) null else Ch2L1Forbidden(zone, back, from)
        }
        val watch = Ch2L1WatchSpec(
            range = w.double("range", d.range, min = 0.0, max = 64.0),
            crouchRange = w.double("crouch_range", d.crouchRange, min = 0.0, max = 64.0),
            fovDegrees = w.double("fov_degrees", d.fovDegrees, min = 1.0, max = 360.0),
            proximity = w.double("proximity", d.proximity, min = 0.0, max = 16.0),
            crouchNear = w.double("crouch_near", d.crouchNear, min = 0.0, max = 16.0),
            stareZone = w.string("stare_zone", d.stareZone),
            stareTicks = seconds("stare_seconds", d.stareTicks),
            stareReach = w.double("stare_reach", d.stareReach, min = 1.0, max = 64.0),
            noticeTicks = maxOf(1, seconds("notice_seconds", d.noticeTicks, 30.0)),
            decayTicks = maxOf(1, seconds("decay_seconds", d.decayTicks, 30.0)),
            graceTicks = seconds("grace_seconds", d.graceTicks, 60.0),
            chaseTicks = seconds("chase_seconds", d.chaseTicks, 60.0),
            giveUpTicks = seconds("give_up_seconds", d.giveUpTicks, 60.0),
            catchReach = w.double("catch_reach", d.catchReach, min = 0.5, max = 8.0),
            runPace = w.double("run_pace", d.runPace, min = 0.5, max = 6.0),
            warnLine = w.string("warn_line", d.warnLine),
            caughtLine = w.string("caught_line", d.caughtLine),
            forbidden = forbidden,
            backElsewhere = w.string("back_elsewhere", d.backElsewhere),
            darkZones = if (w.has("dark_zones")) w.stringList("dark_zones") else d.darkZones,
            darkFrom = state(w, "dark_from") ?: d.darkFrom,
            darkRange = w.double("dark_range", d.darkRange, min = 0.0, max = 64.0),
            flashlightRange = w.double("flashlight_range", d.flashlightRange, min = 0.0, max = 64.0),
        )
        w.reportUnknownKeys()
        val patrols = if (!f.has("patrols")) Ch2L1Patrols.DEFAULT_PATROLS else (f.array("patrols")?.toList() ?: emptyList()).mapIndexedNotNull { i, e ->
            if (!e.isJsonObject) {
                f.problems += "patrols[$i]: expected an object"
                return@mapIndexedNotNull null
            }
            val p = JsonFields(e.asJsonObject, "patrols[$i]", f.problems)
            val id = p.requiredString("id")
            val skin = p.string("skin", Ch2L1Skins.DEFAULT)
            val path = p.requiredString("path")
            val spawn = if (p.has("spawn")) p.string("spawn", "") else null
            val pace = p.double("pace", 1.0, min = 0.2, max = 4.0)
            val pauses = (p.array("pauses")?.toList() ?: emptyList()).mapIndexedNotNull { j, pe ->
                if (!pe.isJsonObject) return@mapIndexedNotNull null
                val q = JsonFields(pe.asJsonObject, "patrols[$i].pauses[$j]", f.problems)
                val at = q.requiredString("at")
                val ticks = (q.double("seconds", 3.0, min = 0.0, max = 600.0) * 20).toInt()
                q.reportUnknownKeys()
                at?.let { Ch2L1PatrolSpec.Pause(it, ticks) }
            }
            val from = state(p, "from")
            p.reportUnknownKeys()
            if (skin !in Ch2L1Skins.ALL) f.problems += "patrols[$i].skin: \"$skin\" is not a skin of the factory"
            if (id == null || path == null) null else Ch2L1PatrolSpec(id, skin, path, spawn, pace, pauses, from)
        }
        f.reportUnknownKeys()
        if (patrols.map { it.id }.toSet().size != patrols.size) f.problems += "patrols: two patrols have the same id"
        return Result(Ch2L1Patrols(watch, patrols), f.problems.toList())
    }

    /** A state's name at [key] (`"ACT3_OVERTIME"`), or null when absent or unknown (told). */
    private fun state(fields: JsonFields, key: String): Ch2L1State? {
        if (!fields.has(key)) return null
        val text = fields.string(key, "")
        return Ch2L1State.parse(text) ?: null.also { fields.problems += "${fields.pathOf(key)}: \"$text\" is not a state" }
    }
}

/**
 * A guard walking his round, one tick at a time. Plain Kotlin: told where he is, it says where to walk or that he stands. A closed path
 * goes round and round; an open one there and back. At a point with a pause ([pauses]: the point's index to ticks) he stands that long,
 * facing along the way he came. Interrupted (a scene, a chase) he just picks up again towards the point he was heading for.
 */
class Ch2L1PatrolRunner(private val points: List<Position>, private val closed: Boolean, private val pauses: Map<Int, Int>) {

    sealed interface Command {
        data class Walk(val x: Double, val y: Double, val z: Double, val brake: Boolean) : Command
        data class Stand(val yaw: Float?) : Command
    }

    private var target = if (points.size > 1) 1 else 0
    private var step = 1
    private var waiting = 0
    private var lastYaw: Float? = null

    /** The point he walks to now. */
    val heading: Position get() = points[target]

    fun tick(x: Double, z: Double): Command {
        if (points.isEmpty()) return Command.Stand(null)
        if (waiting > 0) {
            waiting--
            if (waiting > 0) return Command.Stand(lastYaw)
            advance()
            return tick(x, z)
        }
        val p = points[target]
        val dx = p.x - x
        val dz = p.z - z
        if (sqrt(dx * dx + dz * dz) < ARRIVE) {
            val pause = pauses[target] ?: 0
            if (pause > 0) {
                waiting = pause
                return Command.Stand(lastYaw)
            }
            advance()
            return tick(x, z)
        }
        lastYaw = Ch2L1Angles.yawOf(dx, dz)
        return Command.Walk(p.x, p.y, p.z, brake = (pauses[target] ?: 0) > 0)
    }

    private fun advance() {
        if (points.size < 2) return
        if (closed) {
            target = (target + 1) % points.size
            return
        }
        if (target + step !in points.indices) step = -step
        target += step
    }

    companion object {
        const val ARRIVE = 0.4

        /** The index of the point of [points] nearest [at] along the floor. */
        fun nearest(points: List<Position>, at: Position): Int =
            points.indices.minByOrNull { (points[it].x - at.x) * (points[it].x - at.x) + (points[it].z - at.z) * (points[it].z - at.z) } ?: 0
    }
}

/**
 * What a guard thinks of the player, one tick at a time ([Ch2L1WatchSpec]). Plain Kotlin: told each tick whether the player is
 * suspicious where he can see them and how far they are, it says how full his "?" is ([fill], 0 to 1), what [phase] he is in and what
 * just happened ([Event]).
 */
class Ch2L1Watch(private val spec: Ch2L1WatchSpec) {

    enum class Phase { CALM, NOTICING, WARNED, CHASING }

    enum class Event { NOTICED, WARNED, CHASED, CAUGHT, GAVE_UP, CALMED }

    var phase = Phase.CALM
        private set
    var fill = 0.0
        private set
    private var timer = 0
    private var unseen = 0

    /** [suspicious]: seen doing something he should not; [seen]: he sees them at all; [distance] to them along the floor. */
    fun tick(suspicious: Boolean, seen: Boolean, distance: Double): Event? {
        when (phase) {
            Phase.CALM, Phase.NOTICING -> {
                if (suspicious) {
                    val was = phase
                    fill = minOf(1.0, fill + 1.0 / spec.noticeTicks)
                    phase = Phase.NOTICING
                    if (fill >= 1.0 - EPSILON) {
                        fill = 1.0
                        phase = Phase.WARNED
                        timer = 0
                        return Event.WARNED
                    }
                    return if (was == Phase.CALM) Event.NOTICED else null
                }
                fill = maxOf(0.0, fill - 1.0 / spec.decayTicks)
                if (fill <= EPSILON && phase == Phase.NOTICING) {
                    phase = Phase.CALM
                    return Event.CALMED
                }
            }
            Phase.WARNED -> {
                timer++
                if (!suspicious) {
                    unseen++
                    if (unseen >= spec.decayTicks) return calm()
                    return null
                }
                unseen = 0
                if (timer >= spec.graceTicks) {
                    phase = Phase.CHASING
                    timer = 0
                    return Event.CHASED
                }
            }
            Phase.CHASING -> {
                timer++
                if (seen) unseen = 0 else unseen++
                if (seen && (distance <= spec.catchReach || timer >= spec.chaseTicks)) {
                    calm()
                    return Event.CAUGHT
                }
                if (unseen >= spec.giveUpTicks) {
                    calm()
                    return Event.GAVE_UP
                }
            }
        }
        return null
    }

    /** Back to calm, his "?" empty (after a catch, or when the player is put back). */
    fun calm(): Event {
        phase = Phase.CALM
        fill = 0.0
        timer = 0
        unseen = 0
        return Event.CALMED
    }

    companion object {
        private const val EPSILON = 1e-9

        /**
         * True if eyes at ([ex], [ez]) looking along [yaw] (degrees, Minecraft's) see ([tx], [tz]): within [range] and half of [fovDegrees]
         * of the look, or within [proximity] whatever the look. Walls are the caller's.
         */
        fun inView(ex: Double, ez: Double, yaw: Float, tx: Double, tz: Double, range: Double, fovDegrees: Double, proximity: Double): Boolean {
            val dx = tx - ex
            val dz = tz - ez
            val d = sqrt(dx * dx + dz * dz)
            if (d <= proximity) return true
            if (d > range) return false
            val toward = Math.toDegrees(atan2(-dx, dz)).toFloat()
            return abs(Ch2L1Angles.wrap(toward - yaw)) <= fovDegrees / 2
        }
    }
}

/** How long a player has kept their eyes on the auxiliary lab: [tick] with whether they look at it now; [over] once it is more than the spec's. */
class Ch2L1Stare(private val ticks: Int) {
    private var held = 0

    fun tick(looking: Boolean) {
        held = if (looking) held + 1 else 0
    }

    val over: Boolean get() = held > ticks
}
