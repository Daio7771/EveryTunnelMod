package daio7771.everytunnel.platform

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.state.StateManager
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
 * One of the twelve parts of Level 5's safety briefing in the Waiting room, a cork board flat on the wall: [COL]
 * 0 on the left as seen from the front, [ROW] 0 at the bottom. Four columns of a block and a half run across its
 * six blocks, each a pencil drawing over a technical sheet. Right-clicking anywhere in a column, drawing or sheet,
 * opens its sheet ([NoteHooks]): the note is the level's spawn point tagged `note` at that column's cell in the
 * bottom row ([NOTE_COLS]). Set by the map generator; pictures in tools/blocks/briefing.py, models in generate.py.
 */
class BriefingBoardBlock(settings: Settings) : Block(settings) {

    init {
        defaultState = stateManager.defaultState.with(FACING, Direction.NORTH).with(COL, 0).with(ROW, 0)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FACING, COL, ROW)
    }

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        SHAPES.getValue(state.get(FACING))

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult {
        if (world.isClient) return ActionResult.SUCCESS
        // How far across the board the click landed, in blocks from its left edge, and so which column.
        val right = state.get(FACING).rotateYCounterclockwise()
        val inside = hit.pos.subtract(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
        val along = when {
            right.offsetX > 0 -> inside.x
            right.offsetX < 0 -> 1.0 - inside.x
            right.offsetZ > 0 -> inside.z
            else -> 1.0 - inside.z
        }.coerceIn(0.0, 0.999)
        val column = ((state.get(COL) + along) / COLUMN_WIDTH).toInt().coerceIn(0, NOTE_COLS.size - 1)
        val note = pos.offset(right, NOTE_COLS[column] - state.get(COL)).down(state.get(ROW))
        NoteHooks.read(player as ServerPlayerEntity, note)
        return ActionResult.CONSUME
    }

    companion object {
        val FACING: DirectionProperty = Properties.HORIZONTAL_FACING
        val COL: IntProperty = IntProperty.of("col", 0, 5)
        val ROW: IntProperty = IntProperty.of("row", 0, 1)

        /** A column's width, in blocks. */
        private const val COLUMN_WIDTH = 1.5

        /** The cell of each column, in the bottom row, where its note's spawn point stands (tools/map/lab/furnish.py). */
        val NOTE_COLS = listOf(0, 2, 3, 5)

        private val SHAPES = BlockShapes.horizontal(listOf(BlockShapes.box(0, 0, 14, 16, 16, 16)))
    }
}

/**
 * One of the four parts of the printed plastic plate over the safety briefing, SAFETY BRIEFING ([COL] 0 on the
 * left as seen from the front). Only a picture: it does nothing.
 */
class BriefingSignBlock(settings: Settings) : Block(settings) {

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
        val COL: IntProperty = IntProperty.of("col", 0, 3)

        private val SHAPES = BlockShapes.horizontal(listOf(BlockShapes.box(0, 1, 15, 16, 11.25, 16)))
    }
}
