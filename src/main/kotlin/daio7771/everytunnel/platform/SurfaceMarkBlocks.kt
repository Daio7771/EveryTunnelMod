package daio7771.everytunnel.platform

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.item.ItemPlacementContext
import net.minecraft.state.StateManager
import net.minecraft.state.property.DirectionProperty
import net.minecraft.state.property.IntProperty
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.BlockView

/**
 * A stain, crack or mark lying flat on a floor, wall or ceiling (oil, dirt, damp,
 * rust, mould...). No collision, and it never needs support: it stays where it was
 * put, even if the surface behind it goes.
 *
 * [FACING] is the way the mark faces, away from its surface (a floor mark faces up).
 * [PATTERN] picks the picture; what each number shows depends on the surface (see
 * [patterns]). Placed by hand it gets a random pattern.
 */
class StainBlock(settings: Settings) : Block(settings) {

    init {
        defaultState = stateManager.defaultState.with(FACING, Direction.UP).with(PATTERN, 0)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FACING, PATTERN)
    }

    override fun getPlacementState(ctx: ItemPlacementContext): BlockState =
        defaultState.with(FACING, ctx.side).with(PATTERN, ctx.world.random.nextInt(patterns(ctx.side)))

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        SHAPES.getValue(state.get(FACING))

    companion object {
        val FACING: DirectionProperty = Properties.FACING
        val PATTERN: IntProperty = IntProperty.of("pattern", 0, 15)

        /** How many pictures a mark facing this way has (floor, ceiling, walls). */
        fun patterns(facing: Direction): Int = when (facing) {
            Direction.UP -> 14
            Direction.DOWN -> 6
            else -> 10
        }

        /** A 1-pixel sheet against the surface behind the mark. */
        private val SHAPES: Map<Direction, VoxelShape> = mapOf(
            Direction.UP to createCuboidShape(0.0, 0.0, 0.0, 16.0, 1.0, 16.0),
            Direction.DOWN to createCuboidShape(0.0, 15.0, 0.0, 16.0, 16.0, 16.0),
            Direction.SOUTH to createCuboidShape(0.0, 0.0, 0.0, 16.0, 16.0, 1.0),
            Direction.NORTH to createCuboidShape(0.0, 0.0, 15.0, 16.0, 16.0, 16.0),
            Direction.EAST to createCuboidShape(0.0, 0.0, 0.0, 1.0, 16.0, 16.0),
            Direction.WEST to createCuboidShape(15.0, 0.0, 0.0, 16.0, 16.0, 16.0),
        )
    }
}

/**
 * Standing water on the floor. No collision; walking through it adds a splash to the
 * footsteps (the block is in `minecraft:combination_step_sound_blocks`, so the floor's
 * own step plays too).
 */
class PuddleBlock(settings: Settings) : Block(settings) {

    init {
        defaultState = stateManager.defaultState.with(PATTERN, 0)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(PATTERN)
    }

    override fun getPlacementState(ctx: ItemPlacementContext): BlockState =
        defaultState.with(PATTERN, ctx.world.random.nextInt(PATTERNS))

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        SHAPE

    companion object {
        const val PATTERNS = 4
        val PATTERN: IntProperty = IntProperty.of("pattern", 0, PATTERNS - 1)
        private val SHAPE: VoxelShape = createCuboidShape(0.0, 0.0, 0.0, 16.0, 1.0, 16.0)
    }
}
