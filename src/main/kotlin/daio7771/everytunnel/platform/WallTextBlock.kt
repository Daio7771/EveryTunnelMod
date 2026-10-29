package daio7771.everytunnel.platform

import net.minecraft.block.BlockRenderType
import net.minecraft.block.BlockState
import net.minecraft.block.BlockWithEntity
import net.minecraft.block.ShapeContext
import net.minecraft.block.entity.BlockEntity
import net.minecraft.item.ItemPlacementContext
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.listener.ClientPlayPacketListener
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket
import net.minecraft.state.StateManager
import net.minecraft.state.property.DirectionProperty
import net.minecraft.state.property.Properties
import net.minecraft.util.BlockMirror
import net.minecraft.util.BlockRotation
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import net.minecraft.world.BlockView

/**
 * Text on a wall, written with the game's font so it follows the language (Level 3's room
 * nameplates and its entrance lettering): an invisible block in front of the wall, [FACING]
 * the way the text faces (the wall is behind it). What it says and how is kept by its block
 * entity ([WallTextBlockEntity], set by the map generator's `/setblock` data) and drawn by
 * `client.render.WallTextRenderer`. Nothing to touch or bump into.
 */
class WallTextBlock(settings: Settings) : BlockWithEntity(settings) {

    init {
        defaultState = stateManager.defaultState.with(FACING, Direction.NORTH)
    }

    override fun appendProperties(builder: StateManager.Builder<net.minecraft.block.Block, BlockState>) {
        builder.add(FACING)
    }

    override fun getPlacementState(ctx: ItemPlacementContext): BlockState =
        defaultState.with(FACING, ctx.horizontalPlayerFacing.opposite)

    override fun createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = WallTextBlockEntity(pos, state)

    @Deprecated("Deprecated in Java")
    override fun getRenderType(state: BlockState): BlockRenderType = BlockRenderType.INVISIBLE

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        VoxelShapes.empty()

    @Deprecated("Deprecated in Java")
    override fun rotate(state: BlockState, rotation: BlockRotation): BlockState =
        state.with(FACING, rotation.rotate(state.get(FACING)))

    @Deprecated("Deprecated in Java")
    override fun mirror(state: BlockState, mirror: BlockMirror): BlockState =
        state.rotate(mirror.getRotation(state.get(FACING)))

    companion object {
        val FACING: DirectionProperty = Properties.HORIZONTAL_FACING
    }
}

/**
 * What a [WallTextBlock] says: [text], a translation key (`sign.every_tunnel.*`), and its
 * [style]: [Style.PLATE], white letters on a dark nameplate, or [Style.LETTERS], big dark
 * letters straight on the wall; [offset], blocks to the reader's right of the block's centre
 * (to centre a text on a wall an even number of blocks wide); [align], which of the block's
 * edges the text keeps to (a nameplate beside a door keeps to the door's side and grows along
 * the wall, never over the doorway), or `center`. NBT:
 * `{Text: "...", Style: "plate" | "letters", Offset: 0.5f, Align: "left" | "right" | "center"}`.
 */
class WallTextBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(ModBlocks.WALL_TEXT_ENTITY, pos, state) {

    enum class Style { PLATE, LETTERS }

    var text: String = ""
        private set
    var style: Style = Style.PLATE
        private set
    var offset: Float = 0f
        private set
    /** -1: the text's left end at the block's left edge (as read), 1: its right end at the right edge, 0: centred. */
    var align: Int = 0
        private set

    override fun readNbt(nbt: NbtCompound) {
        super.readNbt(nbt)
        text = nbt.getString("Text")
        style = if (nbt.getString("Style") == "letters") Style.LETTERS else Style.PLATE
        offset = nbt.getFloat("Offset")
        align = when (nbt.getString("Align")) { "left" -> -1; "right" -> 1; else -> 0 }
    }

    override fun writeNbt(nbt: NbtCompound) {
        super.writeNbt(nbt)
        nbt.putString("Text", text)
        nbt.putString("Style", style.name.lowercase())
        nbt.putFloat("Offset", offset)
        nbt.putString("Align", when (align) { -1 -> "left"; 1 -> "right"; else -> "center" })
    }

    override fun toUpdatePacket(): Packet<ClientPlayPacketListener> = BlockEntityUpdateS2CPacket.create(this)

    override fun toInitialChunkDataNbt(): NbtCompound = createNbt()
}
