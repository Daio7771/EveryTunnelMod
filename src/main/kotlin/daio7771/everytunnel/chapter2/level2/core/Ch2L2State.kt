package daio7771.everytunnel.chapter2.level2.core

/**
 * Where a player is in Chapter 2's Level 2 (the catacombs): the level's state machine, one value per stretch of the way, in the order
 * they are played, as Level 1's (`level1.core.Ch2L1State`). Plain Kotlin: `level2.platform.Ch2L2` keeps the current one in the player's
 * Chapter 2 progress, so it survives leaving the world and a death.
 *
 * The order is the declaration order, so a later state compares greater. Walking into a place moves the player on to its state
 * (`level.json`, `progress`); the Guard's death is the one state reached by what happens, not by where one goes (phase 5). What
 * entering a state does in the world (an enemy, the gate, the water) is the business of the system that owns it, which listens to
 * `level2.platform.Ch2L2`.
 */
enum class Ch2L2State {
    /** Down Level 1's emergency manhole, at the foot of its ladder. */
    ARRIVED,
    /** In the galleries, where the Hunters are (phase 3). */
    GALLERIES,
    /** On the pump room's overlook and across its floor, where the vibration creature is (phase 4). */
    PUMP_ROOM,
    /** Through the pump room: the catacombs and the lower galleries, on to the vestibule. */
    CATACOMBS,
    /** In the vestibule: the Observant Guard comes out of the wall (phase 5). */
    GUARD_APPEARS,
    /** In the maintenance room, with its four traps: the Guard is to be stunned four times (phase 5). */
    GUARD_FIGHT,
    /** The Guard is dead: back round to the vestibule, BOO! on its wall and the grate up (phase 5). */
    GUARD_DEAD,
    /** The collector: the flood's first stage (phase 6). */
    FLOOD_1,
    /** The walkways: the second stage. */
    FLOOD_2,
    /** The gallery up to the station: the third stage. */
    FLOOD_3,
    /** The pumping station, dry, and the way to the exit. */
    STATION,
    /** Out of the exit: the level is finished (phase 6). */
    DONE;

    /** The state after this one, or null at [DONE]. */
    fun next(): Ch2L2State? = entries.getOrNull(ordinal + 1)

    /** The state before this one, or null at [ARRIVED]. */
    fun previous(): Ch2L2State? = entries.getOrNull(ordinal - 1)

    /** The flood's stage at this state: 0 before it (and in it, the stage still to come), up to 3; the station keeps the third's water. */
    val floodStage: Int
        get() = when {
            this < FLOOD_1 -> 0
            this == FLOOD_1 -> 1
            this == FLOOD_2 -> 2
            else -> 3
        }

    companion object {
        /** Where every player starts. */
        val START = ARRIVED

        /** The state called [text], ignoring case and treating `-` as `_`; null if there is none. */
        fun parse(text: String): Ch2L2State? {
            val key = text.trim().uppercase().replace('-', '_')
            return entries.firstOrNull { it.name == key }
        }
    }
}

/**
 * The level's four encounters, each a run of states from [first] to the state it leaves the player in, [after]: what the debug commands
 * skip and reset (`/et ch2 l2 skip <event>`, `reset <event>`).
 */
enum class Ch2L2Event(val id: String, val first: Ch2L2State, val after: Ch2L2State) {
    HUNTERS("hunters", Ch2L2State.GALLERIES, Ch2L2State.PUMP_ROOM),
    CREATURE("creature", Ch2L2State.PUMP_ROOM, Ch2L2State.CATACOMBS),
    GUARD("guard", Ch2L2State.GUARD_APPEARS, Ch2L2State.GUARD_DEAD),
    FLOOD("flood", Ch2L2State.FLOOD_1, Ch2L2State.STATION);

    /** The state just before it, where resetting it leaves the player. */
    val before: Ch2L2State
        get() = first.previous()!!

    /** Where [state] stands to it: ahead, now, or past. */
    fun progress(state: Ch2L2State): String = when {
        state < first -> "ahead"
        state < after -> "now"
        else -> "past"
    }

    companion object {
        fun parse(text: String): Ch2L2Event? = entries.firstOrNull { it.id == text.trim().lowercase() }
    }
}
