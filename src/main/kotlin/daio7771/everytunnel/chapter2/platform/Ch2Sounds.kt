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

    /** Chapter 2 Level 2's Observant Guard (phase 5): its wall, its steps, blow and death, and the four traps' breakers and plates. */
    val CH2L2_WALL_CRACK = event("event.ch2l2.wall_crack", range = 40f)
    val CH2L2_WALL_BURST = event("event.ch2l2.wall_burst", range = 64f)
    val CH2L2_GUARD_STEP = event("event.ch2l2.guard_step", range = 36f)
    val CH2L2_GUARD_WINDUP = event("event.ch2l2.guard_windup", range = 32f)
    val CH2L2_GUARD_HIT = event("event.ch2l2.guard_hit", range = 40f)
    val CH2L2_GUARD_STUN = event("event.ch2l2.guard_stun", range = 40f)
    val CH2L2_GUARD_DEATH = event("event.ch2l2.guard_death", range = 48f)
    val CH2L2_TRAP_CLACK = event("block.ch2l2.trap_clack", range = 32f)
    val CH2L2_TRAP_CHARGE = event("block.ch2l2.trap_charge", range = 36f)
    val CH2L2_TRAP_ARMED = event("block.ch2l2.trap_armed", range = 32f)
    val CH2L2_TRAP_ZAP = event("event.ch2l2.trap_zap", range = 56f)
    val CH2L2_TRAP_FIZZLE = event("event.ch2l2.trap_fizzle", range = 40f)
    val CH2L2_GUARD_CHEST = event("event.ch2l2.guard_chest", range = 40f)
    val CH2L2_GRATE = event("block.ch2l2.grate", range = 56f)

    /** Chapter 2 Level 2's flood (phase 6): the gate slamming, the pipes groaning and bursting, the jet and the rumble (loops), and the exit's door. */
    val CH2L2_FLOOD_GATE_SLAM = event("event.ch2l2.flood_gate_slam", range = 64f)
    val CH2L2_FLOOD_CREAK = event("event.ch2l2.flood_creak", range = 56f)
    val CH2L2_FLOOD_BURST = event("event.ch2l2.flood_burst", range = 72f)
    val CH2L2_FLOOD_JET = event("ambient.ch2l2.flood_jet", range = 40f)
    val CH2L2_FLOOD_RUMBLE = event("ambient.ch2l2.flood_rumble")
    val CH2L2_FLOOD_RUMBLE_MUFFLED = event("ambient.ch2l2.flood_rumble_muffled")
    val CH2L2_EXIT_DOOR = event("event.ch2l2.exit_door", range = 48f)

    /** Chapter 2 Level 2, Stone Maw's last stand (phase 6.1): the pieces of pipe, the throw and what it does, the ram, the stun, the burst and the collapse. */
    val CH2L2_MAW_PIPE_FALL = event("event.ch2l2.maw_pipe_fall", range = 40f)
    val CH2L2_MAW_THROW = event("event.ch2l2.maw_throw", range = 24f)
    val CH2L2_MAW_HIT = event("event.ch2l2.maw_hit", range = 56f)
    val CH2L2_MAW_PIPE_BREAK = event("event.ch2l2.maw_pipe_break", range = 56f)
    val CH2L2_MAW_WALL_RAM = event("event.ch2l2.maw_wall_ram", range = 72f)
    val CH2L2_MAW_STUNNED = event("ambient.ch2l2.maw_stunned", range = 40f)
    val CH2L2_MAW_BURST = event("event.ch2l2.maw_burst", range = 72f)
    val CH2L2_MAW_COLLAPSE = event("event.ch2l2.maw_collapse", range = 80f)
    val CH2L2_MAW_PILLAR_RAM = event("event.ch2l2.maw_pillar_ram", range = 72f)
    val CH2L2_MAW_PILLAR_CRACK = event("event.ch2l2.maw_pillar_crack", range = 64f)
    val CH2L2_MAW_PILLAR_BREAK = event("event.ch2l2.maw_pillar_break", range = 80f)
    val CH2L2_MAW_DEBRIS_WARN = event("event.ch2l2.maw_debris_warn", range = 32f)
    val CH2L2_MAW_ESCAPE_RUMBLE = event("event.ch2l2.maw_escape_rumble", range = 80f)
    val CH2L2_LIFT_GATE = event("event.ch2l2.lift_gate", range = 48f)
    val CH2L2_LIFT_MOTOR = event("event.ch2l2.lift_motor", range = 48f)

    private fun event(path: String, range: Float? = null): SoundEvent {
        val id = Identifier(EveryTunnel.MOD_ID, path)
        return (if (range == null) SoundEvent.of(id) else SoundEvent.of(id, range)).also { all += it }
    }

    fun register() {
        for (sound in all) Registry.register(Registries.SOUND_EVENT, sound.id, sound)
    }
}
