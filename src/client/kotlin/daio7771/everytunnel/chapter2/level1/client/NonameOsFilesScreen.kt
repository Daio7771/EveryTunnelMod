package daio7771.everytunnel.chapter2.level1.client

import daio7771.everytunnel.chapter2.platform.Ch2Achievements
import daio7771.everytunnel.chapter2.level1.client.NonameOsLook.DIM_GREEN
import daio7771.everytunnel.chapter2.level1.client.NonameOsLook.GREEN
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1Act3Hooks
import daio7771.everytunnel.common.platform.ModSounds
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.resource.language.I18n
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.client.sound.SoundInstance
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.random.Random
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
 *
 * Below the files, the computer's recordings (the aux lab's easter egg): a row with the file's name, AUDIO and a PLAY button, which opens a
 * player: the name, a waveform that moves while it plays, `[??]` where its details would be, PLAY or STOP and CLOSE. It plays from the
 * computer ([pos]), heard like any sound there, and goes on if the window closes; no subtitles, and the server never hears of it. On a
 * [cipher] computer (the basement's) the window's own words are cipher symbols ([NonameOsLook.scramble]); the files' names and texts are not.
 */
class NonameOsFilesScreen(
    private val computer: String,
    private val files: List<String>,
    private val read: MutableList<Boolean>,
    private val recordings: List<Pair<String, String>>,
    private val pos: BlockPos,
    private val cipher: Boolean,
) : Screen(Text.translatable(KEY + "os.title")) {

    private var open = -1
    private var player = -1
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
        NonameOsLook.window(context, textRenderer, width, height, x0, y0, w, h, cipher)
        when {
            player >= 0 -> renderPlayer(context, x0, y0, w, h, mouseX, mouseY)
            open >= 0 -> renderFile(context, x0, y0, w, h, mouseX, mouseY)
            else -> renderList(context, x0, y0, w, h, mouseX, mouseY)
        }
    }

    private fun renderList(context: DrawContext, x0: Int, y0: Int, w: Int, h: Int, mouseX: Int, mouseY: Int) {
        text(context, ui("os.files.$computer"), x0 + 12, y0 + 32, GREEN)
        for ((i, file) in files.withIndex()) {
            val y = rowY(i)
            text(context, I18n.translate(KEY + "doc.$file.name"), x0 + 16, y + 4, GREEN)
            text(context, ui(if (read[i]) "os.file.read" else "os.file.new"), x0 + w - BUTTON_W - 80, y + 4, if (read[i]) DIM_GREEN else GREEN)
            val bx = buttonX()
            NonameOsLook.button(context, textRenderer, ui("os.button.open"), bx, y, BUTTON_W, BUTTON_H, true, inside(mouseX, mouseY, bx, y, BUTTON_W, BUTTON_H))
        }
        for ((i, recording) in recordings.withIndex()) {
            val y = rowY(files.size + i)
            text(context, recording.first, x0 + 16, y + 4, GREEN)
            text(context, ui("os.file.audio"), x0 + w - BUTTON_W - 80, y + 4, DIM_GREEN)
            val bx = buttonX()
            NonameOsLook.button(context, textRenderer, ui("os.button.play"), bx, y, BUTTON_W, BUTTON_H, true, inside(mouseX, mouseY, bx, y, BUTTON_W, BUTTON_H))
        }
        context.fill(x0 + 12, y0 + h - 22, x0 + w - 12, y0 + h - 21, DIM_GREEN)
        text(context, ui("os.hint"), x0 + 12, y0 + h - 14, DIM_GREEN)
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
        NonameOsLook.button(context, textRenderer, ui("os.button.close"), bx, closeY(), BUTTON_W, BUTTON_H, true, inside(mouseX, mouseY, bx, closeY(), BUTTON_W, BUTTON_H))
        if (!shownAll && (Util.getMeasuringTimeMs() / 400) % 2 == 0L) context.fill(x0 + 14, y0 + h - 34, x0 + 20, y0 + h - 32, GREEN)
    }

    private fun renderPlayer(context: DrawContext, x0: Int, y0: Int, w: Int, h: Int, mouseX: Int, mouseY: Int) {
        val (name, sound) = recordings[player]
        text(context, name, x0 + 12, y0 + 32, GREEN)
        context.fill(x0 + 12, y0 + 43, x0 + w - 12, y0 + 44, DIM_GREEN)
        text(context, "[??]", x0 + 14, y0 + 52, DIM_GREEN)
        val playing = isPlaying(sound)
        val mid = y0 + h / 2 + 4
        val left = x0 + 20
        val right = x0 + w - 20
        val now = Util.getMeasuringTimeMs() / 1000.0
        context.fill(left, mid, right, mid + 1, DIM_GREEN)
        if (playing) {
            for (x in left until right step 2) {
                val u = (x - left) * 0.11
                val a = MathHelper.sin((u + now * 7.0).toFloat()) * MathHelper.sin((u * 0.37 - now * 3.1).toFloat()) +
                    0.5f * MathHelper.sin((u * 2.3 + now * 13.0).toFloat()) * MathHelper.sin((now * 1.7 + u * 0.05).toFloat())
                val bar = (kotlin.math.abs(a) * WAVE).toInt()
                context.fill(x, mid - bar, x + 1, mid + bar + 1, GREEN)
            }
        }
        val bx = x0 + w - BUTTON_W - 16
        val px = bx - BUTTON_W - 8
        val label = ui(if (playing) "os.button.stop" else "os.button.play")
        NonameOsLook.button(context, textRenderer, label, px, closeY(), BUTTON_W, BUTTON_H, true, inside(mouseX, mouseY, px, closeY(), BUTTON_W, BUTTON_H))
        NonameOsLook.button(context, textRenderer, ui("os.button.close"), bx, closeY(), BUTTON_W, BUTTON_H, true, inside(mouseX, mouseY, bx, closeY(), BUTTON_W, BUTTON_H))
    }

    private fun isPlaying(sound: String): Boolean {
        val instance = playingNow ?: return false
        if (instance.id != Identifier(EVERY_TUNNEL, sound)) return false
        return client?.soundManager?.isPlaying(instance) == true
    }

    /** Plays [sound] from the computer, or stops it if it is playing. */
    private fun playOrStop(sound: String) {
        val manager = client?.soundManager ?: return
        if (isPlaying(sound)) {
            playingNow?.let(manager::stop)
            playingNow = null
        } else {
            playingNow?.let(manager::stop)
            val instance = PositionedSoundInstance(
                Identifier(EVERY_TUNNEL, sound), SoundCategory.BLOCKS, 1f, 1f, Random.create(), false, 0,
                SoundInstance.AttenuationType.LINEAR, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, false,
            )
            manager.play(instance)
            playingNow = instance
            ClientPlayNetworking.send(Ch2Achievements.RECORDING_PACKET, PacketByteBufs.create().also { it.writeString(sound) })
        }
        beep()
    }

    /** The window's own words (not a file's): the lang key's text, scrambled on a [cipher] computer. */
    private fun ui(key: String): String = I18n.translate(KEY + key).let { if (cipher) NonameOsLook.scramble(it) else it }

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
        if (player >= 0) {
            val bx = left() + w() - BUTTON_W - 16
            if (inside(mouseX, mouseY, bx, closeY(), BUTTON_W, BUTTON_H)) {
                player = -1
                beep()
            } else if (inside(mouseX, mouseY, bx - BUTTON_W - 8, closeY(), BUTTON_W, BUTTON_H)) {
                playOrStop(recordings[player].second)
            }
            return true
        }
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
        for (i in recordings.indices) {
            if (inside(mouseX, mouseY, buttonX(), rowY(files.size + i), BUTTON_W, BUTTON_H)) {
                player = i
                playOrStop(recordings[i].second)
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
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && player >= 0) {
            player = -1
            beep()
            return true
        }
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
        private const val WAVE = 22f
        private const val EVERY_TUNNEL = "every_tunnel"

        /** The recording playing from a computer, if any: it outlives the window, and a new one replaces it. */
        private var playingNow: SoundInstance? = null

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
                val recordings = List(buf.readVarInt()) { buf.readString() to buf.readString() }
                val pos = buf.readBlockPos()
                val cipher = buf.readBoolean()
                client.execute { MinecraftClient.getInstance().setScreen(NonameOsFilesScreen(computer, files, read, recordings, pos, cipher)) }
            }
        }
    }
}
