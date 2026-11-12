package daio7771.everytunnel.client.cutscene

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text
import net.minecraft.util.Util
import org.lwjgl.glfw.GLFW

/**
 * Open while a cinematic plays ([CutsceneView]): it takes every key and the mouse (the player can neither move
 * nor look; the cursor is hidden), never pauses the game, and Escape does nothing. It draws the black bands,
 * 2.39:1 on a 16:9 screen, sliding in and out, his subtitles in the bottom band (whole cues, white after his
 * name in grey, centred, two lines at most), the notice to skip in the bottom right corner, and a skip's
 * black. The jump key skips ([CutsceneView.skipPressed]).
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
        val black = CutsceneView.skipBlack()
        if (black > 0f) context.fill(0, 0, width, height, ((black * 255).toInt() shl 24))
    }

    override fun renderBackground(context: DrawContext) = Unit

    private fun drawSubtitle(context: DrawContext, key: String, band: Int) {
        val text = Text.translatable(SPEAKER).styled { it.withColor(SPEAKER_GREY) }
            .append(Text.literal(" "))
            .append(Text.translatable(key).styled { it.withColor(WHITE) })
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
        private const val SPEAKER = "subtitle.every_tunnel.speaker.director"
        private const val SPEAKER_GREY = 0x9A9A9A
        private const val WHITE = 0xFFFFFF
        /** A subtitle line is at most this share of the screen's width. */
        private const val SUBTITLE_WIDTH = 0.6
    }
}
