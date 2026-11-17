package daio7771.everytunnel.chapter2.level1.platform

import daio7771.everytunnel.chapter2.level1.core.Ch2L1State
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemPlacementContext
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.state.StateManager
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.BlockView
import net.minecraft.world.World
import daio7771.everytunnel.platform.BlockShapes
import daio7771.everytunnel.platform.CardReaderBlock
import daio7771.everytunnel.platform.ModSounds

/**
 * The time clock of Chapter 2's Level 1, where the new hire clocks in. It is a card reader on a wall (the same box, the
 * same models and sounds: no art of its own), [CardReaderBlock.FACING] the way its front looks, red until someone clocks
 * in at it ([CardReaderBlock.UNLOCKED], green). Right-clicked by a player playing the level ([Ch2L1.active]) who has not
 * clocked in yet ([Ch2L1State.ACT1_CLOCKIN]) it beeps, turns green and moves them on to [Ch2L1State.ACT1_TASK1]; once in
 * later steps it buzzes, "You already clocked in". For anyone else it does nothing. The light is the block's, not the
 * player's: it goes red again whenever a player is put back at the clock-in step ([relockNear]). The map places it
 * (on the entrance's south wall).
 */
class TimeClockBlock(settings: Settings) : Block(settings) {

    init {
        defaultState = stateManager.defaultState.with(CardReaderBlock.FACING, net.minecraft.util.math.Direction.NORTH).with(CardReaderBlock.UNLOCKED, false)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(CardReaderBlock.FACING, CardReaderBlock.UNLOCKED)
    }

    override fun getPlacementState(ctx: ItemPlacementContext): BlockState =
        defaultState.with(CardReaderBlock.FACING, ctx.horizontalPlayerFacing.opposite)

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        SHAPES.getValue(state.get(CardReaderBlock.FACING))

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult {
        if (world.isClient) return ActionResult.SUCCESS
        if (player !is ServerPlayerEntity || !Ch2L1.active(player)) return ActionResult.PASS
        if (Ch2L1.state(player) != Ch2L1State.ACT1_CLOCKIN) {
            world.playSound(null, pos, ModSounds.CARD_READER_DENY, SoundCategory.BLOCKS, 1f, 1f)
            player.sendMessage(Text.translatable("every_tunnel.ch2.l1.clock.again"), true)
            return ActionResult.CONSUME
        }
        world.setBlockState(pos, state.with(CardReaderBlock.UNLOCKED, true))
        world.playSound(null, pos, ModSounds.CARD_READER_ACCEPT, SoundCategory.BLOCKS, 1f, 1f)
        player.sendMessage(Text.translatable("every_tunnel.ch2.l1.clock.done"), true)
        Ch2L1.setState(player, Ch2L1State.ACT1_TASK1, "clock in")
        return ActionResult.CONSUME
    }

    companion object {
        /** A small box against the wall behind it: the card reader's (its model is shared). */
        private val SHAPES = BlockShapes.horizontal(listOf(BlockShapes.box(5, 4, 14.5, 11, 12, 16)))

        /** How far from the player the clocks that go red again are looked for. */
        private const val RELOCK_RANGE = 16

        /** Every clock within [RELOCK_RANGE] blocks of [player] shows red again: nobody has clocked in at it. */
        fun relockNear(player: ServerPlayerEntity) {
            val world = player.serverWorld
            val centre = player.blockPos
            val reach = BlockPos(RELOCK_RANGE, RELOCK_RANGE / 2, RELOCK_RANGE)
            for (pos in BlockPos.iterate(centre.subtract(reach), centre.add(reach))) {
                val state = world.getBlockState(pos)
                if (state.block is TimeClockBlock && state.get(CardReaderBlock.UNLOCKED)) {
                    world.setBlockState(pos.toImmutable(), state.with(CardReaderBlock.UNLOCKED, false))
                }
            }
        }
    }
}
