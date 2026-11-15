package daio7771.everytunnel.chapter2.level1.core

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import daio7771.everytunnel.core.level.Position
import daio7771.everytunnel.util.JsonFields
import kotlin.math.sqrt

/** How the coworker takes the player to the lockers (`cutscenes/meeting.json`'s `follow`). */
data class Ch2L1FollowSpec(
    /** Where he waits during the third task (a point anchor), and the way he walks to the lockers (a path anchor, from there). */
    val waitAnchor: String = "coworker_wait",
    val route: String = "coworker_route",
    /** What he says when the last spot is swept, and what he says to a player who lags behind (in turns, never one twice running). */
    val openingLine: String = "follow_01",
    val reminders: List<String> = listOf("follow_02", "follow_03"),
    /** Farther than this from him (and not ahead of him), the player lags: he stops, turns and waits. */
    val lagBlocks: Double = 6.0,
    /** Lagging this long, he says a reminder; then not again for the cooldown. */
    val remindAfterTicks: Int = 240,
    val remindCooldownTicks: Int = 400,
    /** The guide arrow shows over him only beyond this, or when the player cannot see him. */
    val arrowBeyond: Double = 10.0,
    /** How fast he walks, a multiple of the workers' stroll (1.3 blocks a second): a brisk walk, he is in a hurry. */
    val pace: Double = 2.2,
)

/** The meeting in the lockers (`cutscenes/meeting.json`'s `meeting`). */
data class Ch2L1MeetingSpec(
    /** Entering this zone with him there (or close behind) begins it. */
    val zone: String = "taquillas_trigger",
    /** The group of `npcs.json` that holds it with him, and the places of him and the player (point anchors). */
    val group: String = "taquillas",
    val coworkerSpot: String = "meeting_coworker",
    val playerSpot: String = "meeting_player",
    /** The lockers' doors: a spot just outside each (its leaves are found round it); `<door>_inside` is where someone closes it from. */
    val doors: List<String> = listOf("lockers_door_1", "lockers_door_2"),
    /** The voice lines, in order. */
    val lines: List<String> = DEFAULT_LINES,
    /** The pause after each line, and how long the eyes take to turn from one speaker to the next. */
    val gapTicks: Int = 10,
    val turnTicks: Int = 12,
    val flyInTicks: Int = 20,
    val flyOutTicks: Int = 20,
    /** After this line someone closes the door the player came in by, while the eyes watch it for [doorTicks]. */
    val doorAfter: String = "meeting_01",
    val doorTicks: Int = 70,
    /** The coworker hands the badge over as this line reaches this sentence ("Take this."). */
    val cardLine: String = "meeting_12",
    val cardSentence: Int = 1,
    /** worker_a trembles in these lines; worker_b shakes laughing in these. */
    val trembleLines: List<String> = listOf("meeting_03_a"),
    val laughLines: List<String> = listOf("meeting_04_b"),
    /** Nervous: every so often each listener glances at the door for a moment. */
    val glanceEveryTicks: Ch2L1Span = Ch2L1Span.seconds(6.0, 12.0),
    val glanceTicks: Ch2L1Span = Ch2L1Span.seconds(1.0, 1.8),
    /** The members (0-based, in the group's order) who listen with their arms crossed. */
    val crossedArms: List<Int> = listOf(2, 5),
    /** The way everyone leaves the lockers after it, one by one, the coworker first; and where a skip puts the player. */
    val leavePath: String = "meeting_leave",
    val leaveGapTicks: Ch2L1Span = Ch2L1Span.seconds(1.5, 3.0),
    /** How fast they walk out, a multiple of the workers' stroll. */
    val leavePace: Double = 1.8,
    val exitAnchor: String = "taquillas_exit",
) {
    companion object {
        val DEFAULT_LINES = listOf(
            "meeting_01", "meeting_02", "meeting_03_a", "meeting_04_b", "meeting_05", "meeting_06_a", "meeting_07", "meeting_08_b",
            "meeting_09", "meeting_10_a", "meeting_11", "meeting_12", "meeting_13", "meeting_14_b", "meeting_15",
        )
    }
}

/** Act 2 of Chapter 2's Level 1: following the coworker and the meeting, from `cutscenes/meeting.json`. */
data class Ch2L1Act2(val follow: Ch2L1FollowSpec = Ch2L1FollowSpec(), val meeting: Ch2L1MeetingSpec = Ch2L1MeetingSpec()) {
    companion object {
        val DEFAULT = Ch2L1Act2()
    }
}

/**
 * Reads `cutscenes/meeting.json` (every key optional; seconds as numbers or `[min, max]` where a span is meant):
 *
 * ```json
 * {
 *   "follow": { "wait": "coworker_wait", "route": "coworker_route", "opening_line": "follow_01", "reminders": ["follow_02", "follow_03"],
 *               "lag_blocks": 6, "remind_after_seconds": 12, "remind_cooldown_seconds": 20, "arrow_beyond_blocks": 10, "pace": 2.2 },
 *   "meeting": { "zone": "taquillas_trigger", "group": "taquillas", "coworker": "meeting_coworker", "player": "meeting_player",
 *                "doors": ["lockers_door_1", "lockers_door_2"], "lines": ["meeting_01", "..."], "gap_seconds": 0.5, "turn_seconds": 0.6,
 *                "fly_in_seconds": 1, "fly_out_seconds": 1, "door_after": "meeting_01", "door_seconds": 3.5,
 *                "card_line": "meeting_12", "card_sentence": 1, "tremble_lines": ["meeting_03_a"], "laugh_lines": ["meeting_04_b"],
 *                "glance_every_seconds": [6, 12], "glance_seconds": [1, 1.8], "crossed_arms": [2, 5],
 *                "leave_path": "meeting_leave", "leave_gap_seconds": [1.5, 3], "leave_pace": 1.8, "exit": "taquillas_exit" }
 * }
 * ```
 */
object Ch2L1Act2Parser {

    data class Result(val act2: Ch2L1Act2, val problems: List<String>)

    fun parse(root: JsonObject): Result {
        val fields = JsonFields(root, "")
        val f = fields.child("follow")
        val fd = Ch2L1FollowSpec()
        val follow = Ch2L1FollowSpec(
            waitAnchor = f.string("wait", fd.waitAnchor),
            route = f.string("route", fd.route),
            openingLine = f.string("opening_line", fd.openingLine),
            reminders = if (f.has("reminders")) f.stringList("reminders") else fd.reminders,
            lagBlocks = f.double("lag_blocks", fd.lagBlocks, min = 1.0, max = 64.0),
            remindAfterTicks = ticks(f.double("remind_after_seconds", fd.remindAfterTicks / 20.0, min = 0.0, max = 600.0)),
            remindCooldownTicks = ticks(f.double("remind_cooldown_seconds", fd.remindCooldownTicks / 20.0, min = 0.0, max = 600.0)),
            arrowBeyond = f.double("arrow_beyond_blocks", fd.arrowBeyond, min = 0.0, max = 128.0),
            pace = f.double("pace", fd.pace, min = 0.3, max = 4.0),
        )
        f.reportUnknownKeys()
        val m = fields.child("meeting")
        val md = Ch2L1MeetingSpec()
        val meeting = Ch2L1MeetingSpec(
            zone = m.string("zone", md.zone),
            group = m.string("group", md.group),
            coworkerSpot = m.string("coworker", md.coworkerSpot),
            playerSpot = m.string("player", md.playerSpot),
            doors = if (m.has("doors")) m.stringList("doors") else md.doors,
            lines = if (m.has("lines")) m.stringList("lines") else md.lines,
            gapTicks = ticks(m.double("gap_seconds", md.gapTicks / 20.0, min = 0.0, max = 10.0)),
            turnTicks = ticks(m.double("turn_seconds", md.turnTicks / 20.0, min = 0.05, max = 5.0)),
            flyInTicks = ticks(m.double("fly_in_seconds", md.flyInTicks / 20.0, min = 0.05, max = 10.0)),
            flyOutTicks = ticks(m.double("fly_out_seconds", md.flyOutTicks / 20.0, min = 0.05, max = 10.0)),
            doorAfter = m.string("door_after", md.doorAfter),
            doorTicks = ticks(m.double("door_seconds", md.doorTicks / 20.0, min = 0.5, max = 20.0)),
            cardLine = m.string("card_line", md.cardLine),
            cardSentence = m.int("card_sentence", md.cardSentence, min = 0, max = 20),
            trembleLines = if (m.has("tremble_lines")) m.stringList("tremble_lines") else md.trembleLines,
            laughLines = if (m.has("laugh_lines")) m.stringList("laugh_lines") else md.laughLines,
            glanceEveryTicks = span(m, "glance_every_seconds", md.glanceEveryTicks),
            glanceTicks = span(m, "glance_seconds", md.glanceTicks),
            crossedArms = if (m.has("crossed_arms")) ints(m, "crossed_arms") else md.crossedArms,
            leavePath = m.string("leave_path", md.leavePath),
            leaveGapTicks = span(m, "leave_gap_seconds", md.leaveGapTicks),
            leavePace = m.double("leave_pace", md.leavePace, min = 0.3, max = 4.0),
            exitAnchor = m.string("exit", md.exitAnchor),
        )
        m.reportUnknownKeys()
        fields.reportUnknownKeys()
        if (meeting.lines.isEmpty()) fields.problems += "meeting.lines: the meeting has no lines"
        if (meeting.doors.isEmpty()) fields.problems += "meeting.doors: the lockers have no doors"
        return Result(Ch2L1Act2(follow, meeting), fields.problems.toList())
    }

    private fun ticks(seconds: Double): Int = (seconds * 20).toInt()

    private fun span(fields: JsonFields, key: String, default: Ch2L1Span): Ch2L1Span {
        if (!fields.has(key)) return default
        val array = fields.array(key)
        val numbers = array?.map { number(it) }
        val min = numbers?.getOrNull(0)
        val max = numbers?.getOrNull(1)
        if (numbers?.size != 2 || min == null || max == null || min < 0 || max < min) {
            fields.problems += "${fields.pathOf(key)}: expected [min, max] seconds, using [${default.min / 20.0}, ${default.max / 20.0}]"
            return default
        }
        return Ch2L1Span.seconds(min, max)
    }

    private fun ints(fields: JsonFields, key: String): List<Int> =
        fields.array(key)?.mapNotNull { e -> number(e)?.toInt().also { if (it == null) fields.problems += "${fields.pathOf(key)}: $e is not a number, ignored" } }
            ?: emptyList()

    private fun number(e: JsonElement): Double? = if (e.isJsonPrimitive && e.asJsonPrimitive.isNumber) e.asDouble else null
}

/**
 * The coworker leading the player to the lockers, one tick at a time. Plain Kotlin: told where he and the player are, it says what he
 * does ([Step]): he waits while he says his first line ([openingTicks]), then walks the route; when the player lags (farther than
 * `lag_blocks` and not ahead of him) he stops and waits for them, facing them, and after `remind_after_seconds` of it says a reminder,
 * the reminders in turns (never the same twice running) and not again for `remind_cooldown_seconds`. At the end of the route he has
 * [Command.Arrived].
 */
class Ch2L1Follow(private val spec: Ch2L1FollowSpec, private val route: List<Position>, openingTicks: Int) {

    sealed interface Command {
        /** Walk to ([x], [z]); [last]: he stops there. */
        data class Walk(val x: Double, val y: Double, val z: Double, val last: Boolean) : Command

        /** Stand and face the player. */
        data object Wait : Command

        /** At the end of the route. */
        data object Arrived : Command
    }

    /** What he does this tick, and the line he says now (null: none). */
    data class Step(val command: Command, val say: String? = null)

    private var point = if (route.size > 1) 1 else route.size
    private var opening = openingTicks
    private var lagging = 0
    private var cooldown = 0
    private var reminder = -1

    /** True once he has reached the end of the route. */
    val arrived: Boolean
        get() = point >= route.size

    fun tick(coworker: Position, player: Position?): Step {
        if (cooldown > 0) cooldown--
        if (opening > 0) {
            opening--
            return Step(Command.Wait)
        }
        while (point < route.size && flat(coworker, route[point]) < ARRIVE) point++
        if (point >= route.size) return Step(Command.Arrived)
        if (player != null && !lags(coworker, player)) {
            lagging = 0
            val target = route[point]
            return Step(Command.Walk(target.x, target.y, target.z, point == route.size - 1))
        }
        lagging++
        if (lagging >= spec.remindAfterTicks && cooldown == 0 && spec.reminders.isNotEmpty()) {
            lagging = 0
            cooldown = spec.remindCooldownTicks
            reminder = (reminder + 1) % spec.reminders.size
            return Step(Command.Wait, spec.reminders[reminder])
        }
        return Step(Command.Wait)
    }

    /** The player is too far behind: farther than `lag_blocks`, and not nearer the end of the route than he is. */
    private fun lags(coworker: Position, player: Position): Boolean {
        if (flat(coworker, player) <= spec.lagBlocks) return false
        val end = route.last()
        return flat(player, end) >= flat(coworker, end)
    }

    companion object {
        /** Within this of a point of the route (along the floor) he is at it. */
        const val ARRIVE = 0.4

        fun flat(a: Position, b: Position): Double = sqrt((a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z))
    }
}
