package daio7771.everytunnel.client

import daio7771.everytunnel.EveryTunnel
import net.fabricmc.api.ClientModInitializer

/**
 * Client-only entrypoint for Every Tunnel.
 *
 * Phase 0: only logs that the client side has loaded. The custom HUD
 * (`client.hud`) and screen/audio effects (`client.effects`) are
 * registered here in later phases.
 */
object EveryTunnelClient : ClientModInitializer {

    override fun onInitializeClient() {
        EveryTunnel.LOGGER.info("${EveryTunnel.MOD_NAME} loaded (client)")
    }
}
