package daio7771.everytunnel.chapter2.level2.platform

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.block.StairsBlock
import net.minecraft.state.StateManager
import net.minecraft.state.property.EnumProperty
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.BlockView
import net.minecraft.entity.ai.pathing.NavigationType
import net.minecraft.item.ItemPlacementContext

/** The catacomb brick's stairs: [StairsBlock]'s constructor is protected, so the block is a subclass of its own. */
class CatacombStairsBlock(base: BlockState, settings: Settings) : StairsBlock(base, settings)

/**
 * A cable run along the vault of Chapter 2's Level 2 (the maintenance bulbs hang from it): a thin bar at the top of its block along
 * [AXIS], nothing to bump into. Placed by hand it runs along the player's facing.
 */
class CeilingCableBlock(settings: Settings) : Block(settings) {

    init {
        defaultState = stateManager.defaultState.with(AXIS, Direction.Axis.X)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(AXIS)
    }

    override fun getPlacementState(ctx: ItemPlacementContext): BlockState =
        defaultState.with(AXIS, ctx.horizontalPlayerFacing.axis)

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        if (state.get(AXIS) == Direction.Axis.X) SHAPE_X else SHAPE_Z

    @Deprecated("Deprecated in Java")
    override fun canPathfindThrough(state: BlockState, world: BlockView, pos: BlockPos, type: NavigationType): Boolean = true

    companion object {
        val AXIS: EnumProperty<Direction.Axis> = Properties.HORIZONTAL_AXIS
        private val SHAPE_X: VoxelShape = createCuboidShape(0.0, 14.0, 7.0, 16.0, 16.0, 9.0)
        private val SHAPE_Z: VoxelShape = createCuboidShape(7.0, 14.0, 0.0, 9.0, 16.0, 16.0)
    }
}
