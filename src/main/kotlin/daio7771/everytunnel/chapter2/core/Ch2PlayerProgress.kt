package daio7771.everytunnel.chapter2.core

import daio7771.everytunnel.chapter2.level1.core.Ch2L1State

/**
 * What one player has done in Chapter 2, kept through deaths and between sessions, apart from Chapter 1's
 * (`core.progress.PlayerProgress`): where they are in its Level 1, whether they are playing it, and a few counters of it. Plain data,
 * no Minecraft types: `chapter2.platform.Ch2Progress` keeps one per player, saves it and sends it to the client.
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

    /** A new game: everything forgotten. */
    fun clear() {
        l1State = Ch2L1State.START
        l1Active = false
        l1Counters.clear()
    }

    /** One line for the debug command. */
    fun describe(): String = "chapter 2 level 1 ${if (l1Active) "active, " else ""}$l1State, counters $l1Counters"
}
