package daio7771.everytunnel.client

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.client.effects.BreathingEffect
import daio7771.everytunnel.client.effects.FlashlightEffect
import daio7771.everytunnel.client.hud.StatusHud
import daio7771.everytunnel.platform.WorldRulesHooks
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

/**
 * Client-only entrypoint for Every Tunnel.
 *
 * Keeps the client player's block abilities in line with the world rules (so the
 * client predicts what the server allows), receives the player's resources from the
 * server ([ClientResources]), and registers the Flashlight key
 * ([FlashlightControls]), the flashlight's beam ([FlashlightEffect]; drawn by
 * `client.compat.LambDynamicLightsCompat` when LambDynamicLights is installed), the
 * exhausted breathing ([BreathingEffect]) and the status bars ([StatusHud]).
 */
object EveryTunnelClient : ClientModInitializer {

    override fun onInitializeClient() {
        ClientResources.register()
        FlashlightControls.register()
        FlashlightEffect.register()
        BreathingEffect.register()
        StatusHud.register()
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val player = client.player ?: return@register
            WorldRulesHooks.syncAbilities(player, client.interactionManager?.currentGameMode)
        }
        EveryTunnel.LOGGER.info("${EveryTunnel.MOD_NAME} loaded (client)")
    }
}
