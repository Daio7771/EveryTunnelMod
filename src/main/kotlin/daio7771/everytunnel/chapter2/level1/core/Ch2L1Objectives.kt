package daio7771.everytunnel.chapter2.level1.core

/**
 * The objective the panel shows in each state of Chapter 2's Level 1 (`client.hud.ObjectivePanel`): the lang key of its
 * text, under `every_tunnel.ch2.l1.objective.`, or null when there is none (a cinematic is playing, or the level is over).
 * Plain Kotlin. Where a state has a sequence of objectives of its own (the exploring of Act 3: the auxiliary lab, the way into
 * the office, searching it; locked in: going home, then another way out), the first is the one given here; the client moves the panel
 * on from the player's counters ([Ch2L1Act3.objective], [Ch2L1Overtime.objective]).
 */
object Ch2L1Objectives {

    /** The panel's title ("OBJECTIVE"). */
    const val TITLE_KEY = "every_tunnel.ch2.l1.objective_title"

    const val PREFIX = "every_tunnel.ch2.l1.objective."

    /** The text's lang key for [state], or null for no objective. */
    fun keyFor(state: Ch2L1State): String? = when (state) {
        Ch2L1State.ACT1_CLOCKIN -> PREFIX + "clockin"
        Ch2L1State.ACT1_TASK1 -> PREFIX + "task1"
        Ch2L1State.ACT1_TASK2 -> PREFIX + "task2"
        Ch2L1State.ACT1_REAGENTS -> PREFIX + "reagents"
        Ch2L1State.ACT1_GAUGE -> PREFIX + "gauge"
        Ch2L1State.ACT1_TASK3 -> PREFIX + "task3"
        Ch2L1State.ACT2_FOLLOW -> PREFIX + "follow"
        Ch2L1State.ACT2_MEETING -> null
        Ch2L1State.ACT3_EXPLORE -> PREFIX + "explore_lab"
        Ch2L1State.ACT3_CUTSCENE -> null
        Ch2L1State.ACT3_OVERTIME -> PREFIX + "overtime"
        Ch2L1State.ACT3_BASEMENT -> PREFIX + "basement"
        Ch2L1State.ACT3_LOCKED_IN -> PREFIX + Ch2L1Overtime.GO_HOME
        Ch2L1State.DONE -> null
    }
}
