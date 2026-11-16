package daio7771.everytunnel.chapter2

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.chapter2.level1.client.Ch2L1IntroView
import daio7771.everytunnel.chapter2.level1.client.Ch2L1MeetingView
import daio7771.everytunnel.chapter2.level1.client.Ch2L1Subtitles
import daio7771.everytunnel.chapter2.level1.client.FactoryNpcRenderer
import daio7771.everytunnel.chapter2.level1.client.HoldBar
import daio7771.everytunnel.chapter2.level1.client.CaughtOverlay
import daio7771.everytunnel.chapter2.level1.client.Ch2L1OfficeView
import daio7771.everytunnel.chapter2.level1.client.KeypadScreen
import daio7771.everytunnel.chapter2.level1.client.NonameOsFilesScreen
import daio7771.everytunnel.chapter2.level1.client.NonameOsScreen
import daio7771.everytunnel.chapter2.level1.client.ObjectivePanel
import daio7771.everytunnel.chapter2.level1.client.TaskArrow
import daio7771.everytunnel.chapter2.platform.Ch2Blocks
import daio7771.everytunnel.chapter2.platform.Ch2Entities
import daio7771.everytunnel.chapter2.prologue.client.PrologueView
import daio7771.everytunnel.chapter2.prologue.client.ScientistRenderer
import daio7771.everytunnel.client.render.SecurityCameraRenderer
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories

/**
 * Chapter 2's client entrypoint, declared in `fabric.mod.json` after the base game's (`client.EveryTunnelClient`): its scenes for the
 * cutscene view ([PrologueView], [Ch2L1IntroView], [Ch2L1MeetingView]), its Level 1's HUD (subtitles, objective panel, hold bar), the guide arrow and
 * NonameOS, its people's and cameras' renderers and its blocks' render layers.
 */
object Chapter2Client : ClientModInitializer {

    override fun onInitializeClient() {
        PrologueView.register()
        Ch2L1IntroView.register()
        Ch2L1MeetingView.register()
        Ch2L1Subtitles.register()
        ObjectivePanel.register()
        HoldBar.register()
        TaskArrow.register()
        NonameOsScreen.register()
        NonameOsFilesScreen.register()
        KeypadScreen.register()
        CaughtOverlay.register()
        Ch2L1OfficeView.register()
        EntityRendererRegistry.register(Ch2Entities.FACTORY_NPC, ::FactoryNpcRenderer)
        EntityRendererRegistry.register(Ch2Entities.SCIENTIST, ::ScientistRenderer)
        BlockEntityRendererFactories.register(Ch2Blocks.FACTORY_CAMERA_ENTITY) { SecurityCameraRenderer(it, Ch2Blocks.FACTORY_CAMERA, sweeping = true) }
        BlockRenderLayerMap.INSTANCE.putBlock(Ch2Blocks.DIRT_SPOT, RenderLayer.getCutout())
        BlockRenderLayerMap.INSTANCE.putBlock(Ch2Blocks.FACTORY_GLASS, RenderLayer.getTranslucent())
        EveryTunnel.LOGGER.info("${EveryTunnel.MOD_NAME}: Chapter 2 loaded (client)")
    }
}
