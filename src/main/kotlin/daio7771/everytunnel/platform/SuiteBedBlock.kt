package daio7771.everytunnel.platform

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.state.StateManager
import net.minecraft.state.property.BooleanProperty
import net.minecraft.state.property.DirectionProperty
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
 * A bed in Level 4's Suites, in two blocks ([HEAD], and the foot on the side away from [FACING]),
 * set by the map generator. Right-clicked, either half lets the player rest ([SuiteBedHooks]).
 * Not a vanilla bed: nobody sleeps, and it sets no spawn point. The models are in
 * tools/blocks/generate.py.
 */
class SuiteBedBlock(settings: Settings) : Block(settings) {

    init {
        defaultState = stateManager.defaultState.with(FACING, Direction.NORTH).with(HEAD, true)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FACING, HEAD)
    }

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape = SHAPE

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult {
        if (world.isClient) return ActionResult.SUCCESS
        SuiteBedHooks.rest(player as ServerPlayerEntity)
        return ActionResult.CONSUME
    }

    companion object {
        val FACING: DirectionProperty = Properties.HORIZONTAL_FACING
        val HEAD: BooleanProperty = BooleanProperty.of("head")

        private val SHAPE: VoxelShape = createCuboidShape(0.0, 0.0, 0.0, 16.0, 10.0, 16.0)
    }
}
