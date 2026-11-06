package daio7771.everytunnel.client.screen

import daio7771.everytunnel.core.death.DeathCause
import daio7771.everytunnel.platform.DeathHooks
import daio7771.everytunnel.platform.ModSounds
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.client.sound.SoundInstance
import net.minecraft.sound.SoundCategory
import net.minecraft.text.Text
import net.minecraft.util.Util
import org.lwjgl.glfw.GLFW

/**
 * The death screen: on the black of dying (`platform.DeathHooks`, which says what killed the player,
 * [DeathCause]), once the screen is dark, "Killed by", the killer's name big, and a tip under it, in dark
 * red, Minecraft's letters, come up with a low blow (`event.death.reveal`). A death of no named cause
 * says only "You died". Under it, "Try again", in the menus' style (a ">" by it under the mouse): it, Enter,
 * Space or Esc tell the server ([DeathHooks.RETRY_PACKET_ID]), which then brings the screen back. Until
 * then the black stays. The game does not pause.
 *
 * Texts: `death.every_tunnel.<cause>`, `.<cause>.tip` and `death.every_tunnel.try_again` in the lang file.
 */
class DeathScreen private constructor(private val cause: DeathCause) : Screen(Text.translatable("death.every_tunnel.you_died")) {

    private val openedMs = Util.getMeasuringTimeMs()
    private var answered = false
    private var buttonY = 0

    override fun shouldPause(): Boolean = false

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        context.fill(0, 0, width, height, BLACK)
        val shown = Util.getMeasuringTimeMs() - openedMs
        val alpha = (minOf(1f, shown.toFloat() / FADE_MS) * 255).toInt()
        if (alpha < 5) return
        val colour = (alpha shl 24) or COLOUR
        var y: Float
        if (cause == DeathCause.OTHER) {
            y = height / 2f - 9 * NAME_SCALE
            drawCentered(context, Text.translatable("death.every_tunnel.you_died"), NAME_SCALE, y, colour)
            y += 9 * NAME_SCALE + 20
        } else {
            val tip = textRenderer.wrapLines(Text.translatable("death.every_tunnel.${cause.id}.tip"), TIP_WIDTH)
            val block = 9 * KILLED_BY_SCALE + 4 + 9 * NAME_SCALE + 12 + tip.size * 11 + 20 + 9
            y = height / 2f - block / 2
            drawCentered(context, Text.translatable("death.every_tunnel.killed_by"), KILLED_BY_SCALE, y, colour)
            y += 9 * KILLED_BY_SCALE + 4
            drawCentered(context, Text.translatable("death.every_tunnel.${cause.id}"), NAME_SCALE, y, colour)
            y += 9 * NAME_SCALE + 12
            for (line in tip) {
                context.drawText(textRenderer, line, (width - textRenderer.getWidth(line)) / 2, y.toInt(), colour, false)
                y += 11
            }
            y += 20
        }
        buttonY = y.toInt()
        if (shown < BUTTON_MS) return
        val label = Text.translatable("death.every_tunnel.try_again")
        val x = (width - textRenderer.getWidth(label)) / 2
        val chosen = over(mouseX, mouseY)
        if (chosen) context.drawText(textRenderer, Text.literal(">"), x - 10, buttonY, CHOSEN, false)
        context.drawText(textRenderer, label, x, buttonY, if (chosen) CHOSEN else (0xFF shl 24) or COLOUR, false)
    }

    private fun drawCentered(context: DrawContext, line: Text, scale: Float, y: Float, colour: Int) {
        val matrices = context.matrices
        matrices.push()
        matrices.translate(width / 2f, y, 0f)
        matrices.scale(scale, scale, 1f)
        context.drawText(textRenderer, line, -textRenderer.getWidth(line) / 2, 0, colour, false)
        matrices.pop()
    }

    private fun ready(): Boolean = Util.getMeasuringTimeMs() - openedMs >= BUTTON_MS

    private fun over(mouseX: Int, mouseY: Int): Boolean {
        val half = textRenderer.getWidth(Text.translatable("death.every_tunnel.try_again")) / 2 + 12
        return mouseX in (width / 2 - half)..(width / 2 + half) && mouseY in (buttonY - 4)..(buttonY + 12)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (ready() && over(mouseX.toInt(), mouseY.toInt())) tryAgain()
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        when (keyCode) {
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_ESCAPE -> if (ready()) tryAgain()
        }
        return true
    }

    // Esc does what the button does (keyPressed answers it once the button shows).
    override fun shouldCloseOnEsc(): Boolean = false

    override fun removed() {
        // Closed some other way: the server must not wait for good.
        if (!answered) send()
    }

    private fun tryAgain() {
        if (answered) return
        send()
        client?.setScreen(null)
    }

    private fun send() {
        answered = true
        if (ClientPlayNetworking.canSend(DeathHooks.RETRY_PACKET_ID)) ClientPlayNetworking.send(DeathHooks.RETRY_PACKET_ID, PacketByteBufs.empty())
    }

    companion object {
        private const val COLOUR = 0xAA0000
        private const val CHOSEN = 0xFFFF5555.toInt()
        private const val BLACK = 0xFF000000.toInt()
        private const val FADE_MS = 500L
        /** The button shows once the words are up. */
        private const val BUTTON_MS = 700L
        private const val TIP_WIDTH = 260
        private const val NAME_SCALE = 3f
        private const val KILLED_BY_SCALE = 1.5f

        private var pending: DeathCause? = null
        private var receivedMs = 0L

        fun register() {
            ClientPlayNetworking.registerGlobalReceiver(DeathHooks.CAUSE_PACKET_ID) { client, _, buf, _ ->
                val id = buf.readString()
                client.execute {
                    pending = DeathCause.byId(id)
                    receivedMs = Util.getMeasuringTimeMs()
                }
            }
            ClientPlayConnectionEvents.DISCONNECT.register { _, client -> client.execute { pending = null } }
            // Once the screen has faded to black.
            ClientTickEvents.END_CLIENT_TICK.register { client ->
                val cause = pending ?: return@register
                if (Util.getMeasuringTimeMs() - receivedMs < DeathHooks.FADE_OUT_TICKS * 50L) return@register
                pending = null
                open(client, cause)
            }
        }

        private fun open(client: MinecraftClient, cause: DeathCause) {
            client.setScreen(DeathScreen(cause))
            client.soundManager.play(
                PositionedSoundInstance(
                    ModSounds.DEATH_REVEAL.id, SoundCategory.PLAYERS, 1f, 1f, SoundInstance.createRandom(),
                    false, 0, SoundInstance.AttenuationType.NONE, 0.0, 0.0, 0.0, true,
                ),
            )
        }
    }
}
