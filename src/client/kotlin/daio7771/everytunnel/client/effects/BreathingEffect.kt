package daio7771.everytunnel.client.effects

import daio7771.everytunnel.client.ClientResources
import daio7771.everytunnel.platform.ModSounds
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.client.sound.SoundInstance
import net.minecraft.sound.SoundCategory

/**
 * Heavy breathing when the player runs out of stamina.
 *
 * Starts when the server reports the player exhausted and plays to the end (about 8 s,
 * fading out while stamina recovers). It is in the player's head: not positioned, not
 * heard by others. A new one never starts while the previous one is still playing.
 */
object BreathingEffect {

    private const val VOLUME = 0.8f

    private var wasExhausted = false
    private var playing: SoundInstance? = null

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            wasExhausted = false
            playing = null
        }
    }

    private fun tick(client: MinecraftClient) {
        val resources = ClientResources.current
        val exhausted = client.player != null && resources.active && resources.exhausted
        if (exhausted && !wasExhausted) {
            val current = playing
            if (current == null || !client.soundManager.isPlaying(current)) {
                val sound = PositionedSoundInstance(
                    ModSounds.BREATHING_EXHAUSTED.id, SoundCategory.PLAYERS, VOLUME, 1.0f, SoundInstance.createRandom(),
                    false, 0, SoundInstance.AttenuationType.NONE, 0.0, 0.0, 0.0, true,
                )
                client.soundManager.play(sound)
                playing = sound
            }
        }
        wasExhausted = exhausted
    }
}
