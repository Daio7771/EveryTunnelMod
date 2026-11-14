package daio7771.everytunnel.client.effects

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.client.screen.LoadingCover
import daio7771.everytunnel.client.screen.MainMenuScreen
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.sound.MovingSoundInstance
import net.minecraft.client.sound.SoundInstance
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier

/**
 * The main menu's music, in the Music volume, in the menus (no world open, vanilla's title screen not
 * asked for, not loading a world). Each chapter has its own track ([TRACKS]): Chapter 1's is
 * `every_tunnel:music.menu`, Chapter 2's `every_tunnel:music.menu.chapter_2`. The menu says which
 * ([setChapter]) as the player moves over the chapters: the track that plays fades out and the other
 * comes in at once, not after a silence. A track plays once, then [SILENCE_TICKS] of silence, then
 * again; coming back to the menus starts it at once. A sound engine restart cuts it
 * ([soundsReloaded]); it starts again at once, not after a silence.
 * Vanilla's music never plays at all (`MusicTrackerMixin`).
 *
 * Chapter 1's theme is the author's piano (`tools/sounds/menu_piano.py`): a piece that ends on a
 * fade, not a loop. Chapter 2's is a waltz for an old piano and a violin that leaves for the middle, on a worn disc (`tools/sounds/menu_theme_ch2.py`); a chapter
 * whose track's event is missing from sounds.json has a silent menu.
 */
object MenuMusic {

    val ID = Identifier(EveryTunnel.MOD_ID, "music.menu")

    private val TRACKS = mapOf(
        1 to ID,
        2 to Identifier(EveryTunnel.MOD_ID, "music.menu.chapter_2"),
    )

    /** The silence between two plays, in ticks. */
    private const val SILENCE_TICKS = 15 * 20

    /** Fading in at the start of a track, and out when the menu changes chapter, in ticks. */
    private const val FADE_IN_TICKS = 20
    private const val FADE_OUT_TICKS = 16

    private var chapter = 1
    private var playing: Track? = null
    private var leaving: Track? = null
    private var silentTicks = 0

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
    }

    /** The menu shows this chapter's scene now: its track plays (the one that played fades out). Unknown chapters are ignored. */
    fun setChapter(number: Int) {
        if (number == chapter || number !in TRACKS) return
        chapter = number
        leaving?.stopNow()
        leaving = playing?.also { it.fadeOut() }
        playing = null
        silentTicks = 0
    }

    /** The sound engine restarted (a resource reload, from `SoundSystemMixin`): what played is gone, not ended. */
    @JvmStatic
    fun soundsReloaded() {
        playing = null
        leaving = null
        silentTicks = 0
    }

    private fun tick(client: MinecraftClient) {
        val inMenus = client.world == null && !MainMenuScreen.vanillaShown && !LoadingCover.active
        val sounds = client.soundManager
        if (!inMenus) {
            playing?.let { sounds.stop(it) }
            leaving?.let { sounds.stop(it) }
            playing = null
            leaving = null
            silentTicks = 0
            chapter = 1
            return
        }
        leaving?.let { if (!sounds.isPlaying(it)) leaving = null }
        val id = TRACKS.getValue(chapter)
        if (sounds.get(id) == null) return
        val current = playing
        if (current != null) {
            if (sounds.isPlaying(current)) return
            // It just ended: the silence starts.
            playing = null
            silentTicks = SILENCE_TICKS
        }
        if (silentTicks > 0) {
            silentTicks--
            return
        }
        playing = Track(id).also { sounds.play(it) }
    }

    /** A track in the player's head, fading in as it starts and out when [fadeOut] says so. */
    private class Track(id: Identifier) : MovingSoundInstance(SoundEvent.of(id), SoundCategory.MUSIC, SoundInstance.createRandom()) {

        private var age = 0
        private var fadingOut = -1

        init {
            repeat = false
            volume = START_VOLUME
            pitch = 1f
            relative = true
            attenuationType = SoundInstance.AttenuationType.NONE
        }

        fun fadeOut() {
            fadingOut = 0
        }

        fun stopNow() = setDone()

        override fun tick() {
            age++
            if (fadingOut >= 0) {
                fadingOut++
                if (fadingOut >= FADE_OUT_TICKS) return setDone()
                volume = (1f - fadingOut / FADE_OUT_TICKS.toFloat()).coerceAtLeast(START_VOLUME)
            } else {
                volume = (age / FADE_IN_TICKS.toFloat()).coerceIn(START_VOLUME, 1f)
            }
        }

        private companion object {
            /** Above zero: the game does not start a sound whose volume is zero. */
            const val START_VOLUME = 0.0001f
        }
    }
}
