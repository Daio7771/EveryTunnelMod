package daio7771.everytunnel.platform

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.item.ItemPlacementContext
import net.minecraft.state.StateManager
import net.minecraft.state.property.BooleanProperty
import net.minecraft.state.property.DirectionProperty
import net.minecraft.state.property.IntProperty
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.BlockView

/**
 * An elevator's floor indicator: a small red LED display on the wall, [FACING] the way its
 * front looks, showing [FLOOR] (index into [LABELS]) and, while [MOVING], a lit down arrow.
 * The new game's intro counts it down from the top floor to B1 (`IntroHooks`); it rests on
 * B1. The models (one per label and arrow) come from tools/blocks/generate.py.
 */
class FloorIndicatorBlock(settings: Settings) : Block(settings) {

    init {
        defaultState = stateManager.defaultState.with(FACING, Direction.NORTH).with(FLOOR, LABELS.lastIndex).with(MOVING, false)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FACING, FLOOR, MOVING)
    }

    override fun getPlacementState(ctx: ItemPlacementContext): BlockState =
        defaultState.with(FACING, ctx.horizontalPlayerFacing.opposite)

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        SHAPES.getValue(state.get(FACING))

    companion object {
        /** Top floor first: the intro goes through them in this order. */
        val LABELS = listOf("3", "2", "1", "G", "B1")

        val FACING: DirectionProperty = Properties.HORIZONTAL_FACING
        val FLOOR: IntProperty = IntProperty.of("floor", 0, LABELS.lastIndex)
        val MOVING: BooleanProperty = BooleanProperty.of("moving")

        /** A display box against the wall behind it, in the upper part of the block. */
        private val SHAPES = BlockShapes.horizontal(listOf(BlockShapes.box(2, 8, 14.5, 14, 13, 16)))
    }
}
