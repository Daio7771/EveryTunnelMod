package daio7771.everytunnel.client.effects

import daio7771.everytunnel.platform.LoungeMusicHooks
import daio7771.everytunnel.platform.ModSounds
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.client.sound.MovingSoundInstance
import net.minecraft.client.sound.SoundInstance
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.util.math.Vec3d
import kotlin.random.Random

/**
 * Level 4's jazz, as the server says (`platform.LoungeMusicHooks`). Minecraft plays only a few streams at
 * once, so there is not one per speaker: one sound sits at whichever speaker is nearest the player. Two run
 * together, the piece and its muffled copy for the Calm room, and each fades to its volume.
 *
 * In the blackout the pitch starts to slide after [BEND_DELAY_TICKS] from one random value to another, in
 * [PITCH_LOW]..[PITCH_HIGH], like a tape failing.
 */
object LoungeMusic {

    private const val FADE_PER_TICK = 0.02f
    private const val BEND_DELAY_TICKS = 60
    private const val PITCH_LOW = 0.6f
    private const val PITCH_HIGH = 1.2f
    private const val PITCH_PER_TICK = 0.006f
    /** The muffled copy also runs a little slow. */
    private const val FAR_PITCH = 0.85f

    private var main: Speaker? = null
    private var far: Speaker? = null
    private var speakers: List<Vec3d> = emptyList()
    private var blackout = false
    private var bendIn = 0
    private var bend = 1f
    private var bendTarget = 1f

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(LoungeMusicHooks.PACKET_ID) { client, _, buf, _ ->
            val mainVolume = buf.readFloat()
            val farVolume = buf.readFloat()
            val out = buf.readBoolean()
            val places = List(buf.readVarInt()) { Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()) }
            client.execute { change(client, mainVolume, farVolume, out, places) }
        }
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> stop() }
    }

    private fun change(client: MinecraftClient, mainVolume: Float, farVolume: Float, out: Boolean, places: List<Vec3d>) {
        speakers = places
        if (out && !blackout) bendIn = BEND_DELAY_TICKS
        blackout = out
        if (!out) {
            bend = 1f
            bendTarget = 1f
        }
        if (main == null) main = Speaker(ModSounds.MUSIC_LOUNGE, 1f).also { client.soundManager.play(it) }
        if (far == null) far = Speaker(ModSounds.MUSIC_LOUNGE_FAR, FAR_PITCH).also { client.soundManager.play(it) }
        main?.target = mainVolume
        far?.target = farVolume
    }

    private fun stop() {
        main?.finish()
        far?.finish()
        main = null
        far = null
        blackout = false
        bend = 1f
        bendTarget = 1f
    }

    private fun tick(client: MinecraftClient) {
        if (client.isPaused || !blackout) return
        if (bendIn > 0) {
            bendIn--
            return
        }
        if (bend == bendTarget) bendTarget = PITCH_LOW + Random.nextFloat() * (PITCH_HIGH - PITCH_LOW)
        bend = if (bend < bendTarget) minOf(bendTarget, bend + PITCH_PER_TICK) else maxOf(bendTarget, bend - PITCH_PER_TICK)
    }

    private class Speaker(sound: SoundEvent, private val basePitch: Float) :
        MovingSoundInstance(sound, SoundCategory.MUSIC, SoundInstance.createRandom()) {

        var target = 0f

        init {
            repeat = true
            repeatDelay = 0
            volume = 0f
            pitch = basePitch
            attenuationType = SoundInstance.AttenuationType.LINEAR
        }

        /** Silent at first, and it must run all the same. */
        override fun shouldAlwaysPlay(): Boolean = true

        fun finish() = setDone()

        override fun tick() {
            volume = if (volume < target) minOf(target, volume + FADE_PER_TICK) else maxOf(target, volume - FADE_PER_TICK)
            pitch = basePitch * bend
            val player = MinecraftClient.getInstance().player ?: return
            val nearest = speakers.minByOrNull { it.squaredDistanceTo(player.pos) } ?: return
            x = nearest.x
            y = nearest.y
            z = nearest.z
        }
    }
}
