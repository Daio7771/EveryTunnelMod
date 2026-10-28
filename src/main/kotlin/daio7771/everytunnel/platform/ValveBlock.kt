package daio7771.everytunnel.platform

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.item.ItemPlacementContext
import net.minecraft.state.StateManager
import net.minecraft.state.property.BooleanProperty
import net.minecraft.state.property.DirectionProperty
import net.minecraft.state.property.EnumProperty
import net.minecraft.state.property.Properties
import net.minecraft.util.BlockMirror
import net.minecraft.util.BlockRotation
import net.minecraft.util.StringIdentifiable
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.BlockView

/** The letter on a valve's plates. */
enum class ValveLetter(private val id: String) : StringIdentifiable {
    A("a"), B("b"), C("c"), D("d"), E("e");

    override fun asString(): String = id
}

/**
 * A gate valve inline in a vertical pipe on a wall: the pipe runs through the block (it lines
 * up with [ConnectingPropBlock] pipes above and below), a stub goes back into the wall, and a
 * red handwheel on a stem looks the way the block is [FACING]. [OPEN] shows a rising stem
 * standing out through the wheel, which is turned an eighth round; closed, the stem sits in the
 * hub. [LETTER] is painted on the plates either side of the body (Level 3's valves A to E).
 *
 * The valves of Level 3 are placed by the map generator; turning them (the puzzle) comes
 * later, so for now nothing changes [OPEN] in game. The model is in tools/blocks/generate.py
 * and matches [SHAPES].
 */
class ValveBlock(settings: Settings) : Block(settings) {

    init {
        defaultState = stateManager.defaultState.with(FACING, Direction.NORTH).with(OPEN, false).with(LETTER, ValveLetter.A)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FACING, OPEN, LETTER)
    }

    override fun getPlacementState(ctx: ItemPlacementContext): BlockState =
        defaultState.with(FACING, ctx.horizontalPlayerFacing.opposite)

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        SHAPES.getValue(state.get(FACING))

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

        /** Facing north: the pipe, the stub into the wall (south), the body, the wheel and its stem. */
        private val SHAPES = BlockShapes.horizontal(listOf(
            BlockShapes.box(5, 0, 5, 11, 16, 11),
            BlockShapes.box(5, 5, 11, 11, 11, 16),
            BlockShapes.box(4, 4, 4, 12, 12, 12),
            BlockShapes.box(2, 2, 1, 14, 14, 4),
        ))
    }
}
