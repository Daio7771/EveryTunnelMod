package daio7771.everytunnel.chapter1.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories
import daio7771.everytunnel.chapter1.level2.client.PaleManRenderer
import daio7771.everytunnel.chapter1.level3.client.BoxShelfRenderer
import daio7771.everytunnel.chapter1.level3.client.ObserverRenderer
import daio7771.everytunnel.chapter1.level4.client.DartRenderer
import daio7771.everytunnel.chapter1.level4.client.FilmPlayer
import daio7771.everytunnel.chapter1.level4.client.FilmWalkerRenderer
import daio7771.everytunnel.chapter1.level4.client.LoungeMusic
import daio7771.everytunnel.chapter1.level4.client.SafeScreen
import daio7771.everytunnel.chapter1.level5.client.DeadGuardRenderer
import daio7771.everytunnel.chapter1.level5.client.DirectorRenderer
import daio7771.everytunnel.chapter1.level5.client.DirectorVoice
import daio7771.everytunnel.chapter1.level5.client.LabFigureRenderer
import daio7771.everytunnel.chapter1.level5.client.SubjectRenderer
import daio7771.everytunnel.chapter1.level5.client.TerminalScreen
import daio7771.everytunnel.chapter1.platform.Ch1Blocks
import daio7771.everytunnel.chapter1.platform.Ch1Entities
import daio7771.everytunnel.chapter1.level2.client.HeartbeatEffect
import daio7771.everytunnel.chapter1.level5.client.DirectorCutscene
import daio7771.everytunnel.common.EveryTunnel

/**
 * Chapter 1's client entrypoint: the renderers of its creatures, its levels' sounds and screens, after the shared ones
 * ([daio7771.everytunnel.common.common.client.EveryTunnelClient]), and before Chapter 2's.
 */
object Chapter1Client : ClientModInitializer {

    override fun onInitializeClient() {
        HeartbeatEffect.register()
        TensionMusic.register()
        DirectorCutscene.register()
        FilmPlayer.register()
        LoungeMusic.register()
        DirectorVoice.register()
        TerminalScreen.register()
        FinalCreditsScreen.register()
        SafeScreen.register()
        EntityRendererRegistry.register(Ch1Entities.PALE_MAN, ::PaleManRenderer)
        EntityRendererRegistry.register(Ch1Entities.OBSERVER, ::ObserverRenderer)
        EntityRendererRegistry.register(Ch1Entities.FILM_WALKER, ::FilmWalkerRenderer)
        EntityRendererRegistry.register(Ch1Entities.DART, ::DartRenderer)
        EntityRendererRegistry.register(Ch1Entities.LAB_FIGURE, ::LabFigureRenderer)
        EntityRendererRegistry.register(Ch1Entities.SUBJECT, ::SubjectRenderer)
        EntityRendererRegistry.register(Ch1Entities.DEAD_GUARD, ::DeadGuardRenderer)
        EntityRendererRegistry.register(Ch1Entities.DIRECTOR, ::DirectorRenderer)
        BlockEntityRendererFactories.register(Ch1Blocks.BOX_SHELF_ENTITY, ::BoxShelfRenderer)
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(), Ch1Blocks.VALVE, Ch1Blocks.DRAIN_LADDER, Ch1Blocks.CHALK_ARROW)
        EveryTunnel.LOGGER.info("Chapter 1 loaded (client)")
    }
}
