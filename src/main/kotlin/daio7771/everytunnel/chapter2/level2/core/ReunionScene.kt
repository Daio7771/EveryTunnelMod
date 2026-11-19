package daio7771.everytunnel.chapter2.level2.core

import daio7771.everytunnel.chapter2.core.toLocal
import daio7771.everytunnel.core.cutscene.Cutscene
import daio7771.everytunnel.core.cutscene.Local
import daio7771.everytunnel.core.cutscene.Look
import daio7771.everytunnel.core.cutscene.SceneFrame
import daio7771.everytunnel.core.cutscene.Shot
import daio7771.everytunnel.core.cutscene.Subtitle
import daio7771.everytunnel.core.cutscene.Timeline
import daio7771.everytunnel.core.level.Position
import kotlin.math.roundToInt

/**
 * The reunion with the coworker and worker B (phase 6.1) as a scene, in the manner of Level 1's meeting (`Ch2L1MeetingScene`): the player
 * stands held at their anchor, the black bands come in and the camera is their own eyes, turning smoothly to whoever speaks
 * ([Ch2L2Reunion.lines] in order, each speaker's place read from the anchors). With no lines the eyes rest on the coworker for
 * `silent_seconds`. After the last line the eyes turn to the door of Level 3 while the two start walking to it for `leave_seconds`.
 * The server reads the same [Built] for what happens when ([beatAt], [leaveStart]); the client draws it with `CutsceneView`. Plain Kotlin.
 */
object ReunionScene {

    /** A standing person's eyes over their feet. */
    const val EYE = 1.62

    /** A subtitle stays this long after its voice ends. */
    const val HOLD_TICKS = 12

    data class Beat(val line: Ch2L2ReunionLine, val start: Int, val end: Int)

    class Built(
        val timeline: Timeline,
        val beats: List<Beat>,
        /** The first line begins here (after the fly-in). */
        val linesStart: Int,
        /** The last line has ended: the three turn to the door and the NPCs begin to leave. */
        val leaveStart: Int,
        /** The eyes are back in the player's head from here: the end function runs. */
        val shotsEnd: Int,
        val total: Int,
    ) {
        /** The line being said at [tick], or null. */
        fun beatAt(tick: Double): Beat? = beats.firstOrNull { tick >= it.start && tick < it.end }
    }

    /** The scene for [reunion] seen from [frame] (where the player stands, facing the NPCs); [places]: the NPCs' feet by skin; [leave]: where the three go. */
    fun build(reunion: Ch2L2Reunion, frame: SceneFrame, places: Map<String, Position>, leave: Position): Built {
        val eyes = Local(0.0, 0.0, EYE)
        fun eyesOf(p: Position) = frame.toLocal(Position(p.x, p.y + EYE, p.z))
        val between = places.values.takeIf { it.isNotEmpty() }?.let { all ->
            Position(all.map { it.x }.average(), all.map { it.y }.average(), all.map { it.z }.average())
        } ?: leave
        val shots = mutableListOf<Shot>()
        val beats = mutableListOf<Beat>()
        val flyIn = ticks(reunion.flyInSeconds)
        var tick = flyIn
        var looking: Local? = null
        fun turnTo(to: Local) {
            val from = looking
            if (from != null && from != to) {
                val turn = ticks(reunion.turnSeconds)
                shots += Shot(turn, eyes, eyes, Look.At(from), Look.At(to))
                tick += turn
            }
            looking = to
        }
        val linesStart = tick
        if (reunion.lines.isEmpty()) {
            turnTo(eyesOf(places["coworker"] ?: between))
            val silent = ticks(reunion.silentSeconds).coerceAtLeast(1)
            shots += Shot(silent, eyes, eyes, Look.At(looking!!))
            tick += silent
        }
        for (line in reunion.lines) {
            turnTo(eyesOf(places[line.who] ?: between))
            val length = ticks(line.seconds).coerceAtLeast(1)
            val gap = ticks(reunion.gapSeconds)
            shots += Shot(length + gap, eyes, eyes, Look.At(looking!!))
            beats += Beat(line, tick, tick + length)
            tick += length + gap
        }
        val leaveStart = tick
        turnTo(eyesOf(leave).let { Local(it.forward, it.right, it.up - 0.4) })
        val leaving = ticks(reunion.leaveSeconds)
        shots += Shot(leaving, eyes, eyes, Look.At(looking!!))
        tick += leaving
        val flyOut = ticks(reunion.flyOutSeconds)
        val subtitles = beats.map { Subtitle("${reunion.textKey}.${it.line.key}", it.start, it.end + HOLD_TICKS) }
        val cutscene = Cutscene(shots, flyIn, flyOut)
        return Built(Timeline(cutscene, emptyList(), subtitles), beats, linesStart, leaveStart, tick, cutscene.totalTicks)
    }

    private fun ticks(seconds: Double) = (seconds * 20.0).roundToInt()
}
