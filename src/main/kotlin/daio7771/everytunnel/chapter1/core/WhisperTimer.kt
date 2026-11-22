package daio7771.everytunnel.chapter1.core

/**
 * Level 1's whisper easter egg (`chapter1.platform.WhisperHooks`), one timer a player: after [stillTicks] without moving a whisper
 * plays, and the next not before [cooldownTicks] after it, however long they stand (the still time starts over after each whisper
 * and whenever they move). One [tick] per game tick (20 per second); plain Kotlin, unit tested.
 */
class WhisperTimer(private val stillTicks: Int = STILL_TICKS, private val cooldownTicks: Int = COOLDOWN_TICKS) {

    private var still = 0
    private var cooldown = 0

    /** [moved]: the player has moved since the last tick. True on the tick a whisper is to play. */
    fun tick(moved: Boolean): Boolean {
        if (cooldown > 0) cooldown--
        if (moved) {
            still = 0
            return false
        }
        if (++still < stillTicks || cooldown > 0) return false
        still = 0
        cooldown = cooldownTicks
        return true
    }

    /** The player left Level 1 or the world: forgotten, so the first whisper there is five seconds of stillness away again. */
    fun reset() {
        still = 0
        cooldown = 0
    }

    companion object {
        /** Five seconds still. */
        const val STILL_TICKS = 5 * 20

        /** At least a minute between two whispers. */
        const val COOLDOWN_TICKS = 60 * 20

        /** Moving less than this many blocks in a tick is standing still (a breath of drift, not a step). */
        const val STILL_BLOCKS = 0.05
    }
}
