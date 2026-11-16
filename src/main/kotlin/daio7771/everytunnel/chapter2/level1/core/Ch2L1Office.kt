package daio7771.everytunnel.chapter2.level1.core

import com.google.gson.JsonObject
import daio7771.everytunnel.chapter2.level1.core.Ch2L1IntroScene.toLocal
import daio7771.everytunnel.core.cutscene.Cutscene
import daio7771.everytunnel.core.cutscene.Local
import daio7771.everytunnel.core.cutscene.Look
import daio7771.everytunnel.core.cutscene.SceneFrame
import daio7771.everytunnel.core.cutscene.Shot
import daio7771.everytunnel.core.cutscene.TimedLine
import daio7771.everytunnel.core.cutscene.Timeline
import daio7771.everytunnel.core.cutscene.VoiceLine
import daio7771.everytunnel.core.level.Position
import daio7771.everytunnel.util.JsonFields
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * The office's cinematic (Act 3, phase 8), from `cutscenes/office.json` (every key optional; seconds where the spec has ticks). The
 * player is held behind Rowan's desk ([playerMark], facing the door); Rowan comes in from [spawn] through [doorway] to [threshold], shuts
 * the door, says his lines ([lines], in order) walking round the desk ([round]) to its corner ([lean]), slides the pass across the desk
 * ([cardFrom] to [cardTo]) as [cardLine] reaches [cardSentence], claps, and leaves by the doorway to [exit], the door left open.
 */
data class Ch2L1OfficeSpec(
    val playerMark: String = "office_player_mark",
    val door: String = "door_anchor",
    val spawn: String = "rowan_spawn",
    val doorway: String = "rowan_doorway",
    val threshold: String = "rowan_threshold",
    val round: List<String> = listOf("rowan_round_1", "rowan_round_2"),
    val lean: String = "rowan_lean",
    val exit: String = "rowan_exit",
    val cardFrom: String = "card_slide_start",
    val cardTo: String = "card_slide_end",
    val lines: List<String> = (1..8).map { "rowan_office_c$it" },
    /** Rowan's walk, blocks a second. */
    val walkSpeed: Double = 1.4,
    /** Before the scene, a player elsewhere in the office glides to the mark over this. */
    val glideTicks: Int = 20,
    val flyInTicks: Int = 10,
    val flyOutTicks: Int = 20,
    /** The eyes on the door before it opens, and Rowan's turn to shut it behind him. */
    val doorWaitTicks: Int = 24,
    val closeTicks: Int = 20,
    val gapTicks: Int = 10,
    /** Silences after a line (line id to ticks): he stops smiling after c3, waits for an answer after c5. */
    val pauses: Map<String, Int> = mapOf("rowan_office_c3" to 20, "rowan_office_c5" to 50),
    val walkLine: String = "rowan_office_c2",
    val palmsLine: String = "rowan_office_c3",
    val palmsSentence: Int = 1,
    val leanLine: String = "rowan_office_c4",
    val glanceLine: String = "rowan_office_c5",
    val glanceTicks: Int = 24,
    val tieLine: String = "rowan_office_c6",
    val pointLine: String = "rowan_office_c7",
    val cardLine: String = "rowan_office_c8",
    val cardSentence: Int = 1,
    val slideTicks: Int = 16,
    val clapSentence: Int = 2,
    val clapTicks: Int = 40,
    /** The camera shakes a little through [walkLine]; it pushes [push] of the way to his face through [leanLine] and back through the next. */
    val shake: Double = 0.3,
    val push: Double = 0.3,
    /** The low hum: in from the start of this line, out at the start of that one. */
    val humFrom: String = "rowan_office_c3",
    val humUntil: String = "rowan_office_c8",
) {
    companion object {
        val DEFAULT = Ch2L1OfficeSpec()
    }
}

object Ch2L1OfficeParser {

    data class Result(val spec: Ch2L1OfficeSpec, val problems: List<String>)

    fun parse(root: JsonObject): Result {
        val f = JsonFields(root, "")
        val d = Ch2L1OfficeSpec.DEFAULT
        fun ticks(key: String, default: Int, max: Double = 60.0) = (f.double(key, default / 20.0, min = 0.0, max = max) * 20).toInt()
        val pauses = if (!f.has("pause_seconds")) d.pauses else {
            val p = f.child("pause_seconds")
            val out = linkedMapOf<String, Int>()
            for (key in (root.getAsJsonObject("pause_seconds")?.keySet() ?: emptySet())) out[key] = (p.double(key, 0.0, min = 0.0, max = 30.0) * 20).toInt()
            out
        }
        val spec = Ch2L1OfficeSpec(
            playerMark = f.string("player_mark", d.playerMark),
            door = f.string("door", d.door),
            spawn = f.string("spawn", d.spawn),
            doorway = f.string("doorway", d.doorway),
            threshold = f.string("threshold", d.threshold),
            round = if (f.has("round")) f.stringList("round") else d.round,
            lean = f.string("lean", d.lean),
            exit = f.string("exit", d.exit),
            cardFrom = f.string("card_from", d.cardFrom),
            cardTo = f.string("card_to", d.cardTo),
            lines = if (f.has("lines")) f.stringList("lines") else d.lines,
            walkSpeed = f.double("walk_speed", d.walkSpeed, min = 0.3, max = 6.0),
            glideTicks = ticks("glide_seconds", d.glideTicks, 5.0),
            flyInTicks = maxOf(1, ticks("fly_in_seconds", d.flyInTicks, 5.0)),
            flyOutTicks = maxOf(1, ticks("fly_out_seconds", d.flyOutTicks, 5.0)),
            doorWaitTicks = ticks("door_wait_seconds", d.doorWaitTicks, 10.0),
            closeTicks = maxOf(1, ticks("close_seconds", d.closeTicks, 10.0)),
            gapTicks = ticks("gap_seconds", d.gapTicks, 10.0),
            pauses = pauses,
            walkLine = f.string("walk_line", d.walkLine),
            palmsLine = f.string("palms_line", d.palmsLine),
            palmsSentence = f.int("palms_sentence", d.palmsSentence, min = 0, max = 20),
            leanLine = f.string("lean_line", d.leanLine),
            glanceLine = f.string("glance_line", d.glanceLine),
            glanceTicks = ticks("glance_seconds", d.glanceTicks, 10.0),
            tieLine = f.string("tie_line", d.tieLine),
            pointLine = f.string("point_line", d.pointLine),
            cardLine = f.string("card_line", d.cardLine),
            cardSentence = f.int("card_sentence", d.cardSentence, min = 0, max = 20),
            slideTicks = maxOf(1, ticks("slide_seconds", d.slideTicks, 5.0)),
            clapSentence = f.int("clap_sentence", d.clapSentence, min = 0, max = 20),
            clapTicks = ticks("clap_seconds", d.clapTicks, 10.0),
            shake = f.double("shake", d.shake, min = 0.0, max = 1.0),
            push = f.double("push", d.push, min = 0.0, max = 0.8),
            humFrom = f.string("hum_from", d.humFrom),
            humUntil = f.string("hum_until", d.humUntil),
        )
        f.reportUnknownKeys()
        val problems = f.problems.toMutableList()
        for ((key, name) in listOf(spec.cardLine to "card_line", spec.humFrom to "hum_from", spec.humUntil to "hum_until")) {
            if (key !in spec.lines) problems += "$name: \"$key\" is not one of the lines"
        }
        return Result(spec, problems)
    }
}

/**
 * The office's cinematic as a scene: the camera is the player's own eyes, held behind the desk, following Rowan as he comes in, goes
 * round, leans on the desk and leaves; it shakes a little while he walks round them and pushes in to his face as he leans forward. Rowan's
 * every move is laid out here tick by tick ([Segment]: from a place to another, or still, in a pose, looking at the player or at the
 * door), so that the server moves him on the same ticks the client's camera follows him. The cutscene machinery of the other scenes
 * ([Cutscene], [Timeline]); plain Kotlin, unit tested.
 */
object Ch2L1OfficeScene {

    const val EYE = 1.62
    const val HOLD_TICKS = 12

    data class Beat(val line: Ch2L1VoiceLine, val start: Int, val end: Int)

    /** Rowan from [start] to [end]: from [from] to [to] (the same place: he stands), in [pose], looking at the door or not. */
    data class Segment(val start: Int, val end: Int, val from: Position, val to: Position, val pose: Ch2L1Pose, val lookDoor: Boolean = false) {
        val moving: Boolean get() = from != to

        fun at(tick: Double): Position {
            val t = if (end <= start) 1.0 else ((tick - start) / (end - start)).coerceIn(0.0, 1.0)
            return Position(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t, from.z + (to.z - from.z) * t)
        }
    }

    class Built(
        val timeline: Timeline,
        val beats: List<Beat>,
        val track: List<Segment>,
        /** Ticks: the door opens for him, shuts behind him, opens again as he leaves. */
        val doorOpens: Int,
        val doorCloses: Int,
        val doorReopens: Int,
        /** The pass slides across the desk from [slideStart] to [slideEnd] (then it is the player's). */
        val slideStart: Int,
        val slideEnd: Int,
        val humStart: Int,
        val humEnd: Int,
        val shake: IntRange,
        val shakeAmount: Double,
        /** Rowan is through the door on his way out: the camera flies back. */
        val linesEnd: Int,
        private val poses: List<Pair<IntRange, Ch2L1Pose>>,
    ) {
        val totalTicks: Int get() = timeline.cutscene.totalTicks

        fun segmentAt(tick: Double): Segment = track.lastOrNull { tick >= it.start } ?: track.first()

        /** Where Rowan's feet are at [tick]. */
        fun rowanAt(tick: Double): Position = segmentAt(tick).at(tick)

        /** His pose at [tick]: a moment's (open palms, clapping) over his segment's. */
        fun poseAt(tick: Double): Ch2L1Pose = poses.lastOrNull { tick.toInt() in it.first }?.second ?: segmentAt(tick).pose

        fun beatAt(tick: Double): Beat? = beats.firstOrNull { tick >= it.start && tick < it.end }

        fun subtitleBeatAt(tick: Double): Beat? = beats.lastOrNull { tick >= it.start && tick < it.end + HOLD_TICKS }
    }

    data class Result(val built: Built?, val problem: String?)

    fun build(spec: Ch2L1OfficeSpec, anchors: Ch2L1Anchors, voices: Ch2L1Voices, frame: SceneFrame): Result {
        fun place(id: String): Position? = anchors.point(id)?.position
        val spawn = place(spec.spawn) ?: return Result(null, "no anchor ${spec.spawn}")
        val doorway = place(spec.doorway) ?: return Result(null, "no anchor ${spec.doorway}")
        val threshold = place(spec.threshold) ?: return Result(null, "no anchor ${spec.threshold}")
        val round = spec.round.map { place(it) ?: return Result(null, "no anchor $it") }
        val lean = place(spec.lean) ?: return Result(null, "no anchor ${spec.lean}")
        val exit = place(spec.exit) ?: return Result(null, "no anchor ${spec.exit}")
        val lines = spec.lines.map { voices.line(it) ?: return Result(null, "no voice line $it") }

        val eyes = Local(0.0, 0.0, EYE)
        fun head(p: Position) = frame.toLocal(Position(p.x, p.y + EYE, p.z))
        fun walkTicks(a: Position, b: Position): Int {
            val d = sqrt((a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z))
            return maxOf(1, ceil(d / spec.walkSpeed * 20).toInt())
        }

        val track = mutableListOf<Segment>()
        val shots = mutableListOf<Shot>()
        val beats = mutableListOf<Beat>()
        val timed = mutableListOf<TimedLine>()
        val poses = mutableListOf<Pair<IntRange, Ch2L1Pose>>()
        var tick = spec.flyInTicks
        var at = spawn
        var camera = eyes

        /** Rowan stands (or walks to [to]) for [ticks]; the eyes follow his head, the camera going to [cameraTo]. */
        fun segment(ticks: Int, to: Position, pose: Ch2L1Pose, lookDoor: Boolean = false, cameraTo: Local = camera, look: Local? = null) {
            if (ticks <= 0) return
            track += Segment(tick, tick + ticks, at, to, pose, lookDoor)
            val lookFrom = look ?: head(at)
            val lookTo = look ?: head(to)
            shots += Shot(ticks, camera, cameraTo, Look.At(lookFrom), Look.At(lookTo))
            tick += ticks
            at = to
            camera = cameraTo
        }

        fun walk(to: Position, pose: Ch2L1Pose = Ch2L1Pose.NONE) = segment(walkTicks(at, to), to, pose)

        // Before: the eyes on the door; it opens, he comes in, turns and shuts it behind him.
        track += Segment(0, tick, spawn, spawn, Ch2L1Pose.NONE)
        val doorLook = head(doorway)
        segment(spec.doorWaitTicks, spawn, Ch2L1Pose.NONE, look = doorLook)
        val doorOpens = tick - spec.doorWaitTicks / 3
        walk(doorway)
        walk(threshold)
        segment(spec.closeTicks, threshold, Ch2L1Pose.NONE, lookDoor = true)
        val doorCloses = tick - spec.closeTicks / 3

        var slideStart = -1
        var humStart = -1
        var humEnd = -1
        var shake = IntRange.EMPTY
        for (line in lines) {
            val ticks = ceil(line.ms / 50.0).toInt()
            val start = tick
            beats += Beat(line, start, start + ticks)
            timed += TimedLine(VoiceLine(line.id, 0, line.ms, emptyList()), start, start + ticks)
            fun cueTick(sentence: Int) = start + ((line.cues.firstOrNull { it.firstSentence == sentence } ?: line.cues.last()).atMs / 50)
            if (line.id == spec.humFrom) humStart = start
            if (line.id == spec.humUntil) humEnd = start
            val pushed = Local(
                eyes.forward + (head(lean).forward - eyes.forward) * spec.push, eyes.right + (head(lean).right - eyes.right) * spec.push,
                eyes.up + (head(lean).up - eyes.up) * spec.push,
            )
            when (line.id) {
                spec.walkLine -> {
                    // Round the desk to the player's side, then to the desk's corner, along the line; the camera shakes a little.
                    val legs = round + lean
                    val lengths = legs.runningFold(at) { a, b -> b }.zipWithNext { a, b -> walkTicks(a, b) }
                    val total = lengths.sum()
                    shake = start until start + ticks
                    for ((i, to) in legs.withIndex()) {
                        val share = if (i == legs.lastIndex) start + ticks - tick else (ticks.toDouble() * lengths[i] / total).toInt()
                        segment(maxOf(1, share), to, Ch2L1Pose.CUFFS)
                    }
                }
                spec.palmsLine -> {
                    poses += (cueTick(spec.palmsSentence) until start + ticks) to Ch2L1Pose.PALMS
                    segment(ticks, at, Ch2L1Pose.LEANING)
                }
                spec.leanLine -> segment(ticks, at, Ch2L1Pose.LEAN_FORWARD, cameraTo = pushed)
                spec.glanceLine -> {
                    segment(minOf(spec.glanceTicks, ticks), at, Ch2L1Pose.NONE, lookDoor = true, cameraTo = eyes)
                    segment(ticks - minOf(spec.glanceTicks, ticks), at, Ch2L1Pose.NONE)
                }
                spec.tieLine -> segment(ticks, at, Ch2L1Pose.TIE)
                spec.pointLine -> segment(ticks, at, Ch2L1Pose.POINTING, lookDoor = false)
                spec.cardLine -> {
                    slideStart = cueTick(spec.cardSentence)
                    poses += (slideStart until slideStart + spec.slideTicks) to Ch2L1Pose.OFFERING
                    val clap = cueTick(spec.clapSentence)
                    poses += (clap until start + ticks + spec.clapTicks) to Ch2L1Pose.CLAPPING
                    segment(ticks + spec.clapTicks, at, Ch2L1Pose.NONE)
                }
                else -> segment(ticks, at, Ch2L1Pose.NONE)
            }
            val pause = spec.pauses[line.id] ?: 0
            segment(spec.gapTicks + pause, at, if (line.id == spec.palmsLine) Ch2L1Pose.LEANING else Ch2L1Pose.NONE)
        }
        if (slideStart < 0) return Result(null, "the card line ${spec.cardLine} is not in the scene")
        // He leaves the way he came, the door opening before him, and on down the corridor.
        walk(threshold)
        val doorReopens = tick - 6
        walk(doorway)
        walk(spawn)
        val linesEnd = tick
        val outside = Position(exit.x, exit.y, exit.z)
        track += Segment(tick, tick + walkTicks(spawn, outside) + spec.flyOutTicks + Cutscene.BANDS_TICKS, spawn, outside, Ch2L1Pose.NONE)
        val timeline = Timeline(Cutscene(shots, spec.flyInTicks, spec.flyOutTicks), timed, emptyList())
        return Result(
            Built(
                timeline, beats, track, doorOpens, doorCloses, doorReopens, slideStart, slideStart + spec.slideTicks,
                if (humStart < 0) Int.MAX_VALUE else humStart, if (humEnd < 0) Int.MAX_VALUE else humEnd, shake, spec.shake, linesEnd, poses,
            ),
            null,
        )
    }
}
