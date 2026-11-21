package daio7771.everytunnel.chapter2.level1.core

import daio7771.everytunnel.chapter2.level1.core.Ch2L1IntroScene.toLocal
import daio7771.everytunnel.common.core.cutscene.Cutscene
import daio7771.everytunnel.common.core.cutscene.Local
import daio7771.everytunnel.common.core.cutscene.Look
import daio7771.everytunnel.common.core.cutscene.SceneFrame
import daio7771.everytunnel.common.core.cutscene.Shot
import daio7771.everytunnel.common.core.cutscene.TimedLine
import daio7771.everytunnel.common.core.cutscene.Timeline
import daio7771.everytunnel.common.core.cutscene.VoiceLine
import daio7771.everytunnel.common.core.level.Position
import java.util.Random
import kotlin.math.ceil

/**
 * The meeting in the lockers (Act 2), as a scene: the player stands frozen in the semicircle facing the coworker, the black bands come in
 * and the camera is their own eyes, turning smoothly to whoever speaks (`meeting.json`'s lines in order, each speaker's place read from
 * the anchors: the coworker's spot, or the place of the group's member with that skin), and to the door while someone closes it after
 * the first line. It is the cutscene machinery of the other scenes ([Cutscene], [Timeline]; the client draws it with
 * `chapter2.level1.client.Ch2L1MeetingView`); the server reads the same [Built] for what happens when: who speaks ([beatAt]), when the
 * door closes, when the badge is handed over. Plain Kotlin, unit tested.
 */
object Ch2L1MeetingScene {

    /** A standing person's eyes over their feet. */
    const val EYE = 1.62

    /** The subtitle of a line stays this long after its voice ends. */
    const val HOLD_TICKS = 12

    /** One line of the meeting: its voice, who says it (a skin: `coworker`, `worker_a`, `worker_b`) and its ticks. */
    data class Beat(val line: Ch2L1VoiceLine, val speaker: String, val start: Int, val end: Int)

    class Built(
        val timeline: Timeline,
        val beats: List<Beat>,
        /** The door shot: the eyes are on the door from [doorStart] to [doorEnd]; it shuts at [doorCloses]. */
        val doorStart: Int,
        val doorEnd: Int,
        val doorCloses: Int,
        /** The tick the coworker hands the badge over. */
        val cardTick: Int,
        /** The last line has ended: the eyes go back, the bands go, everyone starts to leave. */
        val linesEnd: Int,
    ) {
        /** The line being said at [tick], or null. */
        fun beatAt(tick: Double): Beat? = beats.firstOrNull { tick >= it.start && tick < it.end }

        /** The line whose subtitle is on screen at [tick] (it stays [HOLD_TICKS] after the voice), or null. */
        fun subtitleBeatAt(tick: Double): Beat? = beats.lastOrNull { tick >= it.start && tick < it.end + HOLD_TICKS }
    }

    data class Result(val built: Built?, val problem: String?)

    /** Where each speaker's eyes are: the coworker at his spot, the others at the place of the group's member with their skin. */
    fun speakerPlaces(spec: Ch2L1MeetingSpec, anchors: Ch2L1Anchors, members: List<Ch2L1GroupMember>): Map<String, Position> {
        val out = linkedMapOf<String, Position>()
        anchors.point(spec.coworkerSpot)?.let { out[Ch2L1Skins.COWORKER] = it.position }
        for (member in members) {
            if (member.skin in out) continue
            anchors.point(member.at)?.let { out[member.skin] = it.position }
        }
        return out
    }

    /**
     * The scene for [spec] seen from [frame] (where the player stands, facing the coworker), the door they came in by being [door]
     * (an index into the spec's doors).
     */
    fun build(
        spec: Ch2L1MeetingSpec, anchors: Ch2L1Anchors, voices: Ch2L1Voices, members: List<Ch2L1GroupMember>, frame: SceneFrame, door: Int,
    ): Result {
        val places = speakerPlaces(spec, anchors, members)
        val doorAnchor = spec.doors.getOrNull(door)?.let { anchors.point(it) } ?: return Result(null, "no door ${spec.doors.getOrNull(door) ?: door}")
        val eyes = Local(0.0, 0.0, EYE)
        fun eyesOf(p: Position) = frame.toLocal(Position(p.x, p.y + EYE, p.z))
        val doorLook = frame.toLocal(Position(doorAnchor.position.x, doorAnchor.position.y + 1.3, doorAnchor.position.z))
        val shots = mutableListOf<Shot>()
        val beats = mutableListOf<Beat>()
        val lines = mutableListOf<TimedLine>()
        var tick = spec.flyInTicks
        var looking: Local? = null
        var doorStart = -1
        var doorEnd = -1
        var cardTick = -1
        fun turnTo(to: Local) {
            val from = looking
            if (from != null && from != to) {
                shots += Shot(spec.turnTicks, eyes, eyes, Look.At(from), Look.At(to))
                tick += spec.turnTicks
            }
            looking = to
        }
        for (id in spec.lines) {
            val line = voices.line(id) ?: return Result(null, "no voice line $id")
            val speaker = line.speakerKey.substringAfterLast('.')
            val place = places[speaker] ?: return Result(null, "no place for $speaker (the speaker of $id)")
            turnTo(eyesOf(place))
            val ticks = ceil(line.ms / 50.0).toInt()
            shots += Shot(ticks + spec.gapTicks, eyes, eyes, Look.At(looking!!))
            beats += Beat(line, speaker, tick, tick + ticks)
            lines += TimedLine(VoiceLine(id, 0, line.ms, emptyList()), tick, tick + ticks)
            if (id == spec.cardLine) {
                val cue = line.cues.firstOrNull { it.firstSentence == spec.cardSentence } ?: line.cues.firstOrNull()
                cardTick = tick + (cue?.atMs ?: 0) / 50
            }
            tick += ticks + spec.gapTicks
            if (id == spec.doorAfter) {
                turnTo(doorLook)
                doorStart = tick
                shots += Shot(spec.doorTicks, eyes, eyes, Look.At(doorLook))
                tick += spec.doorTicks
                doorEnd = tick
            }
        }
        if (cardTick < 0) return Result(null, "the card line ${spec.cardLine} is not in the meeting")
        val linesEnd = tick
        val timeline = Timeline(Cutscene(shots, spec.flyInTicks, spec.flyOutTicks), lines, emptyList())
        val doorCloses = if (doorStart < 0) -1 else doorStart + spec.doorTicks * 2 / 3
        return Result(Built(timeline, beats, doorStart, doorEnd, doorCloses, cardTick, linesEnd), null)
    }
}

/**
 * The listeners' nerves in the meeting: each member of the group glances at the door now and then (every `glance_every_seconds`, for
 * `glance_seconds`), each on their own times, the same in every game ([seed]). Plain Kotlin.
 */
class Ch2L1Glances(spec: Ch2L1MeetingSpec, members: Int, lengthTicks: Int, seed: Long) {

    private val glances: List<List<IntRange>> = List(members) { i ->
        val random = Random(seed * 31 + i)
        val out = mutableListOf<IntRange>()
        var at = spec.glanceEveryTicks.pick(random) / 2 + random.nextInt(40)
        while (at < lengthTicks) {
            val length = spec.glanceTicks.pick(random).coerceAtLeast(1)
            out += at until at + length
            at += length + spec.glanceEveryTicks.pick(random).coerceAtLeast(1)
        }
        out
    }

    /** True if member [index] is glancing at the door at [tick]. */
    fun glancing(index: Int, tick: Int): Boolean = glances.getOrNull(index)?.any { tick in it } == true

    /** Member [index]'s glances, for the tests. */
    fun of(index: Int): List<IntRange> = glances.getOrElse(index) { emptyList() }
}
