package daio7771.everytunnel.platform

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemPlacementContext
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.state.StateManager
import net.minecraft.state.property.DirectionProperty
import net.minecraft.state.property.Properties
import net.minecraft.util.ActionResult
import net.minecraft.util.BlockMirror
import net.minecraft.util.BlockRotation
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.BlockView
import net.minecraft.world.World

/**
 * A cork board on a wall with a note pinned to it, [FACING] the way its front looks.
 * Right-clicked, the note opens on paper ([NoteHooks]); which note is the level's spawn point
 * tagged `note` at the board. The model is in tools/blocks/generate.py and matches [SHAPES].
 */
class NoteBoardBlock(settings: Settings) : Block(settings) {

    init {
        defaultState = stateManager.defaultState.with(FACING, Direction.NORTH)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FACING)
    }

    override fun getPlacementState(ctx: ItemPlacementContext): BlockState =
        defaultState.with(FACING, ctx.horizontalPlayerFacing.opposite)

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        SHAPES.getValue(state.get(FACING))

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult {
        if (world.isClient) return ActionResult.SUCCESS
        NoteHooks.read(player as ServerPlayerEntity, pos)
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

        /** The board flat on the wall behind it. */
        private val SHAPES = BlockShapes.horizontal(listOf(BlockShapes.box(1, 2, 15, 15, 14, 16)))
    }
}
