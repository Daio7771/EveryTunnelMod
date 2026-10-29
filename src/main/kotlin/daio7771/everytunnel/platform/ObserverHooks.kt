package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.level.Position
import daio7771.everytunnel.world.LevelTriggers
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtList
import net.minecraft.nbt.NbtString
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket
import net.minecraft.network.packet.s2c.play.TitleS2CPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.PersistentState

/**
 * The Observers ([ObserverEntity]) in the world:
 *
 * - **Spawning:** one per spawn point tagged [TAG], once per world, the first time a player
 *   walks into its level (like [PaleManSpawner]; remembered in `data/every_tunnel_observers.dat`).
 *   New games are counted by [PaleManSpawner.newGames], which sends him home too.
 * - **Alerts:** a box put on Storage's shelf ([BoxHooks]) is an alert ([alert]); every Observer in
 *   that world takes the alerts newer than the last he took ([alertsSince]), and every player
 *   there sees "He's coming. Hide." in the middle of the screen.
 * - **A player's death** sends the Observers of that level home ([sendHome], from [DeathHooks]).
 */
object ObserverHooks {

    const val TAG = "observant_guard"

    /** A box went on the shelf at [position], at server tick [tick], in [dimension]. */
    data class Alert(val dimension: String, val position: Position, val tick: Long)

    private var server: MinecraftServer? = null
    // Server thread only; only the last few are kept.
    private val alerts = ArrayDeque<Alert>()

    fun register() {
        ServerLifecycleEvents.SERVER_STARTED.register { server = it }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            server = null
            alerts.clear()
        }
        LevelTriggers.register { change ->
            val levelId = change.current.levelId
            if (change.levelChanged && levelId != null) spawnFor(levelId)
        }
    }

    /** A box went on the shelf: an alert at [pos] (where he comes to), and every player there is warned. */
    fun alert(world: ServerWorld, pos: BlockPos) {
        alerts.addLast(Alert(world.registryKey.value.toString(), Position(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5), world.server.ticks.toLong()))
        while (alerts.size > MAX_ALERTS) alerts.removeFirst()
        for (player in world.players) warn(player)
    }

    /** A warning in big dark red letters in the middle of the screen: "He's coming. Hide." by default. */
    fun warn(player: ServerPlayerEntity, key: String = "message.every_tunnel.observer_coming") {
        val network = player.networkHandler
        network.sendPacket(TitleFadeS2CPacket(WARNING_FADE_IN, WARNING_STAY, WARNING_FADE_OUT))
        network.sendPacket(SubtitleS2CPacket(Text.empty()))
        network.sendPacket(TitleS2CPacket(Text.translatable(key).formatted(Formatting.DARK_RED)))
    }

    /** The alerts in [world] made after server tick [tick], oldest first. */
    fun alertsSince(world: ServerWorld, tick: Long): List<Alert> {
        val dimension = world.registryKey.value.toString()
        return alerts.filter { it.dimension == dimension && it.tick > tick }
    }

    /** The Observers of [levelId] back at their spawn points (a player died there). */
    fun sendHome(world: ServerWorld, levelId: String) {
        val level = LevelManager.get(levelId) ?: return
        for (observer in world.getEntitiesByType(ModEntities.OBSERVER) { it.isAlive && it.homeKey?.startsWith("$levelId/") == true }) {
            val point = level.spawnPoint(observer.homeKey!!.substringAfter('/')) ?: continue
            observer.returnHome(point.position.x, point.position.y, point.position.z, point.yaw)
        }
    }

    private fun spawnFor(levelId: String) {
        val server = server ?: return
        val level = LevelManager.get(levelId) ?: return
        val points = level.spawnPoints.filter { TAG in it.tags }
        if (points.isEmpty()) return
        val world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(level.dimension))) ?: return
        val state = state(server)
        for (point in points) {
            val key = "$levelId/${point.id}"
            if (key in state.spawned) continue
            val observer = ModEntities.OBSERVER.create(world) ?: continue
            observer.refreshPositionAndAngles(point.position.x, point.position.y, point.position.z, point.yaw, 0f)
            observer.headYaw = point.yaw
            observer.homeKey = key
            observer.setPersistent()
            if (world.spawnEntity(observer)) {
                state.spawned += key
                state.markDirty()
                EveryTunnel.LOGGER.info("Observer spawned at {} ({}, {}, {})", key, point.position.x, point.position.y, point.position.z)
            }
        }
    }

    /** The spawn point [key] has no Observer any more. */
    fun forget(key: String) {
        val state = state(server ?: return)
        if (state.spawned.remove(key)) state.markDirty()
    }

    private fun state(server: MinecraftServer): SpawnedState =
        server.overworld.persistentStateManager.getOrCreate(SpawnedState::read, ::SpawnedState, "every_tunnel_observers")

    private const val MAX_ALERTS = 16
    /** The warning's title times, in ticks. */
    private const val WARNING_FADE_IN = 5
    private const val WARNING_STAY = 50
    private const val WARNING_FADE_OUT = 20

    /** Spawn points ("level/point") that already have their Observer in this world. */
    class SpawnedState : PersistentState() {
        val spawned: MutableSet<String> = linkedSetOf()

        override fun writeNbt(nbt: NbtCompound): NbtCompound {
            nbt.put("spawned", NbtList().apply { spawned.forEach { add(NbtString.of(it)) } })
            return nbt
        }

        companion object {
            fun read(nbt: NbtCompound): SpawnedState = SpawnedState().apply {
                nbt.getList("spawned", NbtElement.STRING_TYPE.toInt()).forEach { spawned += it.asString() }
            }
        }
    }
}
