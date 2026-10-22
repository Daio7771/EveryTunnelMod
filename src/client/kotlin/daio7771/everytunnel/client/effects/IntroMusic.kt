package daio7771.everytunnel.client.effects

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.platform.IntroHooks
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.client.sound.MovingSoundInstance
import net.minecraft.client.sound.SoundInstance
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier
import kotlin.math.PI
import kotlin.math.sin

/**
 * The song of a level's intro (the elevator), started and ended by the server
 * (`platform.IntroHooks`). It plays once, in the player's head, in the Music volume
 * category. When the doors open it dies like a tape running down: over the fade the
 * pitch sinks to half (Minecraft's lowest) with a growing wow and flutter, while the
 * volume falls to nothing.
 */
object IntroMusic {

    private var song: Song? = null

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(IntroHooks.MUSIC_PACKET_ID) { client, _, buf, _ ->
            when (buf.readByte()) {
                IntroHooks.MUSIC_START -> {
                    val id = Identifier(buf.readString())
                    client.execute { start(client, id) }
                }
                IntroHooks.MUSIC_END -> {
                    val seconds = buf.readFloat()
                    client.execute { song?.dieOut(seconds) }
                }
            }
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, client -> client.execute { stop() } }
    }

    private fun start(client: MinecraftClient, id: Identifier) {
        stop()
        song = Song(SoundEvent.of(id)).also { client.soundManager.play(it) }
        EveryTunnel.LOGGER.info("Intro music: {}", id)
    }

    private fun stop() {
        song?.stopNow()
        song = null
    }

    private class Song(sound: SoundEvent) : MovingSoundInstance(sound, SoundCategory.MUSIC, SoundInstance.createRandom()) {

        /** Ticks into the dying out, and its length; -1 while it plays normally. */
        private var dyingTicks = -1
        private var dyingLength = 1

        init {
            repeat = false
            volume = 1f
            pitch = 1f
            relative = true
            attenuationType = SoundInstance.AttenuationType.NONE
        }

        fun dieOut(seconds: Float) {
            dyingTicks = 0
            dyingLength = (seconds * 20f).toInt().coerceAtLeast(1)
        }

        fun stopNow() = setDone()

        override fun tick() {
            if (dyingTicks < 0) return
            val t = (++dyingTicks).toFloat() / dyingLength
            if (t >= 1f) return setDone()
            val wow = 0.06f * t * sin(2.0 * PI * 1.3 * dyingTicks / 20.0).toFloat()
            val flutter = 0.03f * t * (random.nextFloat() - 0.5f)
            pitch = (1f - 0.5f * t * t + wow + flutter).coerceIn(0.5f, 1f)
            volume = (1f - t) * (1f - t)
        }
    }
}
