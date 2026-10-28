package daio7771.everytunnel.platform

import daio7771.everytunnel.core.puzzle.ValveCode
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemPlacementContext
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.state.StateManager
import net.minecraft.state.property.BooleanProperty
import net.minecraft.state.property.DirectionProperty
import net.minecraft.state.property.EnumProperty
import net.minecraft.state.property.IntProperty
import net.minecraft.state.property.Properties
import net.minecraft.util.ActionResult
import net.minecraft.util.BlockMirror
import net.minecraft.util.BlockRotation
import net.minecraft.util.Hand
import net.minecraft.util.StringIdentifiable
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.BlockView
import net.minecraft.world.World

/** The letter on a valve's plates. */
enum class ValveLetter(private val id: String) : StringIdentifiable {
    A("a"), B("b"), C("c"), D("d"), E("e");

    override fun asString(): String = id
}

/**
 * A gate valve inline in a vertical pipe on a wall: the pipe runs through the block (it lines
 * up with [ConnectingPropBlock] pipes above and below), a stub goes back into the wall, and a
 * red handwheel on a stem looks the way the block is [FACING]. [LETTER] is painted on the
 * plates either side of the body, next to a little window with the dial, [TURNS] (0 to 9).
 *
 * Right-clicked, it turns once more ([ValveCode.next]: from 9 back to 0) with a clunk and a
 * gurgle in the pipe, and Level 3's puzzle checks the valves ([ValveHooks.turned]). Once they
 * are all right they are [OPEN]: the rising stem stands out through the wheel, turned an eighth
 * round, and they no longer turn (a dull knock). The model is in tools/blocks/generate.py and
 * matches [SHAPES].
 */
class ValveBlock(settings: Settings) : Block(settings) {

    init {
        defaultState = stateManager.defaultState.with(FACING, Direction.NORTH).with(OPEN, false).with(LETTER, ValveLetter.A)
            .with(TURNS, 0)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FACING, OPEN, LETTER, TURNS)
    }

    override fun getPlacementState(ctx: ItemPlacementContext): BlockState =
        defaultState.with(FACING, ctx.horizontalPlayerFacing.opposite)

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        SHAPES.getValue(state.get(FACING))

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult {
        if (world.isClient) return ActionResult.SUCCESS
        if (state.get(OPEN)) {
            world.playSound(null, pos, ModSounds.VALVE_TURN, SoundCategory.BLOCKS, 0.5f, 0.55f)        // stuck: a dull knock
            return ActionResult.CONSUME
        }
        world.setBlockState(pos, state.with(TURNS, ValveCode.next(state.get(TURNS))))
        world.playSound(null, pos, ModSounds.VALVE_TURN, SoundCategory.BLOCKS, 1f, 0.95f + world.random.nextFloat() * 0.1f)
        ValveHooks.turned(world as ServerWorld, pos)
        return ActionResult.CONSUME
    }

    @Deprecated("Deprecated in Java")
    override fun rotate(state: BlockState, rotation: BlockRotation): BlockState =
        state.with(FACING, rotation.rotate(state.get(FACING)))

    @Deprecated("Deprecated in Java")
    override fun mirror(state: BlockState, mirror: BlockMirror): BlockState =
        state.rotate(mirror.getRotation(state.get(FACING)))

    companion object {
        val FACING: DirectionProperty = Properties.HORIZONTAL_FACING
        val OPEN: BooleanProperty = Properties.OPEN
        val LETTER: EnumProperty<ValveLetter> = EnumProperty.of("letter", ValveLetter::class.java)
        val TURNS: IntProperty = IntProperty.of("turns", 0, ValveCode.POSITIONS - 1)

        /** Facing north: the pipe, the stub into the wall (south), the body, the wheel and its stem. */
        private val SHAPES = BlockShapes.horizontal(listOf(
            BlockShapes.box(5, 0, 5, 11, 16, 11),
            BlockShapes.box(5, 5, 11, 11, 11, 16),
            BlockShapes.box(4, 4, 4, 12, 12, 12),
            BlockShapes.box(2, 2, 1, 14, 14, 4),
        ))
    }
}
