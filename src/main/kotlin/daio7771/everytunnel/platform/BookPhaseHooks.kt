package daio7771.everytunnel.platform

import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.entities.ObserverBrain
import daio7771.everytunnel.systems.ObserverNearness
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.sound.SoundCategory
import java.util.UUID

/**
 * Level 3's book phase (the boxes done, not every book back, [BookHooks.booksOut]): help to
 * notice the Observer, since the books go back to the counter in his library. For every player
 * in such a level, every tick, [ObserverNearness] decides from the distance to the nearest
 * Observer of that level: the library clock ticking in the head, faster and louder as he nears
 * (`event.observer.tick` / `tock`), and "He's near. Hide." ([ObserverHooks.warn]) when he comes
 * close. Only where the world rules apply ([ResourceHooks.appliesTo]).
 */
object BookPhaseHooks {

    private val nearness = HashMap<UUID, ObserverNearness>()
    /** Level id -> (server tick checked, books out); checked again after [CHECK_TICKS]. */
    private val phase = HashMap<String, Pair<Int, Boolean>>()

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register(::tick)
        ServerLifecycleEvents.SERVER_STOPPED.register {
            nearness.clear()
            phase.clear()
        }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> nearness.remove(handler.player.uuid) }
    }

    /** The book phase began, ended or started over ([BookHooks]): checked again on the next tick. */
    fun phaseChanged() {
        phase.clear()
    }

    private fun tick(server: MinecraftServer) {
        val active = HashSet<UUID>()
        for (player in server.playerManager.playerList) {
            if (!ResourceHooks.appliesTo(player)) continue
            val world = player.serverWorld
            val level = LevelManager.levelAt(world.registryKey.value.toString(), player.x, player.y, player.z) ?: continue
            val checked = phase[level.id]
            val booksOut = if (checked != null && server.ticks - checked.first < CHECK_TICKS) {
                checked.second
            } else {
                BookHooks.booksOut(world, level).also { phase[level.id] = server.ticks to it }
            }
            if (!booksOut) continue
            active += player.uuid
            val observer = world.getEntitiesByType(ModEntities.OBSERVER, player.boundingBox.expand(SEARCH_RANGE)) {
                it.isAlive && it.homeKey?.startsWith("${level.id}/") == true
            }.minByOrNull { it.squaredDistanceTo(player) }
            val distance = observer?.let { Math.sqrt(it.squaredDistanceTo(player)) }
            val step = nearness.getOrPut(player.uuid) { ObserverNearness() }.tick(distance, observer?.brainState in HUNTING)
            step.beat?.let { beat ->
                val sound = if (beat == ObserverNearness.Beat.TICK) ModSounds.OBSERVER_TICK else ModSounds.OBSERVER_TOCK
                player.playSound(sound, SoundCategory.HOSTILE, step.volume, 1f)
            }
            if (step.warn) ObserverHooks.warn(player, "message.every_tunnel.observer_near")
        }
        nearness.keys.retainAll(active)
    }

    private const val CHECK_TICKS = 20
    /** How far round a player to look for an Observer (beyond the clock's range). */
    private const val SEARCH_RANGE = 32.0
    private val HUNTING = setOf(ObserverBrain.State.CHASE, ObserverBrain.State.SEARCH, ObserverBrain.State.GRAB)
}
