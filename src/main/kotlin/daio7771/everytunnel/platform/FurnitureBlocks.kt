package daio7771.everytunnel.platform

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.item.ItemPlacementContext
import net.minecraft.state.StateManager
import net.minecraft.state.property.BooleanProperty
import net.minecraft.state.property.DirectionProperty
import net.minecraft.state.property.Properties
import net.minecraft.util.BlockMirror
import net.minecraft.util.BlockRotation
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.BlockView
import net.minecraft.world.WorldAccess

/*
 * Furniture that joins its neighbours of the same kind, so the model can leave out the legs,
 * ends or caps where two pieces meet (Level 3's offices and library). Each kind works out its
 * joins when placed and whenever a neighbour changes; `tools/map/offices/` sets the same
 * states when it builds the level, since `/setblock` takes the state as given. Models are in
 * tools/blocks/generate.py; boxes are in pixels for the model facing north (see [BlockShapes])
 * and give the outline (and the collision, unless the settings say `noCollision`).
 */

/**
 * A row piece (desk, counter, sofa, bench, whiteboard): it faces a way, and joins the same
 * block facing the same way on its [LEFT] and [RIGHT], as seen from its front.
 */
class RowFurnitureBlock(settings: Settings, boxes: List<BlockShapes.Box>) : Block(settings) {

    private val shapes: Map<Direction, VoxelShape> = BlockShapes.horizontal(boxes)

    init {
        defaultState = stateManager.defaultState.with(FACING, Direction.NORTH).with(LEFT, false).with(RIGHT, false)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FACING, LEFT, RIGHT)
    }

    override fun getPlacementState(ctx: ItemPlacementContext): BlockState =
        joined(defaultState.with(FACING, ctx.horizontalPlayerFacing.opposite), ctx.world, ctx.blockPos)

    @Deprecated("Deprecated in Java")
    override fun getStateForNeighborUpdate(
        state: BlockState, direction: Direction, neighborState: BlockState,
        world: WorldAccess, pos: BlockPos, neighborPos: BlockPos,
    ): BlockState = if (direction.axis.isHorizontal) joined(state, world, pos) else state

    private fun joined(state: BlockState, world: BlockView, pos: BlockPos): BlockState {
        val facing = state.get(FACING)
        fun joins(side: Direction): Boolean {
            val other = world.getBlockState(pos.offset(side))
            return other.isOf(this) && other.get(FACING) == facing
        }
        return state.with(LEFT, joins(left(facing))).with(RIGHT, joins(left(facing).opposite))
    }

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        shapes.getValue(state.get(FACING))

    @Deprecated("Deprecated in Java")
    override fun rotate(state: BlockState, rotation: BlockRotation): BlockState =
        state.with(FACING, rotation.rotate(state.get(FACING)))

    @Deprecated("Deprecated in Java")
    override fun mirror(state: BlockState, mirror: BlockMirror): BlockState =
        state.rotate(mirror.getRotation(state.get(FACING))).with(LEFT, state.get(RIGHT)).with(RIGHT, state.get(LEFT))

    companion object {
        val FACING: DirectionProperty = Properties.HORIZONTAL_FACING
        val LEFT: BooleanProperty = BooleanProperty.of("left")
        val RIGHT: BooleanProperty = BooleanProperty.of("right")

        /** The side on the left of someone standing in front of a piece facing [facing], looking at it. */
        fun left(facing: Direction): Direction = facing.rotateYClockwise()
    }
}

/**
 * A tile of a bigger table (meeting table, reading table): it joins the same block on any of
 * its four sides, so tiles make one top with legs only at its outer corners. No facing.
 */
class TileFurnitureBlock(settings: Settings, boxes: List<BlockShapes.Box>) : Block(settings) {

    private val shape: VoxelShape = BlockShapes.rotated(boxes, 0, 0)

    init {
        defaultState = SIDES.values.fold(stateManager.defaultState) { s, p -> s.with(p, false) }
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(*SIDES.values.toTypedArray())
    }

    override fun getPlacementState(ctx: ItemPlacementContext): BlockState =
        SIDES.entries.fold(defaultState) { s, (side, p) -> s.with(p, ctx.world.getBlockState(ctx.blockPos.offset(side)).isOf(this)) }

    @Deprecated("Deprecated in Java")
    override fun getStateForNeighborUpdate(
        state: BlockState, direction: Direction, neighborState: BlockState,
        world: WorldAccess, pos: BlockPos, neighborPos: BlockPos,
    ): BlockState = SIDES[direction]?.let { state.with(it, neighborState.isOf(this)) } ?: state

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape = shape

    @Deprecated("Deprecated in Java")
    override fun rotate(state: BlockState, rotation: BlockRotation): BlockState =
        SIDES.entries.fold(state) { s, (side, p) -> s.with(SIDES.getValue(rotation.rotate(side)), state.get(p)) }

    @Deprecated("Deprecated in Java")
    override fun mirror(state: BlockState, mirror: BlockMirror): BlockState =
        SIDES.entries.fold(state) { s, (side, p) -> s.with(SIDES.getValue(mirror.apply(side)), state.get(p)) }

    companion object {
        val SIDES: Map<Direction, BooleanProperty> = mapOf(
            Direction.NORTH to Properties.NORTH, Direction.EAST to Properties.EAST,
            Direction.SOUTH to Properties.SOUTH, Direction.WEST to Properties.WEST,
        )
    }
}

/**
 * A tall piece made of stacked blocks (bookshelf, server rack, locker, metal shelf, vending
 * machine): it faces a way and joins the same block facing the same way [UP] and [DOWN], so
 * the stack has one base and one top.
 */
open class StackFurnitureBlock(settings: Settings, boxes: List<BlockShapes.Box>) : Block(settings) {

    private val shapes: Map<Direction, VoxelShape> = BlockShapes.horizontal(boxes)

    init {
        defaultState = stateManager.defaultState.with(FACING, Direction.NORTH).with(UP, false).with(DOWN, false)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FACING, UP, DOWN)
    }

    override fun getPlacementState(ctx: ItemPlacementContext): BlockState =
        joined(defaultState.with(FACING, ctx.horizontalPlayerFacing.opposite), ctx.world, ctx.blockPos)

    @Deprecated("Deprecated in Java")
    override fun getStateForNeighborUpdate(
        state: BlockState, direction: Direction, neighborState: BlockState,
        world: WorldAccess, pos: BlockPos, neighborPos: BlockPos,
    ): BlockState = if (direction.axis.isVertical) joined(state, world, pos) else state

    private fun joined(state: BlockState, world: BlockView, pos: BlockPos): BlockState {
        fun joins(side: Direction): Boolean {
            val other = world.getBlockState(pos.offset(side))
            return other.isOf(this) && other.get(FACING) == state.get(FACING)
        }
        return state.with(UP, joins(Direction.UP)).with(DOWN, joins(Direction.DOWN))
    }

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        shapes.getValue(state.get(FACING))

    @Deprecated("Deprecated in Java")
    override fun rotate(state: BlockState, rotation: BlockRotation): BlockState =
        state.with(FACING, rotation.rotate(state.get(FACING)))

    @Deprecated("Deprecated in Java")
    override fun mirror(state: BlockState, mirror: BlockMirror): BlockState =
        state.rotate(mirror.getRotation(state.get(FACING)))

    companion object {
        val FACING: DirectionProperty = Properties.HORIZONTAL_FACING
        val UP: BooleanProperty = Properties.UP
        val DOWN: BooleanProperty = Properties.DOWN
    }
}
