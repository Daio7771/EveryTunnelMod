package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.puzzle.SafeDial
import daio7771.everytunnel.core.save.LoungeSave
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.state.StateManager
import net.minecraft.state.property.BooleanProperty
import net.minecraft.state.property.DirectionProperty
import net.minecraft.state.property.Properties
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.BlockView
import net.minecraft.world.World

/**
 * The safes in Level 4's Suites, optional, a diary in each. A combination is drawn for each safe the
 * first time it is tried in a game ([SafeDial.draw]). The dial itself runs on the client
 * (`client.screen.SafeScreen`), which is told the combination and tells back how it went. Open, the
 * safe reads as a note: its text is the level's spawn point tagged `note` at the safe ([NoteHooks]).
 */
object SafeHooks {

    /** To the client: x, y, z of the safe, the combination (count, numbers), the failures so far. */
    val OPEN_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "safe_open")

    /** To the server: x, y, z of the safe, the failures now, whether it opened. */
    val RESULT_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "safe_result")

    private val combinations = HashMap<BlockPos, List<Int>>()
    private val failures = HashMap<BlockPos, Int>()

    fun register() {
        ServerPlayNetworking.registerGlobalReceiver(RESULT_PACKET_ID) { server, player, _, buf, _ ->
            val pos = buf.readBlockPos()
            val failed = buf.readVarInt()
            val solved = buf.readBoolean()
            server.execute { result(player, pos, failed, solved) }
        }
    }

    /** A new game, or the map rebuilt. */
    fun reset() {
        combinations.clear()
        failures.clear()
    }

    /** For [ContinueGame]. */
    fun save(save: LoungeSave, world: ServerWorld) {
        save.safes = combinations.map { (pos, combination) ->
            val state = world.getBlockState(pos)
            val open = state.isOf(ModBlocks.SUITE_SAFE) && state.get(SuiteSafeBlock.OPEN)
            LoungeSave.Safe(pos.x, pos.y, pos.z, combination, failures[pos] ?: 0, open)
        }
    }

    fun restore(save: LoungeSave, world: ServerWorld) {
        for (safe in save.safes) {
            val pos = BlockPos(safe.x, safe.y, safe.z)
            combinations[pos] = safe.combination
            failures[pos] = safe.failures
            val state = world.getBlockState(pos)
            if (safe.open && state.isOf(ModBlocks.SUITE_SAFE)) world.setBlockState(pos, state.with(SuiteSafeBlock.OPEN, true))
        }
    }

    fun use(player: ServerPlayerEntity, pos: BlockPos) {
        if (!ServerPlayNetworking.canSend(player, OPEN_PACKET_ID)) return
        val combination = combinations.getOrPut(pos.toImmutable()) { SafeDial.draw() }
        val buf = PacketByteBufs.create()
        buf.writeBlockPos(pos)
        buf.writeVarInt(combination.size)
        combination.forEach(buf::writeVarInt)
        buf.writeVarInt(failures[pos] ?: 0)
        ServerPlayNetworking.send(player, OPEN_PACKET_ID, buf)
    }

    private fun result(player: ServerPlayerEntity, pos: BlockPos, failed: Int, solved: Boolean) {
        val world = player.serverWorld
        val state = world.getBlockState(pos)
        if (!state.isOf(ModBlocks.SUITE_SAFE) || state.get(SuiteSafeBlock.OPEN)) return
        failures[pos.toImmutable()] = failed
        if (!solved) return
        world.setBlockState(pos, state.with(SuiteSafeBlock.OPEN, true))
        world.playSound(null, pos, ModSounds.SAFE_OPEN, SoundCategory.BLOCKS, 1f, 1f)
        EveryTunnel.LOGGER.info("{} opened a safe at {}", player.name.string, pos.toShortString())
    }
}

/** A safe on the floor of a suite: closed, the dial; [OPEN], the diary inside. */
class SuiteSafeBlock(settings: Settings) : Block(settings) {

    init {
        defaultState = stateManager.defaultState.with(FACING, Direction.NORTH).with(OPEN, false)
    }

    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>) {
        builder.add(FACING, OPEN)
    }

    @Deprecated("Deprecated in Java")
    override fun getOutlineShape(state: BlockState, world: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape =
        SHAPES.getValue(state.get(FACING))

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult {
        if (world.isClient) return ActionResult.SUCCESS
        val server = player as ServerPlayerEntity
        if (state.get(OPEN)) NoteHooks.read(server, pos) else SafeHooks.use(server, pos)
        return ActionResult.CONSUME
    }

    companion object {
        val FACING: DirectionProperty = Properties.HORIZONTAL_FACING
        val OPEN: BooleanProperty = BooleanProperty.of("open")

        private val SHAPES = BlockShapes.horizontal(listOf(BlockShapes.box(1, 0, 2, 15, 14, 16)))
    }
}
