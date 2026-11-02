package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.level.LevelDefinition
import daio7771.everytunnel.world.LevelTriggers
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.entity.ItemEntity
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction

/**
 * Level 4's closing, after the four pieces make the master card ([CalmReaderBlock]):
 *
 * - **The blackout:** taking the Master Key on the Calm room's table ([keyTaken], from [LootBlock]) puts
 *   out every lamp of the level at once and lights the emergency lamps ([EmergencyLampBlock]) on the way
 *   back to the Security room. A wet floor sign stands now in the middle of the Hall (`wet_sign`). The
 *   level stays safe, and the monitors stay on. [blackout] is for the music of the audio phase.
 * - **The big door** ([BigDoorBlock], `big_door`, its bottom left part): a dull knock, until the key
 *   is tried. Then the key is used and it opens from the centre out, over 3 s ([OPENING]).
 * - **The way out:** walking on into a zone tagged [SHUT_TAG], halfway along the dark stretch, slams it
 *   behind the player. [EndingHooks] does the rest, at the end of the stretch.
 *
 * What it changes in the world is not undone here: a new game rebuilds the level ([LoungeHooks.newGame]).
 * Sounds are vanilla's for now.
 */
object LoungeClosingHooks {

    const val SHUT_TAG = "big_door_shut"
    private const val LEVEL_ID = "level_4"
    private const val STEP_TICKS = 30
    private const val KEEP_TICKS = 40
    private const val SEARCH_BLOCKS = 100.0

    /** Which columns go at each step, from the centre out. */
    private val OPENING = listOf(listOf(2, 3), listOf(1, 4), listOf(0, 5))

    private val KEPT by lazy { setOf(ModItems.CARD_NW, ModItems.CARD_NE, ModItems.CARD_SW, ModItems.CARD_SE, ModItems.MASTER_CARD, ModItems.MASTER_KEY) }

    private var server: MinecraftServer? = null
    private var doorFacing: Direction? = null
    private var openingTicks = -1
    private var shut = false

    /** The lights went out. */
    var blackout = false
        private set

    fun register() {
        ServerLifecycleEvents.SERVER_STARTED.register { server = it }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            server = null
            reset()
        }
        LevelTriggers.register { change ->
            val tags = change.enteredZones.flatMap { key -> LevelManager.get(key.levelId)?.zone(key.zoneId)?.tags ?: emptyList() }
            val server = server
            if (SHUT_TAG in tags && server != null) slam(server)
        }
        ServerTickEvents.END_SERVER_TICK.register(::tick)
    }

    /** A new game, or the map rebuilt. */
    fun reset() {
        blackout = false
        doorFacing = null
        openingTicks = -1
        shut = false
    }

    fun keyTaken(player: ServerPlayerEntity) {
        if (blackout) return
        val (level, world) = place(player.server) ?: return
        blackout = true
        var lamps = 0
        for (zone in level.zones) {
            val box = zone.box
            for (pos in BlockPos.iterate(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)) {
                val state = world.getBlockState(pos)
                if (state.isOf(ModBlocks.WARM_LAMP) && state.get(WarmLampBlock.LIT)) {
                    world.setBlockState(pos, state.with(WarmLampBlock.LIT, false))
                    lamps++
                } else if (state.isOf(ModBlocks.FLUORESCENT_LIGHT)) {
                    world.setBlockState(pos, FluorescentLightBlock.withMode(state, LightMode.OFF))
                } else if (state.isOf(ModBlocks.EMERGENCY_LAMP) && !state.get(EmergencyLampBlock.LIT)) {
                    world.setBlockState(pos, state.with(EmergencyLampBlock.LIT, true))
                }
            }
        }
        // Looking the way someone coming up the Hall would come from.
        pointOf(level, "wet_sign")?.let { world.setBlockState(it, ModBlocks.WET_FLOOR_SIGN.defaultState.with(PropBlock.FACING, Direction.EAST)) }
        world.playSound(null, player.blockPos, ModSounds.FLUORESCENT_OFF, SoundCategory.BLOCKS, 2f, 0.6f)
        EveryTunnel.LOGGER.info("The Master Key is taken: {} lamps go out", lamps)
    }

    fun useBigDoor(player: ServerPlayerEntity, state: BlockState) {
        val world = player.serverWorld
        if (doorFacing != null) return
        val slot = (0 until player.inventory.size()).firstOrNull { player.inventory.getStack(it).isOf(ModItems.MASTER_KEY) }
        if (slot == null) {
            world.playSound(null, player.blockPos, SoundEvents.BLOCK_IRON_DOOR_CLOSE, SoundCategory.BLOCKS, 0.6f, 0.5f)
            return
        }
        player.inventory.getStack(slot).decrement(1)
        doorFacing = state.get(BigDoorBlock.FACING)
        openingTicks = 0
        EveryTunnel.LOGGER.info("{} opens the big door", player.name.string)
    }

    private fun tick(server: MinecraftServer) {
        if (openingTicks >= 0) {
            val step = openingTicks / STEP_TICKS
            if (openingTicks % STEP_TICKS == 0 && step < OPENING.size) openStep(server, OPENING[step])
            openingTicks = if (step < OPENING.size) openingTicks + 1 else -1
        }
        if (server.ticks % KEEP_TICKS == 0) keepDropped(server)
    }

    private fun openStep(server: MinecraftServer, cols: List<Int>) {
        val (level, world) = place(server) ?: return
        val origin = pointOf(level, "big_door") ?: return
        val facing = doorFacing ?: return
        for (col in cols) {
            for (row in 0..3) world.setBlockState(partAt(origin, facing, col, row), Blocks.AIR.defaultState)
        }
        val at = partAt(origin, facing, cols[0], 1)
        world.playSound(null, at, SoundEvents.BLOCK_PISTON_CONTRACT, SoundCategory.BLOCKS, 1.5f, 0.5f)
        world.playSound(null, at, SoundEvents.BLOCK_IRON_DOOR_OPEN, SoundCategory.BLOCKS, 1f, 0.5f)
    }

    private fun slam(server: MinecraftServer) {
        val facing = doorFacing ?: return
        if (shut) return
        val (level, world) = place(server) ?: return
        val origin = pointOf(level, "big_door") ?: return
        shut = true
        openingTicks = -1
        for (col in 0..5) {
            for (row in 0..3) {
                val state = ModBlocks.BIG_DOOR.defaultState.with(BigDoorBlock.FACING, facing).with(BigDoorBlock.COL, col).with(BigDoorBlock.ROW, row)
                world.setBlockState(partAt(origin, facing, col, row), state)
            }
        }
        world.playSound(null, partAt(origin, facing, 2, 1), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 3f, 0.5f)
    }

    /** To the right of someone looking at the door's front is [facing] turned counterclockwise. */
    private fun partAt(origin: BlockPos, facing: Direction, col: Int, row: Int): BlockPos =
        origin.offset(facing.rotateYCounterclockwise(), col).up(row)

    /** A piece, the card or the key left on the floor would vanish after 5 minutes, and with it the way out. */
    private fun keepDropped(server: MinecraftServer) {
        val (level, world) = place(server) ?: return
        if (LoungeHooks.notBuiltYet(level)) return
        val centre = pointOf(level, "wet_sign") ?: return
        world.getEntitiesByClass(ItemEntity::class.java, Box(centre).expand(SEARCH_BLOCKS)) { it.stack.item in KEPT }.forEach { it.setNeverDespawn() }
    }

    private fun place(server: MinecraftServer): Pair<LevelDefinition, ServerWorld>? {
        val level = LevelManager.get(LEVEL_ID) ?: return null
        val world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(level.dimension))) ?: return null
        return level to world
    }

    private fun pointOf(level: LevelDefinition, id: String): BlockPos? =
        level.spawnPoint(id)?.let { BlockPos.ofFloored(it.position.x, it.position.y, it.position.z) }
}
