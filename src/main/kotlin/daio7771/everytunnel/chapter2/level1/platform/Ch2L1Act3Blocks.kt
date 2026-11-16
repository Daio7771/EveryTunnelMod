package daio7771.everytunnel.chapter2.level1.platform

import daio7771.everytunnel.platform.BlockShapes
import daio7771.everytunnel.platform.CardReaderBlock
import daio7771.everytunnel.platform.PropBlock
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemPlacementContext
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.state.StateManager
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.BlockView
import net.minecraft.world.World

/**
 * The lock by a locked door of Chapter 2 Level 1's Act 3 ([Ch2L1Act3Hooks]): a box on the wall behind it, [CardReaderBlock.FACING] the
 * way its front looks, its light red until it opened its door ([CardReaderBlock.UNLOCKED], green). [BadgeReaderBlock] takes the coworker's
 * badge, [KeypadBlock] Rowan's code. Used by a player who is not playing the level ([Ch2L1.active]) it does nothing.
 */
abstract class DoorLockBlock(settings: Settings, boxes: List<BlockShapes.Box>) : Block(settings) {

    private val shapes = BlockShapes.horizontal(boxes)

    init {
        defaultState = stateManager.defaultState.with(CardReaderBlock.FACING, Direction.NORTH).with(CardReaderBlock.UNLOCKED, false)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(CardReaderBlock.FACING, CardReaderBlock.UNLOCKED)
    }

    override fun getPlacementState(ctx: ItemPlacementContext): BlockState =
        defaultState.with(CardReaderBlock.FACING, ctx.horizontalPlayerFacing.opposite)

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        shapes.getValue(state.get(CardReaderBlock.FACING))

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult {
        if (!Ch2L1.active(player)) return ActionResult.PASS
        if (world.isClient) return ActionResult.SUCCESS
        return use(player as ServerPlayerEntity, pos)
    }

    protected abstract fun use(player: ServerPlayerEntity, pos: BlockPos): ActionResult
}

/** The badge reader by the auxiliary lab's door: the card reader's look; the coworker's badge opens the door ([Ch2L1Act3Hooks.useReader]). */
class BadgeReaderBlock(settings: Settings) : DoorLockBlock(settings, listOf(BlockShapes.box(5, 4, 14.5, 11, 12, 16))) {
    override fun use(player: ServerPlayerEntity, pos: BlockPos): ActionResult = Ch2L1Act3Hooks.useReader(player, pos)
}

/** The keypad by the office's door: its screen asks for a code ([Ch2L1Act3Hooks.useKeypad]). */
class KeypadBlock(settings: Settings) : DoorLockBlock(settings, listOf(BlockShapes.box(4, 2, 14.5, 12, 14, 16))) {
    override fun use(player: ServerPlayerEntity, pos: BlockPos): ActionResult = Ch2L1Act3Hooks.useKeypad(player, pos)
}

/** Rowan's portrait over his desk: looked at closely, its plaque is read ([Ch2L1Act3Hooks.usePortrait]). */
class PortraitBlock(settings: Settings, boxes: List<BlockShapes.Box>) : PropBlock(settings, boxes) {

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult {
        if (!Ch2L1.active(player)) return ActionResult.PASS
        if (world.isClient) return ActionResult.SUCCESS
        return Ch2L1Act3Hooks.usePortrait(player as ServerPlayerEntity)
    }
}
