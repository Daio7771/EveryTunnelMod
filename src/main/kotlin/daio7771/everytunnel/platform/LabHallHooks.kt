package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.world.LevelTriggers
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.MinecraftServer
import net.minecraft.sound.SoundCategory
import net.minecraft.world.PersistentState
import java.util.UUID

/**
 * Level 5's first Distortion Cloud, once a game: walking into the Lab hall (a zone tagged [TAG]), a short
 * dissonant sting (`event.lab.sting`), and a moment later the Cloud, a few steps ahead
 * ([DistortionHooks.appear]; tried again for a few seconds if there is no room yet). Whether it came is
 * kept with the world (`data/every_tunnel_lab_hall.dat`) until a new game ([newGame]).
 */
object LabHallHooks {

    const val TAG = "first_cloud"

    /** From the sting to the Cloud, and how long it keeps trying for a place. */
    private const val DELAY_TICKS = 20L
    private const val GIVE_UP_TICKS = 100L

    private var server: MinecraftServer? = null
    // Server thread only: the players the Cloud is coming for, and from when.
    private val coming = HashMap<UUID, Long>()

    fun register() {
        ServerLifecycleEvents.SERVER_STARTED.register { server = it }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            server = null
            coming.clear()
        }
        LevelTriggers.register { change ->
            val server = server ?: return@register
            val tags = change.enteredZones.flatMap { key -> LevelManager.get(key.levelId)?.zone(key.zoneId)?.tags ?: emptyList() }
            if (TAG !in tags || state(server).came) return@register
            val player = server.playerManager.getPlayer(change.playerId) ?: return@register
            if (!ResourceHooks.appliesTo(player) || player.uuid in coming) return@register
            player.playSound(ModSounds.LAB_STING, SoundCategory.HOSTILE, 1f, 1f)
            coming[player.uuid] = server.ticks.toLong()
        }
        ServerTickEvents.END_SERVER_TICK.register(::tick)
    }

    private fun tick(server: MinecraftServer) {
        if (coming.isEmpty()) return
        val now = server.ticks.toLong()
        val iterator = coming.entries.iterator()
        while (iterator.hasNext()) {
            val (id, since) = iterator.next()
            val player = server.playerManager.getPlayer(id)
            if (player == null || now - since > DELAY_TICKS + GIVE_UP_TICKS) {
                iterator.remove()
                continue
            }
            if (now - since < DELAY_TICKS || (now - since) % 10 != 0L || DeathHooks.isDying(player)) continue
            if (DistortionHooks.appear(player)) {
                iterator.remove()
                val state = state(server)
                state.came = true
                state.markDirty()
                EveryTunnel.LOGGER.info("Level 5's first Cloud, for {}", player.name.string)
            }
        }
    }

    /** A new game: the first Cloud comes again. */
    fun newGame() {
        val server = server ?: return
        coming.clear()
        val state = state(server)
        state.came = false
        state.markDirty()
    }

    private fun state(server: MinecraftServer): CameState =
        server.overworld.persistentStateManager.getOrCreate(CameState::read, ::CameState, "every_tunnel_lab_hall")

    class CameState : PersistentState() {
        var came = false

        override fun writeNbt(nbt: NbtCompound): NbtCompound {
            nbt.putBoolean("came", came)
            return nbt
        }

        companion object {
            fun read(nbt: NbtCompound): CameState = CameState().apply { came = nbt.getBoolean("came") }
        }
    }
}
