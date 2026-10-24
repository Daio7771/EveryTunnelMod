package daio7771.everytunnel.client.screen

import com.mojang.blaze3d.systems.RenderSystem
import daio7771.everytunnel.EveryTunnel
import net.minecraft.client.gui.DrawContext
import net.minecraft.util.Identifier
import net.minecraft.util.Util
import kotlin.math.abs
import kotlin.random.Random

/**
 * Something in the dark of the main menu: when the mouse has been still for [IDLE_MS], the
 * Pale man's silhouette (`textures/gui/menu_figure.png`, from `tools/gui/menu_figure.py`)
 * slowly appears on the right of the screen, far from the menu text, barely visible, and
 * stays there while nothing moves. Moving the mouse (or clicking) makes him vanish at once;
 * the next time he stands somewhere else. No sound.
 */
class MenuFigure {

    private val random = Random(Util.getMeasuringTimeMs())
    private var lastActivityMs = Util.getMeasuringTimeMs()

    /** When he started to appear, or -1 while he is not there. */
    private var shownAtMs = -1L
    /** Where he stands, as fractions of the screen: x of his centre, height of the figure. */
    private var x = 0f
    private var size = 0f
    private var lastX = -1f

    /** The player moved the mouse or clicked: he is gone, and the wait starts over. */
    fun activity() {
        lastActivityMs = Util.getMeasuringTimeMs()
        shownAtMs = -1L
    }

    fun render(context: DrawContext, width: Int, height: Int) {
        val now = Util.getMeasuringTimeMs()
        if (shownAtMs < 0 && now - lastActivityMs >= IDLE_MS) appear(now)
        if (shownAtMs < 0) return

        val opacity = ((now - shownAtMs).toFloat() / FADE_IN_MS).coerceIn(0f, 1f) * PEAK_OPACITY
        val figureHeight = (height * size).toInt()
        val figureWidth = figureHeight / 4
        val left = (width * x).toInt() - figureWidth / 2
        val feet = (height * FEET).toInt()
        RenderSystem.enableBlend()
        context.setShaderColor(1f, 1f, 1f, opacity)
        context.drawTexture(TEXTURE, left, feet - figureHeight, figureWidth, figureHeight, 0f, 0f, 32, 128, 32, 128)
        context.setShaderColor(1f, 1f, 1f, 1f)
        RenderSystem.disableBlend()
    }

    private fun appear(now: Long) {
        // Never twice in the same place.
        do {
            x = MIN_X + random.nextFloat() * (MAX_X - MIN_X)
        } while (lastX >= 0f && abs(x - lastX) < MIN_MOVE)
        lastX = x
        size = MIN_SIZE + random.nextFloat() * (MAX_SIZE - MIN_SIZE)
        shownAtMs = now
    }

    private companion object {
        val TEXTURE = Identifier(EveryTunnel.MOD_ID, "textures/gui/menu_figure.png")

        /** How long the mouse has to be still before he comes. */
        const val IDLE_MS = 15_000L
        const val FADE_IN_MS = 3000L
        /** Barely there: he is far away, in the dark. */
        const val PEAK_OPACITY = 0.14f

        /** On the right, away from the menu on the left. */
        const val MIN_X = 0.55f
        const val MAX_X = 0.9f
        const val MIN_MOVE = 0.12f
        /** Figure height as a share of the screen: the farther, the smaller. */
        const val MIN_SIZE = 0.28f
        const val MAX_SIZE = 0.45f
        /** Where his feet are, as a share of the screen height (the floor of the dark). */
        const val FEET = 0.82f
    }
}
