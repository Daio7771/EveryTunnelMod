package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.PlayerLocationTracker
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.tag.FluidTags
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.math.BlockPos
import java.util.UUID

/**
 * Level 5's deep water: the pools under the drain's catwalks (the Junction's, the Cistern's), in zones tagged
 * [TAG]. A player who falls in (water at their feet and under them: the tunnels' channels, a block deep, do
 * not count) is pulled to the bottom: they cannot swim up, they sink among bubbles while the screen darkens
 * ([DeathHooks.sendFade]), and after [PULL_TICKS] they drown ([DeathHooks] then, the death screen's "Deep
 * Water"). Once caught there is no getting out.
 */
object DeepWaterHooks {

    const val TAG = "deep_water"
    /** How long the pull lasts before they drown. */
    private const val PULL_TICKS = 40
    /** How fast they sink, blocks a tick, whatever they do. */
    private const val SINK_SPEED = 0.12
    /** How dark the screen gets while they sink (DeathHooks' black finishes it). */
    private const val DARKNESS = 0.8f

    // Server thread only: players being pulled under, and the ticks since.
    private val pulled = HashMap<UUID, Int>()

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register(::tick)
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> pulled.remove(handler.player.uuid) }
        ServerLifecycleEvents.SERVER_STOPPED.register { pulled.clear() }
    }

    private fun tick(server: MinecraftServer) {
        for (player in server.playerManager.playerList) {
            if (player.uuid in pulled) continue
            if (!ResourceHooks.appliesTo(player) || DeathHooks.isDying(player) || !inDeepWater(player)) continue
            if (TAG !in LevelManager.tagsOf(PlayerLocationTracker.locationOf(player.uuid).zones)) continue
            pulled[player.uuid] = 0
            player.isSprinting = false
            player.serverWorld.playSound(null, player.x, player.y, player.z, SoundEvents.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE,
                SoundCategory.PLAYERS, 1f, 0.7f)
            DeathHooks.sendFade(player, DARKNESS, PULL_TICKS / 20f)
            EveryTunnel.LOGGER.info("{} fell into deep water: pulled under", player.name.string)
        }
        val iterator = pulled.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val player = server.playerManager.getPlayer(entry.key)
            if (player == null || DeathHooks.isDying(player) || !ResourceHooks.appliesTo(player)) {
                iterator.remove()
                continue
            }
            entry.setValue(entry.value + 1)
            sink(player, entry.value)
            if (entry.value >= PULL_TICKS) {
                iterator.remove()
                player.damage(player.damageSources.drown(), Float.MAX_VALUE)
            }
        }
    }

    /** Water at their feet and under them: a pool, not a channel. */
    private fun inDeepWater(player: ServerPlayerEntity): Boolean {
        val world = player.serverWorld
        val feet = BlockPos.ofFloored(player.x, player.y + 0.2, player.z)
        return world.getFluidState(feet).isIn(FluidTags.WATER) && world.getFluidState(feet.down()).isIn(FluidTags.WATER)
    }

    private fun sink(player: ServerPlayerEntity, ticks: Int) {
        val v = player.velocity
        player.setVelocity(v.x * 0.5, -SINK_SPEED, v.z * 0.5)
        player.velocityModified = true
        if (ticks % 4 == 0) {
            player.serverWorld.spawnParticles(ParticleTypes.BUBBLE, player.x, player.y + 1.0, player.z, 6, 0.3, 0.6, 0.3, 0.05)
        }
        if (ticks % 12 == 0) {
            player.serverWorld.playSound(null, player.x, player.y, player.z, SoundEvents.ENTITY_PLAYER_SWIM,
                SoundCategory.PLAYERS, 0.6f, 0.6f + player.random.nextFloat() * 0.2f)
        }
    }
}
