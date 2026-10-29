package daio7771.everytunnel.client

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.client.effects.AmbiencePlayer
import daio7771.everytunnel.client.effects.BreathingEffect
import daio7771.everytunnel.client.effects.FlashlightEffect
import daio7771.everytunnel.client.effects.HeartbeatEffect
import daio7771.everytunnel.client.effects.IntroMusic
import daio7771.everytunnel.client.effects.FluorescentHum
import daio7771.everytunnel.client.effects.ScreenFade
import daio7771.everytunnel.client.effects.TensionMusic
import daio7771.everytunnel.client.hud.ExhaustionVignette
import daio7771.everytunnel.client.hud.FlashlightOverlay
import daio7771.everytunnel.client.hud.LockerView
import daio7771.everytunnel.client.hud.AchievementToast
import daio7771.everytunnel.client.hud.StatusHud
import daio7771.everytunnel.client.render.BoxShelfRenderer
import daio7771.everytunnel.client.render.WallTextRenderer
import daio7771.everytunnel.client.render.LootRenderer
import daio7771.everytunnel.client.screen.EndOfDemoScreen
import daio7771.everytunnel.client.screen.NoteScreen
import daio7771.everytunnel.client.screen.LoadingCover
import daio7771.everytunnel.client.screen.MainMenuScreen
import daio7771.everytunnel.client.effects.MenuMusic
import daio7771.everytunnel.client.menu.MenuScene
import daio7771.everytunnel.client.render.ObserverRenderer
import daio7771.everytunnel.client.render.PaleManRenderer
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories
import daio7771.everytunnel.platform.ModBlocks
import daio7771.everytunnel.platform.DevWorlds
import daio7771.everytunnel.platform.ModEntities
import daio7771.everytunnel.platform.WorldRulesHooks
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.entity.EmptyEntityRenderer

/**
 * Client-only entrypoint for Every Tunnel.
 *
 * Keeps the client player's block abilities in line with the world rules (so the
 * client predicts what the server allows), receives the player's resources from the
 * server ([ClientResources]), and registers the Flashlight key
 * ([FlashlightControls]), the flashlight's beam ([FlashlightEffect]; drawn by
 * `client.compat.LambDynamicLightsCompat` when LambDynamicLights is installed), the
 * exhausted breathing ([BreathingEffect]), the heartbeat near the Pale man
 * ([HeartbeatEffect]), the level ambience ([AmbiencePlayer]), the
 * hum of fluorescent lights ([FluorescentHum]), the view from inside a locker ([LockerView]),
 * the "Achievement Unlocked!" toasts ([AchievementToast]), the status bars ([StatusHud]) and the
 * loading screen of worlds with levels ([LoadingCover]), and the main menu in place of
 * vanilla's title screen ([MainMenuScreen], with its music: [MenuMusic]).
 * Blocks whose textures have see-through pixels are drawn in the cutout layer, and the
 * Pale man by GeckoLib ([PaleManRenderer]).
 */
object EveryTunnelClient : ClientModInitializer {

    override fun onInitializeClient() {
        // Before quick play opens a world: dev worlds with a template start over.
        DevWorlds.restoreAll()
        // Also before quick play: the map world packed in the jar goes into saves/.
        MapWorldInstaller.install()
        // Before Iris reads its pack settings.
        ShaderSettings.apply()
        ClientResources.register()
        FlashlightControls.register()
        FlashlightEffect.register()
        BreathingEffect.register()
        HeartbeatEffect.register()
        TensionMusic.register()
        IntroMusic.register()
        AmbiencePlayer.register()
        FluorescentHum.register()
        FlashlightOverlay.register() // without shaders: the dark around the beam, under everything else
        LockerView.register() // over the beam's dark, under the HUD
        ExhaustionVignette.register() // before the HUD, so the icons stay on top
        StatusHud.register()
        ScreenFade.register() // after the HUD, so the black covers it
        EndOfDemoScreen.register()
        NoteScreen.register()
        LoadingCover.register()
        MainMenuScreen.register()
        MenuMusic.register()
        MenuScene.register()
        EntityRendererRegistry.register(ModEntities.PALE_MAN, ::PaleManRenderer)
        EntityRendererRegistry.register(ModEntities.OBSERVER, ::ObserverRenderer)
        EntityRendererRegistry.register(ModEntities.LOCKER_SEAT, ::EmptyEntityRenderer)
        AchievementToast.register()
        SprintCheck.register()
        BlockEntityRendererFactories.register(ModBlocks.LOOT_ENTITY, ::LootRenderer)
        BlockEntityRendererFactories.register(ModBlocks.BOX_SHELF_ENTITY, ::BoxShelfRenderer)
        BlockEntityRendererFactories.register(ModBlocks.WALL_TEXT_ENTITY, ::WallTextRenderer)
        BlockRenderLayerMap.INSTANCE.putBlocks(
            RenderLayer.getCutout(),
            ModBlocks.SHOPPING_CART, ModBlocks.CABLE_TRAY, ModBlocks.HANGING_CABLE, ModBlocks.STAIN, ModBlocks.PUDDLE,
            ModBlocks.METAL_GRATING, ModBlocks.VALVE, ModBlocks.POTTED_PLANT, ModBlocks.WALL_CLOCK,
        )
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val player = client.player ?: return@register
            WorldRulesHooks.syncAbilities(player, client.interactionManager?.currentGameMode)
        }
        EveryTunnel.LOGGER.info("${EveryTunnel.MOD_NAME} loaded (client)")
    }
}
