package daio7771.everytunnel.client.cutscene

import daio7771.everytunnel.core.cutscene.TitleCue
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text
import net.minecraft.util.Util
import org.lwjgl.glfw.GLFW

/**
 * Open while a cinematic plays ([CutsceneView]): it takes every key and the mouse (the player can neither move
 * nor look; the cursor is hidden), never pauses the game, and Escape does nothing. It draws the black bands,
 * 2.39:1 on a 16:9 screen, sliding in and out, the subtitles in the bottom band (whole cues, white after the
 * speaker's name in grey if the scene has one, centred, two lines at most), the notice to skip in the bottom
 * right corner, the black a scene opens out of and a skip's black. The jump key skips ([CutsceneView.skipPressed]).
 */
class CutsceneScreen : Screen(Text.empty()) {

    override fun init() {
        val window = client?.window ?: return
        GLFW.glfwSetInputMode(window.handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN)
    }

    override fun shouldPause(): Boolean = false

    override fun shouldCloseOnEsc(): Boolean = false

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (client?.options?.jumpKey?.matchesKey(keyCode, scanCode) == true) CutsceneView.skipPressed()
        return true
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val client = client ?: return
        val band = (height * BAND * CutsceneView.bands(client.tickDelta)).toInt()
        if (band > 0) {
            context.fill(0, 0, width, band, BLACK)
            context.fill(0, height - band, width, height, BLACK)
        }
        CutsceneView.subtitle(client.tickDelta)?.let { drawSubtitle(context, it, band) }
        val alpha = CutsceneView.skip.alpha(Util.getMeasuringTimeMs())
        if (alpha > 0.02f) {
            val text = Text.translatable("message.every_tunnel.skip_scene", client.options.jumpKey.boundKeyLocalizedText)
            val matrices = context.matrices
            matrices.push()
            matrices.translate(width - 8.0, height - 8.0 - textRenderer.fontHeight * NOTICE_SCALE, 0.0)
            matrices.scale(NOTICE_SCALE, NOTICE_SCALE, 1f)
            val color = ((alpha * 255).toInt() shl 24) or NOTICE_GREY
            context.drawText(textRenderer, text, -textRenderer.getWidth(text), 0, color, false)
            matrices.pop()
        }
        val vignette = CutsceneView.vignette(client.tickDelta)
        if (vignette > 0.01f) drawVignette(context, vignette)
        val flash = CutsceneView.flash(client.tickDelta)
        if (flash > 0.01f) context.fill(0, 0, width, height, ((flash * 255).toInt() shl 24) or WHITE)
        val black = maxOf(CutsceneView.skipBlack(), CutsceneView.openingBlack(client.tickDelta))
        if (black > 0f) context.fill(0, 0, width, height, ((black * 255).toInt() shl 24))
        CutsceneView.title()?.let { drawTitle(context, it, CutsceneView.sceneTick(client.tickDelta)) }
    }

    /** The screen's edges darkened by [strength] (0..1): a gradient over the top and bottom and strips down the sides. */
    private fun drawVignette(context: DrawContext, strength: Float) {
        val dark = (strength * 255).toInt().coerceIn(0, 255)
        val depth = (height * VIGNETTE_DEPTH).toInt()
        context.fillGradient(0, 0, width, depth, (dark shl 24), 0)
        context.fillGradient(0, height - depth, width, height, 0, (dark shl 24))
        val side = (width * VIGNETTE_DEPTH).toInt()
        val strips = 16
        for (i in 0 until strips) {
            val a = (dark * (1f - i / strips.toFloat())).toInt()
            val x = side * i / strips
            val w = side / strips + 1
            context.fill(x, 0, x + w, height, a shl 24)
            context.fill(width - x - w, 0, width - x, height, a shl 24)
        }
    }

    /** The title, on the black: its letters typed one by one from where the whole word will begin, so none shifts; fading out at the end. */
    private fun drawTitle(context: DrawContext, cue: TitleCue, tick: Double) {
        val text = Text.translatable(cue.key).string
        val typed = cue.visible(tick, text.length)
        val alpha = cue.alpha(tick)
        if (typed <= 0 || alpha < 0.02) return
        val matrices = context.matrices
        matrices.push()
        matrices.scale(TITLE_SCALE, TITLE_SCALE, 1f)
        val x = (width / TITLE_SCALE - textRenderer.getWidth(text)) / 2f
        val y = (height / TITLE_SCALE - textRenderer.fontHeight) / 2f
        context.drawText(textRenderer, text.take(typed), x.toInt(), y.toInt(), ((alpha * 255).toInt() shl 24) or WHITE, true)
        matrices.pop()
    }

    override fun renderBackground(context: DrawContext) = Unit

    private fun drawSubtitle(context: DrawContext, key: String, band: Int) {
        val speech = Text.translatable(key).styled { it.withColor(WHITE) }
        val text = CutsceneView.speaker()?.let {
            Text.translatable(it).styled { name -> name.withColor(SPEAKER_GREY) }.append(Text.literal(" ")).append(speech)
        } ?: speech
        val lines = textRenderer.wrapLines(text, (width * SUBTITLE_WIDTH).toInt())
        val step = textRenderer.fontHeight + 2
        var y = height - band / 2 - (lines.size * step - 2) / 2
        for (line in lines) {
            context.drawText(textRenderer, line, (width - textRenderer.getWidth(line)) / 2, y, WHITE, false)
            y += step
        }
    }

    companion object {
        /** Each band's share of the screen's height: 2.39:1 inside 16:9 leaves an eighth top and bottom. */
        private const val BAND = (1.0 - (16.0 / 9.0) / 2.39) / 2.0
        private const val BLACK = 0xFF000000.toInt()
        private const val NOTICE_GREY = 0xA8A8A8
        private const val NOTICE_SCALE = 0.75f
        private const val SPEAKER_GREY = 0x9A9A9A
        private const val WHITE = 0xFFFFFF

        /** How far into the screen the vignette reaches (a share of its height or width). */
        private const val VIGNETTE_DEPTH = 0.22f

        /** The title's size: big, over the bands' black. */
        private const val TITLE_SCALE = 3.5f

        /** A subtitle line is at most this share of the screen's width. */
        private const val SUBTITLE_WIDTH = 0.6
    }
}
