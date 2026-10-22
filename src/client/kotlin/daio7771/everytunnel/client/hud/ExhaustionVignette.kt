package daio7771.everytunnel.client.hud

import com.mojang.blaze3d.systems.RenderSystem
import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.client.ClientResources
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.gui.DrawContext
import net.minecraft.util.Identifier
import net.minecraft.util.Util
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * The edges of the screen darken when the body gives out: while exhausted (out of stamina)
 * the vignette closes in to [EXHAUSTED] and swells with the panting breath; while thirst is
 * under [THIRSTY_BELOW] it grows up to [PARCHED] as thirst runs out. It fades in and out
 * over about [FADE_SECONDS]. Only while the survival resources apply; drawn under the HUD.
 *
 * Texture: `textures/gui/vignette.png` (`tools/gui/hud_icons.py`).
 */
object ExhaustionVignette {

    private val TEXTURE = Identifier(EveryTunnel.MOD_ID, "textures/gui/vignette.png")
    private const val EXHAUSTED = 0.75f
    private const val PARCHED = 0.6f
    private const val THIRSTY_BELOW = 0.25f
    private const val BREATH_HZ = 0.55
    private const val FADE_SECONDS = 0.8f

    private var strength = 0f
    private var lastMs = Util.getMeasuringTimeMs()

    fun register() {
        HudRenderCallback.EVENT.register { context, _ -> render(context) }
    }

    private fun render(context: DrawContext) {
        val now = Util.getMeasuringTimeMs()
        val dt = ((now - lastMs) / 1000f).coerceIn(0f, 0.25f)
        lastMs = now

        val resources = ClientResources.current
        var target = 0f
        if (resources.active) {
            if (resources.exhausted) {
                target = EXHAUSTED + 0.1f * sin(2.0 * PI * BREATH_HZ * now / 1000.0).toFloat()
            }
            val thirst = if (resources.thirstMax > 0f) resources.thirst / resources.thirstMax else 1f
            if (thirst < THIRSTY_BELOW) target = max(target, PARCHED * (1f - thirst / THIRSTY_BELOW))
        }
        val step = dt / FADE_SECONDS
        strength = if (strength < target) minOf(target, strength + step) else maxOf(target, strength - step)
        if (strength <= 0.01f) return

        RenderSystem.enableBlend()
        context.setShaderColor(1f, 1f, 1f, strength.coerceIn(0f, 1f))
        val w = context.scaledWindowWidth
        val h = context.scaledWindowHeight
        context.drawTexture(TEXTURE, 0, 0, 0f, 0f, w, h, w, h)
        context.setShaderColor(1f, 1f, 1f, 1f)
        RenderSystem.disableBlend()
    }
}
