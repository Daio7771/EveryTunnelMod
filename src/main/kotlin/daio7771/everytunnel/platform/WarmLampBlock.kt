package daio7771.everytunnel.platform

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.state.StateManager
import net.minecraft.state.property.BooleanProperty
import net.minecraft.util.math.BlockPos
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.BlockView

/**
 * Level 4's ceiling light, a frosted glass under a round of brass: warm and silent, with none of
 * the fluorescents' hum or flicker. [LIT] says whether it gives light. The model is in
 * tools/blocks/generate.py.
 */
class WarmLampBlock(settings: Settings) : Block(settings) {

    init {
        defaultState = stateManager.defaultState.with(LIT, true)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(LIT)
    }

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape = SHAPE

    companion object {
        val LIT: BooleanProperty = BooleanProperty.of("lit")

        /** A little under the fluorescents' 13: the lounge is soft, not bright. */
        private const val LIGHT_LEVEL = 12

        private val SHAPE: VoxelShape = createCuboidShape(4.0, 13.0, 4.0, 12.0, 16.0, 12.0)

        fun luminance(state: BlockState): Int = if (state.get(LIT)) LIGHT_LEVEL else 0
    }
}
