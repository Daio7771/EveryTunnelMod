package daio7771.everytunnel.platform

import daio7771.everytunnel.core.wall.WallMessageState
import net.minecraft.block.BlockRenderType
import net.minecraft.block.BlockState
import net.minecraft.block.BlockWithEntity
import net.minecraft.block.ShapeContext
import net.minecraft.block.entity.BlockEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.listener.ClientPlayPacketListener
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket
import net.minecraft.state.StateManager
import net.minecraft.state.property.DirectionProperty
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import net.minecraft.world.BlockView

/**
 * A message painted on a wall by whoever was here before: an invisible block in front of the wall, [FACING]
 * the way the text looks. What it says is its block entity's ([WallMessageBlockEntity]), set by the map
 * generator (`tools/map/wall_messages.py`), and `client.render.WallMessageRenderer` draws it. Nothing to
 * touch or bump into.
 */
class WallMessageBlock(settings: Settings) : BlockWithEntity(settings) {

    init {
        defaultState = stateManager.defaultState.with(FACING, Direction.NORTH)
    }

    override fun appendProperties(builder: StateManager.Builder<net.minecraft.block.Block, BlockState>) {
        builder.add(FACING)
    }

    override fun createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = WallMessageBlockEntity(pos, state)

    @Deprecated("Deprecated in Java")
    override fun getRenderType(state: BlockState): BlockRenderType = BlockRenderType.INVISIBLE

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        VoxelShapes.empty()

    companion object {
        val FACING: DirectionProperty = Properties.HORIZONTAL_FACING
    }
}

/**
 * A wall message's data. NBT: `{Text: "<lang key>", Changed: "<lang key>", Size: 0.5f, Rotation: -2f, Trigger:
 * "fixed" | "on_approach" | "on_look_away_change", Once: 1b, Patch: 1b}`: [size] is a line's height in blocks,
 * [rotation] degrees off level, [patch] a pale patch behind it on a dark wall. A line break in the lang text
 * makes two lines. What has happened to it is the client's ([WallMessageState]).
 */
class WallMessageBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(ModBlocks.WALL_MESSAGE_ENTITY, pos, state) {

    var text = ""
        private set
    var changedText = ""
        private set
    var size = 0.5f
        private set
    var rotation = 0f
        private set
    var trigger = WallMessageState.Trigger.FIXED
        private set
    var once = true
        private set
    var patch = false
        private set
    private var triggerName = "fixed"

    override fun readNbt(nbt: NbtCompound) {
        super.readNbt(nbt)
        text = nbt.getString("Text")
        changedText = nbt.getString("Changed")
        size = nbt.getFloat("Size")
        rotation = nbt.getFloat("Rotation")
        triggerName = nbt.getString("Trigger")
        trigger = WallMessageState.Trigger.parse(triggerName)
        once = !nbt.contains("Once") || nbt.getBoolean("Once")
        patch = nbt.getBoolean("Patch")
    }

    override fun writeNbt(nbt: NbtCompound) {
        super.writeNbt(nbt)
        nbt.putString("Text", text)
        nbt.putString("Changed", changedText)
        nbt.putFloat("Size", size)
        nbt.putFloat("Rotation", rotation)
        nbt.putString("Trigger", triggerName)
        nbt.putBoolean("Once", once)
        nbt.putBoolean("Patch", patch)
    }

    override fun toUpdatePacket(): Packet<ClientPlayPacketListener> = BlockEntityUpdateS2CPacket.create(this)

    override fun toInitialChunkDataNbt(): NbtCompound = createNbt()
}
