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
    /** One gulp (4 variations), played on the player while they drink a water bottle. */
    val DRINK = event("player.drink")
    /** One beat (4 variations), repeated by the client faster as the Pale man gets closer. */
    val HEARTBEAT = event("player.heartbeat")

    /** Ear ringing, stereo: played to a player who survives a hit from the Pale man. */
    val TINNITUS = event("player.tinnitus")

    /** Stereo, in the player's head: dying (2 variations), and walking into the next level. */
    val DEATH = event("event.death")
    val LEVEL_TRANSITION = event("event.level_transition")

    /** The elevator: its bell when it arrives, and its doors sliding open. */
    val ELEVATOR_DING = event("block.elevator.ding")
    val ELEVATOR_DOORS = event("block.elevator.doors")

    /** The exit's card reader: card accepted (beeps, lock opens), or refused (buzz). */
    val CARD_READER_ACCEPT = event("block.card_reader.accept")
    val CARD_READER_DENY = event("block.card_reader.deny")
    val VALVE_TURN = event("block.valve.turn")
    val VALVE_RELEASE = event("block.valve.release")

    /** A Level 3 locker's door, getting in or out ([HidingHooks]); it makes no noise for enemies. */
    val LOCKER_DOOR = event("block.locker.door")

    /** A rolling shutter coming down and slamming shut (heard up to 32 blocks away). */
    val SHUTTER_CLOSE = event("block.shutter.close", range = 32f)

    /** "He heard you": stereo, played to the player whose noise started a Pale man chase. */
    val STINGER_HEARD = event("music.stinger.heard")
    /** Music layers (stereo loops) picked by the client from what the Pale man does (`client.effects.TensionMusic`). */
    val TENSION_PULSE = event("music.tension.pulse")
    val TENSION_RISING = event("music.tension.rising")
    val TENSION_STRINGS = event("music.tension.strings")
    val CHASE = event("music.chase")

    /**
     * The Pale man: bare feet walking (heard up to 16 blocks away) and running (24, so the
     * server sends it that far), and his breathing (8, follows him).
     */
    val PALE_MAN_STEP = event("entity.pale_man.step")
    val PALE_MAN_STEP_RUN = event("entity.pale_man.step_run", range = 24f)
    val PALE_MAN_BREATHING = event("entity.pale_man.breathing")

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

    /** [range]: fixed distance in blocks the server sends it (default: 16, more for volumes over 1). */
    private fun event(path: String, range: Float? = null): SoundEvent {
        val id = Identifier(EveryTunnel.MOD_ID, path)
        return (if (range == null) SoundEvent.of(id) else SoundEvent.of(id, range)).also { all += it }
    }

    fun register() {
        for (sound in all) Registry.register(Registries.SOUND_EVENT, sound.id, sound)
    }
}
