package daio7771.everytunnel.client.cutscene

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.cutscene.GeigerClicks
import daio7771.everytunnel.core.cutscene.PrologueScene
import net.minecraft.client.MinecraftClient
import net.minecraft.client.sound.MovingSoundInstance
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.client.sound.SoundInstance
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d

/**
 * What the prologue's laboratory sounds like (`client.cutscene.CutsceneView` starts, ticks and stops it): the
 * lights' hum, flat under the Ambient slider, and a drone under it, in the Music slider, whose volume follows the
 * scene ([PrologueScene.humLevel], [PrologueScene.droneLevel]); and the radiation counter's Geiger clicks, which
 * come from the counter on the bench (positional, so they cross from one ear to the other as the camera passes and
 * grow nearer), ever faster ([PrologueScene.geigerRate], drawn tick by tick by [GeigerClicks]). The voice is
 * [CutsceneView]'s own. Nothing here is saved: a scene's sound begins and ends with it.
 */
object PrologueSound {

    /** The loops' volumes at their fullest: the voice stays on top of both. */
    private const val HUM_VOLUME = 1.0f
    private const val DRONE_VOLUME = 0.7f
    private const val GEIGER_VOLUME = 0.9f

    /** Above zero: the game does not start a sound whose volume is zero. */
    private const val START_VOLUME = 0.0001f

    private val clicks = GeigerClicks(seed = 20241114L)

    private var hum: Bed? = null
    private var drone: Bed? = null
    private var counter: Vec3d? = null

    /** Begins the sound of a scene whose radiation counter stands at [counterAt]. */
    fun start(client: MinecraftClient, counterAt: Vec3d) {
        stop(client)
        counter = counterAt
        hum = Bed("ambient.prologue.hum", SoundCategory.AMBIENT).also { client.soundManager.play(it) }
        drone = Bed("music.prologue.drone", SoundCategory.MUSIC).also { client.soundManager.play(it) }
    }

    /** One tick of the scene ([tick] of [total]): the loops' volumes, and the counts that fall in it. */
    fun tick(client: MinecraftClient, tick: Int, total: Int) {
        hum?.level = (PrologueScene.humLevel(tick.toDouble(), total) * HUM_VOLUME).toFloat()
        drone?.level = (PrologueScene.droneLevel(tick.toDouble(), total) * DRONE_VOLUME).toFloat()
        val at = counter ?: return
        val counts = clicks.counts(tick, PrologueScene.geigerRate(tick.toDouble(), total))
        if (counts == 0) return
        val id = when {
            counts == 1 -> "item.detector.click"
            counts < 5 -> "item.detector.clicks"
            else -> "item.detector.crackle"
        }
        client.soundManager.play(
            PositionedSoundInstance(
                Identifier(EveryTunnel.MOD_ID, id), SoundCategory.BLOCKS, GEIGER_VOLUME, 1f, SoundInstance.createRandom(),
                false, 0, SoundInstance.AttenuationType.LINEAR, at.x, at.y, at.z, false,
            ),
        )
    }

    /** Ends it, at once: the scene is over or was skipped. Safe to call when nothing plays. */
    fun stop(client: MinecraftClient) {
        hum?.stopNow()
        drone?.stopNow()
        hum = null
        drone = null
        counter = null
    }

    /** A looping sound in the player's head whose volume the scene sets ([level]) every tick. */
    private class Bed(id: String, category: SoundCategory) :
        MovingSoundInstance(SoundEvent.of(Identifier(EveryTunnel.MOD_ID, id)), category, SoundInstance.createRandom()) {

        @Volatile
        var level = START_VOLUME

        init {
            repeat = true
            repeatDelay = 0
            volume = START_VOLUME
            pitch = 1f
            relative = true
            attenuationType = SoundInstance.AttenuationType.NONE
        }

        fun stopNow() = setDone()

        override fun tick() {
            volume = level.coerceAtLeast(START_VOLUME)
        }
    }
}
