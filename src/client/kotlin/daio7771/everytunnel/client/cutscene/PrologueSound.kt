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
 * What the prologue's laboratory sounds like (`client.cutscene.CutsceneView` starts, ticks and stops it), all on the
 * scene's ticks ([PrologueScene.PLAN]): the lights' hum, flat under the Ambient slider, and a drone under it, in the
 * Music slider, whose volume follows the scene ([PrologueScene.humLevel], [PrologueScene.droneLevel]); the radiation
 * counter's Geiger clicks, from the counter on the bench (positional, so they cross from one ear to the other as the
 * camera passes), ever faster ([PrologueScene.geigerRate], drawn tick by tick by [GeigerClicks]); a fluorescent's
 * flicker at each beat of the lights going off and on, and the last beat's power cut and restart; a crack of bones as
 * he goes through each stage of his change; the Level 2 stinger's hit and his breathing when he becomes a Pale man; and, at the cut to black, everything stops dead, and
 * the title lands on the black with one low blow. The voice is [CutsceneView]'s own. Nothing here is saved: a scene's
 * sound begins and ends with it.
 */
object PrologueSound {

    /** The loops' volumes at their fullest: the voice stays on top of both. */
    private const val HUM_VOLUME = 1.0f
    private const val DRONE_VOLUME = 0.7f
    private const val GEIGER_VOLUME = 0.9f
    private const val FLICKER_VOLUME = 1.0f
    private const val BREATH_VOLUME = 1.0f
    private const val REVEAL_VOLUME = 0.9f
    private const val CRACK_VOLUME = 1.0f
    private const val TITLE_VOLUME = 1.0f

    /** Above zero: the game does not start a sound whose volume is zero. */
    private const val START_VOLUME = 0.0001f

    private val clicks = GeigerClicks(seed = 20241114L)

    private var hum: Bed? = null
    private var drone: Bed? = null
    private var counter: Vec3d? = null
    private var lights: Vec3d? = null
    private var him: Vec3d? = null

    /** Begins the sound of a scene whose radiation counter stands at [counterAt], its lights over [lightsAt] and the scientist at [hisAt]. */
    fun start(client: MinecraftClient, counterAt: Vec3d, lightsAt: Vec3d, hisAt: Vec3d) {
        stop(client)
        counter = counterAt
        lights = lightsAt
        him = hisAt
        hum = Bed("ambient.prologue.hum", SoundCategory.AMBIENT).also { client.soundManager.play(it) }
        drone = Bed("music.prologue.drone", SoundCategory.MUSIC).also { client.soundManager.play(it) }
    }

    /** One tick of the scene: the loops' volumes, the counts that fall in it, and whatever sound is cued on it. */
    fun tick(client: MinecraftClient, tick: Int) {
        val cues = PrologueScene.PLAN.cues
        val now = tick.toDouble()
        hum?.level = (PrologueScene.humLevel(now, cues.cutAt) * HUM_VOLUME).toFloat()
        drone?.level = (PrologueScene.droneLevel(now, cues.swapAt, cues.cutAt) * DRONE_VOLUME).toFloat()
        geiger(client, tick, PrologueScene.geigerRate(now, cues.cutAt))
        val beats = cues.darkBeats
        val change = beats.last()
        lights?.let { at ->
            if (beats.any { it.first == tick }) {
                play(client, if (tick == change.first) "block.fluorescent.off" else "block.fluorescent.flicker", SoundCategory.BLOCKS, FLICKER_VOLUME, at)
            }
            if (tick == change.last + 1) play(client, "block.fluorescent.start", SoundCategory.BLOCKS, FLICKER_VOLUME, at)
        }
        if (tick == cues.sickAt || tick == cues.stretchAt) {
            him?.let { play(client, "event.prologue.crack", SoundCategory.HOSTILE, CRACK_VOLUME, it) }
        }
        if (tick == cues.swapAt) {
            play(client, "music.prologue.reveal", SoundCategory.MUSIC, REVEAL_VOLUME, null)
            him?.let { play(client, "entity.pale_man.breathing", SoundCategory.HOSTILE, BREATH_VOLUME, it) }
        }
        if (tick == cues.titleAt) play(client, "event.prologue.title", SoundCategory.MUSIC, TITLE_VOLUME, null)
    }

    private fun geiger(client: MinecraftClient, tick: Int, rate: Double) {
        val at = counter ?: return
        val counts = clicks.counts(tick, rate)
        if (counts == 0) return
        val id = when {
            counts == 1 -> "item.detector.click"
            counts < 5 -> "item.detector.clicks"
            else -> "item.detector.crackle"
        }
        play(client, id, SoundCategory.BLOCKS, GEIGER_VOLUME, at)
    }

    /** Plays [id] once: from [at] if given (fading out with distance), else in the player's head. */
    private fun play(client: MinecraftClient, id: String, category: SoundCategory, volume: Float, at: Vec3d?) {
        val identifier = Identifier(EveryTunnel.MOD_ID, id)
        val sound = if (at != null) {
            PositionedSoundInstance(identifier, category, volume, 1f, SoundInstance.createRandom(), false, 0, SoundInstance.AttenuationType.LINEAR, at.x, at.y, at.z, false)
        } else {
            PositionedSoundInstance(identifier, category, volume, 1f, SoundInstance.createRandom(), false, 0, SoundInstance.AttenuationType.NONE, 0.0, 0.0, 0.0, true)
        }
        client.soundManager.play(sound)
    }

    /** Ends it, at once: the scene is over or was skipped. Safe to call when nothing plays. */
    fun stop(client: MinecraftClient) {
        hum?.stopNow()
        drone?.stopNow()
        hum = null
        drone = null
        counter = null
        lights = null
        him = null
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
