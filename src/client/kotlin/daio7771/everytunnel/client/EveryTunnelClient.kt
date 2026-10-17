package daio7771.everytunnel.client

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.platform.WorldRulesHooks
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

/**
 * Client-only entrypoint for Every Tunnel.
 *
 * Keeps the client player's block abilities in line with the world rules (so the
 * client predicts what the server allows). The custom HUD (`client.hud`) and
 * screen/audio effects (`client.effects`) are registered here in later phases.
 */
object EveryTunnelClient : ClientModInitializer {

    override fun onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val player = client.player ?: return@register
            WorldRulesHooks.syncAbilities(player, client.interactionManager?.currentGameMode)
        }
        EveryTunnel.LOGGER.info("${EveryTunnel.MOD_NAME} loaded (client)")
    }
}
