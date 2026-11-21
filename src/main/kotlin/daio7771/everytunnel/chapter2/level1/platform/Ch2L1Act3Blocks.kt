package daio7771.everytunnel.chapter2.level1.platform

import daio7771.everytunnel.common.platform.BlockShapes
import daio7771.everytunnel.common.platform.CardReaderBlock
import daio7771.everytunnel.common.platform.PropBlock
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemPlacementContext
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.state.StateManager
import net.minecraft.state.property.Properties
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
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

/**
 * The emergency manhole in the basement's floor (locked in after the overtime, [Ch2L1EndingHooks]): a block of the floor, its iron lid
 * flush with it, shut ([OPEN] false) until a click held on it lifts it. Open, it is a hole with a rim to stand on and a shaft
 * going down (the lid lies slid aside on the floor); a click on it, or stepping into it, takes the player down. Its outline is
 * always the whole block, so that it can be clicked.
 */
class ManholeBlock(settings: Settings) : Block(settings) {

    init {
        defaultState = stateManager.defaultState.with(OPEN, false)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(OPEN)
    }

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape = VoxelShapes.fullCube()

    @Deprecated("Deprecated in Java")
    override fun getCollisionShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        if (state.get(OPEN)) RIM else VoxelShapes.fullCube()

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult {
        if (!Ch2L1.active(player)) return ActionResult.PASS
        // A swing on the client, so that the game goes on repeating a held click (the lid comes up over a few seconds).
        if (world.isClient) return ActionResult.SUCCESS
        return Ch2L1EndingHooks.useManhole(player as ServerPlayerEntity, pos)
    }

    companion object {
        val OPEN = Properties.OPEN

        /** Open: only the rim is solid, two pixels wide, so that a player steps into the hole. */
        private val RIM: VoxelShape = VoxelShapes.union(
            Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 16.0, 2.0),
            Block.createCuboidShape(0.0, 0.0, 14.0, 16.0, 16.0, 16.0),
            Block.createCuboidShape(0.0, 0.0, 2.0, 2.0, 16.0, 14.0),
            Block.createCuboidShape(14.0, 0.0, 2.0, 16.0, 16.0, 14.0),
            Block.createCuboidShape(2.0, 0.0, 2.0, 14.0, 1.0, 14.0),
        )
    }
}
