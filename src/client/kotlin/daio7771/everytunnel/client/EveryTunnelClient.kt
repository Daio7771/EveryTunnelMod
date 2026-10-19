package daio7771.everytunnel.client

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.client.effects.AmbiencePlayer
import daio7771.everytunnel.client.effects.BreathingEffect
import daio7771.everytunnel.client.effects.FlashlightEffect
import daio7771.everytunnel.client.effects.FluorescentHum
import daio7771.everytunnel.client.hud.StatusHud
import daio7771.everytunnel.platform.ModBlocks
import daio7771.everytunnel.platform.WorldRulesHooks
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.render.RenderLayer

/**
 * Client-only entrypoint for Every Tunnel.
 *
 * Keeps the client player's block abilities in line with the world rules (so the
 * client predicts what the server allows), receives the player's resources from the
 * server ([ClientResources]), and registers the Flashlight key
 * ([FlashlightControls]), the flashlight's beam ([FlashlightEffect]; drawn by
 * `client.compat.LambDynamicLightsCompat` when LambDynamicLights is installed), the
 * exhausted breathing ([BreathingEffect]), the level ambience ([AmbiencePlayer]), the
 * hum of fluorescent lights ([FluorescentHum]) and the status bars ([StatusHud]).
 * Blocks whose textures have see-through pixels are drawn in the cutout layer.
 */
object EveryTunnelClient : ClientModInitializer {

    override fun onInitializeClient() {
        ClientResources.register()
        FlashlightControls.register()
        FlashlightEffect.register()
        BreathingEffect.register()
        AmbiencePlayer.register()
        FluorescentHum.register()
        StatusHud.register()
        BlockRenderLayerMap.INSTANCE.putBlocks(
            RenderLayer.getCutout(),
            ModBlocks.SHOPPING_CART, ModBlocks.CABLE_TRAY, ModBlocks.HANGING_CABLE, ModBlocks.STAIN, ModBlocks.PUDDLE,
        )
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val player = client.player ?: return@register
            WorldRulesHooks.syncAbilities(player, client.interactionManager?.currentGameMode)
        }
        EveryTunnel.LOGGER.info("${EveryTunnel.MOD_NAME} loaded (client)")
    }
}
