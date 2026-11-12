package daio7771.everytunnel.core.cutscene

import kotlin.random.Random

/**
 * What the director says, over and over, once his scene is over and the player stays in his office: his
 * phrases ([DirectorLines.loop]) from the last to the first ([order]), and round again, one every
 * [MIN_GAP]..[MAX_GAP] ticks after the one before ends, only while the player is in the room ([tick]'s
 * `inRoom`). He never attacks. Each time he looks at the player a little less ([gaze]). Plain Kotlin, unit
 * tested.
 */
class DirectorLoop(phrases: List<VoiceLine>, private val random: Random = Random.Default) {

    /** Last to first: "Whenever you're ready." first, "Did you hear me?" last. */
    val order: List<VoiceLine> = phrases.reversed()

    /** How many he has said. */
    var said = 0
        private set

    private var nextAt = Int.MIN_VALUE

    /** Starts counting from [now]: his first phrase comes a gap later. */
    fun start(now: Int) {
        said = 0
        nextAt = now + gap()
    }

    /** At tick [now]: the phrase he says now, if any. Away from the room, he waits for the player to come back. */
    fun tick(now: Int, inRoom: Boolean): VoiceLine? {
        if (nextAt == Int.MIN_VALUE) return null
        if (!inRoom) {
            nextAt = maxOf(nextAt, now + BACK_IN)
            return null
        }
        if (now < nextAt) return null
        val line = order[said % order.size]
        said++
        nextAt = now + DirectorScene.ticks(line.ms) + gap()
        return line
    }

    /** How much he looks at the player while saying his [n]th phrase (0 the first): less each time, then not at all. */
    fun gaze(n: Int): Double = (1.0 - n * GAZE_STEP).coerceAtLeast(0.0)

    private fun gap(): Int = random.nextInt(MIN_GAP, MAX_GAP + 1)

    companion object {
        const val MIN_GAP = 20 * 20
        const val MAX_GAP = 30 * 20

        /** Coming back into the room, he waits at least this long before speaking. */
        const val BACK_IN = 5 * 20

        const val GAZE_STEP = 0.15
    }
}
