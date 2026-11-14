package daio7771.everytunnel.core.cutscene

/**
 * The prologue of Chapter 2, shot by shot, from the scientist: he stands at his bench facing it (north, so his
 * right is east, +x), his back to the camera. It opens out of black on a slow tracking shot left to right along
 * the bench, past everything on it, which comes to a stop a few blocks behind and to the left of him, so that he
 * comes into the frame at the right as the camera arrives ([TRAVEL]); then the camera holds there, creeping in a
 * little while its aim turns onto his back ([HOLD]). Everything is given from him, so the shots fit wherever the
 * room is built ([RoomPlan]). Plain Kotlin, unit tested.
 *
 * Step 1 of the prologue: no voice yet; [build] takes how long the hold lasts, which the voice lines will set.
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

    /** The hold, for now: what is left once the voice lines set the pace. */
    const val HOLD = 8 * 20

    val SCENE: Timeline by lazy { build(HOLD) }

    fun build(holdTicks: Int): Timeline {
        val travel = Shot(
            TRAVEL, Local(BEHIND, START_RIGHT, CAMERA_UP), Local(BEHIND, STOP_RIGHT, CAMERA_UP),
            Look.At(Local(BEHIND + AHEAD, START_RIGHT, AIM_UP)), Look.At(Local(BEHIND + AHEAD, STOP_RIGHT, AIM_UP)),
        )
        val hold = Shot(
            holdTicks, Local(BEHIND, STOP_RIGHT, CAMERA_UP), Local(BEHIND + 0.5, STOP_RIGHT + 0.6, CAMERA_UP + 0.05),
            Look.At(Local(BEHIND + AHEAD, STOP_RIGHT, AIM_UP)), Look.At(HIS_BACK),
        )
        return Timeline(Cutscene(listOf(travel, hold), 0, 0), emptyList(), emptyList(), blackTicks = BLACK, fadeTicks = FADE)
    }
}
