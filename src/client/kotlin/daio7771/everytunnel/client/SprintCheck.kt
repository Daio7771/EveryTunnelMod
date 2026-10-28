package daio7771.everytunnel.client

import daio7771.everytunnel.EveryTunnel
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.effect.StatusEffects

/**
 * A diagnosis for "the player cannot sprint": while the sprint key is held, walking forwards,
 * and the player is not sprinting, the log says why, at most every [EVERY_TICKS] (only in
 * worlds with levels). Each line lists what stands in the way: the mod's own reasons (out of
 * stamina, carrying a box or book) and vanilla's (hunger, using an item, a crouching pose under
 * a low ceiling, bumping into something, in the air, blindness).
 */
object SprintCheck {

    private const val EVERY_TICKS = 40
    private var wait = 0

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
    }

    private fun tick(client: MinecraftClient) {
        if (wait > 0) wait--
        val player = client.player ?: return
        if (!ClientResources.current.active || !client.options.sprintKey.isPressed || player.isSprinting) return
        if (player.input.movementForward < 0.8f || wait > 0) return
        wait = EVERY_TICKS
        val reasons = buildList {
            if (ClientResources.current.exhausted) add("exhausted")
            if (ClientResources.isSprintBlocked() && !ClientResources.current.exhausted) add("carrying a box or book")
            if (player.hungerManager.foodLevel <= 6 && !player.abilities.allowFlying) add("hungry (food ${player.hungerManager.foodLevel})")
            if (player.isUsingItem) add("using an item")
            if (player.isInSneakingPose || player.input.sneaking) add("crouching (pose ${player.pose})")
            if (player.horizontalCollision) add("bumping into something")
            if (!player.isOnGround) add("in the air")
            if (player.hasStatusEffect(StatusEffects.BLINDNESS)) add("blind")
            if (player.hasVehicle()) add("riding ${player.vehicle?.type?.untranslatedName}")
        }
        EveryTunnel.LOGGER.info("[sprint] held but not sprinting at ({}, {}, {}): {}", player.blockX, player.blockY, player.blockZ,
            reasons.ifEmpty { listOf("no reason found (speed ${player.movementSpeed})") }.joinToString(", "))
    }
}
