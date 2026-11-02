package daio7771.everytunnel.core.puzzle

import kotlin.random.Random

/**
 * A safe's dial, cracked by ear (`client.screen.SafeScreen`). It turns one number at a time; each makes
 * a [Event.TICK], the number being looked for a [Event.CLICK]. Changing direction on it fixes it, and the
 * next is looked for. Changing direction anywhere else, with some already fixed, starts over
 * ([Event.RESET], a failure). Reaching the last number opens the safe ([Event.SOLVED]). After
 * [TREMBLE_AFTER] failures the dial trembles for as long as it stands on the right number. Plain Kotlin, unit tested.
 */
class SafeDial(val combination: List<Int>, failures: Int = 0) {

    enum class Event { TICK, CLICK, RESET, SOLVED }

    var position = 0
        private set
    var step = 0
        private set
    var failures = failures
        private set
    var solved = false
        private set
    private var direction = 0

    val onTarget: Boolean
        get() = position == combination[step]

    val trembling: Boolean
        get() = failures >= TREMBLE_AFTER && onTarget

    /** One number on: [turn] 1 or -1. */
    fun turn(turn: Int): Event {
        if (solved) return Event.SOLVED
        var reset = false
        if (direction != 0 && turn != direction) {
            if (onTarget) {
                step++
            } else if (step > 0) {
                step = 0
                failures++
                reset = true
            }
        }
        direction = turn
        position = Math.floorMod(position + turn, NUMBERS)
        return when {
            reset -> Event.RESET
            !onTarget -> Event.TICK
            step == combination.lastIndex -> {
                solved = true
                Event.SOLVED
            }
            else -> Event.CLICK
        }
    }

    companion object {
        const val NUMBERS = 40
        const val TREMBLE_AFTER = 3
        private const val LENGTH = 3
        /** Numbers close together would click almost at once. */
        private const val MIN_APART = 5

        private fun apart(a: Int, b: Int): Int = minOf(Math.floorMod(a - b, NUMBERS), Math.floorMod(b - a, NUMBERS))

        /** Far enough from each other, and from 0, where the dial starts. */
        fun draw(random: Random = Random.Default): List<Int> {
            val out = mutableListOf<Int>()
            var last = 0
            while (out.size < LENGTH) {
                val next = random.nextInt(NUMBERS)
                if (apart(next, last) >= MIN_APART) {
                    out += next
                    last = next
                }
            }
            return out
        }
    }
}
