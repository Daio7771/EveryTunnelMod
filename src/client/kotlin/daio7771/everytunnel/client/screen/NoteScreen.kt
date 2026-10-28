package daio7771.everytunnel.client.screen

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.puzzle.NoteText
import daio7771.everytunnel.platform.NoteHooks
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.sound.SoundEvents
import net.minecraft.text.OrderedText
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import java.io.IOException
import kotlin.math.roundToInt

/**
 * A note found in a level, read on a sheet of the guide's worn paper ([GuideScreen]) over the
 * game, which goes on meanwhile (it does not pause). Opened by the server (`NoteHooks`) with the
 * note's id and the values for its placeholders.
 *
 * - The text is `assets/every_tunnel/notes/<lang>/<id>.txt`: Spanish (`es`) for any `es_*`
 *   language, English (`en`) otherwise, and English when the language has no such note. Its
 *   first line is the title; blank lines split paragraphs; `{A}` and `{A:word}` are filled
 *   ([NoteText]), the words from the lang keys `note.every_tunnel.number.<0-9>`.
 * - Esc, E or a click closes it; the mouse wheel scrolls a note longer than the screen.
 */
class NoteScreen(private val noteId: String, private val values: Map<String, Int>) : Screen(Text.empty()) {

    private var lines: List<Pair<OrderedText, Boolean>> = emptyList()      // (line, bold title)
    private var scroll = 0.0

    override fun init() {
        val client = client ?: return
        val raw = loadText(client, noteId)
        val filled = NoteText.fill(raw, values) { Text.translatable("note.every_tunnel.number.$it").string }
        val (heading, paragraphs) = NoteText.split(filled)
        val width = sheetWidth() - PAD_X * 2
        val out = mutableListOf<Pair<OrderedText, Boolean>>()
        for (line in textRenderer.wrapLines(Text.literal(heading).styled { it.withBold(true) }, width)) out += line to true
        for (paragraph in paragraphs) {
            out += OrderedText.EMPTY to false
            for (row in paragraph.split('\n')) {
                for (line in textRenderer.wrapLines(Text.literal(row), width)) out += line to false
            }
        }
        lines = out
    }

    override fun shouldPause(): Boolean = false

    private fun sheetWidth(): Int = (width - MARGIN * 2).coerceIn(SHEET_MIN, SHEET_MAX)

    private fun sheetHeight(): Int = PAD_TOP + lines.size * LINE_HEIGHT + PAD_BOTTOM

    private fun maxScroll(): Double = maxOf(0, sheetHeight() + MARGIN * 2 - height).toDouble()

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        val w = sheetWidth()
        val h = sheetHeight()
        val x = (width - w) / 2
        val y = if (h + MARGIN * 2 <= height) (height - h) / 2 else MARGIN - scroll.roundToInt()
        context.fill(x + SHADOW, y + SHADOW, x + w + SHADOW, y + h + SHADOW, SHADOW_COLOR)
        context.drawTexture(PAPER, x, y, 0f, 0f, w, h, PAPER_SIZE, PAPER_SIZE)
        EDGE_SHADES.forEachIndexed { i, shade ->
            context.fill(x + i, y + i, x + w - i, y + i + 1, shade)
            context.fill(x + i, y + h - i - 1, x + w - i, y + h - i, shade)
            context.fill(x + i, y + i + 1, x + i + 1, y + h - i - 1, shade)
            context.fill(x + w - i - 1, y + i + 1, x + w - i, y + h - i - 1, shade)
        }
        context.drawBorder(x, y, w, h, OUTLINE)
        lines.forEachIndexed { i, (line, bold) ->
            context.drawText(textRenderer, line, x + PAD_X, y + PAD_TOP + i * LINE_HEIGHT, if (bold) INK_DARK else INK, false)
        }
        super.render(context, mouseX, mouseY, delta)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        scroll = (scroll - amount * LINE_HEIGHT * 3).coerceIn(0.0, maxScroll())
        return true
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        close()
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (client?.options?.inventoryKey?.matchesKey(keyCode, scanCode) == true) {
            close()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    companion object {
        private val PAPER = Identifier(EveryTunnel.MOD_ID, "textures/gui/guide_paper.png")
        private const val PAPER_SIZE = 64

        private const val MARGIN = 12
        private const val SHEET_MIN = 160
        private const val SHEET_MAX = 260
        private const val PAD_X = 18
        private const val PAD_TOP = 18
        private const val PAD_BOTTOM = 20
        private const val SHADOW = 3
        /** Vanilla's font is 9 pixels tall; one more between lines. */
        private const val LINE_HEIGHT = 10

        // The guide's ink and paper (GuideScreen).
        private const val INK = 0xFF2C2B30.toInt()
        private const val INK_DARK = 0xFF1C1B20.toInt()
        private const val OUTLINE = 0xFF6F6A5E.toInt()
        private val EDGE_SHADES = intArrayOf(0x40806838, 0x2A806838, 0x16806838)
        private const val SHADOW_COLOR = 0x60000000

        /** The note's text in the game's language (see the class header); a short line if it is missing. */
        private fun loadText(client: MinecraftClient, id: String): String {
            val language = if (client.languageManager.language.startsWith("es_")) "es" else "en"
            for (lang in listOf(language, "en").distinct()) {
                val resource = client.resourceManager.getResource(Identifier(EveryTunnel.MOD_ID, "notes/$lang/$id.txt")).orElse(null) ?: continue
                try {
                    return resource.reader.use { it.readText() }
                } catch (e: IOException) {
                    EveryTunnel.LOGGER.warn("Note {} could not be read", id, e)
                }
            }
            EveryTunnel.LOGGER.warn("No text for note {}", id)
            return "..."
        }

        fun register() {
            ClientPlayNetworking.registerGlobalReceiver(NoteHooks.NOTE_PACKET_ID) { client, _, buf, _ ->
                val id = buf.readString()
                val values = (0 until buf.readVarInt()).associate { buf.readString() to buf.readVarInt() }
                client.execute {
                    client.setScreen(NoteScreen(id, values))
                    client.soundManager.play(PositionedSoundInstance.master(SoundEvents.ITEM_BOOK_PAGE_TURN, 1f))
                }
            }
        }
    }
}
