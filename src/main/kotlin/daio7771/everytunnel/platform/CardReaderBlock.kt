package daio7771.everytunnel.platform

import daio7771.everytunnel.core.achievement.Achievement
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.DoorBlock
import net.minecraft.block.ShapeContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemPlacementContext
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.state.StateManager
import net.minecraft.state.property.BooleanProperty
import net.minecraft.state.property.DirectionProperty
import net.minecraft.state.property.Properties
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.BlockView
import net.minecraft.world.World

/**
 * A keycard reader on a wall, [FACING] the way its front looks. Locked it shows a red light;
 * right-clicked by a player carrying the keycard it beeps, turns green ([UNLOCKED]) and opens
 * the iron doors within [DOOR_REACH] blocks (which hands cannot open), and everyone there earns
 * "Employee of the Month" ([AchievementHooks]). Without the card it
 * buzzes: "It needs a keycard". A new game locks it and closes the doors again
 * ([KeycardHooks.resetReaders]).
 */
class CardReaderBlock(settings: Settings) : Block(settings) {

    init {
        defaultState = stateManager.defaultState.with(FACING, Direction.NORTH).with(UNLOCKED, false)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FACING, UNLOCKED)
    }

    override fun getPlacementState(ctx: ItemPlacementContext): BlockState =
        defaultState.with(FACING, ctx.horizontalPlayerFacing.opposite)

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        SHAPES.getValue(state.get(FACING))

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult {
        if (world.isClient) return ActionResult.SUCCESS
        if (state.get(UNLOCKED)) return ActionResult.CONSUME
        if (!player.inventory.containsAny { it.isOf(ModItems.KEYCARD) }) {
            world.playSound(null, pos, ModSounds.CARD_READER_DENY, SoundCategory.BLOCKS, 1f, 1f)
            player.sendMessage(Text.translatable("message.every_tunnel.needs_keycard"), true)
            return ActionResult.CONSUME
        }
        world.setBlockState(pos, state.with(UNLOCKED, true))
        world.playSound(null, pos, ModSounds.CARD_READER_ACCEPT, SoundCategory.BLOCKS, 1f, 1f)
        setDoors(world as ServerWorld, pos, open = true)
        AchievementHooks.puzzleSolved(world, Achievement.KEYCARD)
        return ActionResult.CONSUME
    }

    companion object {
        val FACING: DirectionProperty = Properties.HORIZONTAL_FACING
        val UNLOCKED: BooleanProperty = BooleanProperty.of("unlocked")
        const val DOOR_REACH = 2

        /** A small box against the wall behind it; the model in tools/blocks/generate.py matches. */
        private val SHAPES = BlockShapes.horizontal(listOf(BlockShapes.box(5, 4, 14.5, 11, 12, 16)))

        /** Opens or closes the iron doors (lower halves) within [DOOR_REACH] blocks of [pos]. */
        fun setDoors(world: ServerWorld, pos: BlockPos, open: Boolean) {
            for (door in BlockPos.iterate(pos.add(-DOOR_REACH, -DOOR_REACH, -DOOR_REACH), pos.add(DOOR_REACH, DOOR_REACH, DOOR_REACH))) {
                val state = world.getBlockState(door)
                val block = state.block
                if (block is DoorBlock && state.get(DoorBlock.HALF) == net.minecraft.block.enums.DoubleBlockHalf.LOWER) {
                    block.setOpen(null, world, state, door.toImmutable(), open)
                }
            }
        }
    }
}
