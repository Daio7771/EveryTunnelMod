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
 * The main menu's music: `every_tunnel:music.menu`, looping, in the Music volume, in the
 * menus (no world open, vanilla's title screen not asked for, not loading a world). Vanilla's
 * music never plays at all (`MusicTrackerMixin`).
 *
 * Until the song exists (no `music.menu` event in sounds.json) the menu is silent.
 */
object MenuMusic {

    val ID = Identifier(EveryTunnel.MOD_ID, "music.menu")

    private var playing: SoundInstance? = null

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
    }

    private fun tick(client: MinecraftClient) {
        val inMenus = client.world == null && !MainMenuScreen.vanillaShown && !LoadingCover.active
        val sounds = client.soundManager
        if (!inMenus) {
            playing?.let { sounds.stop(it) }
            playing = null
            return
        }
        if (sounds.get(ID) == null) return
        val current = playing
        if (current == null || !sounds.isPlaying(current)) {
            playing = PositionedSoundInstance(
                ID, SoundCategory.MUSIC, 1f, 1f, SoundInstance.createRandom(), true, 0,
                SoundInstance.AttenuationType.NONE, 0.0, 0.0, 0.0, true,
            ).also { sounds.play(it) }
        }
    }
}
