package daio7771.everytunnel.platform

import net.minecraft.entity.ai.pathing.NavigationType
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.ConnectingBlock
import net.minecraft.block.ShapeContext
import net.minecraft.item.ItemPlacementContext
import net.minecraft.state.StateManager
import net.minecraft.state.property.BooleanProperty
import net.minecraft.util.BlockMirror
import net.minecraft.util.BlockRotation
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import net.minecraft.world.BlockView
import net.minecraft.world.WorldAccess

/**
 * Pipes, ducts and cable trays: a core plus an arm towards every side it connects to,
 * so straight runs, corners and junctions build themselves.
 *
 * A side connects when it has an arm ([arms]) and the neighbour is the same block, or,
 * for horizontal sides, a full solid face (a run ends inside the wall instead of in the
 * air), or, up and down when [throughValves], a [ValveBlock] (it carries the pipe through
 * itself, so a pipe runs on through the valve). A run going up or down (joined above or
 * below) grows no arms into walls: one on every block of a riser reads as a comb. Since
 * that hangs on the sides above and below, every update works out all sides again. Never
 * up or down into a solid block: the ceiling and beams above a run must not
 * grow stubs. `tools/map/map_generator.py` sets the same connections when it builds a
 * level, since `/setblock` takes the state as given.
 */
class ConnectingPropBlock(
    settings: Settings,
    core: BlockShapes.Box,
    private val arms: Map<Direction, BlockShapes.Box>,
    private val throughValves: Boolean = false,
) : Block(settings) {

    /** Mobs path round it, not into it (see [BlockShapes.pathfindThrough]). */
    @Deprecated("Deprecated in Java")
    override fun canPathfindThrough(state: BlockState, world: BlockView, pos: BlockPos, type: NavigationType): Boolean =
        BlockShapes.pathfindThrough(state, world, pos, type)

    private val shapes: Map<BlockState, VoxelShape>

    init {
        defaultState = SIDES.values.fold(stateManager.defaultState) { s, p -> s.with(p, false) }
        val coreShape = BlockShapes.rotated(listOf(core), 0, 0)
        shapes = stateManager.states.associateWith { state ->
            arms.entries
                .filter { (side, _) -> state.get(SIDES.getValue(side)) }
                .map { (_, box) -> BlockShapes.rotated(listOf(box), 0, 0) }
                .fold(coreShape) { a, b -> VoxelShapes.union(a, b) }
        }
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(*SIDES.values.toTypedArray())
    }

    override fun getPlacementState(ctx: ItemPlacementContext): BlockState = connections(ctx.world, ctx.blockPos)

    @Deprecated("Deprecated in Java")
    override fun getStateForNeighborUpdate(
        state: BlockState, direction: Direction, neighborState: BlockState,
        world: WorldAccess, pos: BlockPos, neighborPos: BlockPos,
    ): BlockState = connections(world, pos, direction, neighborState)

    /** The state with every side's arm, [changed] being [changedState] (a neighbour being updated). */
    private fun connections(world: BlockView, pos: BlockPos, changed: Direction? = null, changedState: BlockState? = null): BlockState {
        val neighbors = Direction.entries.associateWith { side ->
            if (side == changed && changedState != null) changedState else world.getBlockState(pos.offset(side))
        }
        val joined = neighbors.mapValues { (side, neighbor) -> joins(side, neighbor) }
        val riser = joined.getValue(Direction.UP) || joined.getValue(Direction.DOWN)
        return Direction.entries.fold(defaultState) { state, side ->
            val wall = !riser && side.axis.isHorizontal && side in arms &&
                neighbors.getValue(side).isSideSolidFullSquare(world, pos.offset(side), side.opposite)
            state.with(SIDES.getValue(side), joined.getValue(side) || wall)
        }
    }

    /** The same block, or a valve above or below, on a side with an arm. */
    private fun joins(side: Direction, neighbor: BlockState): Boolean {
        if (side !in arms) return false
        if (neighbor.isOf(this)) return true
        return throughValves && side.axis.isVertical && neighbor.block is ValveBlock
    }

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        shapes.getValue(state)

    @Deprecated("Deprecated in Java")
    override fun rotate(state: BlockState, rotation: BlockRotation): BlockState {
        var turned = state
        for (side in Direction.Type.HORIZONTAL) {
            turned = turned.with(SIDES.getValue(rotation.rotate(side)), state.get(SIDES.getValue(side)))
        }
        return turned
    }

    @Deprecated("Deprecated in Java")
    override fun mirror(state: BlockState, mirror: BlockMirror): BlockState {
        var mirrored = state
        for (side in Direction.Type.HORIZONTAL) {
            mirrored = mirrored.with(SIDES.getValue(mirror.apply(side)), state.get(SIDES.getValue(side)))
        }
        return mirrored
    }

    companion object {
        val SIDES: Map<Direction, BooleanProperty> = ConnectingBlock.FACING_PROPERTIES
    }
}
