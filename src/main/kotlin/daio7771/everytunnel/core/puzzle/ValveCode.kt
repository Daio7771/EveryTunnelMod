package daio7771.everytunnel.core.puzzle

import kotlin.random.Random

/**
 * Level 3's valve puzzle in plain Kotlin (unit tested): every valve has a dial of [POSITIONS]
 * full turns, 0 to 9, and one click turns it once more, from 9 back to 0 ([next]). A new game
 * draws a digit for each valve ([random]; any digit, 0 included); the electrician's note tells
 * them, and the valves are right when each shows its own ([solved]).
 */
object ValveCode {

    const val POSITIONS = 10

    /** A digit 0..9 for each of [valves] valves. */
    fun random(random: Random, valves: Int): List<Int> = List(valves) { random.nextInt(POSITIONS) }

    /** The dial after one more turn: 9 goes back to 0. */
    fun next(turns: Int): Int = (turns + 1).mod(POSITIONS)

    /** Every valve on its digit; a missing valve (null) is never right. */
    fun solved(code: List<Int>, turns: List<Int?>): Boolean =
        code.size == turns.size && code.indices.all { turns[it] == code[it] }
}
