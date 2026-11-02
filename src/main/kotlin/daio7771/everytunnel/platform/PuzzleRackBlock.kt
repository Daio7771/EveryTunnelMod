package daio7771.everytunnel.platform

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.state.StateManager
import net.minecraft.state.property.BooleanProperty
import net.minecraft.state.property.DirectionProperty
import net.minecraft.state.property.IntProperty
import net.minecraft.state.property.Properties
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.BlockView
import net.minecraft.world.World

/**
 * Where a record or a film reel of Level 4's puzzles waits ([LoungePuzzleHooks]): [INDEX] says
 * which, [HAS] whether it is there, [HINT] glows softly when it is the next right one. Right-click
 * takes it, or puts back the one in hand. Set by the map generator. Models in tools/blocks/generate.py.
 */
class PuzzleRackBlock(settings: Settings) : Block(settings) {

    init {
        defaultState = stateManager.defaultState.with(FACING, Direction.NORTH).with(INDEX, 0).with(HAS, true).with(HINT, false)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FACING, INDEX, HAS, HINT)
    }

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape = SHAPE

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult {
        if (world.isClient) return ActionResult.SUCCESS
        LoungePuzzleHooks.useRack(player as ServerPlayerEntity, state)
        return ActionResult.CONSUME
    }

    companion object {
        val FACING: DirectionProperty = Properties.HORIZONTAL_FACING
        val INDEX: IntProperty = IntProperty.of("index", 0, 3)
        val HAS: BooleanProperty = BooleanProperty.of("has")
        val HINT: BooleanProperty = BooleanProperty.of("hint")

        private val SHAPE: VoxelShape = createCuboidShape(1.0, 0.0, 1.0, 15.0, 14.0, 15.0)
    }
}
