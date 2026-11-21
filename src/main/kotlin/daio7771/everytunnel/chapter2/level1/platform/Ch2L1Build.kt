package daio7771.everytunnel.chapter2.level1.platform

import daio7771.everytunnel.chapter2.core.MapPlan
import daio7771.everytunnel.chapter2.platform.Ch2Blocks
import daio7771.everytunnel.chapter2.platform.MapBuild
import daio7771.everytunnel.common.platform.ModBlocks
import net.minecraft.block.BlockState
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos

/**
 * Puts Chapter 2's Level 1 in the world, and takes it out: the chapter's [MapBuild] for this level's plan ([MapPlan], from
 * `tools/map/chapter2/ch2l1/generate.py`, at [PLAN]), built where the plan's origin says, in the dimension of the anchors, when the level is first
 * needed ([Ch2L1.enter] asks) and again whenever the plan changes (the world remembers the fingerprint, `data/every_tunnel_ch2_l1.dat`).
 * Its own on top: [zoneLights], the lamps of a zone turned down and up (the dark LAB of the overtime, the basement at the end).
 */
object Ch2L1Build {

    const val PLAN = "data/every_tunnel/ch2/l1/map.json"

    private val build = MapBuild("Chapter 2 Level 1", "every_tunnel_ch2_l1", PLAN) { Ch2L1DataLoader.data.anchors.dimension }

    /** The plan, read once. */
    val plan: MapPlan get() = build.plan

    /** Why the last [MapBuild.Asked.FAILED] failed. */
    val lastProblem: String? get() = build.lastProblem

    fun register() = build.register()

    fun isBuilding(): Boolean = build.isBuilding()

    /** The level stands in the world, built from the current plan. */
    fun isBuilt(server: MinecraftServer): Boolean = build.isBuilt(server)

    /**
     * The plan's ceiling lamps in the zone [zoneId] off ([Ch2Blocks.FACTORY_LIGHT_OFF]), one in [emergencyEvery] (0: none) a faint red
     * emergency light ([Ch2Blocks.FACTORY_LIGHT_EMERGENCY]); or, not [dim], all back on. The dark LAB of the overtime, the basement at the end.
     */
    fun zoneLights(world: ServerWorld, zoneId: String, dim: Boolean, emergencyEvery: Int = 0) {
        val box = Ch2L1DataLoader.data.anchors.zone(zoneId)?.box ?: return
        val lamps = plan.lights.map { BlockPos(plan.worldX(it.x), plan.worldY(it.y), plan.worldZ(it.z)) }
            .filter { box.contains(it.x + 0.5, it.y - 0.5, it.z + 0.5) || box.contains(it.x + 0.5, it.y + 0.5, it.z + 0.5) }
        for ((i, pos) in lamps.withIndex()) {
            val state = world.getBlockState(pos)
            val lit = state.isOf(ModBlocks.LAB_CEILING_LIGHT)
            val dark = state.isOf(Ch2Blocks.FACTORY_LIGHT_OFF) || state.isOf(Ch2Blocks.FACTORY_LIGHT_EMERGENCY)
            val emergency = emergencyEvery > 0 && i % emergencyEvery == 0
            when {
                dim && lit -> world.setBlockState(pos, (if (emergency) Ch2Blocks.FACTORY_LIGHT_EMERGENCY else Ch2Blocks.FACTORY_LIGHT_OFF).defaultState)
                !dim && dark -> world.setBlockState(pos, ModBlocks.LAB_CEILING_LIGHT.defaultState)
            }
        }
    }

    /** The block the plan puts at the world cell [pos], or null where no fill of the plan reaches (or its block is unknown). */
    fun planState(pos: BlockPos): BlockState? = build.planState(pos)

    /**
     * Builds the level if it is not built (or, with [rebuild], whatever there is), then calls [whenDone] on the server thread.
     * [MapBuild.Asked] says what happened.
     */
    fun ask(server: MinecraftServer, rebuild: Boolean, whenDone: () -> Unit): MapBuild.Asked = build.ask(server, rebuild, whenDone)

    /** Empties everything the plan built; the world forgets it was built. [MapBuild.Asked.BUSY] if a job runs. */
    fun clear(server: MinecraftServer, whenDone: () -> Unit): MapBuild.Asked = build.clear(server, whenDone)
}
