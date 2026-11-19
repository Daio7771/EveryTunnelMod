package daio7771.everytunnel.chapter2.level1.core

import daio7771.everytunnel.chapter2.core.toLocal as sharedToLocal
import daio7771.everytunnel.core.cutscene.Cutscene
import daio7771.everytunnel.core.cutscene.Local
import daio7771.everytunnel.core.cutscene.Look
import daio7771.everytunnel.core.cutscene.SceneFrame
import daio7771.everytunnel.core.cutscene.Shot
import daio7771.everytunnel.core.cutscene.Subtitle
import daio7771.everytunnel.core.cutscene.TimedLine
import daio7771.everytunnel.core.cutscene.Timeline
import daio7771.everytunnel.core.cutscene.VoiceLine
import daio7771.everytunnel.core.level.Position
import kotlin.math.ceil

/**
 * The cinematic of the first announcement (Rowan's `a1`, when the first task is done and the player has left the terminal): the
 * player cannot move, the black bands come in, the camera turns out of their eyes to the shots of `speakers.json` (one: from where
 * they stand, at a fixed ceiling speaker) and flies back into their eyes while the bands go. It is the cutscene machinery of the other
 * scenes ([Cutscene], [Timeline]; `client.cutscene.CutsceneView` draws it) with the shots' places read from anchors, in world
 * coordinates, so moving the map moves the shots. Plain Kotlin, unit tested.
 *
 * The chime ([Built.dingTick]) sounds as the first shot begins, the voice [Built.lineStart] after it, and the whole line's text
 * is the subtitle. The scene ends [Built.timeline]'s `cutscene.totalTicks` after it began.
 */
object Ch2L1IntroScene {

    /** The subtitle stays this long after the voice ends. */
    private const val HOLD_TICKS = 12

    /** A shot's camera called this is the player's own eyes, wherever they stand: the shot only turns it. */
    const val PLAYER = "player"

    /** A standing player's eyes over their feet. */
    private const val EYE = 1.62

    class Built(val timeline: Timeline, val dingTick: Int, val lineStart: Int)

    /** The scene, or why it cannot be made: an anchor [spec] names that [anchors] does not have. */
    data class Result(val built: Built?, val problem: String?)

    /**
     * The scene for [spec], seen from [frame] (where the player stands, whose view the camera leaves and returns to), with the
     * voice [lineMs] long whose text is the lang key [textKey], the chime [dingTicks] long before it.
     */
    fun build(spec: Ch2L1IntroSpec, anchors: Ch2L1Anchors, frame: SceneFrame, lineMs: Int, textKey: String, dingTicks: Int): Result {
        fun place(id: String): Local? =
            if (id == PLAYER) Local(0.0, 0.0, EYE) else anchors.point(id)?.let { frame.toLocal(it.position) }
        val shots = mutableListOf<Shot>()
        for (shot in spec.shots) {
            val from = place(shot.camera) ?: return Result(null, "no anchor ${shot.camera}")
            val to = shot.cameraTo?.let { place(it) ?: return Result(null, "no anchor $it") } ?: from
            val look = place(shot.look) ?: return Result(null, "no anchor ${shot.look}")
            shots += Shot(shot.ticks, from, to, Look.At(look))
        }
        val dingTick = spec.flyInTicks
        val lineStart = dingTick + dingTicks
        val lineEnd = lineStart + ceil(lineMs / 50.0).toInt()
        val timeline = Timeline(
            Cutscene(shots, spec.flyInTicks, spec.flyOutTicks),
            listOf(TimedLine(VoiceLine(spec.line, 0, lineMs, emptyList()), lineStart, lineEnd)),
            listOf(Subtitle(textKey, lineStart, lineEnd + HOLD_TICKS)),
        )
        return Result(Built(timeline, dingTick, lineStart), null)
    }

    /** [position] as the scene's someone sees it: so far ahead, to their right, up (the inverse of [SceneFrame.at]). */
    fun SceneFrame.toLocal(position: Position): Local = sharedToLocal(position)
}
