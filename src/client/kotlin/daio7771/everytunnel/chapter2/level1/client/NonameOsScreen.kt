package daio7771.everytunnel.chapter2.level1.client

import daio7771.everytunnel.chapter2.level1.client.NonameOsLook.BAR
import daio7771.everytunnel.chapter2.level1.client.NonameOsLook.DIM_GREEN
import daio7771.everytunnel.chapter2.level1.client.NonameOsLook.GREEN
import daio7771.everytunnel.chapter2.level1.client.NonameOsLook.HOVER
import daio7771.everytunnel.chapter2.level1.client.NonameOsLook.SCREEN
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Bits
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1TaskHooks
import daio7771.everytunnel.platform.ModSounds
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.resource.language.I18n
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.text.Text
import net.minecraft.util.Util

/**
 * NonameOS, the factory's terminal (Chapter 2 Level 1's task 1, [Ch2L1TaskHooks]): green phosphor on black in the monitor's frame
 * of Level 5's computers ([NonameOsLook]), with the SAMPLE REGISTRY window: one row for each sample tube, its code, what became
 * of it (not scanned, scanned, registering, registered) and a REGISTER button for the ones taken. A click on the button fills a bar over
 * `register_seconds`; when it is full the screen asks the server to register that tube ([Ch2L1TaskHooks.OS_REGISTER_PACKET]), which answers
 * with the registry as it stands ([update]). When the last one is registered the batch is closed and the screen logs off by itself.
 * Escape logs off at any time. The game does not pause. Act 3's computers show their documents in another window ([NonameOsFilesScreen]).
 */
class NonameOsScreen(
    private var codes: List<String>,
    private var taken: Int,
    private var registered: Int,
    private var done: Boolean,
    private val registerTicks: Int,
) : Screen(Text.translatable(KEY + "os.title")) {

    private var registering = -1
    private var registeringSinceMs = 0L
    private var requested = false
    private var doneAtMs = 0L

    override fun shouldPause(): Boolean = false

    /** Logging off, by Escape, by the batch closing or by anything else: the server waits for this before Rowan speaks. */
    override fun removed() {
        ClientPlayNetworking.send(Ch2L1TaskHooks.OS_CLOSED_PACKET, PacketByteBufs.empty())
    }

    /** The server's answer: the registry as it stands now. */
    fun update(codes: List<String>, taken: Int, registered: Int, done: Boolean) {
        this.codes = codes
        this.taken = taken
        this.registered = registered
        if (done && !this.done) doneAtMs = Util.getMeasuringTimeMs()
        this.done = done
        if (registering >= 0 && Ch2L1Bits.has(registered, registering)) registering = -1
    }

    private fun left() = (width - minOf(WIDTH, width - 20)) / 2

    private fun top() = (height - minOf(HEIGHT, height - 20)) / 2

    private fun rowY(i: Int) = top() + 62 + i * ROW

    private fun buttonX() = left() + minOf(WIDTH, width - 20) - BUTTON_W - 16

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val now = Util.getMeasuringTimeMs()
        if (done && doneAtMs == 0L) doneAtMs = now
        if (done && now - doneAtMs > LOG_OFF_MS) {
            close()
            return
        }
        val w = minOf(WIDTH, width - 20)
        val h = minOf(HEIGHT, height - 20)
        val x0 = left()
        val y0 = top()
        NonameOsLook.window(context, textRenderer, width, height, x0, y0, w, h)
        text(context, I18n.translate(KEY + "os.registry"), x0 + 12, y0 + 34, GREEN)
        text(context, I18n.translate(KEY + "os.expected", codes.size), x0 + 12, y0 + 46, DIM_GREEN)

        if (registering >= 0 && !requested && (now - registeringSinceMs) >= registerTicks * 50L) {
            requested = true
            ClientPlayNetworking.send(Ch2L1TaskHooks.OS_REGISTER_PACKET, PacketByteBufs.create().also { it.writeVarInt(registering) })
        }
        if (registering >= 0 && now - registeringSinceMs > registerTicks * 50L + TIMEOUT_MS) registering = -1

        for ((i, code) in codes.withIndex()) {
            val y = rowY(i)
            val isTaken = Ch2L1Bits.has(taken, i)
            val isRegistered = Ch2L1Bits.has(registered, i)
            val status = when {
                isRegistered -> "os.row.registered"
                registering == i -> "os.row.registering"
                isTaken -> "os.row.scanned"
                else -> "os.row.not_scanned"
            }
            text(context, code, x0 + 16, y + 4, if (isTaken) GREEN else DIM_GREEN)
            text(context, I18n.translate(KEY + status), x0 + 86, y + 4, if (isRegistered) GREEN else DIM_GREEN)
            drawButton(context, i, y, isTaken && !isRegistered, now, mouseX, mouseY)
        }
        val count = Ch2L1Bits.count(registered)
        val footerY = y0 + h - 30
        context.fill(x0 + 12, footerY - 6, x0 + w - 12, footerY - 5, DIM_GREEN)
        text(context, I18n.translate(KEY + "os.progress", count, codes.size), x0 + 12, footerY, GREEN)
        text(context, I18n.translate(KEY + if (done) "os.done" else "os.hint"), x0 + 12, footerY + 12, if (done) GREEN else DIM_GREEN)
        if ((now / 500) % 2 == 0L) context.fill(x0 + 12, y0 + h - 12, x0 + 18, y0 + h - 10, GREEN)
    }

    private fun drawButton(context: DrawContext, i: Int, y: Int, enabled: Boolean, now: Long, mouseX: Int, mouseY: Int) {
        val x = buttonX()
        val hover = enabled && registering < 0 && mouseX in x..(x + BUTTON_W) && mouseY in y..(y + BUTTON_H)
        val colour = if (enabled) GREEN else DIM_GREEN
        context.fill(x, y, x + BUTTON_W, y + BUTTON_H, colour)
        context.fill(x + 1, y + 1, x + BUTTON_W - 1, y + BUTTON_H - 1, if (hover) HOVER else SCREEN)
        if (registering == i) {
            val fraction = ((now - registeringSinceMs) / (registerTicks * 50f)).coerceIn(0f, 1f)
            context.fill(x + 1, y + 1, x + 1 + ((BUTTON_W - 2) * fraction).toInt(), y + BUTTON_H - 1, BAR)
        }
        val label = I18n.translate(KEY + "os.button.register")
        text(context, label, x + (BUTTON_W - textRenderer.getWidth(label)) / 2, y + 4, if (enabled) GREEN else DIM_GREEN)
    }

    private fun text(context: DrawContext, text: String, x: Int, y: Int, colour: Int) {
        context.drawText(textRenderer, text, x, y, colour, false)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button != 0 || done || registering >= 0) return true
        val x = buttonX()
        for (i in codes.indices) {
            val y = rowY(i)
            if (mouseX >= x && mouseX <= x + BUTTON_W && mouseY >= y && mouseY <= y + BUTTON_H &&
                Ch2L1Bits.has(taken, i) && !Ch2L1Bits.has(registered, i)
            ) {
                registering = i
                requested = false
                registeringSinceMs = Util.getMeasuringTimeMs()
                client?.soundManager?.play(PositionedSoundInstance.master(ModSounds.TERMINAL_KEY, 1f, 0.25f))
                return true
            }
        }
        return true
    }

    companion object {
        private const val KEY = "every_tunnel.ch2.l1."
        private const val WIDTH = 360
        private const val HEIGHT = 240
        private const val ROW = 28
        private const val BUTTON_W = 96
        private const val BUTTON_H = 16
        private const val LOG_OFF_MS = 3000L
        private const val TIMEOUT_MS = 3000L

        fun register() {
            ClientPlayNetworking.registerGlobalReceiver(Ch2L1TaskHooks.OS_OPEN_PACKET) { client, _, buf, _ ->
                val codes = List(buf.readVarInt()) { buf.readString() }
                val taken = buf.readInt()
                val registered = buf.readInt()
                val done = buf.readBoolean()
                val ticks = buf.readVarInt()
                client.execute {
                    val open = client.currentScreen
                    if (open is NonameOsScreen) open.update(codes, taken, registered, done)
                    else MinecraftClient.getInstance().setScreen(NonameOsScreen(codes, taken, registered, done, ticks))
                }
            }
        }
    }
}
