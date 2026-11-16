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

    private fun event(path: String, range: Float? = null): SoundEvent {
        val id = Identifier(EveryTunnel.MOD_ID, path)
        return (if (range == null) SoundEvent.of(id) else SoundEvent.of(id, range)).also { all += it }
    }

    fun register() {
        for (sound in all) Registry.register(Registries.SOUND_EVENT, sound.id, sound)
    }
}
