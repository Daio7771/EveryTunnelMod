package daio7771.everytunnel.platform

import daio7771.everytunnel.config.ConfigManager
import daio7771.everytunnel.systems.ResourceInput
import daio7771.everytunnel.systems.ResourceState
import daio7771.everytunnel.systems.ResourceSystem
import daio7771.everytunnel.world.WorldRules
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.stat.Stats
import net.minecraft.world.PersistentState
import java.util.UUID

/**
 * Drives [ResourceSystem] from the server tick.
 *
 * - Applies to Survival/Adventure players while the [WorldRules] are active.
 * - Sprinting comes from the player's sprint flag; jumps from the vanilla jump
 *   statistic, which the server increments for every jump.
 * - Empty thirst deals "dehydration" damage (vanilla `dryOut` damage source).
 * - Vanilla hunger is held full: thirst replaces it, and a low food level would
 *   otherwise block sprinting without the player seeing why.
 * - Death resets resources to full.
 * - States are saved with the world (`data/every_tunnel_resources.dat` in the
 *   save folder) and synced to the client with [ResourceSyncPacket].
 */
object ResourceHooks {

    private const val TICK_SECONDS = 1.0 / 20.0
    private const val SAVE_ID = "every_tunnel_resources"

    // Server thread only.
    private val lastJumpStat = HashMap<UUID, Int>()
    private val lastSent = HashMap<UUID, ResourceSyncPacket>()

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register(::tick)
        ServerPlayerEvents.AFTER_RESPAWN.register { _, newPlayer, alive ->
            if (!alive) ResourceSystem.reset(newPlayer.uuid)
        }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            lastJumpStat.remove(handler.player.uuid)
            lastSent.remove(handler.player.uuid)
        }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            lastJumpStat.clear()
            lastSent.clear()
            ResourceSystem.clear()
        }
    }

    fun appliesTo(player: ServerPlayerEntity): Boolean =
        player.isAlive && WorldRules.appliesTo(player.isCreative, player.isSpectator)

    private fun tick(server: MinecraftServer) {
        val config = ConfigManager.gameplay
        val save = saveData(server)
        for (player in server.playerManager.playerList) {
            val jumps = jumpsSinceLastTick(player)
            if (!appliesTo(player)) {
                sync(player, ResourceSyncPacket.INACTIVE)
                continue
            }
            val step = ResourceSystem.tick(player.uuid, ResourceInput(player.isSprinting, jumps), TICK_SECONDS, config)
            save.markDirty()

            player.hungerManager.foodLevel = 20
            if (step.damage > 0.0) player.damage(player.damageSources.dryOut(), step.damage.toFloat())

            val s = step.state
            sync(player, ResourceSyncPacket.of(s.stamina, config.stamina.max, s.thirst, config.thirst.max, s.exhausted))
        }
    }

    private fun jumpsSinceLastTick(player: ServerPlayerEntity): Int {
        val count = player.statHandler.getStat(Stats.CUSTOM.getOrCreateStat(Stats.JUMP))
        val previous = lastJumpStat.put(player.uuid, count) ?: return 0
        return (count - previous).coerceAtLeast(0)
    }

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
                })
            }
            nbt.put("players", players)
            return nbt
        }

        companion object {
            fun read(nbt: NbtCompound): SaveData {
                val players = nbt.getCompound("players")
                val loaded = HashMap<UUID, ResourceState>()
                for (key in players.keys) {
                    val id = runCatching { UUID.fromString(key) }.getOrNull() ?: continue
                    val p = players.getCompound(key)
                    loaded[id] = ResourceState(p.getDouble("stamina"), p.getDouble("thirst"), p.getBoolean("exhausted"))
                }
                ResourceSystem.replaceAll(loaded)
                return SaveData()
            }
        }
    }
}
