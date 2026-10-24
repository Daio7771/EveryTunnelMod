package daio7771.everytunnel.client.hud

import com.mojang.blaze3d.systems.RenderSystem
import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.client.ShaderMode
import daio7771.everytunnel.client.effects.FlashlightEffect
import daio7771.everytunnel.config.ConfigManager
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.util.Identifier
import net.minecraft.util.Util
import kotlin.math.exp
import kotlin.math.max

/**
 * The flashlight's spot on screen, without shaders only ([ShaderMode]): block light draws the
 * beam as steps of light, so over the world the screen goes dark around a round spot in the
 * middle (`flashlight_spot.png`), a little warmer at its heart (`flashlight_glow.png`; both
 * from `tools/gui/flashlight_spot.py`). With shaders the pack does the light and this is off.
 *
 * - The spot sways: turning the camera leaves it behind for a moment, as a hand-held light
 *   lags the head ([SWAY_PER_DEGREE], at most [MAX_SWAY] of the screen height), and it
 *   settles back over [SWAY_SECONDS].
 * - It follows the flashlight's flicker and dimming: a weaker beam darkens it all
 *   ([DIM_DARKNESS]).
 * - It appears and goes with the flashlight, fading over [FADE_SECONDS].
 *
 * Drawn under the HUD (registered before it), over the world.
 */
object FlashlightOverlay {

    private val SPOT = Identifier(EveryTunnel.MOD_ID, "textures/gui/flashlight_spot.png")
    private val GLOW = Identifier(EveryTunnel.MOD_ID, "textures/gui/flashlight_glow.png")
    private const val TEXTURE_SIZE = 256

    /** The spot's diameter, as a share of the screen height. */
    private const val SPOT_SIZE = 1.5f
    /** The darkness at the spot texture's edge (`tools/gui/flashlight_spot.py`, EDGE_ALPHA), kept beyond it. */
    private const val EDGE_ALPHA = 0.90f
    /** The warm tint of the hot spot. */
    private const val GLOW_R = 1.0f
    private const val GLOW_G = 0.88f
    private const val GLOW_B = 0.68f
    private const val SWAY_PER_DEGREE = 0.004f
    private const val MAX_SWAY = 0.06f
    private const val SWAY_SECONDS = 0.18f
    /** How dark everything gets with the beam at nothing (a flicker, a dying battery). */
    private const val DIM_DARKNESS = 0.6f
    private const val FADE_SECONDS = 0.15f

    private var lastMs = Util.getMeasuringTimeMs()
    private var lastYaw = Float.NaN
    private var lastPitch = Float.NaN
    private var swayX = 0f
    private var swayY = 0f
    private var shown = 0f

    fun register() {
        HudRenderCallback.EVENT.register { context, tickDelta -> render(context, tickDelta) }
    }

    private fun render(context: DrawContext, tickDelta: Float) {
        val now = Util.getMeasuringTimeMs()
        val dt = ((now - lastMs) / 1000f).coerceIn(0f, 0.25f)
        lastMs = now

        val client = MinecraftClient.getInstance()
        val player = client.player
        val beam = FlashlightEffect.beam
        val on = beam != null && player != null && !ShaderMode.shadersOn && !client.options.hudHidden
        val step = dt / FADE_SECONDS
        shown = if (on) minOf(1f, shown + step) else maxOf(0f, shown - step)
        if (player == null) return

        // Sway: the spot lags behind the turn, then catches up.
        val yaw = player.getYaw(tickDelta)
        val pitch = player.getPitch(tickDelta)
        if (!lastYaw.isNaN()) {
            swayX = (swayX - (yaw - lastYaw) * SWAY_PER_DEGREE).coerceIn(-MAX_SWAY, MAX_SWAY)
            swayY = (swayY - (pitch - lastPitch) * SWAY_PER_DEGREE).coerceIn(-MAX_SWAY, MAX_SWAY)
        }
        lastYaw = yaw
        lastPitch = pitch
        val settle = exp(-dt / SWAY_SECONDS)
        swayX *= settle
        swayY *= settle
        if (shown <= 0.01f) return

        val config = ConfigManager.gameplay.flashlight
        val dimming = if (beam != null && config.beamBrightness > 0.0) (beam.brightness / config.beamBrightness).toFloat() else 1f
        val w = context.scaledWindowWidth
        val h = context.scaledWindowHeight
        val size = (h * SPOT_SIZE).toInt()
        val cx = w / 2 + (swayX * h).toInt()
        val cy = h / 2 + (swayY * h).toInt()
        val left = cx - size / 2
        val top = cy - size / 2

        RenderSystem.enableBlend()
        // The warm heart first, then the dark around it.
        context.setShaderColor(GLOW_R, GLOW_G, GLOW_B, shown * dimming.coerceIn(0f, 1f))
        context.drawTexture(GLOW, left, top, size, size, 0f, 0f, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE)
        context.setShaderColor(1f, 1f, 1f, shown)
        context.drawTexture(SPOT, left, top, size, size, 0f, 0f, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE)
        context.setShaderColor(1f, 1f, 1f, 1f)
        // Beyond the texture, the same darkness as its edge.
        val edge = argb(EDGE_ALPHA * shown)
        context.fill(0, 0, w, max(0, top), edge)
        context.fill(0, top + size, w, h, edge)
        context.fill(0, max(0, top), max(0, left), top + size, edge)
        context.fill(left + size, max(0, top), w, top + size, edge)
        // A weaker beam darkens it all.
        val dim = (1f - dimming).coerceIn(0f, 1f) * DIM_DARKNESS * shown
        if (dim > 0.01f) context.fill(0, 0, w, h, argb(dim))
        RenderSystem.disableBlend()
    }

    private fun argb(alpha: Float): Int = (alpha.coerceIn(0f, 1f) * 255f).toInt() shl 24
}
