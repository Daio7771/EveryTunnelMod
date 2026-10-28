package daio7771.everytunnel.client.hud

import com.mojang.blaze3d.systems.RenderSystem
import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.platform.HidingHooks
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.util.Identifier

/**
 * The view from inside a locker (`platform.HidingHooks`): the inside of its door over the whole
 * screen, dark steel with the vent's louvres, the world showing through the slits
 * (`locker_view.png`, from `tools/gui/locker_view.py`, stretched to the window). While hidden,
 * the outline of the block under the crosshair (the locker itself) is not drawn.
 *
 * Drawn under the HUD (registered before it), over the world and the flashlight's spot.
 */
object LockerView {

    private val TEXTURE = Identifier(EveryTunnel.MOD_ID, "textures/gui/locker_view.png")
    private const val TEXTURE_WIDTH = 512
    private const val TEXTURE_HEIGHT = 288

    private fun hidden(): Boolean = MinecraftClient.getInstance().player?.let { HidingHooks.isHidden(it) } == true

    fun register() {
        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register { _, _ -> !hidden() }
        HudRenderCallback.EVENT.register { context, _ ->
            if (!hidden()) return@register
            RenderSystem.enableBlend()
            context.drawTexture(
                TEXTURE, 0, 0, context.scaledWindowWidth, context.scaledWindowHeight,
                0f, 0f, TEXTURE_WIDTH, TEXTURE_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT,
            )
            RenderSystem.disableBlend()
        }
    }
}
