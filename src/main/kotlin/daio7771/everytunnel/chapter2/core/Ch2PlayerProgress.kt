package daio7771.everytunnel.chapter2.core

import daio7771.everytunnel.chapter2.level1.core.Ch2L1State
import daio7771.everytunnel.chapter2.level2.core.Ch2L2Snapshot
import daio7771.everytunnel.chapter2.level2.core.Ch2L2State
/**
 * What one player has done in Chapter 2, kept through deaths and between sessions, apart from Chapter 1's
 * (`common.core.progress.PlayerProgress`): where they are in its Levels 1 and 2, whether they are playing one, a few counters of each, and
 * Level 2's saved game. Plain data, no Minecraft types: `chapter2.platform.Ch2Progress` keeps one per player, saves it and sends it to
 * the client.
 */
class Ch2PlayerProgress {

    /** Where the player is in Chapter 2's Level 1 ([Ch2L1State]); [Ch2L1State.START] before they begin it. */
    var l1State: Ch2L1State = Ch2L1State.START

    /**
     * The player is playing Chapter 2's Level 1: its rules apply to them (no flashlight, no damage, free and silent sprint,
     * no status bars) wherever they stand. Set by `Ch2L1.enter`, cleared by `leave` and by a new game.
     */
    var l1Active: Boolean = false

    private val l1Counters = linkedMapOf<String, Int>()

    /**
     * Chapter 2's Level 1 as a few whole numbers by name (`Ch2L1Tasks.TUBES`...: the tubes taken, the samples registered, the dirt
     * swept as bits, whether the broom was taken, the announcements heard). A number that is 0 is not kept.
     */
    val l1CounterMap: Map<String, Int>
        get() = l1Counters.toMap()

    fun l1Counter(key: String): Int = l1Counters[key] ?: 0

    fun setL1Counter(key: String, value: Int) {
        if (value == 0) l1Counters.remove(key) else l1Counters[key] = value
    }

    /** Forgets every counter whose name starts with [prefix] (all of them if it is empty). */
    fun clearL1Counters(prefix: String = "") {
        l1Counters.keys.removeAll { it.startsWith(prefix) }
    }

    /** Where the player is in Chapter 2's Level 2 ([Ch2L2State]); [Ch2L2State.START] before they begin it. */
    var l2State: Ch2L2State = Ch2L2State.START

    /**
     * The player is playing Chapter 2's Level 2: its rules apply to them (the flashlight, the survival bars, damage). Set by
     * `Ch2L2.enter` and CONTINUE, cleared by `leave` (leaving the world) and by a new game.
     */
    var l2Active: Boolean = false

    private val l2Counters = linkedMapOf<String, Int>()

    /** Chapter 2's Level 2 as a few whole numbers by name, as Level 1's. A number that is 0 is not kept. */
    val l2CounterMap: Map<String, Int>
        get() = l2Counters.toMap()

    fun l2Counter(key: String): Int = l2Counters[key] ?: 0

    fun setL2Counter(key: String, value: Int) {
        if (value == 0) l2Counters.remove(key) else l2Counters[key] = value
    }

    /** All of Level 2's counters at once (CONTINUE puts back the checkpoint's). */
    fun replaceL2Counters(counters: Map<String, Int>) {
        l2Counters.clear()
        counters.forEach { (key, value) -> setL2Counter(key, value) }
    }

    /** Level 2 at its last checkpoint: where a death and CONTINUE come back to; null before the level, or once it is forgotten. */
    var l2Snapshot: Ch2L2Snapshot? = null

    /** Level 2 forgotten (a new start of it): its state, counters and saved game. */
    fun clearL2() {
        l2State = Ch2L2State.START
        l2Active = false
        l2Counters.clear()
        l2Snapshot = null
    }

    /** A new game: everything forgotten. */
    fun clear() {
        l1State = Ch2L1State.START
        l1Active = false
        l1Counters.clear()
        clearL2()
    }

    /** One line for the debug command. */
    fun describe(): String =
        "chapter 2 level 1 ${if (l1Active) "active, " else ""}$l1State, counters $l1Counters; " +
            "level 2 ${if (l2Active) "active, " else ""}$l2State, counters $l2Counters, saved at ${l2Snapshot?.checkpoint ?: "nothing"}"
}
