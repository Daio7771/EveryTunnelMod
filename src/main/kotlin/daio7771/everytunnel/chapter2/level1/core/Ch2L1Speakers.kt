package daio7771.everytunnel.chapter2.level1.core

import com.google.gson.JsonObject
import daio7771.everytunnel.util.JsonFields
import java.util.Random

/** A line the loudspeakers say at a fixed moment: [delayTicks] after the player reaches the state [after]. */
data class Ch2L1FixedLine(val line: String, val after: Ch2L1State, val delayTicks: Int)

/** One shot of the first announcement's cinematic: the camera's anchor, what it looks at, how long; [cameraTo] to drift. */
data class Ch2L1IntroShot(val camera: String, val cameraTo: String?, val look: String, val ticks: Int)

/** The first announcement's cinematic (see [Ch2L1IntroScene]): [line] is the announcement it plays. */
data class Ch2L1IntroSpec(val line: String, val flyInTicks: Int, val flyOutTicks: Int, val shots: List<Ch2L1IntroShot>)

/**
 * The loudspeaker system of Act 1, from `speakers.json`. Plain Kotlin.
 *
 * ```json
 * {
 *   "zones": ["speaker_entrance", "speaker_lab", "speaker_main", "speaker_west"],
 *   "emitters": { "speaker_lab": "speaker_lab_src" },
 *   "volume": 3.0, "ding_seconds": 1.5, "after_terminal_seconds": 1,
 *   "fixed": [ { "line": "rowan_loyal_a1", "after": "ACT1_TASK2", "delay_seconds": 0 } ],
 *   "pool": ["rowan_loyal_a2", "rowan_loyal_a3", "rowan_loyal_a5"],
 *   "cooldown_seconds": [75, 120],
 *   "intro": { "line": "rowan_loyal_a1", "fly_in_ticks": 10, "fly_out_ticks": 20,
 *              "shots": [ { "camera": "player", "look": "speaker_lab_src", "ticks": 135 } ] }
 * }
 * ```
 *
 * A zone is an anchor zone of `anchors.json` and its emitter the point anchor of the ceiling speaker that speaks in it. A
 * line is a voice line (`voice.<id>`). The first fixed line is the opening: Rowan says nothing before it, and it comes when the
 * first task is done and the player has left the terminal ([afterTerminalTicks] later). The fixed lines play at their moment; the pool
 * lines once each, in random order, with a cooldown (a random number of seconds between the two) after the one before, and only after
 * the opening; never two at once, and only while the player stands in a zone; when it is not in one, a line waits for them. Reaching
 * [Ch2L1State.ACT2_FOLLOW] cancels everything. `rowan_loyal_a6` is in neither list: it is kept for the office's scene.
 */
data class Ch2L1Speakers(
    val zones: List<String>,
    val emitters: Map<String, String>,
    val volume: Float,
    val dingTicks: Int,
    val afterTerminalTicks: Int,
    val fixed: List<Ch2L1FixedLine>,
    val pool: List<String>,
    val cooldownMinTicks: Int,
    val cooldownMaxTicks: Int,
    val intro: Ch2L1IntroSpec?,
) {
    companion object {
        /** The state from which the loudspeakers are quiet for good. */
        val CANCELLED_FROM = Ch2L1State.ACT2_FOLLOW
    }
}

object Ch2L1SpeakersParser {

    data class Result(val speakers: Ch2L1Speakers, val problems: List<String>)

    private val DEFAULT_ZONES = listOf("speaker_entrance", "speaker_lab", "speaker_main", "speaker_west")

    fun parse(root: JsonObject): Result {
        val fields = JsonFields(root, "")
        val problems = fields.problems
        val zones = fields.stringList("zones").ifEmpty { DEFAULT_ZONES }
        val emitters = parseEmitters(fields, zones)
        val volume = fields.double("volume", 3.0, min = 0.5, max = 10.0).toFloat()
        val dingTicks = (fields.double("ding_seconds", 1.5, min = 0.0, max = 10.0) * 20).toInt()
        val afterTerminal = (fields.double("after_terminal_seconds", 1.0, min = 0.0, max = 60.0) * 20).toInt()
        val fixed = parseFixed(fields)
        val pool = if (fields.has("pool")) fields.stringList("pool") else listOf("rowan_loyal_a2", "rowan_loyal_a3", "rowan_loyal_a5")
        val (min, max) = parseCooldown(fields)
        val intro = if (fields.has("intro")) parseIntro(fields.child("intro")) else null
        fields.reportUnknownKeys()
        return Result(Ch2L1Speakers(zones, emitters, volume, dingTicks, afterTerminal, fixed, pool, min, max, intro), problems.toList())
    }

    private fun parseEmitters(fields: JsonFields, zones: List<String>): Map<String, String> {
        val child = fields.child("emitters")
        val out = linkedMapOf<String, String>()
        for (zone in zones) out[zone] = child.string(zone, "${zone}_src")
        child.reportUnknownKeys()
        return out
    }

    private fun parseFixed(fields: JsonFields): List<Ch2L1FixedLine> {
        if (!fields.has("fixed")) {
            return listOf(
                Ch2L1FixedLine("rowan_loyal_a1", Ch2L1State.ACT1_TASK2, 0),
                Ch2L1FixedLine("rowan_loyal_a4", Ch2L1State.ACT1_TASK3, 100),
            )
        }
        val out = mutableListOf<Ch2L1FixedLine>()
        fields.array("fixed")?.forEachIndexed { i, element ->
            if (!element.isJsonObject) {
                fields.problems += "fixed[$i]: expected an object, skipped"
                return@forEachIndexed
            }
            val entry = JsonFields(element.asJsonObject, "fixed[$i]", fields.problems)
            val line = entry.requiredString("line")
            val afterName = entry.string("after", "")
            val after = Ch2L1State.parse(afterName)
            val delay = (entry.double("delay_seconds", 0.0, min = 0.0, max = 600.0) * 20).toInt()
            entry.reportUnknownKeys()
            if (line == null || after == null) {
                fields.problems += "fixed[$i]: needs a line and an \"after\" state (got \"$afterName\"), skipped"
            } else {
                out += Ch2L1FixedLine(line, after, delay)
            }
        }
        return out
    }

    private fun parseCooldown(fields: JsonFields): Pair<Int, Int> {
        val array = fields.array("cooldown_seconds")
        val numbers = array?.mapNotNull { if (it.isJsonPrimitive && it.asJsonPrimitive.isNumber) it.asDouble else null }
        if (numbers == null || numbers.size != 2 || numbers.any { it < 0 } || numbers[0] > numbers[1]) {
            if (array != null) fields.problems += "cooldown_seconds: expected [min, max] seconds, using [75, 120]"
            return 75 * 20 to 120 * 20
        }
        return (numbers[0] * 20).toInt() to (numbers[1] * 20).toInt()
    }

    private fun parseIntro(intro: JsonFields): Ch2L1IntroSpec? {
        val line = intro.string("line", "rowan_loyal_a1")
        val flyIn = intro.int("fly_in_ticks", 10, min = 1, max = 200)
        val flyOut = intro.int("fly_out_ticks", 20, min = 1, max = 200)
        val shots = mutableListOf<Ch2L1IntroShot>()
        intro.array("shots")?.forEachIndexed { i, element ->
            if (!element.isJsonObject) {
                intro.problems += "${intro.pathOf("shots")}[$i]: expected an object, skipped"
                return@forEachIndexed
            }
            val entry = JsonFields(element.asJsonObject, "${intro.pathOf("shots")}[$i]", intro.problems)
            val camera = entry.requiredString("camera")
            val look = entry.requiredString("look")
            val to = if (entry.has("camera_to")) entry.string("camera_to", "") else null
            val ticks = entry.int("ticks", 40, min = 1, max = 2000)
            entry.reportUnknownKeys()
            if (camera != null && look != null) shots += Ch2L1IntroShot(camera, to, look, ticks)
        }
        intro.reportUnknownKeys()
        if (shots.isEmpty()) {
            intro.problems += "${intro.pathOf("shots")}: no usable shot, the first announcement has no cinematic"
            return null
        }
        return Ch2L1IntroSpec(line, flyIn, flyOut, shots)
    }
}

/**
 * What the loudspeakers do for one player, tick by tick, by the rules in [Ch2L1Speakers]. Plain Kotlin and no clock of its own:
 * the caller gives the game's tick and whether the player stands in a speaker zone; [lineTicks] is how long a line takes
 * (the ding included, which the caller knows). [onState] tells it the state the player reached.
 */
class Ch2L1SpeakerSchedule(
    private val data: Ch2L1Speakers,
    private val lineTicks: (String) -> Int,
    private val random: Random,
) {
    private class Due(val line: String, val at: Long)

    private val played = mutableSetOf<String>()
    private val due = mutableListOf<Due>()
    private var cancelled = false
    private var poolOpen = false
    private var busyUntil = 0L
    private var nextPoolAt = Long.MAX_VALUE
    private var seen: Ch2L1State? = null

    /**
     * The player's progress is behind the last state this schedule was told of: they started a new game, or went back by a command that did not
     * tell the schedule. What it remembers of what was said is then stale, and the caller makes a new one.
     */
    fun regressed(state: Ch2L1State): Boolean = seen?.let { state.ordinal < it.ordinal } ?: false

    /** Lines said so far (what the player's progress remembers). */
    val playedLines: Set<String> get() = played

    /** Puts back what the player had heard before the world closed. */
    fun restore(lines: Collection<String>) {
        played += lines
    }

    /** Nothing plays before [untilTick] (the player has just left the terminal: a beat before Rowan speaks). */
    fun holdUntil(untilTick: Long) {
        busyUntil = maxOf(busyUntil, untilTick)
    }

    /** The player reached [state] at tick [now]. */
    fun onState(state: Ch2L1State, now: Long) {
        seen = state
        if (state.ordinal >= Ch2L1Speakers.CANCELLED_FROM.ordinal) {
            cancelled = true
            due.clear()
            return
        }
        cancelled = false
        for (fixed in data.fixed) {
            if (fixed.after == state && fixed.line !in played && due.none { it.line == fixed.line }) due += Due(fixed.line, now + fixed.delayTicks)
        }
        // A player put past the opening's state (by a command, a skip) has not heard the opening line and never will: it counts as said.
        val first = data.fixed.firstOrNull()
        if (first != null && state.ordinal > first.after.ordinal && first.line !in played) {
            played += first.line
            due.removeAll { it.line == first.line }
        }
        val inAct1 = state.ordinal >= Ch2L1State.ACT1_TASK1.ordinal
        if (inAct1 && !poolOpen && openingLinePlayed()) openPool(now)
    }

    /** The line to start now, or null. [inZone]: the player stands where a speaker can be heard. */
    fun tick(now: Long, inZone: Boolean): String? {
        if (cancelled || !inZone || now < busyUntil) return null
        val fixed = due.filter { it.at <= now }.minByOrNull { it.at }
        if (fixed != null) {
            due.remove(fixed)
            return start(fixed.line, now)
        }
        if (!poolOpen && openingLinePlayed() && due.isEmpty()) openPool(now)
        if (!poolOpen || now < nextPoolAt) return null
        val left = data.pool.filter { it !in played }
        if (left.isEmpty()) return null
        return start(left[random.nextInt(left.size)], now)
    }

    private fun openingLinePlayed(): Boolean = data.fixed.firstOrNull()?.line?.let { it in played } ?: true

    private fun openPool(now: Long) {
        poolOpen = true
        nextPoolAt = now + cooldown()
    }

    private fun start(line: String, now: Long): String {
        played += line
        busyUntil = now + lineTicks(line)
        if (poolOpen || line == data.fixed.firstOrNull()?.line) nextPoolAt = busyUntil + cooldown()
        if (!poolOpen && line == data.fixed.firstOrNull()?.line) poolOpen = true
        return line
    }

    private fun cooldown(): Long =
        (data.cooldownMinTicks + if (data.cooldownMaxTicks > data.cooldownMinTicks) random.nextInt(data.cooldownMaxTicks - data.cooldownMinTicks + 1) else 0).toLong()
}
