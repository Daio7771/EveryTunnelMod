package daio7771.everytunnel.client.effects

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.client.screen.LoadingCover
import daio7771.everytunnel.client.screen.MainMenuScreen
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.client.sound.SoundInstance
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier

/**
 * The main menu's music: `every_tunnel:music.menu`, in the Music volume, in the menus (no
 * world open, vanilla's title screen not asked for, not loading a world). It plays once,
 * then [SILENCE_TICKS] of silence, then again; coming back to the menus starts it at once.
 * A sound engine restart cuts it ([soundsReloaded]); it starts again at once, not after a
 * silence.
 * Vanilla's music never plays at all (`MusicTrackerMixin`).
 *
 * The theme is synthesized by `tools/sounds/menu_piano.py`: a piece that ends on a fade, not
 * a loop. Without that event in sounds.json the menu is silent.
 */
object MenuMusic {

    val ID = Identifier(EveryTunnel.MOD_ID, "music.menu")

    /** The silence between two plays, in ticks. */
    private const val SILENCE_TICKS = 15 * 20

    private var playing: SoundInstance? = null
    private var silentTicks = 0

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
    }

    /** The sound engine restarted (a resource reload, from `SoundSystemMixin`): what played is gone, not ended. */
    @JvmStatic
    fun soundsReloaded() {
        playing = null
        silentTicks = 0
    }

    private fun tick(client: MinecraftClient) {
        val inMenus = client.world == null && !MainMenuScreen.vanillaShown && !LoadingCover.active
        val sounds = client.soundManager
        if (!inMenus) {
            playing?.let { sounds.stop(it) }
            playing = null
            silentTicks = 0
            return
        }
        if (sounds.get(ID) == null) return
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
        playing = PositionedSoundInstance(
            ID, SoundCategory.MUSIC, 1f, 1f, SoundInstance.createRandom(), false, 0,
            SoundInstance.AttenuationType.NONE, 0.0, 0.0, 0.0, true,
        ).also { sounds.play(it) }
    }
}
