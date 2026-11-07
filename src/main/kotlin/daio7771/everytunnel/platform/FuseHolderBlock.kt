package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.sound.SoundCategory
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
 * One slot of Level 5's fuse box, by the stairs' lever: holders side by side on a wall make the box
 * ([box]). Each takes only the fuse of the amperage on its label ([AMPS], an index into
 * [ModItems.FUSE_AMPS]): aimed at with that fuse in hand, a right click puts it in ([FILLED],
 * `block.fuse.insert`); any other fuse snaps back out (`block.fuse.reject`), and nothing else happens.
 * Once every holder of the box is full, the box hums (`block.fuse.power`) and its lamps light ([LIT]):
 * the lever beside it works from then on ([unpoweredNear], [LabDoorHooks]).
 *
 * The map puts the box up with one fuse already in; the level built anew puts it back so. Thin, on its
 * wall: nothing collides with it.
 */
class FuseHolderBlock(settings: Settings) : Block(settings) {

    init {
        defaultState = stateManager.defaultState.with(FACING, Direction.NORTH).with(AMPS, 0).with(FILLED, false).with(LIT, false)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FACING, AMPS, FILLED, LIT)
    }

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        SHAPES.getValue(state.get(FACING))

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult {
        val stack = player.getStackInHand(hand)
        val fuse = ModItems.FUSES.indexOf(stack.item)
        if (fuse < 0 || state.get(FILLED)) return ActionResult.PASS
        if (world.isClient) return ActionResult.SUCCESS
        if (fuse != state.get(AMPS)) {
            world.playSound(null, pos, ModSounds.FUSE_REJECT, SoundCategory.BLOCKS, 0.9f, 1f)
            return ActionResult.CONSUME
        }
        world.setBlockState(pos, state.with(FILLED, true))
        if (!player.abilities.creativeMode) stack.decrement(1)
        world.playSound(null, pos, ModSounds.FUSE_INSERT, SoundCategory.BLOCKS, 0.9f, 1f)
        val holders = box(world, pos, state.get(FACING))
        if (holders.all { world.getBlockState(it).get(FILLED) }) {
            for (holder in holders) world.setBlockState(holder, world.getBlockState(holder).with(LIT, true))
            world.playSound(null, pos, ModSounds.FUSE_POWER, SoundCategory.BLOCKS, 1.2f, 1f)
            EveryTunnel.LOGGER.info("{} completes the fuse box at {}", player.name.string, pos)
        }
        return ActionResult.CONSUME
    }

    companion object {
        val FACING: DirectionProperty = Properties.HORIZONTAL_FACING
        val AMPS: IntProperty = IntProperty.of("amps", 0, 4)
        val FILLED: BooleanProperty = BooleanProperty.of("filled")
        val LIT: BooleanProperty = BooleanProperty.of("lit")

        private val SHAPES = BlockShapes.horizontal(listOf(BlockShapes.box(1, 1, 13, 15, 15, 16)))

        /** How far along its wall a box may run. */
        private const val MAX_SLOTS = 8

        /** The holders of the box [pos] is in: those beside it along its wall, facing the same way, one after another. */
        fun box(world: World, pos: BlockPos, facing: Direction): List<BlockPos> {
            val out = mutableListOf(pos)
            for (side in listOf(facing.rotateYClockwise(), facing.rotateYCounterclockwise())) {
                var next = pos.offset(side)
                while (out.size < MAX_SLOTS) {
                    val state = world.getBlockState(next)
                    if (!state.isOf(ModBlocks.FUSE_HOLDER) || state.get(FACING) != facing) break
                    out += next
                    next = next.offset(side)
                }
            }
            return out
        }

        /** A fuse box within [range] blocks of [pos] (a lever) that is not lit yet: the lever has no power. */
        fun unpoweredNear(world: World, pos: BlockPos, range: Int = 4): Boolean =
            BlockPos.iterate(pos.add(-range, -1, -range), pos.add(range, 1, range)).any {
                val state = world.getBlockState(it)
                state.isOf(ModBlocks.FUSE_HOLDER) && !state.get(LIT)
            }
    }
}
