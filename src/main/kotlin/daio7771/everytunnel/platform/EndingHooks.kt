package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.world.LevelTriggers
import daio7771.everytunnel.world.WorldRules
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.MinecraftServer
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import java.util.UUID

/**
 * The end of the demo: walking into a zone tagged [END_TAG] (a few steps down the stairs
 * behind Level 2's exit) while the world rules apply.
 *
 * Bare footsteps come down the stairs behind the player ([STEPS], the Pale man's own
 * sound), the screen fades to black, and the client shows "End of demo" with the credits
 * (`client.screen.EndOfDemoScreen`), then saves and goes back to the title screen. Opening
 * the world again starts a new game, as it always does ([LoadingHooks]).
 */
object EndingHooks {

    const val END_TAG = "end"

    /** To the client: show the end screen (no payload). */
    val ENDING_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "ending")

    private const val FIRST_STEP_TICK = 10
    private const val STEPS = 5
    private const val STEP_TICKS = 11
    private const val FADE_TICK = FIRST_STEP_TICK + STEPS * STEP_TICKS
    private const val FADE_SECONDS = 2.5f
    private const val SCREEN_TICK = FADE_TICK + 60

    /** [behind]: unit vector, horizontal, pointing back up the stairs the player came down. */
    private class Ending(val behind: Vec3d, var ticks: Int = 0)

    // Server thread only.
    private val endings = HashMap<UUID, Ending>()
    private var server: MinecraftServer? = null

    fun register() {
        ServerLifecycleEvents.SERVER_STARTED.register { server = it }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            server = null
            endings.clear()
        }
        LevelTriggers.register { change ->
            val reachedEnd = change.enteredZones.any { key ->
                LevelManager.get(key.levelId)?.zone(key.zoneId)?.tags?.contains(END_TAG) == true
            }
            if (!reachedEnd || change.playerId in endings) return@register
            val player = server?.playerManager?.getPlayer(change.playerId) ?: return@register
            if (!WorldRules.appliesTo(player.isCreative, player.isSpectator)) return@register
            val look = player.rotationVector
            val behind = Vec3d(-look.x, 0.0, -look.z).let { if (it.lengthSquared() < 1e-4) Vec3d(0.0, 0.0, 1.0) else it.normalize() }
            endings[player.uuid] = Ending(behind)
            EveryTunnel.LOGGER.info("{} reached the end of the demo", player.name.string)
            PlaytestLog.endOfDemo(player)
        }
        ServerTickEvents.END_SERVER_TICK.register(::tick)
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> endings.remove(handler.player.uuid) }
    }

    private fun tick(server: MinecraftServer) {
        val iterator = endings.entries.iterator()
        while (iterator.hasNext()) {
            val (id, ending) = iterator.next()
            val player = server.playerManager.getPlayer(id)
            if (player == null) {
                iterator.remove()
                continue
            }
            val t = ending.ticks++
            val step = (t - FIRST_STEP_TICK) / STEP_TICKS
            if (t >= FIRST_STEP_TICK && (t - FIRST_STEP_TICK) % STEP_TICKS == 0 && step < STEPS) {
                // Each step a little closer and lower: someone coming down the stairs after you.
                val distance = 7.0 - step * 0.9
                val height = 3.5 - step * 0.6
                val at = player.pos.add(ending.behind.multiply(distance)).add(0.0, height, 0.0)
                player.serverWorld.playSound(
                    null, at.x, at.y, at.z, ModSounds.PALE_MAN_STEP, SoundCategory.HOSTILE, 0.9f, 0.9f + player.random.nextFloat() * 0.1f,
                )
            }
            if (t == FADE_TICK) DeathHooks.sendFade(player, 1f, FADE_SECONDS)
            if (t >= SCREEN_TICK) {
                iterator.remove()
                if (ServerPlayNetworking.canSend(player, ENDING_PACKET_ID)) {
                    ServerPlayNetworking.send(player, ENDING_PACKET_ID, PacketByteBufs.empty())
                }
            }
        }
    }
}
