package daio7771.everytunnel.chapter2.level2.core

import daio7771.everytunnel.core.cutscene.Cutscene
import daio7771.everytunnel.core.cutscene.Local
import daio7771.everytunnel.core.cutscene.Look
import daio7771.everytunnel.core.cutscene.Shot
import daio7771.everytunnel.core.cutscene.Timeline

/**
 * Stone Maw's death as a short third-person cinematic, skippable (phase 6.1, remade in 6.2; like the Guard's, `Ch2L2GuardScenes`): the
 * creature limps to the middle of the arena and kneels with its hands flat on the floor and its head bowed, a crack runs over the ceiling,
 * small bits fall, then the first big slab lands on it and slams it flat and more slabs follow. The frame is the arena's centre facing
 * the way the player came in (so "forward" is the arena's far wall); the timings are `maw.death`'s: [Phases] says when each moment lands
 * so that the platform's actions (the walk, the kneel, the collapse, the slab that crushes it, the far door) follow the camera. Plain Kotlin.
 */
object MawDeathScene {

    const val FLY_IN = 10
    const val FLY_OUT = 10

    /** The plan's seed: the live fall and a skip's rubble are the same mound. */
    const val COLLAPSE_SEED = 61

    /**
     * Ticks from the scene's start: the walk ends and the kneel begins at [crouchAt]; the ceiling comes down at [collapseAt]; the first slab
     * reaches the creature at [impactAt] (it is crushed, hidden by the burst); the dust settles at [settledAt]. [landings] are the ticks
     * each slab reaches the stone (the first is the hardest).
     */
    data class Phases(val crouchAt: Int, val collapseAt: Int, val impactAt: Int, val settledAt: Int, val total: Int, val landings: List<Int>)

    @Volatile
    private var cached: Pair<Ch2L2Maw, Phases>? = null

    fun phases(maw: Ch2L2Maw): Phases {
        cached?.let { (m, p) -> if (m === maw) return p }
        val walk = ticks(maw.deathWalkSeconds)
        val crouch = ticks(maw.crouchSeconds)
        val collapse = ticks(maw.collapseSeconds)
        val after = ticks(maw.afterSeconds)
        val shots = FLY_IN + walk + crouch + collapse + after
        val collapseAt = FLY_IN + walk + crouch
        val plan = MawCollapse.plan(maw, COLLAPSE_SEED)
        val landings = MawCollapse.landings(plan, maw.slabStartSpeed)
        val slabs = plan.indices.filter { plan[it].kind == MawCollapse.Kind.SLAB }
        val impact = collapseAt + maw.firstSlabDelayTicks + MawCollapse.fallTicks(MawCollapse.TOP_CELL - maw.crushHeight, maw.slabStartSpeed)
        val phases = Phases(FLY_IN + walk, collapseAt, impact, shots, shots + FLY_OUT + Cutscene.BANDS_TICKS, slabs.map { collapseAt + landings[it] })
        cached = maw to phases
        return phases
    }

    fun timeline(maw: Ch2L2Maw): Timeline {
        val walk = ticks(maw.deathWalkSeconds)
        val crouch = ticks(maw.crouchSeconds)
        val collapse = ticks(maw.collapseSeconds)
        val after = ticks(maw.afterSeconds)
        val p = phases(maw)
        // The collapse shot is held till the first slab hits, then draws back from the dust.
        val hold = (p.impactAt - p.collapseAt).coerceIn(1, (collapse - 1).coerceAtLeast(1))
        val back = (collapse - hold).coerceAtLeast(1)
        val centreLow = Local(0.0, 0.0, 1.3)
        val hands = Local(0.6, 0.0, 0.9)
        return Timeline(
            Cutscene(
                listOf(
                    // From the entrance side, wide and low: it comes into the frame and limps to the middle.
                    Shot(walk, Local(-9.0, 3.5, 2.2), Local(-8.0, 3.0, 2.0), Look.At(Local(2.0, 0.0, 1.5)), Look.At(centreLow)),
                    // Closer, from its left, in profile (it kneels facing the far wall): a slow push in on the bowed head and the hands on
                    // the floor, then the aim tilts up to the first crack in the ceiling.
                    Shot(crouch, Local(-1.0, -4.8, 1.1), Local(-0.4, -3.2, 0.9), Look.At(hands), Look.At(Local(0.0, 0.0, 4.5))),
                    // The ceiling comes down, wide, from the side: the ceiling over it and the creature both in view, held till the slab hits.
                    Shot(hold, Local(0.0, -6.5, 2.6), Local(0.0, -6.3, 2.6), Look.At(Local(0.0, 0.0, 3.4))),
                    // Then drawing back from the dust.
                    Shot(back, Local(0.0, -6.3, 2.6), Local(-3.0, -8.0, 3.0), Look.At(Local(0.0, 0.0, 3.4)), Look.At(centreLow)),
                    // The settling dust, the rubble where it stood.
                    Shot(after, Local(-3.0, -8.0, 3.0), Local(-4.0, -8.5, 3.2), Look.At(Local(0.0, 0.0, 2.0)), Look.At(Local(0.0, 0.0, 2.4))),
                ),
                FLY_IN, FLY_OUT,
            ),
            emptyList(), emptyList(),
        )
    }

    private fun ticks(seconds: Double) = Math.round(seconds * 20.0).toInt()
}
