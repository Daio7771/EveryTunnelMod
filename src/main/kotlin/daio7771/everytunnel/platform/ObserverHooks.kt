package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.PlayerLocationTracker
import daio7771.everytunnel.core.level.Position
import daio7771.everytunnel.core.spawn.PostLedger
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
import java.util.UUID

/**
 * The Observers ([ObserverEntity]) in the world:
 *
 * - **Spawning:** one per spawn point tagged [TAG], once per world, the first time a player
 *   walks into its level (like [PaleManSpawner]; remembered in `data/every_tunnel_observers.dat`).
 *   New games are counted by [PaleManSpawner.newGames], which sends him home too.
 * - **Rebuilds:** the map is rebuilt whenever the world opens, and its build kills the Observers.
 *   `/kill` is not instant on a living thing (it plays its death for a second), so [levelsRebuilt]
 *   discards them itself, at once, and puts one back for each level a player is in. Who owns each
 *   spawn point is kept in [posts], so a leftover (an Observer that was killed and is still dying,
 *   or one saved with its chunk that loads late) never wipes the record of the one that replaced it.
 * - **Alerts:** a box put on Storage's shelf ([BoxHooks]) is an alert ([alert]); every Observer in
 *   that world takes the alerts newer than the last he took ([alertsSince]), and every player
 *   there sees "He's coming." and "Hide." in the middle of the screen.
 * - **A player's death** sends the Observers of that level home ([sendHome], from [DeathHooks]).
 */
object ObserverHooks {

    const val TAG = "observant_guard"

    /** A box went on the shelf at [position], at server tick [tick], in [dimension]. */
    data class Alert(val dimension: String, val position: Position, val tick: Long)

    private var server: MinecraftServer? = null
    // Server thread only; only the last few are kept.
    private val alerts = ArrayDeque<Alert>()
    // Server thread only.
    private val posts = PostLedger<UUID>()

    fun register() {
        ServerLifecycleEvents.SERVER_STARTED.register { server = it }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            server = null
            alerts.clear()
            posts.clear()
        }
        LevelTriggers.register { change ->
            val levelId = change.current.levelId
            // Nobody to watch once Level 3 is erased (a spectator can still fly through its zones).
            if (change.levelChanged && levelId != null && !LoungeHooks.levelThreeErased) spawnFor(levelId)
        }
    }

    /** A box went on the shelf: an alert at [pos] (where he comes to), and every player there is warned. */
    fun alert(world: ServerWorld, pos: BlockPos) {
        alerts.addLast(Alert(world.registryKey.value.toString(), Position(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5), world.server.ticks.toLong()))
        while (alerts.size > MAX_ALERTS) alerts.removeFirst()
        for (player in world.players) warn(player)
    }

    /**
     * A warning in big dark red letters in the middle of the screen: "He's coming." by default, and "Hide." under it
     * in smaller ones. Two lines, because the screen's title is drawn at four times the size and never wraps:
     * a sentence in one line ran off the screen in Spanish.
     */
    fun warn(player: ServerPlayerEntity, key: String = "message.every_tunnel.observer_coming") {
        val network = player.networkHandler
        network.sendPacket(TitleFadeS2CPacket(WARNING_FADE_IN, WARNING_STAY, WARNING_FADE_OUT))
        network.sendPacket(SubtitleS2CPacket(Text.translatable("message.every_tunnel.observer_hide").formatted(Formatting.DARK_RED)))
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

    /**
     * The levels were rebuilt, and the build's `kill` was sent to every Observer in them. A `kill` on a living
     * thing is damage, not removal: the Observer plays his death for 20 ticks and only then leaves the world
     * (and [ObserverEntity.remove] forgets his spawn point). So at this moment he still stood in the world, his
     * spawn point still looked filled, nothing was spawned, and a second later he was gone for the whole game
     * (a world opened inside Level 3, CONTINUE). The Observers are discarded here, which removes them at once
     * and forgets their spawn points, and one comes back for each level a player is in. A level entered later
     * spawns its own, as ever ([spawnFor]).
     */
    fun levelsRebuilt(server: MinecraftServer) {
        discardAll(server)
        if (LoungeHooks.levelThreeErased) return
        val levels = server.playerManager.playerList
            .mapNotNull { PlayerLocationTracker.locationOf(it.uuid).levelId }
            .toSet()
        for (levelId in levels) spawnFor(levelId)
    }

    private fun discardAll(server: MinecraftServer) {
        var discarded = 0
        for (world in server.worlds) {
            for (entity in world.iterateEntities().toList()) {
                if (entity is ObserverEntity) {
                    entity.discard()
                    discarded++
                }
            }
        }
        EveryTunnel.LOGGER.info("Levels rebuilt: {} Observer(s) discarded", discarded)
    }

    /**
     * False for an Observer that is a leftover: another living one owns his spawn point (he was saved with his
     * chunk and loaded after the point was filled again). He discards himself, without touching the record.
     */
    fun claim(observer: ObserverEntity): Boolean {
        val key = observer.homeKey ?: return true
        val world = observer.world as? ServerWorld ?: return true
        val mine = posts.claim(key, observer.uuid) { owner -> world.getEntity(owner)?.isAlive == true }
        if (!mine) EveryTunnel.LOGGER.info("Observer {} is a leftover of {}: discarded", observer.uuid, key)
        return mine
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
            if (key in state.spawned) {
                EveryTunnel.LOGGER.info("Observer at {}: the world already has him, not spawned again", key)
                continue
            }
            val observer = ModEntities.OBSERVER.create(world) ?: continue
            observer.refreshPositionAndAngles(point.position.x, point.position.y, point.position.z, point.yaw, 0f)
            observer.headYaw = point.yaw
            observer.homeKey = key
            observer.setPersistent()
            if (world.spawnEntity(observer)) {
                posts.assign(key, observer.uuid)
                state.spawned += key
                state.markDirty()
                EveryTunnel.LOGGER.info("Observer spawned at {} ({}, {}, {})", key, point.position.x, point.position.y, point.position.z)
            }
        }
    }

    /** The Observer [id] left the spawn point [key]: it has none any more, unless a newer one owns it. */
    fun forget(key: String, id: UUID) {
        if (!posts.release(key, id)) return
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
