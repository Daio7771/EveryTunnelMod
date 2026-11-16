package daio7771.everytunnel.chapter2.level1.core

/**
 * Where a player is in Chapter 2's Level 1 (the factory): the level's state machine, one value per
 * step of its three acts, in the order they are played. Plain Kotlin, no Minecraft types:
 * `platform.Ch2L1` keeps the current one in the player's progress ([daio7771.everytunnel.core.progress.PlayerProgress]),
 * so it survives leaving the world, a death and a new session.
 *
 * The order is the declaration order, so a later state compares greater (`state >= ACT2_FOLLOW`).
 * The states only say where the player is; what happens on entering each one (an objective, a scene, a
 * speaker line) is the business of the system that owns it, which listens to `platform.Ch2L1`.
 */
enum class Ch2L1State {
    /** Arrived at the factory: nothing done yet, the time clock still to punch. */
    ACT1_CLOCKIN,
    /** Act 1, task 1: register the samples at the terminal. */
    ACT1_TASK1,
    /** Act 1, task 2: calibrate the analyzer. */
    ACT1_TASK2,
    /** Act 1, task 3: sweep the west corridor, in front of the auxiliary laboratory's glass. */
    ACT1_TASK3,
    /** Act 2: the coworker has come to take the player to the lockers. */
    ACT2_FOLLOW,
    /** Act 2: the meeting at the lockers (a cinematic). */
    ACT2_MEETING,
    /** Act 3: sneaking about the auxiliary laboratory, the staff corridor and the office. */
    ACT3_EXPLORE,
    /** Act 3: the office cinematic, from the second paper closed. */
    ACT3_CUTSCENE,
    /** Act 3: after the office, with the unpaid overtime pass; the way to the sealed zone is open. */
    ACT3_OVERTIME,
    /** Act 3: through the sealed door (it slammed shut behind): the overtime itself, sweeping the basement. */
    ACT3_BASEMENT,
    /** The level is finished: the basement swept, its lights went out, and the game goes back to the menu. */
    DONE;

    /** The state after this one, or null at [DONE]. */
    fun next(): Ch2L1State? = entries.getOrNull(ordinal + 1)

    companion object {
        /** Where every player starts, and what a new game goes back to. */
        val START = ACT1_CLOCKIN

        /**
         * The state called [text], ignoring case and treating `-` as `_` (`act3-explore` is `ACT3_EXPLORE`);
         * null if there is none.
         */
        fun parse(text: String): Ch2L1State? {
            val key = text.trim().uppercase().replace('-', '_')
            return entries.firstOrNull { it.name == key }
        }
    }
}
