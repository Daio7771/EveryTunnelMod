package daio7771.everytunnel.core.wall

/**
 * How a wall message stands for the player looking at it ([update], each frame): [opacity] and whether its
 * text has [changed]. Plain Kotlin, unit tested.
 *
 * - [Trigger.FIXED]: always there.
 * - [Trigger.ON_APPROACH]: fades in within [APPROACH_BLOCKS]. Once seen it stays if `once`; otherwise it fades
 *   out again past [LEAVE_BLOCKS].
 * - [Trigger.ON_LOOK_AWAY_CHANGE]: after being looked at, from within [VIEW_BLOCKS], it changes as soon as it is
 *   out of sight. If `once` it stays changed; otherwise it goes back the next time.
 */
class WallMessageState(private val trigger: Trigger, private val once: Boolean) {

    enum class Trigger {
        FIXED, ON_APPROACH, ON_LOOK_AWAY_CHANGE;

        companion object {
            fun parse(name: String): Trigger = when (name) {
                "on_approach" -> ON_APPROACH
                "on_look_away_change" -> ON_LOOK_AWAY_CHANGE
                else -> FIXED
            }
        }
    }

    var opacity = if (trigger == Trigger.ON_APPROACH) 0f else 1f
        private set
    var changed = false
        private set

    private var revealed = false
    private var seen = false

    /** [distance] in blocks; [angle] in degrees, between where the player looks and the message; [seconds] since the last call. */
    fun update(distance: Double, angle: Double, seconds: Float) {
        when (trigger) {
            Trigger.FIXED -> Unit
            Trigger.ON_APPROACH -> {
                if (distance <= APPROACH_BLOCKS) revealed = true
                if (!once && distance > LEAVE_BLOCKS) revealed = false
                val step = seconds / FADE_SECONDS
                opacity = if (revealed) minOf(1f, opacity + step) else maxOf(0f, opacity - step)
            }
            Trigger.ON_LOOK_AWAY_CHANGE -> {
                if (once && changed) return
                if (distance <= VIEW_BLOCKS && angle <= LOOK_AT_DEGREES) {
                    seen = true
                } else if (seen && angle >= AWAY_DEGREES) {
                    changed = !changed
                    seen = false
                }
            }
        }
    }

    companion object {
        const val APPROACH_BLOCKS = 6.0
        const val LEAVE_BLOCKS = 9.0
        const val VIEW_BLOCKS = 16.0
        const val LOOK_AT_DEGREES = 25.0
        /** Past the edge of a wide field of view, so that nobody sees it happen. */
        const val AWAY_DEGREES = 80.0
        private const val FADE_SECONDS = 1.5f
    }
}
