package daio7771.everytunnel.platform

import net.minecraft.entity.ai.pathing.NavigationType
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.item.ItemPlacementContext
import net.minecraft.state.StateManager
import net.minecraft.state.property.BooleanProperty
import net.minecraft.state.property.EnumProperty
import net.minecraft.state.property.IntProperty
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.BlockView

/**
 * One block of a sliding elevator door: a brushed steel sheet, 2 pixels thick, through the
 * middle of the block. [AXIS] is the horizontal direction the sheet runs (and slides)
 * along, [POSITIVE] whether it opens towards +x/+z, and [STAGE] how far it has slid
 * (0 closed, 1 and 2 on its way into the wall; open is air).
 *
 * The intro of a level (`IntroHooks`) closes and opens them in the zones tagged
 * `elevator_door`: the half of the opening on the low side slides that way, the other
 * half the other way.
 */
class ElevatorDoorBlock(settings: Settings) : Block(settings) {

    /** Mobs path round it, not into it (see [BlockShapes.pathfindThrough]). */
    @Deprecated("Deprecated in Java")
    override fun canPathfindThrough(state: BlockState, world: BlockView, pos: BlockPos, type: NavigationType): Boolean =
        BlockShapes.pathfindThrough(state, world, pos, type)

    init {
        defaultState = stateManager.defaultState.with(AXIS, Direction.Axis.X).with(POSITIVE, true).with(STAGE, 0)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(AXIS, POSITIVE, STAGE)
    }

    override fun getPlacementState(ctx: ItemPlacementContext): BlockState =
        defaultState.with(AXIS, if (ctx.horizontalPlayerFacing.axis == Direction.Axis.X) Direction.Axis.Z else Direction.Axis.X)

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape {
        val offset = state.get(STAGE) * SLIDE_PIXELS.toDouble()
        val (from, to) = if (state.get(POSITIVE)) offset to 16.0 else 0.0 to 16.0 - offset
        return if (state.get(AXIS) == Direction.Axis.X) {
            createCuboidShape(from, 0.0, 7.0, to, 16.0, 9.0)
        } else {
            createCuboidShape(7.0, 0.0, from, 9.0, 16.0, to)
        }
    }

    companion object {
        val AXIS: EnumProperty<Direction.Axis> = Properties.HORIZONTAL_AXIS
        val POSITIVE: BooleanProperty = BooleanProperty.of("positive")
        val STAGE: IntProperty = IntProperty.of("stage", 0, 2)

        /** Pixels the sheet slides per stage (the models in tools/blocks/generate.py match). */
        const val SLIDE_PIXELS = 5
    }
}
