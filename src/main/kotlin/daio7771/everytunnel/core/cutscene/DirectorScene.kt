package daio7771.everytunnel.core.cutscene

import kotlin.math.ceil

/** A voice line placed in a scene: its voice from tick [start] to tick [end] (from the scene's start). */
data class TimedLine(val line: VoiceLine, val start: Int, val end: Int)

/** A subtitle on screen from tick [start] to tick [end], its text under the lang key [key]. */
data class Subtitle(val key: String, val start: Int, val end: Int)

/** A scene's camera ([cutscene]) with its voice [lines] and their [subtitles], all on the same ticks. */
class Timeline(val cutscene: Cutscene, val lines: List<TimedLine>, val subtitles: List<Subtitle>) {

    /** The subtitle on screen at [tick] (fractional), or null. */
    fun subtitleAt(tick: Double): Subtitle? = subtitles.firstOrNull { tick >= it.start && tick < it.end }
}

/**
 * The director's scene at the top of Level 5 (the boss's cinematic), shot by shot, from him: he sits in his
 * chair facing the door, his face [FACE] up from his floor. His lines ([DirectorLines]) set its pace: each
 * after the one before with a tired pause ([LINE_GAP], [PART_GAP] between parts, [MASK_PAUSE] while he takes
 * his mask off, before [MASK_LINE]), and each framing lasts until the pause after its last line, where it cuts.
 * P1 a wide shot from the door pushing in on him; P2 a close-up; P3 over his shoulder at the player by the
 * door, then the other way, him with his wall of monitors behind; P4 wide, as he stands and points. Plain
 * Kotlin, unit tested.
 */
object DirectorScene {

    /** His face, sitting. */
    private val FACE = Local(0.0, 0.0, 1.72)

    private const val FLY_IN = 3 * 20
    private const val FLY_OUT = 2 * 20

    /** From the first shot to his first word. */
    private const val LEAD_IN = 20
    private const val LINE_GAP = 16
    private const val PART_GAP = 30
    private const val MASK_PAUSE = 40
    const val MASK_LINE = "boss_06"

    /** From his last word to the camera flying back. */
    private const val TAIL = 30

    /** A line's last subtitle stays this long after the voice, unless the next line begins first. */
    private const val HOLD = 12

    /** A framing: the camera from [from] to [to] looking at [look], until the pause after line [until]. */
    private class Framing(val until: String, val from: Local, val to: Local, val look: Look)

    private val FRAMINGS = listOf(
        // P1: from just inside the door, pushing in slowly on him at his desk.
        Framing("boss_04", Local(13.0, 0.3, 1.65), Local(6.5, 0.6, 1.75), Look.At(FACE)),
        // P2: close on his face, over his desk.
        Framing("boss_10", Local(2.1, 0.7, 1.8), Local(1.7, 0.55, 1.78), Look.At(FACE)),
        // P3: over his right shoulder at the player by the door while he tells of the tests and the subjects;
        // then from the room's middle back at him, his wall of monitors behind him.
        Framing("boss_13", Local(-0.75, 0.55, 2.1), Local(-0.7, 0.45, 2.05), Look.Player),
        Framing("boss_16", Local(6.0, -2.6, 1.9), Local(5.4, -2.3, 1.9), Look.At(FACE)),
        // P4: wide from the corner, him and his desk.
        Framing("boss_20", Local(8.5, 3.2, 2.5), Local(8.0, 2.6, 2.4), Look.At(Local(0.0, 0.0, 1.3))),
    )

    val SCENE: Timeline by lazy { build(DirectorLines.load()) }

    fun ticks(ms: Int): Int = ceil(ms / 50.0).toInt()

    fun build(voice: DirectorLines): Timeline {
        val lines = voice.lines
        val timed = mutableListOf<TimedLine>()
        val cuts = mutableMapOf<String, Int>()
        var t = FLY_IN + LEAD_IN
        for ((i, line) in lines.withIndex()) {
            val end = t + ticks(line.ms)
            timed += TimedLine(line, t, end)
            val next = lines.getOrNull(i + 1)
            val gap = when {
                next == null -> TAIL
                next.id == MASK_LINE -> MASK_PAUSE
                next.part != line.part -> PART_GAP
                else -> LINE_GAP
            }
            cuts[line.id] = if (next == null) end + TAIL else end + gap / 2
            t = end + gap
        }
        var shotStart = FLY_IN
        val shots = FRAMINGS.map { f ->
            val end = cuts[f.until] ?: error("the director's lines have no ${f.until}")
            Shot(end - shotStart, f.from, f.to, f.look).also { shotStart = end }
        }
        val subtitles = mutableListOf<Subtitle>()
        for ((i, tl) in timed.withIndex()) {
            val nextStart = timed.getOrNull(i + 1)?.start ?: Int.MAX_VALUE
            for ((k, cue) in tl.line.cues.withIndex()) {
                val start = tl.start + cue.atMs / 50
                val end = tl.line.cues.getOrNull(k + 1)?.let { tl.start + it.atMs / 50 } ?: minOf(tl.end + HOLD, nextStart)
                subtitles += Subtitle(cue.key, start, end)
            }
        }
        return Timeline(Cutscene(shots, FLY_IN, FLY_OUT), timed, subtitles)
    }
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
