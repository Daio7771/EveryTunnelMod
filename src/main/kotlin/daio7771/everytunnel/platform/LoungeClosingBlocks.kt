package daio7771.everytunnel.platform

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
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
 * One part of Level 4's big door, 6 x 4 blocks: [COL] 0 on the left as seen from the Security room,
 * [ROW] 0 at the bottom. Right-clicked anywhere it tries the key ([LoungeClosingHooks.useBigDoor]).
 * Set by the map generator; models and textures in tools/blocks/generate.py.
 */
class BigDoorBlock(settings: Settings) : Block(settings) {

    init {
        defaultState = stateManager.defaultState.with(FACING, Direction.NORTH).with(COL, 0).with(ROW, 0)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FACING, COL, ROW)
    }

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult {
        if (world.isClient) return ActionResult.SUCCESS
        LoungeClosingHooks.useBigDoor(player as ServerPlayerEntity, state)
        return ActionResult.CONSUME
    }

    companion object {
        val FACING: DirectionProperty = Properties.HORIZONTAL_FACING
        val COL: IntProperty = IntProperty.of("col", 0, 5)
        val ROW: IntProperty = IntProperty.of("row", 0, 3)
    }
}

/**
 * The reader by the Calm room's door. Right-clicked by someone carrying the whole master card, crafted
 * from its four corners, it opens the iron doors next to it ([CardReaderBlock.setDoors]) and stays
 * [UNLOCKED]. Without the card it does nothing at all. The card is kept.
 */
class CalmReaderBlock(settings: Settings) : Block(settings) {

    init {
        defaultState = stateManager.defaultState.with(FACING, Direction.NORTH).with(UNLOCKED, false)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FACING, UNLOCKED)
    }

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        SHAPES.getValue(state.get(FACING))

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult {
        if (world.isClient) return ActionResult.SUCCESS
        if (state.get(UNLOCKED) || !player.inventory.containsAny { it.isOf(ModItems.MASTER_CARD) }) return ActionResult.CONSUME
        world.setBlockState(pos, state.with(UNLOCKED, true))
        world.playSound(null, pos, ModSounds.CARD_READER_ACCEPT, SoundCategory.BLOCKS, 1f, 1f)
        CardReaderBlock.setDoors(world as ServerWorld, pos, open = true)
        return ActionResult.CONSUME
    }

    companion object {
        val FACING: DirectionProperty = Properties.HORIZONTAL_FACING
        val UNLOCKED: BooleanProperty = BooleanProperty.of("unlocked")

        private val SHAPES = BlockShapes.horizontal(listOf(BlockShapes.box(5, 4, 14, 11, 12, 16)))
    }
}

/** A small emergency lamp on a wall, dark until the blackout ([LoungeClosingHooks]) lights it ([LIT]). */
class EmergencyLampBlock(settings: Settings) : Block(settings) {

    init {
        defaultState = stateManager.defaultState.with(FACING, Direction.NORTH).with(LIT, false)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FACING, LIT)
    }

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        SHAPES.getValue(state.get(FACING))

    companion object {
        val FACING: DirectionProperty = Properties.HORIZONTAL_FACING
        val LIT: BooleanProperty = BooleanProperty.of("lit")

        /** Dim: enough to see the next one, not the room. */
        const val LIGHT_LEVEL = 5

        private val SHAPES = BlockShapes.horizontal(listOf(BlockShapes.box(6, 5, 14, 10, 11, 16)))
    }
}
