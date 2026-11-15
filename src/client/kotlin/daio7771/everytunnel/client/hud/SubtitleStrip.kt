package daio7771.everytunnel.client.hud

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text

/**
 * A voice's subtitle drawn over the HUD (over the hotbar and the bars), as the director's phrases are
 * (`client.cutscene.DirectorVoice`): the speaker's name in grey, then the cue in white, on a dark strip so that it
 * reads over anything, wrapped to two lines or so.
 */
object SubtitleStrip {

    /** The strip's bottom, this far over the screen's bottom: above the hotbar and the bars over it. */
    private const val ABOVE_HUD = 62
    private const val SPEAKER_GREY = 0x9A9A9A
    private const val WHITE = 0xFFFFFF
    private const val STRIP = 0x88000000.toInt()

    /** How much of the screen's width a line may take. */
    private const val WIDTH = 0.6

    /** Draws [text] (already translated) after the name under [speakerKey] (a lang key; null for no name). */
    fun draw(context: DrawContext, speakerKey: String?, text: String) {
        val font = MinecraftClient.getInstance().textRenderer
        val width = context.scaledWindowWidth
        val subtitle = Text.empty()
        if (speakerKey != null) {
            subtitle.append(Text.translatable(speakerKey).styled { it.withColor(SPEAKER_GREY) }).append(Text.literal(" "))
        }
        subtitle.append(Text.literal(text).styled { it.withColor(WHITE) })
        val lines = font.wrapLines(subtitle, (width * WIDTH).toInt())
        val step = font.fontHeight + 2
        val widest = lines.maxOf { font.getWidth(it) }
        var y = context.scaledWindowHeight - ABOVE_HUD - lines.size * step
        context.fill((width - widest) / 2 - 4, y - 3, (width + widest) / 2 + 4, y + lines.size * step + 1, STRIP)
        for (line in lines) {
            context.drawText(font, line, (width - font.getWidth(line)) / 2, y, WHITE, true)
            y += step
        }
    }
}
