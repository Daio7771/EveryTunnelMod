package daio7771.everytunnel.platform

import daio7771.everytunnel.config.ConfigManager
import daio7771.everytunnel.config.GameplayConfig
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.PlayerLocationTracker
import daio7771.everytunnel.systems.ResourceInput
import daio7771.everytunnel.systems.ResourceState
import daio7771.everytunnel.systems.ResourceSystem
import daio7771.everytunnel.world.WorldRules
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.world.PersistentState
import java.util.UUID

/**
 * Drives [ResourceSystem] from the server tick.
 *
 * - Stamina, thirst and battery drain apply to Survival/Adventure players while the
 *   [WorldRules] are active. The flashlight works in any game mode except Spectator
 *   in worlds with levels (see [FlashlightHooks.available]), but only drains the
 *   battery where the rules apply.
 * - Sprinting comes from the player's sprint flag; jumps from the vanilla jump
 *   statistic, which the server increments for every jump.
 * - Empty thirst deals "dehydration" damage (vanilla `dryOut` damage source).
 * - In a safe level ([SafeLevelHooks]) thirst and the battery do not run down and sprinting costs
 *   nothing ([ResourceSystem]); the client is told, to dim those bars.
 * - Vanilla hunger is held full: thirst replaces it, and a low food level would
 *   otherwise block sprinting without the player seeing why.
 * - In a zone tagged [NO_SPRINT_TAG] nobody sprints (the client is told, and holds back too). In
 *   one tagged [DEAD_BATTERY_TAG] the battery is empty: walking in, it drops to nothing (a flashlight
 *   on goes out as if run down), no battery item helps, and walking out it is full again (Level 5's
 *   entry corridor, where the only light is the Cloud's).
 * - The radiation detector has no item: in Level 5, outside the zones tagged [NO_DETECTOR_TAG] (the way in, before the
 *   airlock), the client is told it works ([ResourceSyncPacket.detector]; `client.effects.DetectorEffect`).
 * - A food's energy ([FoodHooks], `ResourceState.energySeconds`) runs down here with the rest and is sent as
 *   [ResourceSyncPacket.energised], so the HUD's stamina row is gold while it lasts. It is not saved with the world.
 * - Death resets resources to full (full battery, flashlight off).
 * - States are saved with the world (`data/every_tunnel_resources.dat` in the
 *   save folder) and synced to the client with [ResourceSyncPacket].
 */
object ResourceHooks {

    const val NO_SPRINT_TAG = "no_sprint"
    const val DEAD_BATTERY_TAG = "dead_battery"
    /** Level 5's zones where the detector does not work (the way in, before the airlock): tagged [NO_DETECTOR_TAG]. */
    const val NO_DETECTOR_TAG = "no_detector"
    private const val DETECTOR_LEVEL = "level_5"
    private const val TICK_SECONDS = 1.0 / 20.0
    private const val SAVE_ID = "every_tunnel_resources"

    // Server thread only.
    private val jumps = JumpCounter()
    private val lastSent = HashMap<UUID, ResourceSyncPacket>()
    /** Players in a zone tagged [DEAD_BATTERY_TAG]. */
    private val inDark = HashSet<UUID>()

    fun register() {
        TickProfiler.endServerTick.register(::tick)
        ServerPlayerEvents.AFTER_RESPAWN.register { _, newPlayer, alive ->
            if (!alive) ResourceSystem.reset(newPlayer.uuid)
        }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            jumps.forget(handler.player.uuid)
            lastSent.remove(handler.player.uuid)
            inDark.remove(handler.player.uuid)
        }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            jumps.clear()
            lastSent.clear()
            inDark.clear()
            ResourceSystem.clear()
        }
    }

    /** [player] stands where the battery is dead ([DEAD_BATTERY_TAG]). */
    fun inDark(player: ServerPlayerEntity): Boolean = player.uuid in inDark

    /** Survival or Adventure in a world with levels, alive, and not behind the loading screen ([LoadingHooks]). */
    fun appliesTo(player: ServerPlayerEntity): Boolean =
        player.isAlive && WorldRules.appliesTo(player.isCreative, player.isSpectator) && !LoadingHooks.isLoading(player)

    private fun tick(server: MinecraftServer) {
        val config = ConfigManager.gameplay
        val save = saveData(server)
        for (player in server.playerManager.playerList) {
            val jumpCount = jumps.jumpsSince(player)
            if (!appliesTo(player)) {
                var state = ResourceSystem.stateOf(player.uuid, config)
                if (state.flashlightOn && !FlashlightHooks.available(player)) {
                    state = ResourceSystem.switchOff(state)
                    ResourceSystem.set(player.uuid, state)
                    save.markDirty()
                }
                sync(player, packetOf(false, state, config))
                continue
            }
            val safe = SafeLevelHooks.isSafe(player)
            val tags = LevelManager.tagsOf(PlayerLocationTracker.locationOf(player.uuid).zones)
            val noSprint = NO_SPRINT_TAG in tags
            if (noSprint) player.isSprinting = false
            darkness(player, DEAD_BATTERY_TAG in tags, config)
            val input = ResourceInput(player.isSprinting, jumpCount, safe)
            val step = ResourceSystem.tick(player.uuid, input, TICK_SECONDS, config)
            save.markDirty()

            player.hungerManager.foodLevel = 20
            if (step.damage > 0.0) player.damage(player.damageSources.dryOut(), step.damage.toFloat())
            if (step.batteryDied) FlashlightHooks.batteryDied(player)

            val level = PlayerLocationTracker.locationOf(player.uuid).levelId
            val detector = level == DETECTOR_LEVEL && NO_DETECTOR_TAG !in tags
            sync(player, packetOf(true, step.state, config, safe, noSprint, player.uuid in inDark, detector))
        }
    }

    /** In a zone tagged [DEAD_BATTERY_TAG] ([dark]) the battery is empty; out of it again, full. */
    private fun darkness(player: ServerPlayerEntity, dark: Boolean, config: GameplayConfig) {
        val state = ResourceSystem.stateOf(player.uuid, config)
        if (dark) {
            inDark += player.uuid
            if (state.battery <= 0.0 && !state.flashlightOn) return
            ResourceSystem.set(player.uuid, state.copy(battery = 0.0, flashlightOn = false))
            if (state.flashlightOn) FlashlightHooks.batteryDied(player)
        } else if (inDark.remove(player.uuid)) {
            ResourceSystem.set(player.uuid, state.copy(battery = config.flashlight.batteryMaxSeconds))
        }
    }

    private fun packetOf(
        active: Boolean, s: ResourceState, config: GameplayConfig, safe: Boolean = false, noSprint: Boolean = false, dark: Boolean = false,
        detector: Boolean = false,
    ) = ResourceSyncPacket.of(
        active, s.stamina, config.stamina.max, s.thirst, config.thirst.max, s.exhausted,
        s.flashlightOn, s.battery, config.flashlight.batteryMaxSeconds, safe, noSprint, dark, s.energySeconds > 0.0, detector,
    )

    private fun sync(player: ServerPlayerEntity, packet: ResourceSyncPacket) {
        if (lastSent[player.uuid] == packet) return
        if (!ServerPlayNetworking.canSend(player, ResourceSyncPacket.ID)) return
        lastSent[player.uuid] = packet
        val buf = PacketByteBufs.create()
        packet.write(buf)
        ServerPlayNetworking.send(player, ResourceSyncPacket.ID, buf)
    }

    /** Loads the saved states into [ResourceSystem] on first use per server; cached by vanilla after that. */
    private fun saveData(server: MinecraftServer): SaveData =
        server.overworld.persistentStateManager.getOrCreate(SaveData::read, ::SaveData, SAVE_ID)

    /** World save storage; the live states are in [ResourceSystem]. Timers are not saved. */
    private class SaveData : PersistentState() {

        override fun writeNbt(nbt: NbtCompound): NbtCompound {
            val players = NbtCompound()
            for ((id, state) in ResourceSystem.all()) {
                players.put(id.toString(), NbtCompound().apply {
                    putDouble("stamina", state.stamina)
                    putDouble("thirst", state.thirst)
                    putBoolean("exhausted", state.exhausted)
                    putDouble("battery", state.battery)
                    putBoolean("flashlight_on", state.flashlightOn)
                })
            }
            nbt.put("players", players)
            return nbt
        }

        companion object {
            fun read(nbt: NbtCompound): SaveData {
                val players = nbt.getCompound("players")
                val fullBattery = ConfigManager.gameplay.flashlight.batteryMaxSeconds
                val loaded = HashMap<UUID, ResourceState>()
                for (key in players.keys) {
                    val id = runCatching { UUID.fromString(key) }.getOrNull() ?: continue
                    val p = players.getCompound(key)
                    loaded[id] = ResourceState(
                        stamina = p.getDouble("stamina"),
                        thirst = p.getDouble("thirst"),
                        // Saves from before the flashlight existed have no battery: start full.
                        battery = if (p.contains("battery")) p.getDouble("battery") else fullBattery,
                        flashlightOn = p.getBoolean("flashlight_on"),
                        exhausted = p.getBoolean("exhausted"),
                    )
                }
                ResourceSystem.replaceAll(loaded)
                return SaveData()
            }
        }
    }
}
