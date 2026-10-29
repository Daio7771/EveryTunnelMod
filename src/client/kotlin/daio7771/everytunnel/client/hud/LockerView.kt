package daio7771.everytunnel.client.hud

import com.mojang.blaze3d.systems.RenderSystem
import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.platform.HidingHooks
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import net.minecraft.util.Identifier

/**
 * The view from inside a locker (`platform.HidingHooks`): the inside of its door over the whole
 * screen, dark steel with the vent's louvres, the world showing through the slits
 * (`locker_view.png`, from `tools/gui/locker_view.py`, stretched to the window). While hidden,
 * the outline of the block under the crosshair (the locker itself) is not drawn.
 *
 * Drawn under the HUD (registered before it), over the world and the flashlight's spot.
 *
 * Getting in, the vanilla "Press ... to Dismount" over the hotbar is replaced by the mod's own
 * line (`message.every_tunnel.locker_exit`): sneaking or a right-click gets you out.
 */
object LockerView {

    private val TEXTURE = Identifier(EveryTunnel.MOD_ID, "textures/gui/locker_view.png")
    private const val TEXTURE_WIDTH = 512
    private const val TEXTURE_HEIGHT = 288

    private fun hidden(): Boolean = MinecraftClient.getInstance().player?.let { HidingHooks.isHidden(it) } == true

    private var wasHidden = false

    fun register() {
        // After the packet that seats the player (and shows vanilla's line), the same tick or the next.
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val now = hidden()
            if (now && !wasHidden) {
                val sneak = client.options.sneakKey.boundKeyLocalizedText
                client.inGameHud.setOverlayMessage(Text.translatable("message.every_tunnel.locker_exit", sneak), false)
            }
            wasHidden = now
        }
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
