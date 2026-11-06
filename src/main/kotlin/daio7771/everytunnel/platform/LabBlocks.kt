package daio7771.everytunnel.platform

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.state.StateManager
import net.minecraft.state.property.DirectionProperty
import net.minecraft.state.property.IntProperty
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.BlockView

/**
 * One column of a leaf of Level 5's airlock doors (4 x 3 blocks, two leaves meeting in the middle). [FACING]
 * is the way its leaf slides open, into the wall beside the opening; [WIDTH] how much of the column the leaf
 * still fills, in quarters, from that side. [LabHooks] slides the leaves a quarter at a time; nobody opens
 * them by hand. Only a column filled across stops the light. Models and textures in tools/blocks/generate.py.
 */
class AirlockDoorBlock(settings: Settings) : Block(settings) {

    init {
        defaultState = stateManager.defaultState.with(FACING, Direction.WEST).with(WIDTH, 4)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FACING, WIDTH)
    }

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape = shape(state)

    @Deprecated("Deprecated in Java")
    override fun getCollisionShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape = shape(state)

    @Deprecated("Deprecated in Java")
    override fun getOpacity(state: BlockState, world: BlockView, pos: BlockPos): Int =
        if (state.get(WIDTH) == 4) world.maxLightLevel else 0

    companion object {
        val FACING: DirectionProperty = Properties.HORIZONTAL_FACING
        val WIDTH: IntProperty = IntProperty.of("width", 1, 4)

        /** The leaf is 6 pixels thick, in the middle of the wall. */
        private val SHAPES: Map<Pair<Direction, Int>, VoxelShape> = buildMap {
            for (width in 1..4) {
                val w = 4.0 * width
                put(Direction.WEST to width, createCuboidShape(0.0, 0.0, 5.0, w, 16.0, 11.0))
                put(Direction.EAST to width, createCuboidShape(16.0 - w, 0.0, 5.0, 16.0, 16.0, 11.0))
                put(Direction.NORTH to width, createCuboidShape(5.0, 0.0, 0.0, 11.0, 16.0, w))
                put(Direction.SOUTH to width, createCuboidShape(5.0, 0.0, 16.0 - w, 11.0, 16.0, 16.0))
            }
        }

        fun shape(state: BlockState): VoxelShape = SHAPES.getValue(state.get(FACING) to state.get(WIDTH))
    }
}
