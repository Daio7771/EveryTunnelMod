package daio7771.everytunnel.chapter2.level1.core

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import daio7771.everytunnel.core.level.Position
import daio7771.everytunnel.util.JsonFields
import java.util.Random
import kotlin.math.sqrt

/** One step of a worker's routine as `npcs.json` writes it, before its anchors are looked up. */
sealed interface Ch2L1StepSpec {
    /** Stands still, [minSeconds] to [maxSeconds]. */
    data class Stand(val minSeconds: Double, val maxSeconds: Double) : Ch2L1StepSpec

    /** Walks the path [path] from its first point to its last (and, if it is closed, back to its first), or the other way with [reverse]. */
    data class Walk(val path: String, val reverse: Boolean) : Ch2L1StepSpec

    /** Goes to the point anchor [at], faces the way it faces and types there for [minSeconds] to [maxSeconds]. */
    data class Type(val at: String, val minSeconds: Double, val maxSeconds: Double) : Ch2L1StepSpec
}

/**
 * A person of the factory's ordinary life: [skin], the [group] they belong to (the LAB's go in phase 8), where they start and what they do, over
 * and over; [pace]: their own walking pace (a multiple of `walk_speed`), null to draw one ([Ch2L1Life.pace]); [until]: from this state on
 * they are no longer in the factory (they leave out of sight; worker_a's and worker_b's ambient doubles, at the meeting from Act 2), null: always.
 */
data class Ch2L1WorkerSpec(
    val id: String, val skin: String, val group: String, val spawn: String, val steps: List<Ch2L1StepSpec>, val pace: Double? = null,
    val until: Ch2L1State? = null,
)

/** One of a group's people, standing at the point anchor [at] facing the way it faces (the lockers' group of the meeting). */
data class Ch2L1GroupMember(val skin: String, val at: String)

/**
 * The factory's people, from `npcs.json`. Plain Kotlin.
 *
 * ```json
 * {
 *   "walk_speed": 1.0,
 *   "workers": [
 *     { "id": "lab_01", "skin": "worker_a", "group": "lab", "spawn": "npc_lab_01",
 *       "routine": [ { "type": "npc_lab_01", "seconds": [20, 40] }, { "stand": [3, 6] } ] },
 *     { "id": "lab_07", "skin": "generic_2", "group": "lab", "spawn": "npc_lab_07",
 *       "routine": [ { "walk": "route_lab_a" }, { "stand": [4, 8] }, { "walk": "route_lab_a", "reverse": true } ] }
 *   ],
 *   "groups": { "taquillas": [ { "skin": "worker_a", "at": "taquillas_npc_01" } ] }
 * }
 * ```
 *
 * A routine is a list of steps done in order and then over again: `stand` (seconds, a number or [min, max]), `walk` (a path anchor,
 * optionally `reverse`) and `type` (a point anchor, with `seconds`). Positions are anchors, so moving the map moves the people. [walkSpeed]
 * multiplies how fast they walk, and a worker's `pace` (optional) how fast that one does. The skin is one of [Ch2L1Skins.ALL]. `life`
 * (optional) is how alive they look: [Ch2L1Life].
 */
data class Ch2L1NpcData(
    val walkSpeed: Double,
    val workers: List<Ch2L1WorkerSpec>,
    val groups: Map<String, List<Ch2L1GroupMember>>,
    val life: Ch2L1Life = Ch2L1Life.DEFAULT,
) {
    companion object {
        val EMPTY = Ch2L1NpcData(1.0, emptyList(), emptyMap())
    }
}

object Ch2L1NpcsParser {

    data class Result(val data: Ch2L1NpcData, val problems: List<String>)

    private val ID = Regex("[a-z0-9_]+")

    fun parse(root: JsonObject): Result {
        val fields = JsonFields(root, "")
        val walkSpeed = fields.double("walk_speed", 1.0, min = 0.1, max = 4.0)
        val workers = mutableListOf<Ch2L1WorkerSpec>()
        val seen = mutableSetOf<String>()
        fields.array("workers")?.forEachIndexed { i, element ->
            val entry = objectAt(element, "workers[$i]", fields.problems) ?: return@forEachIndexed
            val id = entry.requiredString("id")
            val skin = skin(entry, fields.problems)
            val group = entry.string("group", "ambient")
            val spawn = entry.requiredString("spawn")
            val steps = parseSteps(entry, fields.problems)
            val pace = if (entry.has("pace")) entry.double("pace", 1.0, min = 0.3, max = 3.0) else null
            val until = if (entry.has("until")) {
                val name = entry.string("until", "")
                Ch2L1State.parse(name).also { if (it == null) fields.problems += "workers[$i].until: \"$name\" is not a state, ignored" }
            } else {
                null
            }
            entry.reportUnknownKeys()
            when {
                id == null || spawn == null -> fields.problems += "workers[$i]: needs an id and a spawn anchor, skipped"
                !ID.matches(id) -> fields.problems += "workers[$i]: the id \"$id\" is not a-z, 0-9 and _, skipped"
                !seen.add(id) -> fields.problems += "workers[$i]: the id \"$id\" is repeated, skipped"
                else -> workers += Ch2L1WorkerSpec(id, skin, group, spawn, steps, pace, until)
            }
        }
        val life = life(fields.child("life"))
        val groups = linkedMapOf<String, List<Ch2L1GroupMember>>()
        val groupsObject = fields.child("groups")
        val groupsJson = root.get("groups")
        if (groupsJson != null && groupsJson.isJsonObject) {
            for (name in groupsJson.asJsonObject.keySet()) {
                val array = groupsObject.array(name) ?: continue
                val members = mutableListOf<Ch2L1GroupMember>()
                array.forEachIndexed { i, element ->
                    val entry = objectAt(element, "groups.$name[$i]", fields.problems) ?: return@forEachIndexed
                    val at = entry.requiredString("at")
                    val skin = skin(entry, fields.problems)
                    entry.reportUnknownKeys()
                    if (at != null) members += Ch2L1GroupMember(skin, at)
                }
                groups[name] = members
            }
        }
        groupsObject.reportUnknownKeys()
        fields.reportUnknownKeys()
        return Result(Ch2L1NpcData(walkSpeed, workers, groups, life), fields.problems.toList())
    }

    private fun life(fields: JsonFields): Ch2L1Life {
        val d = Ch2L1Life.DEFAULT
        fun span(key: String, default: Ch2L1Span): Ch2L1Span {
            if (!fields.has(key)) return default
            val array = fields.array(key)
            val numbers = array?.map { it.takeIf { e -> e.isJsonPrimitive && e.asJsonPrimitive.isNumber }?.asDouble }
            val min = numbers?.getOrNull(0)
            val max = numbers?.getOrNull(1)
            if (numbers?.size != 2 || min == null || max == null || min < 0 || max < min) {
                fields.problems += "${fields.pathOf(key)}: expected [min, max] seconds, using [${default.min / 20.0}, ${default.max / 20.0}]"
                return default
            }
            return Ch2L1Span.seconds(min, max)
        }
        return Ch2L1Life(
            paceSpread = fields.double("pace_spread", d.paceSpread, min = 0.0, max = 0.5),
            accelTicks = (fields.double("accel_seconds", d.accelTicks / 20.0, min = 0.0, max = 5.0) * 20).toInt(),
            turnDegrees = fields.double("turn_degrees", d.turnDegrees.toDouble(), min = 1.0, max = 90.0).toFloat(),
            linger = fields.double("linger_chance", d.linger, min = 0.0, max = 1.0),
            lingerTicks = span("linger_seconds", d.lingerTicks),
            glanceTicks = span("glance_seconds", d.glanceTicks),
            playerRange = fields.double("player_range", d.playerRange, min = 0.0, max = 16.0),
            playerLookTicks = span("player_look_seconds", d.playerLookTicks),
            playerCooldownTicks = span("player_cooldown_seconds", d.playerCooldownTicks),
            chatRange = fields.double("chat_range", d.chatRange, min = 0.0, max = 8.0),
            chatChance = fields.double("chat_chance", d.chatChance, min = 0.0, max = 1.0),
            chatTicks = span("chat_seconds", d.chatTicks),
            chatCooldownTicks = span("chat_cooldown_seconds", d.chatCooldownTicks),
        ).also { fields.reportUnknownKeys() }
    }

    private fun objectAt(element: JsonElement, path: String, problems: MutableList<String>): JsonFields? {
        if (!element.isJsonObject) {
            problems += "$path: expected an object, skipped"
            return null
        }
        return JsonFields(element.asJsonObject, path, problems)
    }

    private fun skin(entry: JsonFields, problems: MutableList<String>): String {
        val skin = entry.string("skin", Ch2L1Skins.DEFAULT)
        if (skin in Ch2L1Skins.ALL) return skin
        problems += "${entry.pathOf("skin")}: \"$skin\" is not a skin (${Ch2L1Skins.ALL.joinToString()}), using ${Ch2L1Skins.DEFAULT}"
        return Ch2L1Skins.DEFAULT
    }

    private val STEP_KEYS = mapOf("stand" to setOf("stand"), "walk" to setOf("walk", "reverse"), "type" to setOf("type", "seconds"))

    private fun parseSteps(worker: JsonFields, problems: MutableList<String>): List<Ch2L1StepSpec> {
        val steps = mutableListOf<Ch2L1StepSpec>()
        worker.array("routine")?.forEachIndexed { i, element ->
            val path = "${worker.pathOf("routine")}[$i]"
            if (!element.isJsonObject) {
                problems += "$path: expected an object, skipped"
                return@forEachIndexed
            }
            val step = element.asJsonObject
            val kinds = STEP_KEYS.keys.filter { step.has(it) }
            if (kinds.size != 1) {
                problems += "$path: needs exactly one of stand, walk and type, skipped"
                return@forEachIndexed
            }
            val kind = kinds[0]
            for (key in step.keySet()) if (key !in STEP_KEYS.getValue(kind)) problems += "$path.$key: unknown key for a $kind step, ignored (typo?)"
            fun text(key: String): String? =
                step.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                    .also { if (it == null) problems += "$path.$key: expected a name, skipped" }
            when (kind) {
                "stand" -> seconds(step.get("stand"), "$path.stand", problems)?.let { steps += Ch2L1StepSpec.Stand(it.first, it.second) }
                "walk" -> text("walk")?.let { steps += Ch2L1StepSpec.Walk(it, step.get("reverse")?.takeIf { r -> r.isJsonPrimitive }?.asBoolean ?: false) }
                else -> {
                    val range = seconds(step.get("seconds") ?: com.google.gson.JsonPrimitive(20), "$path.seconds", problems) ?: return@forEachIndexed
                    text("type")?.let { steps += Ch2L1StepSpec.Type(it, range.first, range.second) }
                }
            }
        }
        return steps
    }

    /** A number, or [min, max], of seconds. */
    private fun seconds(element: JsonElement, path: String, problems: MutableList<String>): Pair<Double, Double>? {
        fun number(e: JsonElement) = if (e.isJsonPrimitive && e.asJsonPrimitive.isNumber) e.asDouble else null
        val range = when {
            element.isJsonArray && element.asJsonArray.size() == 2 -> number(element.asJsonArray[0]) to number(element.asJsonArray[1])
            else -> number(element) to number(element)
        }
        val (min, max) = range
        if (min == null || max == null || min < 0 || max < min) {
            problems += "$path: expected seconds (a number or [min, max]), skipped"
            return null
        }
        return min to max
    }
}

/** A step with its places looked up: what [Ch2L1RoutineRunner] runs. Positions are in the world's coordinates. */
sealed interface Ch2L1Step {
    data class Stand(val minTicks: Int, val maxTicks: Int) : Ch2L1Step

    data class Walk(val points: List<Position>) : Ch2L1Step

    data class Type(val spot: Position, val yaw: Float, val minTicks: Int, val maxTicks: Int) : Ch2L1Step
}

object Ch2L1Routines {

    data class Resolved(val spawn: Anchor, val steps: List<Ch2L1Step>)

    /** [worker]'s spawn point and steps with every anchor looked up in [anchors]; null and a problem if one is missing. */
    fun resolve(worker: Ch2L1WorkerSpec, anchors: Ch2L1Anchors, problems: MutableList<String> = mutableListOf()): Resolved? {
        val spawn = anchors.point(worker.spawn)
        if (spawn == null) {
            problems += "worker ${worker.id}: no anchor ${worker.spawn}"
            return null
        }
        val steps = mutableListOf<Ch2L1Step>()
        for (step in worker.steps) {
            when (step) {
                is Ch2L1StepSpec.Stand -> steps += Ch2L1Step.Stand(ticks(step.minSeconds), ticks(step.maxSeconds))
                is Ch2L1StepSpec.Walk -> {
                    val path = anchors.path(step.path)
                    if (path == null) {
                        problems += "worker ${worker.id}: no path ${step.path}"
                        return null
                    }
                    val points = if (step.reverse) path.points.reversed() else path.points
                    steps += Ch2L1Step.Walk(if (path.closed) points + points.first() else points)
                }
                is Ch2L1StepSpec.Type -> {
                    val spot = anchors.point(step.at)
                    if (spot == null) {
                        problems += "worker ${worker.id}: no anchor ${step.at}"
                        return null
                    }
                    steps += Ch2L1Step.Type(spot.position, spot.yaw, ticks(step.minSeconds), ticks(step.maxSeconds))
                }
            }
        }
        return Resolved(spawn, steps)
    }

    private fun ticks(seconds: Double): Int = (seconds * 20).toInt()
}

/**
 * Runs a routine, one tick at a time, for one person: told where they are, it says what to do now ([Command]). Plain Kotlin, no clock, no
 * world: the entity does what it says (walks toward a point, stands facing a way, types). A step ends when its time is up (stand, type)
 * or its last point is reached (walk); after the last step the routine begins again. Anyone who does not get nearer to a point for
 * [STUCK_TICKS] ticks (a bump, a door) is told to snap to it, so a routine never stops for good. On the way, at each point of a path but the
 * first and the last, they stop a moment [linger] of the times (for [lingerTicks]), like someone who remembers something.
 */
class Ch2L1RoutineRunner(
    private val steps: List<Ch2L1Step>,
    private val random: Random,
    private val linger: Double = 0.0,
    private val lingerTicks: Ch2L1Span = Ch2L1Span(0, 0),
) {

    sealed interface Command {
        /** Stand still; face [yaw] if it is not null; [typing] with the arms out. */
        data class Stand(val yaw: Float?, val typing: Boolean) : Command

        /** Walk to the point; [last]: they stop there (they brake on the way); [snap]: put them there, they are stuck. */
        data class MoveTo(val x: Double, val y: Double, val z: Double, val snap: Boolean, val last: Boolean = false) : Command
    }

    private var index = 0
    private var point = 0
    private var wait = -1
    private var pause = 0
    private var arrived = false
    private var stuck = 0
    private var nearest = Double.MAX_VALUE

    fun tick(x: Double, y: Double, z: Double): Command {
        if (steps.isEmpty()) return Command.Stand(null, false)
        when (val step = steps[index]) {
            is Ch2L1Step.Stand -> {
                if (wait < 0) wait = pick(step.minTicks, step.maxTicks)
                if (--wait <= 0) advance()
                return Command.Stand(null, false)
            }
            is Ch2L1Step.Walk -> {
                if (pause > 0) {
                    pause--
                    return Command.Stand(null, false)
                }
                val target = step.points.getOrNull(point)
                if (target == null) {
                    advance()
                    return Command.Stand(null, false)
                }
                if (near(x, z, target)) {
                    point++
                    stuck = 0
                    nearest = Double.MAX_VALUE
                    if (point >= step.points.size) {
                        advance()
                        return Command.Stand(null, false)
                    }
                    if (point >= 2 && linger > 0 && random.nextDouble() < linger) {
                        val ticks = lingerTicks.pick(random)
                        if (ticks > 0) {
                            pause = ticks - 1
                            return Command.Stand(null, false)
                        }
                    }
                    return moveTo(step.points[point], x, z, point == step.points.size - 1)
                }
                return moveTo(target, x, z, point == step.points.size - 1)
            }
            is Ch2L1Step.Type -> {
                if (!arrived) {
                    if (!near(x, z, step.spot)) return moveTo(step.spot, x, z, true)
                    arrived = true
                    wait = pick(step.minTicks, step.maxTicks)
                }
                if (--wait <= 0) advance()
                return Command.Stand(step.yaw, true)
            }
        }
    }

    private fun moveTo(target: Position, x: Double, z: Double, last: Boolean): Command {
        val distance = distance(x, z, target)
        if (distance < nearest - HEADWAY) {
            nearest = distance
            stuck = 0
        } else {
            stuck++
        }
        val snap = stuck > STUCK_TICKS
        if (snap) {
            stuck = 0
            nearest = Double.MAX_VALUE
        }
        return Command.MoveTo(target.x, target.y, target.z, snap, last)
    }

    private fun advance() {
        index = (index + 1) % steps.size
        point = 0
        wait = -1
        pause = 0
        arrived = false
        stuck = 0
        nearest = Double.MAX_VALUE
    }

    private fun near(x: Double, z: Double, target: Position): Boolean = distance(x, z, target) < ARRIVE

    private fun distance(x: Double, z: Double, target: Position): Double {
        val dx = target.x - x
        val dz = target.z - z
        return sqrt(dx * dx + dz * dz)
    }

    private fun pick(min: Int, max: Int): Int = if (max > min) min + random.nextInt(max - min + 1) else min

    companion object {
        /** Within this many blocks of a point (along the floor) they are at it. */
        const val ARRIVE = 0.35

        /** Ticks without getting nearer to the point (by [HEADWAY]) before they are put at it: a bump, a door, not a long walk. */
        const val STUCK_TICKS = 100

        /** Getting this much nearer to the point counts as making headway. */
        const val HEADWAY = 0.03
    }
}
