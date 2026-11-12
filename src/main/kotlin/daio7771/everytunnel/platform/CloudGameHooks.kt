package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.config.ConfigManager
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.ZoneKey
import daio7771.everytunnel.core.level.LevelDefinition
import daio7771.everytunnel.core.level.Position
import daio7771.everytunnel.entities.CloudGame
import daio7771.everytunnel.entities.Gaze
import daio7771.everytunnel.world.LevelTriggers
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import java.util.UUID

/**
 * The game of Level 5's entry corridor ([CloudGame], played as Halt plays it). Walking into its zone (tagged [TAG], from
 * its start line to the airlock) starts one for that player: a Cloud behind them ([DistortionCloudEntity.inGame]).
 * Walking out of it, at either end, ends it: the Cloud goes. The corridor's line runs from the spawn point
 * [START] to [END]; the Cloud keeps on it, at the floor, moved here every tick, and each jump is heard where it lands
 * (`entity.distortion_cloud.appear`). A death there ends it at once ([died], from [DeathHooks]). Never saved.
 */
object CloudGameHooks {

    const val TAG = "cloud_game"
    private const val START = "cloud_game_start"
    private const val END = "cloud_game_end"
    /** How far back from the start line the Cloud may stand: the corridor goes on that far towards the frame. */
    private const val BEFORE_START = 8.0
    private const val TICK_SECONDS = 1.0 / 20.0

    private class Running(val game: CloudGame, val cloud: DistortionCloudEntity, val start: Vec3d, val along: Vec3d)

    // Server thread only.
    private var server: MinecraftServer? = null
    private val games = HashMap<UUID, Running>()

    fun register() {
        ServerLifecycleEvents.SERVER_STARTED.register { server = it }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            server = null
            games.clear()
        }
        LevelTriggers.register { change ->
            val server = server ?: return@register
            fun tagged(keys: Set<ZoneKey>) = keys.firstOrNull { TAG in (LevelManager.get(it.levelId)?.zone(it.zoneId)?.tags ?: emptyList()) }
            val player = server.playerManager.getPlayer(change.playerId) ?: return@register
            if (tagged(change.exitedZones) != null) stop(player.uuid)
            tagged(change.enteredZones)?.let { key -> LevelManager.get(key.levelId)?.let { start(player, it) } }
        }
        TickProfiler.endServerTick.register(::tick)
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> stop(handler.player.uuid) }
    }

    /** [player] died: their game is over, its Cloud gone at once. */
    fun died(player: ServerPlayerEntity) {
        games.remove(player.uuid)?.cloud?.discard()
    }

    private fun start(player: ServerPlayerEntity, level: LevelDefinition) {
        if (player.uuid in games || !ResourceHooks.appliesTo(player) || DeathHooks.isDying(player)) return
        val a = level.spawnPoint(START)?.position ?: return
        val b = level.spawnPoint(END)?.position ?: return
        val world = player.server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(level.dimension))) ?: return
        val start = Vec3d(a.x, a.y, a.z)
        val line = Vec3d(b.x - a.x, 0.0, b.z - a.z)
        val length = line.length()
        if (length < 1.0) return
        val along = line.multiply(1.0 / length)
        val game = CloudGame(ConfigManager.gameplay.cloud, length, -BEFORE_START, Math::random)
        val cloud = ModEntities.DISTORTION_CLOUD.create(world) ?: return
        cloud.inGame = true
        cloud.levelId = level.id
        val at = start.add(along.multiply(game.start(distance(player, start, along))))
        val facing = Gaze.yawTowards(Position(at.x, at.y, at.z), Position(player.x, player.y, player.z)).toFloat()
        cloud.refreshPositionAndAngles(at.x, at.y, at.z, facing, 0f)
        if (!world.spawnEntity(cloud)) return
        games[player.uuid] = Running(game, cloud, start, along)
        world.playSound(null, at.x, at.y + 1.2, at.z, ModSounds.CLOUD_APPEAR, SoundCategory.HOSTILE, 1.5f, 1f)
        EveryTunnel.LOGGER.info("The entry corridor's game starts for {}", player.name.string)
    }

    private fun stop(id: UUID) {
        val running = games.remove(id) ?: return
        if (!running.cloud.isRemoved) running.cloud.leave()
        EveryTunnel.LOGGER.info("The entry corridor's game is over, after {} jumps", running.game.jumps)
    }

    private fun tick(server: MinecraftServer) {
        val iterator = games.entries.iterator()
        while (iterator.hasNext()) {
            val (id, running) = iterator.next()
            val player = server.playerManager.getPlayer(id)
            if (player == null || running.cloud.isRemoved) {
                iterator.remove()
                if (!running.cloud.isRemoved) running.cloud.discard()
                continue
            }
            if (DeathHooks.isDying(player)) continue
            val step = running.game.tick(distance(player, running.start, running.along), TICK_SECONDS)
            val at = running.start.add(running.along.multiply(step.cloud))
            running.cloud.setPosition(at.x, at.y, at.z)
            running.cloud.progress = step.progress.toFloat()
            if (step.jumped) {
                running.cloud.jumps = running.game.jumps
                player.serverWorld.playSound(null, at.x, at.y + 1.2, at.z, ModSounds.CLOUD_APPEAR, SoundCategory.HOSTILE, 1.5f, 1f)
            }
        }
    }

    /** How far along the corridor's line [player] stands from its start line. */
    private fun distance(player: ServerPlayerEntity, start: Vec3d, along: Vec3d): Double =
        (player.x - start.x) * along.x + (player.z - start.z) * along.z
}
