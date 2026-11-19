package daio7771.everytunnel.chapter2.level2.core

import daio7771.everytunnel.core.cutscene.Cutscene
import daio7771.everytunnel.core.cutscene.Local
import daio7771.everytunnel.core.cutscene.Look
import daio7771.everytunnel.core.cutscene.Shot
import daio7771.everytunnel.core.cutscene.Timeline

/**
 * Stone Maw's death as a short third-person cinematic, skippable (phase 6.1; like the Guard's, `Ch2L2GuardScenes`): the creature walks to
 * the middle of the arena, crouches looking at the floor, a crack runs over the ceiling and part of it comes down on it. The frame is the
 * arena's centre facing the way the player came in (so "forward" is the arena's far wall); the timings are `maw.death`'s: [Phases] says when
 * each moment lands so that the platform's actions (the walk, the crouch, the collapse, the far door) follow the camera. Plain Kotlin.
 */
object MawDeathScene {

    const val FLY_IN = 10
    const val FLY_OUT = 10

    /** Ticks from the scene's start: the walk ends and the crouch begins at [crouchAt]; the ceiling comes down at [collapseAt]; the dust settles at [endsAt]. */
    data class Phases(val crouchAt: Int, val collapseAt: Int, val settledAt: Int, val total: Int)

    fun phases(maw: Ch2L2Maw): Phases {
        val walk = ticks(maw.deathWalkSeconds)
        val crouch = ticks(maw.crouchSeconds)
        val collapse = ticks(maw.collapseSeconds)
        val after = ticks(maw.afterSeconds)
        val shots = FLY_IN + walk + crouch + collapse + after
        return Phases(FLY_IN + walk, FLY_IN + walk + crouch, shots, shots + FLY_OUT + Cutscene.BANDS_TICKS)
    }

    fun timeline(maw: Ch2L2Maw): Timeline {
        val walk = ticks(maw.deathWalkSeconds)
        val crouch = ticks(maw.crouchSeconds)
        val collapse = ticks(maw.collapseSeconds)
        val after = ticks(maw.afterSeconds)
        val centreLow = Local(0.0, 0.0, 1.3)
        return Timeline(
            Cutscene(
                listOf(
                    // From the entrance side, wide and low: it comes into the frame and walks to the middle.
                    Shot(walk, Local(-9.0, 3.5, 2.2), Local(-8.0, 3.0, 2.0), Look.At(Local(2.0, 0.0, 1.5)), Look.At(centreLow)),
                    // Closer, from the side: it crouches and looks at the floor; the camera slips up to the first crack in the ceiling.
                    Shot(crouch, Local(-5.0, -2.6, 1.5), Local(-4.6, -2.2, 1.7), Look.At(centreLow), Look.At(Local(0.0, 0.0, 4.5))),
                    // The ceiling comes down: held, looking up, then drawing back from the dust.
                    Shot(collapse, Local(-4.2, -1.0, 1.2), Local(-6.5, -2.0, 2.2), Look.At(Local(0.0, 0.0, 5.0)), Look.At(centreLow)),
                    // The settling dust, the rubble where it stood.
                    Shot(after, Local(-6.5, -2.0, 2.2), Local(-7.5, -2.5, 2.6), Look.At(centreLow)),
                ),
                FLY_IN, FLY_OUT,
            ),
            emptyList(), emptyList(),
        )
    }

    private fun ticks(seconds: Double) = Math.round(seconds * 20.0).toInt()
}
