package daio7771.everytunnel.platform

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.network.ServerPlayerEntity
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
 * One of the nine parts of the Game room's dartboard ([DartHooks]), a plate flat on the wall: [COL] 0
 * on the left as seen from the front, [ROW] 0 at the bottom. [HINT] above 0 lights the number at that
 * place of the sequence. Set by the map generator; models and textures in tools/blocks/generate.py.
 */
class DartboardBlock(settings: Settings) : Block(settings) {

    init {
        defaultState = stateManager.defaultState.with(FACING, Direction.NORTH).with(COL, 1).with(ROW, 1).with(HINT, 0)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FACING, COL, ROW, HINT)
    }

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        SHAPES.getValue(state.get(FACING))

    companion object {
        val FACING: DirectionProperty = Properties.HORIZONTAL_FACING
        val COL: IntProperty = IntProperty.of("col", 0, 2)
        val ROW: IntProperty = IntProperty.of("row", 0, 2)
        val HINT: IntProperty = IntProperty.of("hint", 0, 3)

        private val SHAPES = BlockShapes.horizontal(listOf(BlockShapes.box(0, 0, 14, 16, 16, 16)))
    }
}

/** The tray of darts on its little table: right-click takes the 3, or gives them back ([DartHooks.useTray]). */
class DartTrayBlock(settings: Settings) : Block(settings) {

    init {
        defaultState = stateManager.defaultState.with(FACING, Direction.NORTH).with(HAS, true)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FACING, HAS)
    }

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        SHAPES.getValue(state.get(FACING))

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult {
        if (world.isClient) return ActionResult.SUCCESS
        DartHooks.useTray(player as ServerPlayerEntity, state)
        return ActionResult.CONSUME
    }

    companion object {
        val FACING: DirectionProperty = Properties.HORIZONTAL_FACING
        val HAS: BooleanProperty = BooleanProperty.of("has")

        private val SHAPES = BlockShapes.horizontal(listOf(BlockShapes.box(2, 0, 4, 14, 3, 12)))
    }
}

/** The brass hatch under the dartboard: it opens on the third hit, with the piece inside ([DartHooks.takePiece]). */
class DartHatchBlock(settings: Settings) : Block(settings) {

    init {
        defaultState = stateManager.defaultState.with(FACING, Direction.NORTH).with(OPEN, false).with(PIECE, false)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FACING, OPEN, PIECE)
    }

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        SHAPES.getValue(state.get(FACING))

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult {
        if (!state.get(PIECE)) return ActionResult.PASS
        if (world.isClient) return ActionResult.SUCCESS
        DartHooks.takePiece(player as ServerPlayerEntity, pos, state)
        return ActionResult.CONSUME
    }

    companion object {
        val FACING: DirectionProperty = Properties.HORIZONTAL_FACING
        val OPEN: BooleanProperty = BooleanProperty.of("open")
        val PIECE: BooleanProperty = BooleanProperty.of("piece")

        private val SHAPES = BlockShapes.horizontal(listOf(BlockShapes.box(3, 2, 13, 13, 12, 16)))
    }
}
