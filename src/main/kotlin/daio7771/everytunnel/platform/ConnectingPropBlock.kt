package daio7771.everytunnel.platform

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
 * air). Never up or down into a solid block: the ceiling and beams above a run must not
 * grow stubs. `tools/map/map_generator.py` sets the same connections when it builds a
 * level, since `/setblock` takes the state as given.
 */
class ConnectingPropBlock(
    settings: Settings,
    core: BlockShapes.Box,
    private val arms: Map<Direction, BlockShapes.Box>,
) : Block(settings) {

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

    override fun getPlacementState(ctx: ItemPlacementContext): BlockState {
        var state = defaultState
        for (side in Direction.entries) {
            val pos = ctx.blockPos.offset(side)
            state = state.with(SIDES.getValue(side), connects(side, ctx.world.getBlockState(pos), ctx.world, pos))
        }
        return state
    }

    @Deprecated("Deprecated in Java")
    override fun getStateForNeighborUpdate(
        state: BlockState, direction: Direction, neighborState: BlockState,
        world: WorldAccess, pos: BlockPos, neighborPos: BlockPos,
    ): BlockState = state.with(SIDES.getValue(direction), connects(direction, neighborState, world, neighborPos))

    private fun connects(side: Direction, neighbor: BlockState, world: BlockView, neighborPos: BlockPos): Boolean {
        if (side !in arms) return false
        if (neighbor.isOf(this)) return true
        return side.axis.isHorizontal && neighbor.isSideSolidFullSquare(world, neighborPos, side.opposite)
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
