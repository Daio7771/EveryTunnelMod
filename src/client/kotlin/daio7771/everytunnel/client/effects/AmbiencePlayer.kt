package daio7771.everytunnel.client.effects

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.level.Ambience
import daio7771.everytunnel.core.level.RandomSound
import daio7771.everytunnel.platform.AmbienceHooks
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.client.sound.MovingSoundInstance
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.client.sound.SoundInstance
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

/**
 * Plays the ambience of the player's current level, as sent by the server
 * (`platform.AmbienceHooks`):
 *
 * - The loop plays everywhere in the level, not positioned, and fades in and out over
 *   [FADE_SECONDS] when the player changes level.
 * - Each random sound waits a random time inside its interval, then plays once at a
 *   random spot around the player: random direction, a distance inside its range,
 *   2 blocks below to 3 above the player. Spots inside walls are fine: Sound Physics
 *   Remastered muffles them, which sounds like something behind the wall.
 *
 * Everything is in the Ambient/Environment volume category and pauses with the game.
 */
object AmbiencePlayer {

    private const val FADE_SECONDS = 2.0

    private var ambience = Ambience.NONE
    private var loop: LoopSound? = null
    private var countdowns = IntArray(0)
    private val random = Random()

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(AmbienceHooks.PACKET_ID) { client, _, buf, _ ->
            val received = AmbienceHooks.read(buf)
            client.execute { change(client, received) }
        }
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
        ClientPlayConnectionEvents.DISCONNECT.register { _, client -> client.execute { change(client, Ambience.NONE) } }
    }

    private fun change(client: MinecraftClient, next: Ambience) {
        if (next.loop != ambience.loop) {
            loop?.fadeOut()
            loop = next.loop?.let { id -> LoopSound(Identifier(id), next.loopVolume).also { client.soundManager.play(it) } }
        } else {
            loop?.targetVolume = next.loopVolume
        }
        // First play somewhere inside the interval, so entering a level does not fire everything at once.
        countdowns = IntArray(next.randomSounds.size) { random.nextInt(ticks(next.randomSounds[it].maxInterval) + 1) }
        ambience = next
        EveryTunnel.LOGGER.debug("Ambience: loop {}, {} random sound(s)", next.loop, next.randomSounds.size)
    }

    private fun tick(client: MinecraftClient) {
        val player = client.player ?: return
        if (client.isPaused) return
        ambience.randomSounds.forEachIndexed { i, sound ->
            if (--countdowns[i] > 0) return@forEachIndexed
            countdowns[i] = ticks(sound.minInterval + random.nextDouble() * (sound.maxInterval - sound.minInterval))
            play(client, sound, player.x, player.eyeY, player.z)
        }
    }

    private fun play(client: MinecraftClient, sound: RandomSound, x: Double, y: Double, z: Double) {
        val angle = random.nextDouble() * Math.PI * 2.0
        val distance = sound.minDistance + random.nextDouble() * (sound.maxDistance - sound.minDistance)
        client.soundManager.play(
            PositionedSoundInstance(
                Identifier(sound.sound), SoundCategory.AMBIENT, sound.volume.toFloat(), 1.0f, SoundInstance.createRandom(),
                false, 0, SoundInstance.AttenuationType.LINEAR,
                x + cos(angle) * distance, y - 2.0 + random.nextDouble() * 5.0, z + sin(angle) * distance, false,
            ),
        )
    }

    private fun ticks(seconds: Double): Int = (seconds * 20.0).toInt().coerceAtLeast(1)

    /** The level loop: not positioned, fades towards [targetVolume], fades out and ends after [fadeOut]. */
    private class LoopSound(id: Identifier, var targetVolume: Double) :
        MovingSoundInstance(SoundEvent.of(id), SoundCategory.AMBIENT, SoundInstance.createRandom()) {

        private var fadingOut = false

        init {
            repeat = true
            repeatDelay = 0
            volume = 0.0f
            relative = true
            attenuationType = SoundInstance.AttenuationType.NONE
        }

        fun fadeOut() {
            fadingOut = true
        }

        /** Starts silent (Minecraft skips sounds at volume 0 unless they ask to always play). */
        override fun shouldAlwaysPlay(): Boolean = true

        override fun tick() {
            val target = if (fadingOut) 0.0 else targetVolume
            val step = (1.0 / (FADE_SECONDS * 20.0)).toFloat()
            volume = if (volume < target) minOf(target.toFloat(), volume + step) else maxOf(target.toFloat(), volume - step)
            if (fadingOut && volume <= 0.0f) setDone()
        }
    }
}
