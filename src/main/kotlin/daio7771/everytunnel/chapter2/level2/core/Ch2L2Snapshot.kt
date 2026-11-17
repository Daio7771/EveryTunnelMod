package daio7771.everytunnel.chapter2.level2.core

/**
 * Chapter 2's saved game: Level 2 as it was at the last [checkpoint] taken ([Ch2L2Checkpoint]): the player's [state] and [counters]
 * then (what they carried is kept beside it, by `chapter2.platform.Ch2Progress`). A death puts the state back to it; the menu's CONTINUE
 * all of it. Plain data.
 */
data class Ch2L2Snapshot(val checkpoint: String, val state: Ch2L2State, val counters: Map<String, Int>)
