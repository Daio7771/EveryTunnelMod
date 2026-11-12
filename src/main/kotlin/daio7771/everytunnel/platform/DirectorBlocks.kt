package daio7771.everytunnel.platform

import net.minecraft.block.Block
import net.minecraft.block.BlockRenderType
import net.minecraft.block.BlockSetType
import net.minecraft.block.BlockState
import net.minecraft.block.BlockWithEntity
import net.minecraft.block.DoorBlock
import net.minecraft.block.ShapeContext
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.state.StateManager
import net.minecraft.state.property.DirectionProperty
import net.minecraft.state.property.EnumProperty
import net.minecraft.state.property.IntProperty
import net.minecraft.state.property.Properties
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.StringIdentifiable
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.BlockView
import net.minecraft.world.World

/**
 * One leaf of a pair of doors that open together (Level 5's director's door): opening or shutting one by hand
 * does the same to the leaf beside it, the same block facing the same way, hinged on the other side. Opening
 * the director's starts his scene ([DirectorSceneHooks]).
 */
class PairedDoorBlock(settings: Settings, type: BlockSetType) : DoorBlock(settings, type) {

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult {
        @Suppress("DEPRECATION")
        val result = super.onUse(state, world, pos, player, hand, hit)
        if (!result.isAccepted) return result
        val now = world.getBlockState(pos)
        if (!now.isOf(this)) return result
        if (now.get(OPEN) && player is ServerPlayerEntity) DirectorSceneHooks.doorOpened(player)
        val facing = now.get(FACING)
        for (side in listOf(facing.rotateYClockwise(), facing.rotateYCounterclockwise())) {
            val other = pos.offset(side)
            val leaf = world.getBlockState(other)
            if (!leaf.isOf(this) || leaf.get(FACING) != facing || leaf.get(HINGE) == now.get(HINGE) || leaf.get(HALF) != now.get(HALF)) continue
            if (leaf.get(OPEN) != now.get(OPEN)) {
                world.setBlockState(other, leaf.with(OPEN, now.get(OPEN)), Block.NOTIFY_LISTENERS or Block.REDRAW_ON_MAIN_THREAD)
            }
        }
        return result
    }
}

/**
 * A security camera on the ceiling (Level 5's anteroom), [FACING] the way it rests. The world draws only its
 * [Part.MOUNT]; the client turns the [Part.HEAD] to follow the player and blinks its [Part.LED]
 * (`client.render.SecurityCameraRenderer`, through its [SecurityCameraBlockEntity]). Nothing to bump into.
 */
class SecurityCameraBlock(settings: Settings) : BlockWithEntity(settings) {

    enum class Part(private val id: String) : StringIdentifiable {
        MOUNT("mount"), HEAD("head"), LED("led");

        override fun asString(): String = id
    }

    init {
        defaultState = stateManager.defaultState.with(FACING, Direction.NORTH).with(PART, Part.MOUNT)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FACING, PART)
    }

    override fun createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = SecurityCameraBlockEntity(pos, state)

    @Deprecated("Deprecated in Java")
    override fun getRenderType(state: BlockState): BlockRenderType = BlockRenderType.MODEL

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape = SHAPE

    companion object {
        val FACING: DirectionProperty = Properties.HORIZONTAL_FACING
        val PART: EnumProperty<Part> = EnumProperty.of("part", Part::class.java)

        private val SHAPE: VoxelShape = createCuboidShape(4.0, 7.0, 4.0, 12.0, 16.0, 12.0)
    }
}

/** Marks a [SecurityCameraBlock] for its client renderer; holds nothing. */
class SecurityCameraBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(ModBlocks.SECURITY_CAMERA_ENTITY, pos, state)

/**
 * One screen of the director's wall of CCTV monitors (Level 5's office): a CRT in its casing showing only snow
 * under its camera's label ([CAM], one of fifteen; the labels and the snow are its textures, `tools/blocks/generate.py`'s
 * CAMERAS), glowing faintly.
 */
class MonitorBankBlock(settings: Settings) : Block(settings) {

    init {
        defaultState = stateManager.defaultState.with(FACING, Direction.NORTH).with(CAM, 0)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FACING, CAM)
    }

    companion object {
        val FACING: DirectionProperty = Properties.HORIZONTAL_FACING
        val CAM: IntProperty = IntProperty.of("cam", 0, 14)
    }
}
