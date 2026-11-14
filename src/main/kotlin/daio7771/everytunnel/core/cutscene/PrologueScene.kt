package daio7771.everytunnel.core.cutscene

import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/** The stages of the scientist's change, in order (`platform.ScientistEntity` is drawn from them). */
object PrologueStages {
    const val WORKING = 0
    const val TREMBLING = 1

    /** Sick: his skin gone pale, his eyes milky, bent over his shaking hands. */
    const val SICK = 2

    /** Stretched: his arms and legs longer, the body drawn thin, the head thrown back. */
    const val STRETCHED = 3

    /** A Pale man, listening. */
    const val PALE = 4
}

/**
 * What happens in the prologue on its ticks beyond the camera and the voice: his hands begin to shake at
 * [trembleAt]; the lights go off in [darkBeats] (tick ranges, each beat a flicker, faster and faster); each long
 * beat of them hides a change of him: [sickAt], [stretchAt] and, in the last beat, [swapAt], when he is a Pale man;
 * at [cutAt] the screen goes black and every sound stops dead; the title is typed from [titleAt]. Plain Kotlin.
 */
data class PrologueCues(
    val trembleAt: Int,
    val sickAt: Int,
    val stretchAt: Int,
    val swapAt: Int,
    val darkBeats: List<IntRange>,
    val cutAt: Int,
    val titleAt: Int,
) {

    /** Are the ceiling's lights on at [tick]? */
    fun lightsOn(tick: Int): Boolean = darkBeats.none { tick in it }

    /** How far he has gone at [tick]: one of [PrologueStages]. */
    fun stageAt(tick: Int): Int = when {
        tick >= swapAt -> PrologueStages.PALE
        tick >= stretchAt -> PrologueStages.STRETCHED
        tick >= sickAt -> PrologueStages.SICK
        tick >= trembleAt -> PrologueStages.TREMBLING
        else -> PrologueStages.WORKING
    }

    /** The ticks he changes stage, with the stage he changes to. */
    val changes: List<Pair<Int, Int>>
        get() = listOf(
            trembleAt to PrologueStages.TREMBLING, sickAt to PrologueStages.SICK,
            stretchAt to PrologueStages.STRETCHED, swapAt to PrologueStages.PALE,
        )
}

/** The lights' beats of the flicker, and the ticks of the two changes of him they hide before the last. */
class Flicker(val beats: List<IntRange>, val sickAt: Int, val stretchAt: Int)

/** The prologue's [timeline] (camera, voice, subtitles, black, title) with its [cues]. */
class PrologueScenePlan(val timeline: Timeline, val cues: PrologueCues)

/**
 * The prologue of Chapter 2, shot by shot, from the scientist: he stands at his bench facing it (north, so his
 * right is east, +x), his back to the camera. It opens out of black on a slow tracking shot left to right along
 * the bench, past everything on it, which comes to a stop a few blocks behind and to the left of him, so that he
 * comes into the frame at the right as the camera arrives ([TRAVEL]); then the camera settles while its aim turns onto
 * his back, and from then on it only creeps in. Everything is given from him, so the shots fit wherever the room is
 * built ([RoomPlan]). Plain Kotlin, unit tested.
 *
 * The scientist's voice sets the pace ([build]): his log entry begins [LEAD] after the camera stops, each line after
 * the one before with a pause. After the second line a quiet [TURN_PAUSE]; then the third: his hands shake, and from
 * its second cue on the lights flicker, faster and faster, and each flash finds him more changed: at two of the
 * longer beats he goes through two stages ([PrologueStages.SICK], [PrologueStages.STRETCHED]), turning a little more
 * to the camera each time; right after the last word of the line the lights go out in a last beat and, in the dark,
 * he is a Pale man, turned to the camera, listening. The fourth line is his scream: it
 * begins a moment after the change and ends dry, and the screen goes black on its last sound, the lab's hum and the
 * drone cut with it. [SILENCE] of black; then "CHAPTER 2" is typed ([TitleCue]), a low hit on its first letter; it is
 * held and fades; the scene ends.
 *
 * The lab's sound rides on the same ticks: the hum is flat, the drone under it rises to the change ([droneLevel]) and
 * the radiation counter on the bench clicks faster and faster ([geigerRate]) from where it stands ([COUNTER]).
 */
object PrologueScene {

    /** The camera's height: a little over a man's eyes at the bench's top (1 block). */
    private const val CAMERA_UP = 1.5

    /** Where the camera looks while tracking: straight ahead, at the bench, a little down. */
    private const val AHEAD = 6.0
    private const val AIM_UP = 1.15

    /** How far behind him (south) the camera tracks, and from how far west of him to how far. */
    private const val BEHIND = -3.2
    private const val START_RIGHT = -10.0
    private const val STOP_RIGHT = -2.2

    /** His upper back, where the camera's aim ends up while he works; and his chest, once he is a Pale man. */
    private val HIS_BACK = Local(0.0, 0.15, 1.3)
    private val HIS_CHEST = Local(0.0, 0.1, 1.5)

    /** Where the camera settles (and creeps on from), and where it ends. */
    private val SETTLED = Local(BEHIND + 0.5, STOP_RIGHT + 0.6, CAMERA_UP + 0.05)
    private val CLOSEST = Local(BEHIND + 0.95, STOP_RIGHT + 0.95, CAMERA_UP + 0.05)

    /** The screen is black this long, then fades out over [FADE]: the room's chunks are still reaching the client. */
    private const val BLACK = 30
    private const val FADE = 50

    /** The tracking shot's length. */
    const val TRAVEL = 16 * 20

    /** From the camera's stop to his first word, and between his first lines. */
    const val LEAD = 30
    const val LINE_GAP = 24

    /** From the end of the second line to the third. */
    const val TURN_PAUSE = 60

    /** From the end of the third line (the change) to the fourth. */
    const val REVEAL_GAP = 6

    /** The lights go off for the change from this many ticks before it to this many after (the last beat of the flicker). */
    private const val CHANGE_BEFORE = 2
    private const val CHANGE_AFTER = 3

    /** The flicker: its first gap between beats, how it shortens, the shortest, and how long a beat is (2 or 3 ticks; 4 for one that hides a change). */
    private const val FIRST_GAP = 18
    private const val GAP_SHRINK = 0.72
    private const val SHORTEST_GAP = 4
    private const val BEAT = 2
    private const val CHANGE_BEAT = 4

    /** How far round to the camera he is turned at each stage (the bench is 0, the camera 1). */
    private const val TURNED_SICK = 0.3
    private const val TURNED_STRETCHED = 0.65

    /** From the cut to black to the title, then the title: typed, held, faded. */
    const val SILENCE = 40
    private const val TYPE = 40
    private const val TITLE_HOLD = 60
    private const val TITLE_FADE = 20
    const val TITLE_KEY = "title.every_tunnel.chapter_2"

    /** A line's last subtitle stays this long after the voice, unless the next line begins first. */
    private const val HOLD_SUBTITLE = 12

    /** The radiation counter's screen, from him (forward is towards his bench): where its clicks come from. Checked against the room's plan. */
    val COUNTER = Local(1.0, -3.0, 1.35)

    val PLAN: PrologueScenePlan by lazy { build(PrologueLines.load()) }

    val SCENE: Timeline get() = PLAN.timeline

    fun build(voice: PrologueLines): PrologueScenePlan {
        val timed = mutableListOf<TimedLine>()
        fun place(id: String, start: Int): TimedLine {
            val line = voice.line(id)
            return TimedLine(line, start, start + DirectorScene.ticks(line.ms)).also { timed += it }
        }
        val first = place("prologue_01", TRAVEL + LEAD)
        val second = place("prologue_02", first.end + LINE_GAP)
        val third = place("prologue_03", second.end + TURN_PAUSE)
        val fourth = place("prologue_04", third.end + REVEAL_GAP)
        val swapAt = third.end
        val cutAt = fourth.end
        val title = TitleCue(TITLE_KEY, cutAt + SILENCE, TYPE, TITLE_HOLD, TITLE_FADE)

        val subtitles = mutableListOf<Subtitle>()
        for ((i, tl) in timed.withIndex()) {
            val nextStart = timed.getOrNull(i + 1)?.start ?: Int.MAX_VALUE
            for ((k, cue) in tl.line.cues.withIndex()) {
                val start = tl.start + cue.atMs / 50
                val end = tl.line.cues.getOrNull(k + 1)?.let { tl.start + it.atMs / 50 } ?: minOf(tl.end + HOLD_SUBTITLE, nextStart)
                subtitles += Subtitle(cue.key, start, end)
            }
        }

        val travel = Shot(
            TRAVEL, Local(BEHIND, START_RIGHT, CAMERA_UP), Local(BEHIND, STOP_RIGHT, CAMERA_UP),
            Look.At(Local(BEHIND + AHEAD, START_RIGHT, AIM_UP)), Look.At(Local(BEHIND + AHEAD, STOP_RIGHT, AIM_UP)),
        )
        val settle = Shot(
            third.start - TRAVEL, Local(BEHIND, STOP_RIGHT, CAMERA_UP), SETTLED,
            Look.At(Local(BEHIND + AHEAD, STOP_RIGHT, AIM_UP)), Look.At(HIS_BACK),
        )
        val creep = Shot(title.end - third.start, SETTLED, CLOSEST, Look.At(HIS_BACK), Look.At(HIS_CHEST))
        val timeline = Timeline(
            Cutscene(listOf(travel, settle, creep), 0, 0), timed, subtitles,
            blackTicks = BLACK, fadeTicks = FADE, cutAt = cutAt, title = title,
        )
        val flicker = flicker(third.start + third.line.cues[1].atMs / 50, swapAt)
        val cues = PrologueCues(third.start, flicker.sickAt, flicker.stretchAt, swapAt, flicker.beats, cutAt, title.start)
        return PrologueScenePlan(timeline, cues)
    }

    /**
     * The lights' beats from [from] to the change at [swapAt]: shorter gaps each time, the last beat round the final change.
     * Two of the beats, a little under half way and three quarters of the way, are longer ([CHANGE_BEAT]) and hide
     * the first two changes, a tick into each.
     */
    fun flicker(from: Int, swapAt: Int): Flicker {
        var long = emptySet<Int>()
        var beats = beats(from, swapAt, long)
        repeat(4) {
            val n = beats.size
            val first = ((n - 1) * 0.4).roundToInt().coerceIn(0, max(0, n - 3))
            val second = ((n - 1) * 0.75).roundToInt().coerceIn(first + 1, max(first + 1, n - 2))
            val next = setOf(first, second)
            if (next == long) return@repeat
            long = next
            beats = beats(from, swapAt, long)
        }
        val (a, b) = long.sorted()
        return Flicker(beats, beats[a].first + 1, beats[b].first + 1)
    }

    private fun beats(from: Int, swapAt: Int, long: Set<Int>): List<IntRange> {
        val last = (swapAt - CHANGE_BEFORE)..(swapAt + CHANGE_AFTER)
        val beats = mutableListOf<IntRange>()
        var at = from
        var gap = FIRST_GAP
        var k = 0
        while (at + CHANGE_BEAT + 1 < last.first - 3) {
            val length = if (k in long) CHANGE_BEAT else BEAT + k % 2
            beats += at until at + length
            at += length + gap
            gap = max(SHORTEST_GAP, (gap * GAP_SHRINK).toInt())
            k++
        }
        beats += last
        return beats
    }

    /** Where he looks, once he is a Pale man: towards the camera where it settles, from [frame] (his feet). */
    fun facingCamera(frame: SceneFrame): Double {
        val (cx, _, cz) = frame.at(SETTLED.forward, SETTLED.right, SETTLED.up)
        return CameraPose.looking(frame.x, 0.0, frame.z, cx, 0.0, cz).yaw
    }

    /** Where he faces at [stage] (one of [PrologueStages]): from the bench round towards the camera, a part of the way at each. */
    fun facing(frame: SceneFrame, stage: Int): Double {
        val share = when (stage) {
            PrologueStages.SICK -> TURNED_SICK
            PrologueStages.STRETCHED -> TURNED_STRETCHED
            PrologueStages.PALE -> 1.0
            else -> 0.0
        }
        var round = (facingCamera(frame) - frame.yaw) % 360.0
        if (round > 180.0) round -= 360.0
        if (round < -180.0) round += 360.0
        return frame.yaw + round * share
    }

    /** The lab's hum (0..1): in over the first second, the black's, then flat, and dead at the cut. */
    fun humLevel(tick: Double, cutAt: Int): Double = if (tick >= cutAt) 0.0 else Cutscene.ease(tick / 20.0)

    /** The drone's level (0..1): none at first, a murmur once the picture is up, rising to the change, dead at the cut. */
    fun droneLevel(tick: Double, swapAt: Int, cutAt: Int): Double {
        if (tick >= cutAt) return 0.0
        val rise = (tick / swapAt).coerceIn(0.0, 1.0)
        return (0.10 + 0.90 * rise * rise) * Cutscene.ease(tick / 60.0)
    }

    /** How fast the radiation counter clicks at [tick] (counts a second): slowly at first, ever faster; none after the cut. */
    fun geigerRate(tick: Double, cutAt: Int): Double =
        if (tick >= cutAt) 0.0 else 1.2 + 9.0 * (tick / cutAt).coerceIn(0.0, 1.0).pow(1.6)
}
