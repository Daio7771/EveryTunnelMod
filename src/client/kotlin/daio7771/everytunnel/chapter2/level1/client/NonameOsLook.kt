package daio7771.everytunnel.chapter2.level1.client

import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.resource.language.I18n

/**
 * How NonameOS looks, shared by its windows ([NonameOsScreen]'s sample registry, [NonameOsFilesScreen]'s files): green phosphor on black
 * in the white frame of Level 5's monitors, over the game dimmed, with its title bar.
 */
object NonameOsLook {

    const val DIM = 0xA0000000.toInt()
    const val FRAME = 0xFFD8DAD6.toInt()
    const val SCREEN = 0xFF060E08.toInt()
    const val GREEN = 0xFF5CE07A.toInt()
    const val DIM_GREEN = 0xFF2F7A42.toInt()
    const val HOVER = 0xFF12301A.toInt()
    const val BAR = 0xFF1E5A2E.toInt()

    private const val KEY = "every_tunnel.ch2.l1."

    /** The game dimmed, the monitor's frame and screen at ([x0], [y0]) of [w] by [h], and the title bar: "NONAME OS", its version, a rule. */
    fun window(context: DrawContext, font: TextRenderer, width: Int, height: Int, x0: Int, y0: Int, w: Int, h: Int) {
        context.fill(0, 0, width, height, DIM)
        context.fill(x0 - 6, y0 - 6, x0 + w + 6, y0 + h + 6, FRAME)
        context.fill(x0, y0, x0 + w, y0 + h, SCREEN)
        val version = I18n.translate(KEY + "os.version")
        context.drawText(font, I18n.translate(KEY + "os.title"), x0 + 12, y0 + 10, GREEN, false)
        context.drawText(font, version, x0 + w - 12 - font.getWidth(version), y0 + 10, DIM_GREEN, false)
        context.fill(x0 + 12, y0 + 22, x0 + w - 12, y0 + 23, DIM_GREEN)
    }

    /** A button: a green outline, filled darker under the mouse, its [label] centred. */
    fun button(context: DrawContext, font: TextRenderer, label: String, x: Int, y: Int, w: Int, h: Int, enabled: Boolean, hover: Boolean) {
        val colour = if (enabled) GREEN else DIM_GREEN
        context.fill(x, y, x + w, y + h, colour)
        context.fill(x + 1, y + 1, x + w - 1, y + h - 1, if (hover && enabled) HOVER else SCREEN)
        context.drawText(font, label, x + (w - font.getWidth(label)) / 2, y + (h - 8) / 2, colour, false)
    }
}
