package daio7771.everytunnel.platform

import daio7771.everytunnel.config.ConfigManager
import daio7771.everytunnel.systems.ResourceSystem
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import java.util.UUID

/**
 * Resting in a bed in the Suites ([SuiteBedBlock]): the screen fades to black, in silence, and the
 * player cannot move; in the dark health and stamina are filled; the screen fades back in. Nothing
 * else changes: it is not a save, and thirst and the battery stay as they are.
 */
object SuiteBedHooks {

    private const val FADE_OUT_TICKS = 40
    /** Filled in the middle of the darkness. */
    private const val FILL_TICK = 70
    private const val FADE_IN_START_TICK = 90
    private const val FADE_IN_TICKS = 40

    private val FREEZE_ID: UUID = UUID.fromString("0f2b6a4e-9a1d-4c37-8e55-3d7c1a92b6f4")

    // Server thread only: player -> ticks since lying down.
    private val resting = HashMap<UUID, Int>()

    fun register() {
        TickProfiler.endServerTick.register(::tick)
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            if (resting.remove(handler.player.uuid) != null) unfreeze(handler.player)
        }
        ServerLifecycleEvents.SERVER_STOPPED.register { resting.clear() }
    }

    fun rest(player: ServerPlayerEntity) {
        if (player.uuid in resting || DeathHooks.isDying(player)) return
        resting[player.uuid] = 0
        player.isSprinting = false
        player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED)?.let { speed ->
            if (speed.getModifier(FREEZE_ID) == null) {
                speed.addTemporaryModifier(
                    EntityAttributeModifier(FREEZE_ID, "Every Tunnel: resting", -1.0, EntityAttributeModifier.Operation.MULTIPLY_TOTAL),
                )
            }
        }
        DeathHooks.sendFade(player, 1f, FADE_OUT_TICKS / 20f)
    }

    private fun tick(server: MinecraftServer) {
        val iterator = resting.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val player = server.playerManager.getPlayer(entry.key)
            if (player == null) {
                iterator.remove()
                continue
            }
            val ticks = entry.value + 1
            entry.setValue(ticks)
            if (ticks == FILL_TICK) fill(player)
            if (ticks >= FADE_IN_START_TICK) {
                iterator.remove()
                unfreeze(player)
                DeathHooks.sendFade(player, 0f, FADE_IN_TICKS / 20f)
            }
        }
    }

    private fun fill(player: ServerPlayerEntity) {
        player.health = player.maxHealth
        val config = ConfigManager.gameplay
        ResourceSystem.set(player.uuid, ResourceSystem.rested(ResourceSystem.stateOf(player.uuid, config), config))
    }

    private fun unfreeze(player: ServerPlayerEntity) {
        player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED)?.removeModifier(FREEZE_ID)
    }
}
