package daio7771.everytunnel.core.cutscene

/**
 * The director's scene at the top of Level 5 (the boss's cinematic), shot by shot, from him: he sits in his
 * chair facing the door, his face [FACE] up from his floor. The parts follow his speech: P1 a wide shot from
 * the door pushing in on him; P2 a close-up, where he takes his mask off; P3 over his shoulder at the player
 * by the door, then the other way, him with his wall of monitors behind; P4 wide, as he stands and points.
 *
 * Every part's length is a stand-in until his voice gives it (a part lasts as long as its lines).
 */
object DirectorScene {

    /** His face, sitting. */
    private val FACE = Local(0.0, 0.0, 1.72)

    private const val P1_TICKS = 14 * 20
    private const val P2_TICKS = 16 * 20
    private const val P3_TICKS = 20 * 20
    private const val P4_TICKS = 12 * 20

    val SCENE = Cutscene(
        shots = listOf(
            // P1: from just inside the door, pushing in slowly on him at his desk.
            Shot(P1_TICKS, Local(13.0, 0.3, 1.65), Local(6.5, 0.6, 1.75), Look.At(FACE)),
            // P2: close on his face, over his desk.
            Shot(P2_TICKS, Local(2.1, 0.7, 1.8), Local(1.7, 0.55, 1.78), Look.At(FACE)),
            // P3: over his right shoulder at the player by the door; then from the room's middle back at him,
            // his wall of monitors behind him.
            Shot(P3_TICKS / 2, Local(-0.75, 0.55, 2.1), Local(-0.7, 0.45, 2.05), Look.Player),
            Shot(P3_TICKS / 2, Local(6.0, -2.6, 1.9), Local(5.4, -2.3, 1.9), Look.At(FACE)),
            // P4: wide from the corner, him and his desk.
            Shot(P4_TICKS, Local(8.5, 3.2, 2.5), Local(8.0, 2.6, 2.4), Look.At(Local(0.0, 0.0, 1.3))),
        ),
        flyInTicks = 3 * 20,
        flyOutTicks = 2 * 20,
    )
}

/**
 * Skipping a cinematic takes two presses: the first shows a notice for [windowMs], and a second press while
 * it shows skips. Plain Kotlin, unit tested.
 */
class SkipPrompt(private val windowMs: Long = 3000L) {

    private var shownAt = Long.MIN_VALUE

    /** A press at [nowMs]: true if it skips. */
    fun press(nowMs: Long): Boolean {
        if (showing(nowMs)) return true
        shownAt = nowMs
        return false
    }

    fun showing(nowMs: Long): Boolean = shownAt != Long.MIN_VALUE && nowMs - shownAt < windowMs

    /** The notice's opacity at [nowMs]: full, fading out over its last half second. */
    fun alpha(nowMs: Long): Float {
        if (!showing(nowMs)) return 0f
        val left = windowMs - (nowMs - shownAt)
        return (left / 500f).coerceIn(0f, 1f)
    }
}
