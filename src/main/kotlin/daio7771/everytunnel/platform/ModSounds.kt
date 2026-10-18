package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier

/**
 * Sound events played from code.
 *
 * Every sound event of the mod, with its files and variations, is declared in
 * `assets/every_tunnel/sounds.json`, which `tools/sounds/export.py` generates together
 * with the .ogg files; those can all be played with `/playsound every_tunnel:<id>`.
 * The ones code plays are also registered here, so they exist in the sound event
 * registry (needed for sounds sent from the server).
 */
object ModSounds {

    private val all = mutableListOf<SoundEvent>()

    val FLASHLIGHT_ON = event("item.flashlight.on")
    val FLASHLIGHT_OFF = event("item.flashlight.off")
    /** Switch pressed with an empty battery. */
    val FLASHLIGHT_DEAD = event("item.flashlight.dead")
    /** Failing contact "bzzt": low battery flicker, and the light dying on its own. */
    val FLASHLIGHT_FLICKER = event("item.flashlight.flicker")
    val BATTERY_OPEN = event("item.battery.open")
    val BATTERY_CLOSE = event("item.battery.close")
    /** Stereo, played in the player's head (not positioned). */
    val BREATHING_EXHAUSTED = event("player.breathing_exhausted")

    private fun event(path: String): SoundEvent =
        SoundEvent.of(Identifier(EveryTunnel.MOD_ID, path)).also { all += it }

    fun register() {
        for (sound in all) Registry.register(Registries.SOUND_EVENT, sound.id, sound)
    }
}
