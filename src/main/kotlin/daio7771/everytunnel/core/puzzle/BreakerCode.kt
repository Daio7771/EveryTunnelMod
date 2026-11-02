package daio7771.everytunnel.core.puzzle

import kotlin.random.Random

/**
 * Level 4's breakers, in the Maintenance room: [answer] says which levers go up. Testing them only
 * tells how many are right, not which. Plain Kotlin, unit tested.
 */
class BreakerCode(val answer: List<Boolean>) {

    /** How many of [levers] match. */
    fun rightCount(levers: List<Boolean>): Int = answer.indices.count { levers.getOrNull(it) == answer[it] }

    fun solvedBy(levers: List<Boolean>): Boolean = rightCount(levers) == answer.size

    companion object {
        const val LEVERS = 5

        /** Never all down: that is how they start, and the first press would solve it. */
        fun draw(random: Random = Random.Default): BreakerCode {
            while (true) {
                val answer = List(LEVERS) { random.nextBoolean() }
                if (answer.any { it }) return BreakerCode(answer)
            }
        }
    }
}
