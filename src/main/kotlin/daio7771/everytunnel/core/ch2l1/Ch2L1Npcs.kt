package daio7771.everytunnel.core.ch2l1

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

/** A person of the factory's ordinary life: [skin], the [group] they belong to (the LAB's go in phase 8), where they start and what they do, over and over. */
data class Ch2L1WorkerSpec(val id: String, val skin: String, val group: String, val spawn: String, val steps: List<Ch2L1StepSpec>)

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
 * multiplies how fast they walk. The skin is one of [Ch2L1Skins.ALL].
 */
data class Ch2L1NpcData(val walkSpeed: Double, val workers: List<Ch2L1WorkerSpec>, val groups: Map<String, List<Ch2L1GroupMember>>) {
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
            entry.reportUnknownKeys()
            when {
                id == null || spawn == null -> fields.problems += "workers[$i]: needs an id and a spawn anchor, skipped"
                !ID.matches(id) -> fields.problems += "workers[$i]: the id \"$id\" is not a-z, 0-9 and _, skipped"
                !seen.add(id) -> fields.problems += "workers[$i]: the id \"$id\" is repeated, skipped"
                else -> workers += Ch2L1WorkerSpec(id, skin, group, spawn, steps)
            }
        }
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
        return Result(Ch2L1NpcData(walkSpeed, workers, groups), fields.problems.toList())
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
 * [STUCK_TICKS] ticks (a bump, a door) is told to snap to it, so a routine never stops for good.
 */
class Ch2L1RoutineRunner(private val steps: List<Ch2L1Step>, private val random: Random) {

    sealed interface Command {
        /** Stand still; face [yaw] if it is not null; [typing] with the arms out. */
        data class Stand(val yaw: Float?, val typing: Boolean) : Command

        /** Walk to the point; [snap]: put them there, they are stuck. */
        data class MoveTo(val x: Double, val y: Double, val z: Double, val snap: Boolean) : Command
    }

    private var index = 0
    private var point = 0
    private var wait = -1
    private var arrived = false
    private var stuck = 0

    fun tick(x: Double, y: Double, z: Double): Command {
        if (steps.isEmpty()) return Command.Stand(null, false)
        when (val step = steps[index]) {
            is Ch2L1Step.Stand -> {
                if (wait < 0) wait = pick(step.minTicks, step.maxTicks)
                if (--wait <= 0) advance()
                return Command.Stand(null, false)
            }
            is Ch2L1Step.Walk -> {
                val target = step.points.getOrNull(point)
                if (target == null) {
                    advance()
                    return Command.Stand(null, false)
                }
                if (near(x, z, target)) {
                    point++
                    stuck = 0
                    if (point >= step.points.size) {
                        advance()
                        return Command.Stand(null, false)
                    }
                    return moveTo(step.points[point], x, z)
                }
                return moveTo(target, x, z)
            }
            is Ch2L1Step.Type -> {
                if (!arrived) {
                    if (!near(x, z, step.spot)) return moveTo(step.spot, x, z)
                    arrived = true
                    wait = pick(step.minTicks, step.maxTicks)
                }
                if (--wait <= 0) advance()
                return Command.Stand(step.yaw, true)
            }
        }
    }

    private fun moveTo(target: daio7771.everytunnel.core.level.Position, x: Double, z: Double): Command {
        stuck++
        val snap = stuck > STUCK_TICKS
        if (snap) stuck = 0
        return Command.MoveTo(target.x, target.y, target.z, snap)
    }

    private fun advance() {
        index = (index + 1) % steps.size
        point = 0
        wait = -1
        arrived = false
        stuck = 0
    }

    private fun near(x: Double, z: Double, target: Position): Boolean {
        val dx = target.x - x
        val dz = target.z - z
        return sqrt(dx * dx + dz * dz) < ARRIVE
    }

    private fun pick(min: Int, max: Int): Int = if (max > min) min + random.nextInt(max - min + 1) else min

    companion object {
        /** Within this many blocks of a point (along the floor) they are at it. */
        const val ARRIVE = 0.35

        /** Ticks without arriving before they are put at the point. */
        const val STUCK_TICKS = 240
    }
}
