package daio7771.everytunnel.core.cutscene

import kotlin.math.pow

/**
 * The prologue of Chapter 2, shot by shot, from the scientist: he stands at his bench facing it (north, so his
 * right is east, +x), his back to the camera. It opens out of black on a slow tracking shot left to right along
 * the bench, past everything on it, which comes to a stop a few blocks behind and to the left of him, so that he
 * comes into the frame at the right as the camera arrives ([TRAVEL]); then the camera holds there, creeping in a
 * little while its aim turns onto his back (the hold). Everything is given from him, so the shots fit wherever the
 * room is built ([RoomPlan]). Plain Kotlin, unit tested.
 *
 * The scientist's voice sets the hold's length ([build]): his log entry begins [LEAD] after the camera stops, each
 * line after the one before with a pause, and the scene ends [TAIL] after the last. Subtitles come with each cue of
 * each line, never ahead of the voice. The lab's sound rides on the same ticks: its hum is flat, the drone
 * under it rises with the scene ([droneLevel]) and the radiation counter on the bench clicks faster and faster
 * ([geigerRate]) from where it stands ([COUNTER]).
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

    /** His upper back, where the camera's aim ends up. */
    private val HIS_BACK = Local(0.0, 0.15, 1.3)

    /** The screen is black this long, then fades out over [FADE]: the room's chunks are still reaching the client. */
    private const val BLACK = 30
    private const val FADE = 50

    /** The tracking shot's length. */
    const val TRAVEL = 16 * 20

    /** From the camera's stop to his first word, between his lines, and from his last word to the end. */
    const val LEAD = 30
    const val LINE_GAP = 24
    const val TAIL = 60

    /** A line's last subtitle stays this long after the voice, unless the next line begins first. */
    private const val HOLD_SUBTITLE = 12

    /** The lines he says so far (the log entry); the transformation's come with its own step. */
    val PLAYED = listOf("prologue_01", "prologue_02")

    /** The radiation counter's screen, from him (forward is towards his bench): where its clicks come from. Checked against the room's plan. */
    val COUNTER = Local(1.0, -3.0, 1.35)

    val SCENE: Timeline by lazy { build(PrologueLines.load()) }

    fun build(voice: PrologueLines): Timeline {
        var t = TRAVEL + LEAD
        val timed = mutableListOf<TimedLine>()
        for (id in PLAYED) {
            val line = voice.line(id)
            val end = t + DirectorScene.ticks(line.ms)
            timed += TimedLine(line, t, end)
            t = end + LINE_GAP
        }
        val subtitles = mutableListOf<Subtitle>()
        for ((i, tl) in timed.withIndex()) {
            val nextStart = timed.getOrNull(i + 1)?.start ?: Int.MAX_VALUE
            for ((k, cue) in tl.line.cues.withIndex()) {
                val start = tl.start + cue.atMs / 50
                val end = tl.line.cues.getOrNull(k + 1)?.let { tl.start + it.atMs / 50 } ?: minOf(tl.end + HOLD_SUBTITLE, nextStart)
                subtitles += Subtitle(cue.key, start, end)
            }
        }
        val holdTicks = timed.last().end + TAIL - TRAVEL
        val travel = Shot(
            TRAVEL, Local(BEHIND, START_RIGHT, CAMERA_UP), Local(BEHIND, STOP_RIGHT, CAMERA_UP),
            Look.At(Local(BEHIND + AHEAD, START_RIGHT, AIM_UP)), Look.At(Local(BEHIND + AHEAD, STOP_RIGHT, AIM_UP)),
        )
        val hold = Shot(
            holdTicks, Local(BEHIND, STOP_RIGHT, CAMERA_UP), Local(BEHIND + 0.5, STOP_RIGHT + 0.6, CAMERA_UP + 0.05),
            Look.At(Local(BEHIND + AHEAD, STOP_RIGHT, AIM_UP)), Look.At(HIS_BACK),
        )
        return Timeline(Cutscene(listOf(travel, hold), 0, 0), timed, subtitles, blackTicks = BLACK, fadeTicks = FADE)
    }

    /** The lab's hum (0..1): in over the first second, the black's, flat after, out over the last second. */
    fun humLevel(tick: Double, total: Int): Double = minOf(Cutscene.ease(tick / 20.0), Cutscene.ease((total - tick) / 20.0))

    /** The drone's level (0..1): none at first, a murmur once the picture is up, rising all through the scene. */
    fun droneLevel(tick: Double, total: Int): Double {
        val rise = (tick / total).coerceIn(0.0, 1.0)
        return (0.10 + 0.90 * rise * rise) * Cutscene.ease(tick / 60.0) * Cutscene.ease((total - tick) / 20.0)
    }

    /** How fast the radiation counter clicks at [tick] (counts a second): slowly at first, ever faster. */
    fun geigerRate(tick: Double, total: Int): Double = 1.2 + 9.0 * (tick / total).coerceIn(0.0, 1.0).pow(1.6)
}
