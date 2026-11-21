package daio7771.everytunnel.chapter2.level2.platform

import daio7771.everytunnel.chapter2.core.MapPlan
import daio7771.everytunnel.chapter2.level2.core.Ch2L2Files
import daio7771.everytunnel.chapter2.platform.MapBuild
import daio7771.everytunnel.common.platform.AlarmLightBlock
import daio7771.everytunnel.common.platform.FluorescentLightBlock
import daio7771.everytunnel.common.platform.LightMode
import daio7771.everytunnel.common.platform.ModBlocks
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.block.LightBlock
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d

/**
 * Puts Chapter 2's Level 2, the catacombs, in the world, and takes it out: the chapter's [MapBuild] for the plan
 * `tools/map/chapter2/ch2l2/generate.py` writes ([MapPlan], at [Ch2L2Files.PLAN]), at its origin (1024, 64, 1280), south of Level 1's, in the
 * anchors' dimension. The world remembers the fingerprint of the plan it was built from (`data/every_tunnel_ch2_l2.dat`), so it is built
 * again only when the plan changes. Mostly empty, its box is about two million cells: a build takes a few seconds in batches.
 *
 * The grate between the vestibule and the collector (`vestibule_gate`, one of [MapPlan.gates]) is built in its plan's `built` state, open
 * in phase 1 so that the level can be walked from end to end; phases 5 and 6 drive it. Its own on top: [zoneLights], the lamps of a
 * zone on, flickering or off (`level.json`, `lights`).
 */
object Ch2L2Build {

    /** The level of the invisible light blocks the generator places (`checks.light.fill_level`), and while their room's lamps flicker. */
    private const val FILL_LEVEL = 14
    private const val FILL_LEVEL_FLICKER = 6

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

    /** The plan's gate [gateId] set to its state [state] ("grate", "open", "whole", "broken"...); false if the plan has no such gate or state. */
    fun setGate(world: ServerWorld, gateId: String, state: String): Boolean {
        val gate = plan.gate(gateId) ?: return false
        val spec = gate.states[state] ?: return false
        val block = build.state(spec)
        for (cell in gate.cells) {
            val pos = BlockPos(plan.worldX(cell.x), plan.worldY(cell.y), plan.worldZ(cell.z))
            if (world.getBlockState(pos) != block) world.setBlockState(pos, block, Block.NOTIFY_ALL)
        }
        return true
    }

    /** The plan's gate [gateId] stands in its state [state] now (judged by its first cell). */
    fun gateIs(world: ServerWorld, gateId: String, state: String): Boolean {
        val gate = plan.gate(gateId) ?: return false
        val spec = gate.states[state] ?: return false
        val cell = gate.cells.firstOrNull() ?: return false
        return world.getBlockState(BlockPos(plan.worldX(cell.x), plan.worldY(cell.y), plan.worldZ(cell.z))) == build.state(spec)
    }

    /** The box the plan's gate [gateId] fills in the world (its cells, whole), or null. */
    fun gateBox(gateId: String): Box? {
        val gate = plan.gate(gateId) ?: return null
        if (gate.cells.isEmpty()) return null
        return Box(
            plan.worldX(gate.cells.minOf { it.x }).toDouble(), plan.worldY(gate.cells.minOf { it.y }).toDouble(), plan.worldZ(gate.cells.minOf { it.z }).toDouble(),
            plan.worldX(gate.cells.maxOf { it.x } + 1).toDouble(), plan.worldY(gate.cells.maxOf { it.y } + 1).toDouble(), plan.worldZ(gate.cells.maxOf { it.z } + 1).toDouble(),
        )
    }

    /** The middle of the plan's gate [gateId] in the world (for its sound), or null. */
    fun gateCentre(gateId: String): Vec3d? {
        val gate = plan.gate(gateId) ?: return null
        if (gate.cells.isEmpty()) return null
        val n = gate.cells.size.toDouble()
        return Vec3d(
            plan.worldX(0) + gate.cells.sumOf { it.x } / n + 0.5, plan.worldY(0) + gate.cells.sumOf { it.y } / n + 0.5,
            plan.worldZ(0) + gate.cells.sumOf { it.z } / n + 0.5,
        )
    }

    /** How many of the plan's lamps stand in each mode now (those in unloaded chunks and any block that is not a lamp are not counted); `/et ch2 l2 perf`. */
    fun lampModes(world: ServerWorld): Map<LightMode, Int> {
        val counts = LightMode.entries.associateWith { 0 }.toMutableMap()
        for (cell in plan.lights) {
            val pos = BlockPos(plan.worldX(cell.x), plan.worldY(cell.y), plan.worldZ(cell.z))
            if (!world.isChunkLoaded(pos)) continue
            val state = world.getBlockState(pos)
            if (state.block !is FluorescentLightBlock) continue
            counts.merge(state.get(FluorescentLightBlock.MODE), 1, Int::plus)
        }
        return counts
    }

    /** The plan's alarm lights (`alarms`: the collector's red ones, off as built) switched on or off; how many changed. */
    fun setAlarms(world: ServerWorld, on: Boolean): Int {
        var changed = 0
        for (cell in plan.alarms) {
            val pos = BlockPos(plan.worldX(cell.x), plan.worldY(cell.y), plan.worldZ(cell.z))
            if (!world.isChunkLoaded(pos)) continue
            val state = world.getBlockState(pos)
            if (!state.isOf(ModBlocks.ALARM_LIGHT) || state.get(AlarmLightBlock.ON) == on) continue
            world.setBlockState(pos, state.with(AlarmLightBlock.ON, on), Block.NOTIFY_ALL)
            changed++
        }
        return changed
    }

    /** The plan's lamps in the zone [zoneId] in [mode] (the cage lamps of the map, or any light of the kind); how many changed. */
    fun zoneLights(world: ServerWorld, zoneId: String, mode: LightMode): Int {
        val box = Ch2L2DataLoader.data.anchors.zone(zoneId)?.box ?: return 0
        var changed = 0
        for (cell in plan.lights) {
            val pos = BlockPos(plan.worldX(cell.x), plan.worldY(cell.y), plan.worldZ(cell.z))
            if (!box.contains(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)) continue
            val state = world.getBlockState(pos)
            val block = state.block as? FluorescentLightBlock ?: continue
            if (state.get(FluorescentLightBlock.MODE) == mode) continue
            world.setBlockState(pos, FluorescentLightBlock.withMode(state, mode), Block.NOTIFY_ALL)
            if (mode == LightMode.FLICKER) world.scheduleBlockTick(pos, block, 20 + world.random.nextInt(40))
            changed++
        }
        // The invisible light blocks of such a room go with its lamps (the floor's light is theirs): full, half while they flicker, none.
        val level = when (mode) {
            LightMode.ON -> FILL_LEVEL
            LightMode.FLICKER -> FILL_LEVEL_FLICKER
            LightMode.OFF -> 0
        }
        for (cell in plan.dim) {
            val pos = BlockPos(plan.worldX(cell.x), plan.worldY(cell.y), plan.worldZ(cell.z))
            if (!box.contains(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)) continue
            val state = world.getBlockState(pos)
            if (!state.isOf(Blocks.LIGHT) || state.get(LightBlock.LEVEL_15) == level) continue
            world.setBlockState(pos, state.with(LightBlock.LEVEL_15, level), Block.NOTIFY_ALL)
        }
        return changed
    }
}
