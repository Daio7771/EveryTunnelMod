package daio7771.everytunnel.chapter2.level2.platform

import daio7771.everytunnel.platform.BlockShapes
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.state.StateManager
import net.minecraft.state.property.DirectionProperty
import net.minecraft.state.property.EnumProperty
import net.minecraft.state.property.Properties
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.StringIdentifiable
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.BlockView
import net.minecraft.world.World

/** What a trap's breaker shows: the lever up and the lamp off ([SPENT]), the lever down and the lamp amber ([CHARGING], blinking with [DIM]) or green ([ARMED]). */
enum class TrapBreakerState(private val id: String) : StringIdentifiable {
    SPENT("spent"), DIM("dim"), CHARGING("charging"), ARMED("armed");

    override fun asString(): String = id
}

/**
 * The breaker of one of the Observant Guard's four traps (Chapter 2 Level 2, phase 5): a lever and a lamp on the maintenance wall beside
 * its plate. Right-clicking pulls it ([Ch2L2Guard.pull]); what it then shows follows the trap's phase ([Ch2L2Guard] sets [STATE] every
 * tick). Models: `tools/blocks/generate.py`.
 */
class TrapBreakerBlock(settings: Settings) : Block(settings) {

    init {
        defaultState = stateManager.defaultState.with(FACING, Direction.NORTH).with(STATE, TrapBreakerState.SPENT)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FACING, STATE)
    }

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        SHAPES.getValue(state.get(FACING))

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult {
        if (world.isClient) return ActionResult.SUCCESS
        Ch2L2Guard.pull(player as ServerPlayerEntity, world as ServerWorld, pos, state)
        return ActionResult.CONSUME
    }

    companion object {
        val FACING: DirectionProperty = Properties.HORIZONTAL_FACING
        val STATE: EnumProperty<TrapBreakerState> = EnumProperty.of("state", TrapBreakerState::class.java)

        private val SHAPES = BlockShapes.horizontal(listOf(BlockShapes.box(3, 0, 10, 13, 16, 16)))

        /** The lamp lights its surroundings a little while it is on. */
        fun luminance(state: BlockState): Int = when (state.get(STATE)) {
            TrapBreakerState.CHARGING -> 5
            TrapBreakerState.ARMED -> 8
            else -> 0
        }
    }
}
