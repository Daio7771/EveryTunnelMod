package daio7771.everytunnel.chapter2.level2.platform

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.block.StairsBlock
import net.minecraft.state.StateManager
import daio7771.everytunnel.common.platform.BlockShapes
import daio7771.everytunnel.common.platform.FluorescentLightBlock
import net.minecraft.state.property.DirectionProperty
import net.minecraft.state.property.EnumProperty
import net.minecraft.state.property.IntProperty
import net.minecraft.util.StringIdentifiable
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.BlockView
import net.minecraft.entity.ai.pathing.NavigationType
import net.minecraft.item.ItemPlacementContext

/** The catacomb brick's stairs: [StairsBlock]'s constructor is protected, so the block is a subclass of its own. */
class CatacombStairsBlock(base: BlockState, settings: Settings) : StairsBlock(base, settings)

/**
 * A cable run along the vault of Chapter 2's Level 2 (the maintenance bulbs hang from it): a thin bar at the top of its block along
 * [AXIS], nothing to bump into. Placed by hand it runs along the player's facing.
 */
class CeilingCableBlock(settings: Settings) : Block(settings) {

    init {
        defaultState = stateManager.defaultState.with(AXIS, Direction.Axis.X)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(AXIS)
    }

    override fun getPlacementState(ctx: ItemPlacementContext): BlockState =
        defaultState.with(AXIS, ctx.horizontalPlayerFacing.axis)

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        if (state.get(AXIS) == Direction.Axis.X) SHAPE_X else SHAPE_Z

    @Deprecated("Deprecated in Java")
    override fun canPathfindThrough(state: BlockState, world: BlockView, pos: BlockPos, type: NavigationType): Boolean = true

    companion object {
        val AXIS: EnumProperty<Direction.Axis> = Properties.HORIZONTAL_AXIS
        private val SHAPE_X: VoxelShape = createCuboidShape(0.0, 14.0, 7.0, 16.0, 16.0, 9.0)
        private val SHAPE_Z: VoxelShape = createCuboidShape(7.0, 14.0, 0.0, 9.0, 16.0, 16.0)
    }
}

/** The colour of a [CatacombLampBlock]'s bulb and shade (vanilla light has no colour: only the fixture is tinted). */
enum class LampTint(private val id: String) : StringIdentifiable {
    WARM("warm"),
    AMBER("amber"),
    COLD("cold"),
    RED("red"),
    WHITE("white");

    override fun asString(): String = id
}

/**
 * Chapter 2 Level 2's lamp: Level 5's caged lamp (same fixture, same on / flicker / off modes, see [FluorescentLightBlock]) but
 * brighter ([LIGHT_LEVEL]) and with a [TINT] on the bulb. The catacombs' route has to be seen in vanilla, without shaders.
 */
class CatacombLampBlock(settings: Settings) : FluorescentLightBlock(settings) {

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        super.appendProperties(builder)
        builder.add(TINT)
    }

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        SHAPES.getValue(state.get(FACING) to state.get(AXIS))

    companion object {
        val TINT: EnumProperty<LampTint> = EnumProperty.of("tint", LampTint::class.java)

        /** Brighter than Level 5's caged lamp (10): enough for the floor under and between two lamps to read without shaders. */
        const val LIGHT_LEVEL = 14

        fun luminance(state: BlockState): Int = if (state.get(LIT)) LIGHT_LEVEL else 0

        private val SHAPES: Map<Pair<Direction, Direction.Axis>, VoxelShape> =
            Direction.entries.flatMap { facing -> listOf(Direction.Axis.X, Direction.Axis.Z).map { facing to it } }
                .associateWith { (facing, axis) ->
                    val (x, y) = modelRotation(facing, axis)
                    BlockShapes.rotated(listOf(BlockShapes.box(5, 4, 5, 11, 16, 11)), x, y)
                }
    }
}

/**
 * A heap of fallen brick or a few bricks on the floor ([PATTERN] picks the arrangement; placed by hand it gets a random one). The
 * rubble is a quarter of a block high and can be walked over; the loose bricks have no collision at all.
 */
class ScatterBlock(settings: Settings, private val shape: VoxelShape) : Block(settings) {

    init {
        defaultState = stateManager.defaultState.with(PATTERN, 0)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(PATTERN)
    }

    override fun getPlacementState(ctx: ItemPlacementContext): BlockState =
        defaultState.with(PATTERN, ctx.world.random.nextInt(PATTERNS))

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape = shape

    @Deprecated("Deprecated in Java")
    override fun canPathfindThrough(state: BlockState, world: BlockView, pos: BlockPos, type: NavigationType): Boolean = true

    companion object {
        const val PATTERNS = 2
        val PATTERN: IntProperty = IntProperty.of("pattern", 0, PATTERNS - 1)
        val RUBBLE_SHAPE: VoxelShape = createCuboidShape(1.0, 0.0, 1.0, 15.0, 4.0, 15.0)
        val BRICKS_SHAPE: VoxelShape = createCuboidShape(2.0, 0.0, 2.0, 14.0, 3.0, 14.0)
    }
}

/** A rust stain flat against a wall ([FACING] is the way it looks, like a vertical [daio7771.everytunnel.common.common.platform.StainBlock]); [PATTERN] picks the picture. */
class RustStainBlock(settings: Settings) : Block(settings) {

    init {
        defaultState = stateManager.defaultState.with(FACING, Direction.NORTH).with(PATTERN, 0)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FACING, PATTERN)
    }

    override fun getPlacementState(ctx: ItemPlacementContext): BlockState =
        defaultState.with(FACING, if (ctx.side.axis.isHorizontal) ctx.side else ctx.horizontalPlayerFacing.opposite)
            .with(PATTERN, ctx.world.random.nextInt(PATTERNS))

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        SHAPES.getValue(state.get(FACING))

    @Deprecated("Deprecated in Java")
    override fun canPathfindThrough(state: BlockState, world: BlockView, pos: BlockPos, type: NavigationType): Boolean = true

    companion object {
        const val PATTERNS = 4
        val FACING: DirectionProperty = Properties.HORIZONTAL_FACING
        val PATTERN: IntProperty = IntProperty.of("pattern", 0, PATTERNS - 1)

        private val SHAPES: Map<Direction, VoxelShape> = mapOf(
            Direction.SOUTH to createCuboidShape(0.0, 0.0, 0.0, 16.0, 16.0, 1.0),
            Direction.NORTH to createCuboidShape(0.0, 0.0, 15.0, 16.0, 16.0, 16.0),
            Direction.EAST to createCuboidShape(0.0, 0.0, 0.0, 1.0, 16.0, 16.0),
            Direction.WEST to createCuboidShape(15.0, 0.0, 0.0, 16.0, 16.0, 16.0),
        )
    }
}
