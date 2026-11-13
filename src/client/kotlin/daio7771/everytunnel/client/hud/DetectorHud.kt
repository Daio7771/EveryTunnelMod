package daio7771.everytunnel.client.hud

import com.mojang.blaze3d.systems.RenderSystem
import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.client.ClientResources
import daio7771.everytunnel.client.effects.DetectorEffect
import daio7771.everytunnel.config.ConfigManager
import daio7771.everytunnel.core.detector.Detector
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.util.Identifier
import kotlin.math.ceil

/**
 * Level 5's detector on the HUD, while it works ([DetectorEffect]): the radiation sign
 * to the right of the right-hand rows (battery and thirst, [StatusHud]), between them. Greyed out with
 * nothing in range; yellow and black fill it from the bottom up as a Distortion Cloud comes nearer.
 * Within `detector.danger_range` a red warning triangle blinks fast beside it.
 *
 * Icons: `textures/gui/detector_icons.png` (39 x 13), painted by `tools/gui/hud_icons.py`: the sign grey
 * at u=0, in colour at u=13, the triangle at u=26.
 */
object DetectorHud {

    private val ICONS = Identifier(EveryTunnel.MOD_ID, "textures/gui/detector_icons.png")
    private const val WIDTH = 39
    private const val HEIGHT = 13
    private const val SIZE = 13
    private const val GAP = 4
    private const val BLINK_TICKS = 3

    fun register() {
        HudRenderCallback.EVENT.register { context, _ -> render(context) }
    }

    private fun render(context: DrawContext) {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return
        if (!DetectorEffect.working || !ClientResources.current.active || player.isSpectator) return
        if (client.interactionManager?.hasStatusBars() != true) return

        val x = context.scaledWindowWidth / 2 + 91 + GAP
        val bottomRow = context.scaledWindowHeight - 39
        val topRow = bottomRow - 10
        val y = topRow + (bottomRow + 9 - topRow - SIZE) / 2
        val detector = Detector(ConfigManager.gameplay.detector)
        val distance = DetectorEffect.distance
        val filled = ceil(detector.level(distance) * SIZE).toInt().coerceIn(0, SIZE)

        RenderSystem.enableBlend()
        context.drawTexture(ICONS, x, y, 0f, 0f, SIZE, SIZE, WIDTH, HEIGHT)
        if (filled > 0) {
            val top = SIZE - filled
            context.drawTexture(ICONS, x, y + top, SIZE.toFloat(), top.toFloat(), SIZE, filled, WIDTH, HEIGHT)
        }
        if (detector.danger(distance) && (player.age / BLINK_TICKS) % 2 == 0) {
            context.drawTexture(ICONS, x + SIZE + 2, y, 2f * SIZE, 0f, SIZE, SIZE, WIDTH, HEIGHT)
        }
        RenderSystem.disableBlend()
    }
}
