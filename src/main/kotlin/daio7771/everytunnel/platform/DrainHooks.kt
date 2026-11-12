package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.ZoneKey
import daio7771.everytunnel.core.progress.Checkpoint
import daio7771.everytunnel.world.LevelTriggers
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import java.util.UUID

/**
 * Level 5's drain (layer 3), down the ladder under the chemical room's hatch. Once the player is down, on the floor
 * at the foot of the ladder (the zone tagged [BOTTOM_TAG]), the hatch over them slams shut
 * ([ChemicalHooks.setHatch], vanilla's iron trapdoor, deep): there is no way back up. A death before the Junction's restart
 * point (the checkpoint still the Lab hall's, or the level's start) comes back up there, so the hatch opens again
 * then, quietly, if the solvent had opened it.
 */
object DrainHooks {

    const val BOTTOM_TAG = "drain_bottom"
    private const val LEVEL_ID = "level_5"
    /** The drain's own restart point (ContinueGame): past it, the way back up stays shut. */
    private const val JUNCTION_POINT = "checkpoint_junction"

    private var server: MinecraftServer? = null
    // Server thread only: players at the foot of the ladder, until they stand on its floor.
    private val arriving = mutableSetOf<UUID>()

    fun register() {
        ServerLifecycleEvents.SERVER_STARTED.register { server = it }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            server = null
            arriving.clear()
        }
        LevelTriggers.register { change ->
            fun tagged(keys: Set<ZoneKey>) =
                keys.any { BOTTOM_TAG in (LevelManager.get(it.levelId)?.zone(it.zoneId)?.tags ?: emptyList()) }
            if (tagged(change.enteredZones)) arriving += change.playerId
            if (tagged(change.exitedZones)) arriving -= change.playerId
        }
        TickProfiler.endServerTick.register(::tick)
    }

    private fun tick(server: MinecraftServer) {
        if (arriving.isEmpty()) return
        val iterator = arriving.iterator()
        while (iterator.hasNext()) {
            val player = server.playerManager.getPlayer(iterator.next())
            if (player == null) {
                iterator.remove()
                continue
            }
            if (!ResourceHooks.appliesTo(player) || !player.isOnGround || player.isClimbing) continue
            iterator.remove()
            if (ChemicalHooks.setHatch(server, open = false)) {
                EveryTunnel.LOGGER.info("Level 5: the drain's hatch slams shut over {}", player.name.string)
            }
        }
    }

    /** The level stands anew (its hatch shut, its grate whole). */
    fun levelBuilt() = arriving.clear()

    /** [player] dies in [levelId] and starts again: above the drain, the way down is open again if it was. */
    fun restart(player: ServerPlayerEntity, levelId: String) {
        if (levelId != LEVEL_ID) return
        arriving -= player.uuid
        val checkpoint = Checkpoint.parse(ProgressService.get().checkpoint(player))
        if (checkpoint?.levelId == LEVEL_ID && checkpoint.pointId == JUNCTION_POINT) return
        if (ChemicalHooks.drainOpened) ChemicalHooks.setHatch(player.server, open = true)
    }
}
