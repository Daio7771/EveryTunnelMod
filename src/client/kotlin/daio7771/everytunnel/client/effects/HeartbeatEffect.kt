package daio7771.everytunnel.client.effects

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.client.ClientResources
import daio7771.everytunnel.platform.ModSounds
import daio7771.everytunnel.platform.PaleManEntity
import daio7771.everytunnel.platform.PaleManMode
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.client.sound.SoundInstance
import net.minecraft.sound.SoundCategory
import kotlin.math.sqrt

/**
 * The player's heartbeat, the warning that the Pale man is near.
 *
 * Within [RANGE] blocks of the nearest Pale man it beats from [MIN_BPM] (at the edge)
 * up to [MAX_BPM] (next to him), and gets louder. While he is chasing it never beats
 * slower than [CHASE_BPM]. In the player's head (not positioned), one beat at a time
 * (`player.heartbeat`, 4 variations), only while the world rules apply (Survival or
 * Adventure in a world with levels).
 */
object HeartbeatEffect {

    private const val RANGE = 24.0
    private const val MIN_BPM = 60.0
    private const val MAX_BPM = 140.0
    private const val CHASE_BPM = 120.0
    private const val MIN_VOLUME = 0.5f
    private const val MAX_VOLUME = 1.0f

    /** Ticks until the next beat; counts down by one per tick. */
    private var untilBeat = 0.0
    private var beating = false

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> stop() }
    }

    private fun stop() {
        untilBeat = 0.0
        if (beating) EveryTunnel.LOGGER.info("Heartbeat stopped")
        beating = false
    }

    private fun tick(client: MinecraftClient) {
        val player = client.player
        val world = client.world
        if (player == null || world == null || client.isPaused || !ClientResources.current.active) return stop()
        val paleMen = world.getEntitiesByClass(PaleManEntity::class.java, player.boundingBox.expand(RANGE)) { it.isAlive }
        val nearest = paleMen.minByOrNull { it.squaredDistanceTo(player) }
        val distance = nearest?.let { sqrt(it.squaredDistanceTo(player)) }
        if (nearest == null || distance == null || distance > RANGE) return stop()

        val closeness = 1.0 - distance / RANGE
        var bpm = MIN_BPM + (MAX_BPM - MIN_BPM) * closeness
        if (paleMen.any { it.mode == PaleManMode.CHASE }) bpm = maxOf(bpm, CHASE_BPM)
        if (!beating) EveryTunnel.LOGGER.info("Heartbeat started: Pale man {} blocks away, {} bpm", distance.toInt(), bpm.toInt())
        beating = true

        untilBeat -= 1.0
        if (untilBeat > 0.0) return
        untilBeat = 20.0 * 60.0 / bpm

        val volume = MIN_VOLUME + (MAX_VOLUME - MIN_VOLUME) * closeness.toFloat()
        client.soundManager.play(
            PositionedSoundInstance(
                ModSounds.HEARTBEAT.id, SoundCategory.PLAYERS, volume, 1.0f, SoundInstance.createRandom(),
                false, 0, SoundInstance.AttenuationType.NONE, 0.0, 0.0, 0.0, true,
            ),
        )
    }
}
