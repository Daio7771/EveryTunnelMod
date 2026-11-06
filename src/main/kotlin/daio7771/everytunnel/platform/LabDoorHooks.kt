package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.level.LevelDefinition
import daio7771.everytunnel.world.LevelTriggers
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.block.BlockState
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos

/**
 * Level 5's locked doors: sliding steel doors (zones tagged [DOOR_TAG]) that stay shut until something
 * opens them, for good. Each slides open slowly like the airlock's ([SlidingDoors]).
 *
 * - **Levers:** Level 4's Maintenance lever ([BreakerLeverBlock]) at a spawn point tagged [LEVER_TAG], named
 *   `lever_<the door's zone id>`. Pulled down, it stays down and opens its door: the layer 1 stairs' (a lever
 *   hidden in the ring) and the shortcut's (a lever in the chemical room, for the way back to the Lab hall).
 * - **The south route:** its door ([SOUTH_DOOR]) opens on walking out of the Office ([OFFICE]), wherever the
 *   player is: a lock is heard letting go far away (`event.lab.unlock`) and "The south route is open." shows.
 *
 * Doors and levers are put back shut by the level's next build; a new game forgets what was opened.
 */
object LabDoorHooks {

    const val DOOR_TAG = "lab_door"
    const val LEVER_TAG = "lab_lever"
    private const val LEVER_PREFIX = "lever_"
    private const val OFFICE = "office"
    private const val SOUTH_DOOR = "south_door"
    private const val LEVEL_ID = "level_5"

    /** Ticks a quarter-block step takes: as slow as the airlock's door ahead. */
    private const val STEP_TICKS = 8

    // Server thread only.
    private val opened = mutableSetOf<String>()
    private var server: MinecraftServer? = null

    fun register() {
        ServerLifecycleEvents.SERVER_STARTED.register { server = it }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            server = null
            opened.clear()
        }
        LevelTriggers.register { change ->
            if (change.exitedZones.none { it.levelId == LEVEL_ID && it.zoneId == OFFICE }) return@register
            val server = server ?: return@register
            val player = server.playerManager.getPlayer(change.playerId) ?: return@register
            if (player.isSpectator || LoadingHooks.isLoading(player) || SOUTH_DOOR in opened) return@register
            val level = LevelManager.get(LEVEL_ID) ?: return@register
            if (open(server, level, SOUTH_DOOR)) {
                player.playSound(ModSounds.LAB_UNLOCK, SoundCategory.PLAYERS, 1f, 1f)
                player.sendMessage(Text.translatable("message.every_tunnel.south_route_open"), true)
            }
        }
    }

    /** A new game, or the map rebuilt: every door is shut again (the next build puts them back). */
    fun reset() {
        opened.clear()
        SlidingDoors.forgetAll()
    }

    /**
     * [player] pulls the lever at [pos]. True if it is one of Level 5's, handled here (the Maintenance room's
     * are [BreakerHooks]'): down it goes, for good, and its door opens.
     */
    fun pull(player: ServerPlayerEntity, world: ServerWorld, pos: BlockPos, state: BlockState): Boolean {
        val level = LevelManager.get(LEVEL_ID) ?: return false
        val point = level.spawnPoints.firstOrNull {
            LEVER_TAG in it.tags && BlockPos.ofFloored(it.position.x, it.position.y, it.position.z) == pos
        } ?: return false
        if (state.get(BreakerLeverBlock.ON)) return true
        world.setBlockState(pos, state.with(BreakerLeverBlock.ON, true))
        world.playSound(null, pos, ModSounds.BREAKER_LEVER, SoundCategory.BLOCKS, 0.8f, 0.85f)
        val door = point.id.removePrefix(LEVER_PREFIX)
        EveryTunnel.LOGGER.info("{} pulls {}", player.name.string, point.id)
        open(world.server, level, door)
        return true
    }

    private fun open(server: MinecraftServer, level: LevelDefinition, doorId: String): Boolean {
        if (doorId in opened) return false
        val zone = level.zones.firstOrNull { it.id == doorId && DOOR_TAG in it.tags } ?: return false
        val world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(level.dimension))) ?: return false
        opened += doorId
        SlidingDoors.open(world, zone.box, STEP_TICKS)
        EveryTunnel.LOGGER.info("Level 5: {} opens", doorId)
        return true
    }
}
