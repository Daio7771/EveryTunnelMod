package daio7771.everytunnel.chapter2.level1.client

import daio7771.everytunnel.chapter2.level1.client.NonameOsLook.DIM_GREEN
import daio7771.everytunnel.chapter2.level1.client.NonameOsLook.GREEN
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1Act3Hooks
import daio7771.everytunnel.platform.ModSounds
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.resource.language.I18n
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.text.OrderedText
import net.minecraft.text.Text
import net.minecraft.util.Util
import org.lwjgl.glfw.GLFW

/**
 * A NonameOS computer of Chapter 2 Level 1's Act 3 ([Ch2L1Act3Hooks]): the look of [NonameOsScreen] ([NonameOsLook]) with a FILES window,
 * one row for each of the computer's files (its name, `doc.<file>.name`; NEW or READ; an OPEN button). Opened, a file fills the window,
 * typed out quickly (a click or a key shows the rest), its text the lang key `every_tunnel.ch2.l1.<file>` (lines split at `\n`), the mouse
 * wheel scrolling a long one. Closing it (Escape or CLOSE) goes back to the list and tells the server ([Ch2L1Act3Hooks.FILE_CLOSED_PACKET]),
 * which only then counts it as read. Escape on the list logs off. The game does not pause.
 */
class NonameOsFilesScreen(
    private val computer: String,
    private val files: List<String>,
    private val read: MutableList<Boolean>,
) : Screen(Text.translatable(KEY + "os.title")) {

    private var open = -1
    private var openedMs = 0L
    private var shownAll = false
    private var lines: List<OrderedText> = emptyList()
    private var total = 0
    private var scroll = 0

    override fun shouldPause(): Boolean = false

    private fun w() = minOf(WIDTH, width - 20)

    private fun h() = minOf(HEIGHT, height - 20)

    private fun left() = (width - w()) / 2

    private fun top() = (height - h()) / 2

    private fun rowY(i: Int) = top() + 52 + i * ROW

    private fun buttonX() = left() + w() - BUTTON_W - 16

    private fun closeY() = top() + h() - 24

    /** How many lines of a document fit between the title and the footer. */
    private fun visibleLines() = maxOf(1, (h() - 70) / LINE)

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val x0 = left()
        val y0 = top()
        val w = w()
        val h = h()
        NonameOsLook.window(context, textRenderer, width, height, x0, y0, w, h)
        if (open < 0) renderList(context, x0, y0, w, h, mouseX, mouseY) else renderFile(context, x0, y0, w, h, mouseX, mouseY)
    }

    private fun renderList(context: DrawContext, x0: Int, y0: Int, w: Int, h: Int, mouseX: Int, mouseY: Int) {
        text(context, I18n.translate(KEY + "os.files.$computer"), x0 + 12, y0 + 32, GREEN)
        for ((i, file) in files.withIndex()) {
            val y = rowY(i)
            text(context, I18n.translate(KEY + "doc.$file.name"), x0 + 16, y + 4, GREEN)
            text(context, I18n.translate(KEY + if (read[i]) "os.file.read" else "os.file.new"), x0 + w - BUTTON_W - 80, y + 4, if (read[i]) DIM_GREEN else GREEN)
            val bx = buttonX()
            NonameOsLook.button(context, textRenderer, I18n.translate(KEY + "os.button.open"), bx, y, BUTTON_W, BUTTON_H, true, inside(mouseX, mouseY, bx, y, BUTTON_W, BUTTON_H))
        }
        context.fill(x0 + 12, y0 + h - 22, x0 + w - 12, y0 + h - 21, DIM_GREEN)
        text(context, I18n.translate(KEY + "os.hint"), x0 + 12, y0 + h - 14, DIM_GREEN)
    }

    private fun renderFile(context: DrawContext, x0: Int, y0: Int, w: Int, h: Int, mouseX: Int, mouseY: Int) {
        val file = files[open]
        text(context, I18n.translate(KEY + "doc.$file.name"), x0 + 12, y0 + 32, GREEN)
        context.fill(x0 + 12, y0 + 43, x0 + w - 12, y0 + 44, DIM_GREEN)
        val typed = if (shownAll) total else ((Util.getMeasuringTimeMs() - openedMs) * CHARS_PER_SECOND / 1000).toInt().coerceAtMost(total)
        if (typed >= total) shownAll = true
        var left = typed
        val shown = visibleLines()
        for ((row, line) in lines.withIndex()) {
            val length = lengthOf(line)
            if (row >= scroll && row < scroll + shown) {
                val y = y0 + 50 + (row - scroll) * LINE
                if (left >= length) context.drawText(textRenderer, line, x0 + 14, y, GREEN, false)
                else if (left > 0) context.drawText(textRenderer, Text.literal(stringOf(line).take(left)), x0 + 14, y, GREEN, false)
            }
            left -= length
        }
        if (lines.size > shown) {
            text(context, "${scroll + 1}-${minOf(lines.size, scroll + shown)} / ${lines.size}", x0 + 14, closeY() + 4, DIM_GREEN)
        }
        val bx = x0 + w - BUTTON_W - 16
        NonameOsLook.button(context, textRenderer, I18n.translate(KEY + "os.button.close"), bx, closeY(), BUTTON_W, BUTTON_H, true, inside(mouseX, mouseY, bx, closeY(), BUTTON_W, BUTTON_H))
        if (!shownAll && (Util.getMeasuringTimeMs() / 400) % 2 == 0L) context.fill(x0 + 14, y0 + h - 34, x0 + 20, y0 + h - 32, GREEN)
    }

    private fun lengthOf(line: OrderedText): Int = stringOf(line).length

    private fun stringOf(line: OrderedText): String {
        val out = StringBuilder()
        line.accept { _, _, codePoint -> out.appendCodePoint(codePoint); true }
        return out.toString()
    }

    private fun text(context: DrawContext, text: String, x: Int, y: Int, colour: Int) {
        context.drawText(textRenderer, text, x, y, colour, false)
    }

    private fun inside(mx: Number, my: Number, x: Int, y: Int, w: Int, h: Int) =
        mx.toDouble() >= x && mx.toDouble() <= x + w && my.toDouble() >= y && my.toDouble() <= y + h

    private fun beep() {
        client?.soundManager?.play(PositionedSoundInstance.master(ModSounds.TERMINAL_KEY, 1f, 0.25f))
    }

    private fun openFile(i: Int) {
        open = i
        openedMs = Util.getMeasuringTimeMs()
        shownAll = false
        scroll = 0
        val width = w() - 28
        lines = I18n.translate(KEY + files[i]).split("\n").flatMap { row ->
            if (row.isBlank()) listOf(OrderedText.EMPTY) else textRenderer.wrapLines(Text.literal(row), width)
        }
        total = lines.sumOf(::lengthOf)
        beep()
    }

    /** Back to the list from a document: the server hears it was read. */
    private fun closeFile() {
        if (open < 0) return
        val buf = PacketByteBufs.create()
        buf.writeString(computer)
        buf.writeString(files[open])
        ClientPlayNetworking.send(Ch2L1Act3Hooks.FILE_CLOSED_PACKET, buf)
        read[open] = true
        open = -1
        beep()
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0) return true
        if (open >= 0) {
            if (inside(mouseX, mouseY, left() + w() - BUTTON_W - 16, closeY(), BUTTON_W, BUTTON_H)) closeFile() else shownAll = true
            return true
        }
        for (i in files.indices) {
            if (inside(mouseX, mouseY, buttonX(), rowY(i), BUTTON_W, BUTTON_H)) {
                openFile(i)
                return true
            }
        }
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (open >= 0) scroll = (scroll - amount.toInt().coerceIn(-3, 3)).coerceIn(0, maxOf(0, lines.size - visibleLines()))
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && open >= 0) {
            closeFile()
            return true
        }
        if (open >= 0 && !shownAll) {
            shownAll = true
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    /** Logging off with a document open (the window closed some other way) still counts it as read. */
    override fun removed() {
        closeFile()
    }

    companion object {
        private const val KEY = "every_tunnel.ch2.l1."
        private const val WIDTH = 360
        private const val HEIGHT = 240
        private const val ROW = 24
        private const val LINE = 10
        private const val BUTTON_W = 64
        private const val BUTTON_H = 16
        private const val CHARS_PER_SECOND = 600

        fun register() {
            ClientPlayNetworking.registerGlobalReceiver(Ch2L1Act3Hooks.FILES_PACKET) { client, _, buf, _ ->
                val computer = buf.readString()
                val count = buf.readVarInt()
                val files = ArrayList<String>(count)
                val read = ArrayList<Boolean>(count)
                repeat(count) {
                    files += buf.readString()
                    read += buf.readBoolean()
                }
                client.execute { MinecraftClient.getInstance().setScreen(NonameOsFilesScreen(computer, files, read)) }
            }
        }
    }
}
