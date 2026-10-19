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
    /** One beat (4 variations), repeated by the client faster as the Pale man gets closer. */
    val HEARTBEAT = event("player.heartbeat")

    /** "He heard you": stereo, played to the player whose noise started a Pale man chase. */
    val STINGER_HEARD = event("music.stinger.heard")

    /** Fluorescent light: loop for lit lights (client), burst, switching on (starter ticks), power down. */
    val FLUORESCENT_HUM = event("block.fluorescent.hum")
    val FLUORESCENT_FLICKER = event("block.fluorescent.flicker")
    val FLUORESCENT_START = event("block.fluorescent.start")
    val FLUORESCENT_OFF = event("block.fluorescent.off")

    /**
     * The props' materials ([ModSoundGroups]): footsteps, and one impact sound for hitting,
     * placing, breaking and landing on them. `block.<material>.step` / `.impact`.
     */
    val CARDBOARD = material("cardboard")
    val PLASTIC = material("plastic")
    val HOLLOW_METAL = material("hollow_metal")
    val METAL_PIPE = material("metal_pipe")
    val SHEET_METAL = material("sheet_metal")
    val WIRE_MESH = material("wire_mesh")
    val WOOD = material("wood")
    val PUDDLE = material("puddle")

    class MaterialSounds(val step: SoundEvent, val impact: SoundEvent)

    private fun material(name: String) = MaterialSounds(event("block.$name.step"), event("block.$name.impact"))

    private fun event(path: String): SoundEvent =
        SoundEvent.of(Identifier(EveryTunnel.MOD_ID, path)).also { all += it }

    fun register() {
        for (sound in all) Registry.register(Registries.SOUND_EVENT, sound.id, sound)
    }
}
