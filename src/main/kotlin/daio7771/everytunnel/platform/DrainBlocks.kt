package daio7771.everytunnel.platform

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.LadderBlock
import net.minecraft.block.ShapeContext
import net.minecraft.state.StateManager
import net.minecraft.state.property.BooleanProperty
import net.minecraft.state.property.DirectionProperty
import net.minecraft.state.property.IntProperty
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import net.minecraft.world.BlockView

/**
 * The ladder from Level 5's chemical room's hatch down to the drain (layer 3): rusty steel, climbed like any
 * ladder (it is in the `minecraft:climbable` tag). Its model is vanilla's ladder with its own texture.
 */
class DrainLadderBlock(settings: Settings) : LadderBlock(settings)

/**
 * A bare bulb in a steel cage, hanging from a ceiling or on a wall: Level 5's drain's lamps, dim and yellow
 * ([LIGHT_LEVEL]); on, flickering or dead like a fluorescent light ([FluorescentLightBlock]).
 */
class CageLampBlock(settings: Settings) : FluorescentLightBlock(settings) {

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        SHAPES.getValue(state.get(FluorescentLightBlock.FACING) to state.get(FluorescentLightBlock.AXIS))

    companion object {
        /** Under the lab's 13: pools of yellow light, the dark between them. */
        private const val LIGHT_LEVEL = 10

        fun luminance(state: BlockState): Int = if (state.get(FluorescentLightBlock.LIT)) LIGHT_LEVEL else 0

        private val SHAPES = shapes(BlockShapes.box(5, 4, 5, 11, 16, 11))
    }
}

/** A square ceiling panel light (the director's office), on like a fluorescent light ([FluorescentLightBlock]). */
class PanelLightBlock(settings: Settings) : FluorescentLightBlock(settings) {

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        SHAPES.getValue(state.get(FluorescentLightBlock.FACING) to state.get(FluorescentLightBlock.AXIS))

    companion object {
        private val SHAPES = shapes(BlockShapes.box(2, 14.5, 2, 14, 16, 14))
    }
}

/** A light fixture's shape per facing and axis, turned like its model ([FluorescentLightBlock.modelRotation]). */
private fun shapes(box: BlockShapes.Box): Map<Pair<Direction, Direction.Axis>, VoxelShape> =
    Direction.entries.flatMap { facing -> listOf(Direction.Axis.X, Direction.Axis.Z).map { facing to it } }
        .associateWith { (facing, axis) ->
            val (x, y) = FluorescentLightBlock.modelRotation(facing, axis)
            BlockShapes.rotated(listOf(box), x, y)
        }

/**
 * Steel railing along the edges of a catwalk over water (Level 5's Junction): on each side where [NORTH],
 * [EAST], [SOUTH] or [WEST] is true. Too high to step or jump over: its collision reaches a block and a half,
 * like a fence's.
 */
class CatwalkRailBlock(settings: Settings) : Block(settings) {

    init {
        defaultState = stateManager.defaultState.with(NORTH, false).with(EAST, false).with(SOUTH, false).with(WEST, false)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(NORTH, EAST, SOUTH, WEST)
    }

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        shape(state, 16.0)

    @Deprecated("Deprecated in Java")
    override fun getCollisionShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        shape(state, 24.0)

    companion object {
        val NORTH: BooleanProperty = Properties.NORTH
        val EAST: BooleanProperty = Properties.EAST
        val SOUTH: BooleanProperty = Properties.SOUTH
        val WEST: BooleanProperty = Properties.WEST

        private val SIDES = listOf(
            NORTH to doubleArrayOf(0.0, 0.0, 16.0, 1.5), SOUTH to doubleArrayOf(0.0, 14.5, 16.0, 16.0),
            WEST to doubleArrayOf(0.0, 0.0, 1.5, 16.0), EAST to doubleArrayOf(14.5, 0.0, 16.0, 16.0),
        )

        private fun shape(state: BlockState, height: Double): VoxelShape =
            SIDES.filter { (side, _) -> state.get(side) }
                .map { (_, b) -> Block.createCuboidShape(b[0], 0.0, b[1], b[2], height, b[3]) }
                .fold(VoxelShapes.empty()) { all, one -> VoxelShapes.union(all, one) }
    }
}

/**
 * A chalk arrow on a wall of the drain (by the foot of the ladder, pointing the way to the Hideout), [COL] of its
 * three parts, left to right as one faces it. Flat on the wall, nothing to bump into.
 */
class ChalkArrowBlock(settings: Settings) : Block(settings) {

    init {
        defaultState = stateManager.defaultState.with(FACING, Direction.NORTH).with(COL, 0)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FACING, COL)
    }

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        SHAPES.getValue(state.get(FACING))

    companion object {
        val FACING: DirectionProperty = Properties.HORIZONTAL_FACING
        val COL: IntProperty = IntProperty.of("col", 0, 2)

        private val SHAPES = BlockShapes.horizontal(listOf(BlockShapes.box(0, 4, 15.5, 16, 12, 16)))
    }
}
