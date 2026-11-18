package daio7771.everytunnel.chapter2.platform

import daio7771.everytunnel.EveryTunnel
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier

/**
 * Chapter 2's sound events that its code plays by name, registered by [daio7771.everytunnel.chapter2.Chapter2]. Their sounds are
 * in `sounds.json` like every other (`tools/sounds/export.py`); voice lines are played by id and need no event here.
 */
object Ch2Sounds {

    private val all = mutableListOf<SoundEvent>()

    /** Chapter 2 Level 1: the loudspeakers' chime, the broom's swish and a lab instrument's pip. */
    val CH2L1_DING = event("event.ch2l1.ding", range = 48f)
    val CH2L1_SWEEP = event("block.ch2l1.sweep")
    val CH2L1_BLIP = event("block.ch2l1.blip")

    /** Chapter 2 Level 1, Act 3: the office's keypad (a key, a wrong code, Rowan's code). */
    val CH2L1_KEYPAD_KEY = event("block.ch2l1.keypad_key")
    val CH2L1_KEYPAD_ERROR = event("block.ch2l1.keypad_error")
    val CH2L1_KEYPAD_ACCEPT = event("block.ch2l1.keypad_accept")

    /** Chapter 2 Level 1, Act 3's patrols: a guard notices something, runs at the player, catches them. */
    val CH2L1_GUARD_SUSPECT = event("event.ch2l1.guard_suspect")
    val CH2L1_GUARD_ALERT = event("event.ch2l1.guard_alert")
    val CH2L1_GUARD_CAUGHT = event("event.ch2l1.guard_caught")

    /** Chapter 2 Level 1, the office's cinematic: the low hum under Rowan's colder lines (a loop, played by the client's scene). */
    val CH2L1_OFFICE_HUM = event("ambient.ch2l1.office_hum")

    /**
     * Chapter 2 Level 1, locked in after the overtime: the sealed door's bolt shot home from outside (heard from the basement), the
     * emergency manhole's lid prised up and let fall, and the climb down its rungs.
     */
    val CH2L1_LOCK_SHUT = event("block.ch2l1.lock_shut", range = 48f)
    val CH2L1_MANHOLE_SCRAPE = event("block.ch2l1.manhole_scrape")
    val CH2L1_MANHOLE_OPEN = event("block.ch2l1.manhole_open", range = 32f)
    val CH2L1_LADDER = event("block.ch2l1.ladder")

    /** Chapter 2 Level 2's pump room: the vibration creature's steps, the click of its teeth and its blow, the pump running, the pipe landing. */
    val CH2L2_CREATURE_STEP = event("event.ch2l2.creature_step", range = 40f)
    val CH2L2_CREATURE_CLICK = event("event.ch2l2.creature_click", range = 40f)
    val CH2L2_CREATURE_WINDUP = event("event.ch2l2.creature_windup", range = 40f)
    val CH2L2_CREATURE_STRIKE = event("event.ch2l2.creature_strike", range = 48f)
    val CH2L2_PUMP_RUN = event("event.ch2l2.pump_run", range = 64f)
    val CH2L2_PIPE_FALL = event("event.ch2l2.pipe_fall", range = 56f)

    private fun event(path: String, range: Float? = null): SoundEvent {
        val id = Identifier(EveryTunnel.MOD_ID, path)
        return (if (range == null) SoundEvent.of(id) else SoundEvent.of(id, range)).also { all += it }
    }

    fun register() {
        for (sound in all) Registry.register(Registries.SOUND_EVENT, sound.id, sound)
    }
}
