package daio7771.everytunnel.chapter2.level2.platform

import daio7771.everytunnel.chapter2.core.MapPlan
import daio7771.everytunnel.chapter2.level2.core.Ch2L2Files
import daio7771.everytunnel.chapter2.platform.MapBuild
import net.minecraft.server.MinecraftServer

/**
 * Puts Chapter 2's Level 2, the catacombs, in the world, and takes it out: the chapter's [MapBuild] for the plan
 * `tools/map/ch2l2/generate.py` writes ([MapPlan], at [Ch2L2Files.PLAN]), at its origin (1024, 64, 1280), south of Level 1's, in the
 * anchors' dimension. The world remembers the fingerprint of the plan it was built from (`data/every_tunnel_ch2_l2.dat`), so it is built
 * again only when the plan changes. Mostly empty, its box is about two million cells: a build takes a few seconds in batches.
 *
 * The grate between the vestibule and the collector (`vestibule_gate`, one of [MapPlan.gates]) is built in its plan's `built` state, open
 * in phase 1 so that the level can be walked from end to end; phases 5 and 6 drive it.
 */
object Ch2L2Build {

    private val build = MapBuild("Chapter 2 Level 2", "every_tunnel_ch2_l2", Ch2L2Files.PLAN) { Ch2L2DataLoader.data.anchors.dimension }

    /** The plan, read once. */
    val plan: MapPlan get() = build.plan

    /** Why the last [MapBuild.Asked.FAILED] failed. */
    val lastProblem: String? get() = build.lastProblem

    fun register() = build.register()

    fun isBuilding(): Boolean = build.isBuilding()

    /** The level stands in the world, built from the current plan. */
    fun isBuilt(server: MinecraftServer): Boolean = build.isBuilt(server)

    /** Builds the level if it is not built (or, with [rebuild], again), then calls [whenDone] on the server thread. */
    fun ask(server: MinecraftServer, rebuild: Boolean, whenDone: () -> Unit): MapBuild.Asked = build.ask(server, rebuild, whenDone)

    /** Empties everything the plan built; the world forgets it was built. [MapBuild.Asked.BUSY] if a job runs. */
    fun clear(server: MinecraftServer, whenDone: () -> Unit): MapBuild.Asked = build.clear(server, whenDone)
}
