package daio7771.everytunnel.platform

import net.minecraft.block.Block
import net.minecraft.block.BlockRenderType
import net.minecraft.block.BlockState
import net.minecraft.block.BlockWithEntity
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.listener.ClientPlayPacketListener
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.state.StateManager
import net.minecraft.state.property.DirectionProperty
import net.minecraft.state.property.IntProperty
import net.minecraft.state.property.Properties
import net.minecraft.util.ActionResult
import net.minecraft.util.BlockMirror
import net.minecraft.util.BlockRotation
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World

/*
 * Level 3's box puzzle ([BoxHooks]): the purple archive boxes and the empty shelf in Storage
 * they are stacked on. Models and textures in tools/blocks/generate.py.
 */

/**
 * A purple archive box lying on the floor or on a piece of furniture, drawn full bright so it
 * stands out anywhere (the block's settings, [ModBlocks.PURPLE_BOX]). Right-click picks it up
 * ([BoxHooks.pickUp]); in a world without levels it is only a box.
 */
class PurpleBoxBlock(settings: Settings, boxes: List<BlockShapes.Box>) : PropBlock(settings, boxes) {

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult {
        if (world.isClient) return ActionResult.SUCCESS
        val server = player as? ServerPlayerEntity ?: return ActionResult.PASS
        return if (BoxHooks.pickUp(server, pos)) ActionResult.CONSUME else ActionResult.PASS
    }
}

/**
 * The shelf the purple boxes go on: 2 blocks wide, 3 high, [FACING] the way its front looks.
 * Each block is one part: [COL] 0 on the left as seen from the front, 1 on the right; [ROW] 0 at
 * the bottom. Five boards 8 pixels apart give 2 x 5 = [SLOTS] places, slot = level * 2 + column,
 * level 0 at the bottom ([slotAt]); which are filled is kept by the block entity of the foot
 * (column 0, row 0: [BoxShelfBlockEntity]) and drawn by `client.render.BoxShelfRenderer`.
 *
 * Right-click with a box puts it in the place pointed at ([BoxHooks.useShelf]). The map
 * generator places the six parts (`tools/map/offices/furnish.py`).
 */
class BoxShelfBlock(settings: Settings) : BlockWithEntity(settings) {

    init {
        defaultState = stateManager.defaultState.with(FACING, Direction.NORTH).with(COL, 0).with(ROW, 0)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FACING, COL, ROW)
    }

    override fun createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? =
        if (state.get(COL) == 0 && state.get(ROW) == 0) BoxShelfBlockEntity(pos, state) else null

    @Deprecated("Deprecated in Java")
    override fun getRenderType(state: BlockState): BlockRenderType = BlockRenderType.MODEL

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult {
        if (world.isClient) return ActionResult.SUCCESS
        val server = player as? ServerPlayerEntity ?: return ActionResult.PASS
        val foot = foot(pos, state)
        val slot = slotAt(foot, state.get(FACING), hit.pos) ?: return ActionResult.PASS
        return if (BoxHooks.useShelf(server, world as ServerWorld, foot, slot)) ActionResult.CONSUME else ActionResult.PASS
    }

    @Deprecated("Deprecated in Java")
    override fun rotate(state: BlockState, rotation: BlockRotation): BlockState =
        state.with(FACING, rotation.rotate(state.get(FACING)))

    @Deprecated("Deprecated in Java")
    override fun mirror(state: BlockState, mirror: BlockMirror): BlockState =
        state.rotate(mirror.getRotation(state.get(FACING)))

    companion object {
        val FACING: DirectionProperty = Properties.HORIZONTAL_FACING
        val COL: IntProperty = IntProperty.of("col", 0, 1)
        val ROW: IntProperty = IntProperty.of("row", 0, 2)

        const val LEVELS = 5
        const val SLOTS = LEVELS * 2
        /** Board k's top over the shelf's foot, in pixels: 1 + 8k. */
        fun boardTop(level: Int): Double = (1 + 8 * level) / 16.0
        /** How far in from the front the boxes stand, from the middle of the block (towards the back). */
        const val DEPTH_OFFSET = 1.0 / 16.0

        /** To the right of someone in front of a shelf facing [facing], looking at it: column 1's way. */
        fun right(facing: Direction): Direction = facing.rotateYCounterclockwise()

        /** The foot (column 0, row 0) of the shelf the part at [pos] belongs to. */
        fun foot(pos: BlockPos, state: BlockState): BlockPos =
            pos.offset(right(state.get(FACING)), -state.get(COL)).down(state.get(ROW))

        /** The middle of the place for [slot]'s box, on its board, in world coordinates. */
        fun slotCentre(foot: BlockPos, facing: Direction, slot: Int): Vec3d {
            val col = slot % 2
            val r = right(facing)
            val back = facing.opposite
            return Vec3d(
                foot.x + 0.5 + r.offsetX * col + back.offsetX * DEPTH_OFFSET,
                foot.y + boardTop(slot / 2),
                foot.z + 0.5 + r.offsetZ * col + back.offsetZ * DEPTH_OFFSET,
            )
        }

        /** The slot at the point [hit] of a shelf whose foot is at [foot], or null if it is off the shelf. */
        fun slotAt(foot: BlockPos, facing: Direction, hit: Vec3d): Int? {
            val r = right(facing)
            val across = (hit.x - (foot.x + 0.5)) * r.offsetX + (hit.z - (foot.z + 0.5)) * r.offsetZ
            val col = if (across > 0.5) 1 else 0
            val up = (hit.y - foot.y) * 16.0
            if (up < 0 || up > 48 || across < -0.5 || across > 1.5) return null
            val level = ((up - 1) / 8).toInt().coerceIn(0, LEVELS - 1)
            return level * 2 + col
        }
    }
}

/** The boxes on a shelf ([BoxShelfBlock]): one flag per slot, saved and sent to the client. */
class BoxShelfBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(ModBlocks.BOX_SHELF_ENTITY, pos, state) {

    private val filled = BooleanArray(BoxShelfBlock.SLOTS)

    fun isFilled(slot: Int): Boolean = filled.getOrElse(slot) { false }

    val count: Int get() = filled.count { it }

    fun set(slot: Int, value: Boolean) {
        if (slot !in filled.indices || filled[slot] == value) return
        filled[slot] = value
        changed()
    }

    fun clear() {
        if (filled.none { it }) return
        filled.fill(false)
        changed()
    }

    private fun changed() {
        markDirty()
        world?.updateListeners(pos, cachedState, cachedState, Block.NOTIFY_LISTENERS)
    }

    override fun readNbt(nbt: NbtCompound) {
        super.readNbt(nbt)
        val bits = nbt.getInt("filled")
        for (i in filled.indices) filled[i] = bits and (1 shl i) != 0
    }

    override fun writeNbt(nbt: NbtCompound) {
        super.writeNbt(nbt)
        nbt.putInt("filled", filled.foldIndexed(0) { i, bits, on -> if (on) bits or (1 shl i) else bits })
    }

    override fun toUpdatePacket(): Packet<ClientPlayPacketListener> = BlockEntityUpdateS2CPacket.create(this)

    override fun toInitialChunkDataNbt(): NbtCompound = createNbt()
}
