package daio7771.everytunnel.chapter2.level2.platform

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.state.StateManager
import net.minecraft.state.property.BooleanProperty
import net.minecraft.util.math.BlockPos
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.BlockView

/**
 * The amber warning beacon on the pump's housing in Chapter 2's Level 2 ([Ch2L2Pump] switches it): [LIT] while the pump runs, blinking
 * slowly in its last, slowing second, dark in the silence. The models are in tools/blocks/common/generate.py.
 */
class PumpBeaconBlock(settings: Settings) : Block(settings) {

    init {
        defaultState = stateManager.defaultState.with(LIT, false)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(LIT)
    }

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape = SHAPE

    companion object {
        val LIT: BooleanProperty = BooleanProperty.of("lit")

        /** As bright as a block can be: seen from the whole pump room. */
        private const val LIGHT_LEVEL = 15

        private val SHAPE: VoxelShape = createCuboidShape(1.0, 0.0, 1.0, 15.0, 13.0, 15.0)

        fun luminance(state: BlockState): Int = if (state.get(LIT)) LIGHT_LEVEL else 0
    }
}
