package daio7771.everytunnel.platform

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.item.ItemPlacementContext
import net.minecraft.state.StateManager
import net.minecraft.state.property.EnumProperty
import net.minecraft.state.property.Properties
import net.minecraft.util.BlockRotation
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.BlockView

/**
 * A piece of a rolling shutter's curtain: a corrugated steel sheet, 2 pixels thick, through
 * the middle of the block. [AXIS] is the horizontal direction the sheet runs along.
 *
 * The level closes shutters on its own (`LevelEntryHooks`), filling the empty blocks of a
 * zone tagged `shutter` row by row from the top; placed by hand, the sheet runs across
 * the player's view.
 */
class ShutterBlock(settings: Settings) : Block(settings) {

    init {
        defaultState = stateManager.defaultState.with(AXIS, Direction.Axis.X)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(AXIS)
    }

    override fun getPlacementState(ctx: ItemPlacementContext): BlockState =
        defaultState.with(AXIS, if (ctx.horizontalPlayerFacing.axis == Direction.Axis.X) Direction.Axis.Z else Direction.Axis.X)

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        if (state.get(AXIS) == Direction.Axis.X) SHAPE_X else SHAPE_Z

    @Deprecated("Deprecated in Java")
    override fun rotate(state: BlockState, rotation: BlockRotation): BlockState = when (rotation) {
        BlockRotation.CLOCKWISE_90, BlockRotation.COUNTERCLOCKWISE_90 ->
            state.with(AXIS, if (state.get(AXIS) == Direction.Axis.X) Direction.Axis.Z else Direction.Axis.X)
        else -> state
    }

    companion object {
        val AXIS: EnumProperty<Direction.Axis> = Properties.HORIZONTAL_AXIS
        private val SHAPE_X: VoxelShape = createCuboidShape(0.0, 0.0, 7.0, 16.0, 16.0, 9.0)
        private val SHAPE_Z: VoxelShape = createCuboidShape(7.0, 0.0, 0.0, 9.0, 16.0, 16.0)

        /** The state for a curtain filling an opening [sizeX] by [sizeZ] blocks wide (the longer side). */
        fun across(sizeX: Int, sizeZ: Int): BlockState =
            ModBlocks.SHUTTER.defaultState.with(AXIS, if (sizeX >= sizeZ) Direction.Axis.X else Direction.Axis.Z)
    }
}
