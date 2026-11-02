package daio7771.everytunnel.client.screen

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.puzzle.SafeDial
import daio7771.everytunnel.platform.ModSounds
import daio7771.everytunnel.platform.SafeHooks
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.sound.SoundEvent
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.RotationAxis
import org.lwjgl.glfw.GLFW
import kotlin.random.Random

/**
 * A safe's dial, over the game, which goes on meanwhile. A and D or the mouse wheel turn it, one number at
 * a time ([SafeDial]); what matters is heard (`block.safe.tick`, `block.safe.click`). Nothing on screen says
 * how far it has gone, but the dial gives a short shake on each right number, and after 3 failures it trembles
 * for as long as it stands on one. The picture comes from `tools/gui/safe_dial.py`. Leaving keeps the failures and forgets the rest.
 */
class SafeScreen(private val pos: BlockPos, private val dial: SafeDial) : Screen(Text.empty()) {

    private var sent = false

    /** Frames left of the shake of a right number, and of the wait before an opened safe closes this. */
    private var shakeFrames = 0
    private var closing = false

    override fun shouldPause(): Boolean = false

    private fun turn(direction: Int) {
        if (dial.solved) return
        when (dial.turn(direction)) {
            SafeDial.Event.TICK -> play(ModSounds.SAFE_TICK)
            SafeDial.Event.CLICK -> {
                play(ModSounds.SAFE_CLICK)
                shakeFrames = SHAKE_TICKS
            }
            SafeDial.Event.RESET -> play(ModSounds.SAFE_TICK)
            SafeDial.Event.SOLVED -> {
                play(ModSounds.SAFE_CLICK)
                shakeFrames = SHAKE_TICKS
                closing = true
            }
        }
    }

    override fun tick() {
        if (shakeFrames > 0) {
            shakeFrames--
            if (shakeFrames == 0 && closing) close()
        }
    }

    private fun play(sound: SoundEvent) {
        client?.soundManager?.play(PositionedSoundInstance.master(sound, 1f))
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context)
        val cx = width / 2
        val cy = height / 2
        val jitter = if (dial.trembling || shakeFrames > 0) Random.nextInt(-1, 2).toFloat() else 0f
        val matrices = context.matrices
        matrices.push()
        matrices.translate(cx + jitter, cy.toFloat(), 0f)
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-dial.position * 360f / SafeDial.NUMBERS + jitter))
        matrices.translate(-DIAL_SIZE / 2f, -DIAL_SIZE / 2f, 0f)
        context.drawTexture(DIAL, 0, 0, 0f, 0f, DIAL_SIZE, DIAL_SIZE, DIAL_SIZE, DIAL_SIZE)
        matrices.pop()
        // The mark the numbers turn under.
        context.fill(cx - 1, cy - DIAL_SIZE / 2 - 6, cx + 1, cy - DIAL_SIZE / 2 + 2, MARK)
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.every_tunnel.safe.controls"), cx, cy + DIAL_SIZE / 2 + 8, HELP)
        super.render(context, mouseX, mouseY, delta)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (amount != 0.0) turn(if (amount > 0) 1 else -1)
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        when (keyCode) {
            GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_LEFT -> turn(-1)
            GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_RIGHT -> turn(1)
            else -> return super.keyPressed(keyCode, scanCode, modifiers)
        }
        return true
    }

    override fun removed() {
        if (sent) return
        sent = true
        val buf = PacketByteBufs.create()
        buf.writeBlockPos(pos)
        buf.writeVarInt(dial.failures)
        buf.writeBoolean(dial.solved)
        ClientPlayNetworking.send(SafeHooks.RESULT_PACKET_ID, buf)
    }

    companion object {
        private val DIAL = Identifier(EveryTunnel.MOD_ID, "textures/gui/safe_dial.png")
        private const val DIAL_SIZE = 128
        private const val SHAKE_TICKS = 8
        private const val MARK = 0xFFD8B048.toInt()
        private const val HELP = 0xFFB0B0A8.toInt()

        fun register() {
            ClientPlayNetworking.registerGlobalReceiver(SafeHooks.OPEN_PACKET_ID) { client: MinecraftClient, _, buf, _ ->
                val pos = buf.readBlockPos()
                val combination = List(buf.readVarInt()) { buf.readVarInt() }
                val failures = buf.readVarInt()
                client.execute { client.setScreen(SafeScreen(pos, SafeDial(combination, failures))) }
            }
        }
    }
}
