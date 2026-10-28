package daio7771.everytunnel.client.effects

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.client.ClientResources
import daio7771.everytunnel.entities.PaleManBrain
import daio7771.everytunnel.platform.ModSounds
import daio7771.everytunnel.entities.ObserverBrain
import daio7771.everytunnel.platform.ObserverEntity
import daio7771.everytunnel.platform.PaleManEntity
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.sound.MovingSoundInstance
import net.minecraft.client.sound.SoundInstance
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import kotlin.math.sqrt

/**
 * The music, picked from what the Pale men around the player are doing:
 *
 * - Nothing while none is within [NEAR] blocks: silence and the level's ambience.
 * - [Layer.PULSE] (low pulse): one is within [NEAR] blocks, wandering.
 * - [Layer.RISING] (endless rising tone): one walks towards a noise.
 * - [Layer.STRINGS] (dissonant strings): one is within [CLOSE] blocks and not walking
 *   to a noise (listening, searching or wandering).
 * - [Layer.CHASE]: one is chasing, within [CHASE_RANGE] blocks.
 * - Level 3's Observer ([observerLayer]): [Layer.OBSERVER_APPROACH] while he walks your way after
 *   seeing you (within [NEAR]), [Layer.OBSERVER_CHASE] while he runs at you (within [CHASE_RANGE]).
 *
 * The strongest layer wins. Stronger layers start at once; a weaker one takes over only
 * after it has been the right one for [SETTLE_TICKS], so brief state changes do not
 * flap the music. Layers crossfade; the chase fades out slowly, without any "he lost
 * you" cue, so the player is never sure. Distances have a margin ([MARGIN]) to leave
 * a layer, for the same reason.
 *
 * Stereo loops, not positioned, in the Music volume category, only while the world
 * rules apply (Survival or Adventure in a world with levels). Vanilla's background
 * music is kept off there.
 */
object TensionMusic {

    private const val NEAR = 48.0
    private const val CLOSE = 12.0
    private const val CHASE_RANGE = 64.0
    private const val MARGIN = 4.0
    private const val SETTLE_TICKS = 30
    /** The Observer's states that are after a player (not after a box). */
    private val OBSERVER_HUNTING = setOf(ObserverBrain.State.CHASE, ObserverBrain.State.SEARCH, ObserverBrain.State.GRAB)

    enum class Layer(val sound: SoundEvent?, val volume: Float, val fadeInSeconds: Double, val fadeOutSeconds: Double) {
        NONE(null, 0f, 0.0, 0.0),
        PULSE(ModSounds.TENSION_PULSE, 0.7f, 4.0, 4.0),
        RISING(ModSounds.TENSION_RISING, 0.6f, 3.0, 3.0),
        STRINGS(ModSounds.TENSION_STRINGS, 0.8f, 2.0, 3.0),
        OBSERVER_APPROACH(ModSounds.OBSERVER_APPROACH, 0.7f, 2.0, 3.0),
        CHASE(ModSounds.CHASE, 1.0f, 1.0, 3.0),
        OBSERVER_CHASE(ModSounds.OBSERVER_CHASE, 1.0f, 0.5, 3.0),
    }

    private var current = Layer.NONE
    /** The weaker layer waiting to take over, and for how many ticks it has been the right one. */
    private var pending = Layer.NONE
    private var pendingTicks = 0
    /** Playing loops, including the ones fading out (a layer that comes back fades in again). */
    private val loops = HashMap<Layer, LayerSound>()

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
        ClientPlayConnectionEvents.DISCONNECT.register { _, client -> client.execute { stopAll() } }
    }

    private fun stopAll() {
        for (loop in loops.values) loop.stopNow()
        loops.clear()
        if (current != Layer.NONE) EveryTunnel.LOGGER.info("Music stopped")
        current = Layer.NONE
        pending = Layer.NONE
        pendingTicks = 0
    }

    private fun tick(client: MinecraftClient) {
        loops.values.removeIf { it.isDone }
        val player = client.player
        val world = client.world
        if (player == null || world == null || !ClientResources.current.active) {
            if (current != Layer.NONE) change(Layer.NONE)
            return
        }
        client.musicTracker.stop()
        if (client.isPaused) return

        val paleMen = world.getEntitiesByClass(PaleManEntity::class.java, player.boundingBox.expand(CHASE_RANGE + MARGIN)) { it.isAlive }
            .maxOfOrNull { layerFor(it.brainState, sqrt(it.squaredDistanceTo(player))) } ?: Layer.NONE
        val observers = world.getEntitiesByClass(ObserverEntity::class.java, player.boundingBox.expand(CHASE_RANGE + MARGIN)) { it.isAlive }
            .maxOfOrNull { observerLayer(it, sqrt(it.squaredDistanceTo(player))) } ?: Layer.NONE
        val wanted = maxOf(paleMen, observers)

        when {
            wanted == current -> pending = Layer.NONE
            wanted > current -> change(wanted)
            wanted != pending -> {
                pending = wanted
                pendingTicks = 0
            }
            ++pendingTicks >= SETTLE_TICKS -> change(wanted)
        }
    }

    /** The layer one Pale man asks for; a layer already playing is kept up to [MARGIN] blocks further. */
    private fun layerFor(state: PaleManBrain.State, distance: Double): Layer {
        fun within(range: Double, layer: Layer) = distance <= range + if (current >= layer) MARGIN else 0.0
        return when {
            state == PaleManBrain.State.CHASE && within(CHASE_RANGE, Layer.CHASE) -> Layer.CHASE
            state != PaleManBrain.State.INVESTIGATE && within(CLOSE, Layer.STRINGS) -> Layer.STRINGS
            state == PaleManBrain.State.INVESTIGATE && within(NEAR, Layer.RISING) -> Layer.RISING
            within(NEAR, Layer.PULSE) -> Layer.PULSE
            else -> Layer.NONE
        }
    }

    /** The layer the Observer asks for: running at you, or walking your way after seeing you. */
    private fun observerLayer(observer: ObserverEntity, distance: Double): Layer {
        fun within(range: Double, layer: Layer) = distance <= range + if (current >= layer) MARGIN else 0.0
        val hunting = observer.brainState in OBSERVER_HUNTING
        return when {
            hunting && observer.running && within(CHASE_RANGE, Layer.OBSERVER_CHASE) -> Layer.OBSERVER_CHASE
            hunting && within(NEAR, Layer.OBSERVER_APPROACH) -> Layer.OBSERVER_APPROACH
            else -> Layer.NONE
        }
    }

    private fun change(next: Layer) {
        EveryTunnel.LOGGER.info("Music: {} -> {}", current, next)
        loops[current]?.fadeOut(current.fadeOutSeconds)
        next.sound?.let { sound ->
            val soundManager = MinecraftClient.getInstance().soundManager
            val loop = loops[next]
            if (loop != null && !loop.isDone && soundManager.isPlaying(loop)) {
                loop.fadeIn(next.fadeInSeconds)
            } else {
                loops[next] = LayerSound(sound, next.volume).also {
                    it.fadeIn(next.fadeInSeconds)
                    soundManager.play(it)
                }
            }
        }
        current = next
        pending = Layer.NONE
        pendingTicks = 0
    }

    /** One layer's loop: fades towards its full volume or towards silence, and ends once silent. */
    private class LayerSound(sound: SoundEvent, private val fullVolume: Float) :
        MovingSoundInstance(sound, SoundCategory.MUSIC, SoundInstance.createRandom()) {

        private var target = 0f
        private var step = 0f

        init {
            repeat = true
            repeatDelay = 0
            volume = 0f
            relative = true
            attenuationType = SoundInstance.AttenuationType.NONE
        }

        fun fadeIn(seconds: Double) = fadeTo(fullVolume, seconds)

        fun fadeOut(seconds: Double) = fadeTo(0f, seconds)

        fun stopNow() = setDone()

        private fun fadeTo(volume: Float, seconds: Double) {
            target = volume
            step = (fullVolume / (seconds * 20.0).coerceAtLeast(1.0)).toFloat()
        }

        /** Starts silent (Minecraft skips sounds at volume 0 unless they ask to always play). */
        override fun shouldAlwaysPlay(): Boolean = true

        override fun tick() {
            volume = if (volume < target) minOf(target, volume + step) else maxOf(target, volume - step)
            if (target == 0f && volume <= 0f) setDone()
        }
    }
}
