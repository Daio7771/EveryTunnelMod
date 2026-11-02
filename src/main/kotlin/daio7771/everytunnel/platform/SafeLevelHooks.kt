package daio7771.everytunnel.platform

import daio7771.everytunnel.core.LevelManager
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.minecraft.entity.damage.DamageTypes
import net.minecraft.server.network.ServerPlayerEntity

/**
 * Levels without risk (`safe` in the level file, Level 4): nothing hurts a player there. Only
 * `/kill` and the void still count, as a way out of being stuck. [ResourceHooks] pauses thirst
 * and the battery for the same players.
 */
object SafeLevelHooks {

    fun register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, source, _ ->
            val player = entity as? ServerPlayerEntity ?: return@register true
            source.isOf(DamageTypes.GENERIC_KILL) || source.isOf(DamageTypes.OUT_OF_WORLD) || !isSafe(player)
        }
    }

    /** [player] stands in a safe level. */
    fun isSafe(player: ServerPlayerEntity): Boolean =
        LevelManager.levelAt(player.serverWorld.registryKey.value.toString(), player.x, player.y, player.z)?.safe == true
}
