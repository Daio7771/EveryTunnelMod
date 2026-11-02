package daio7771.everytunnel.core.puzzle

/**
 * A puzzle of things to do in a given order (Level 4's records and its reel, of the map; [answer]
 * holds their indexes). A wrong one starts it over, without any other penalty. From [hintAfter]
 * failures on, [hint] says what comes next. Plain Kotlin, unit tested.
 */
class OrderPuzzle(val answer: List<Int>, private val hintAfter: Int = 3) {

    enum class Result { RIGHT, WRONG, SOLVED }

    /** How many of [answer] are done. */
    var done: Int = 0
        private set

    var failures: Int = 0
        private set

    val solved: Boolean
        get() = done >= answer.size

    /** Indexes done so far, in order. */
    val placed: List<Int>
        get() = answer.take(done)

    /** What to hint at, or null: not enough failures yet, or nothing left to do. */
    val hint: Int?
        get() = if (failures >= hintAfter && !solved) answer[done] else null

    /** [index] is tried next. Once solved, nothing changes. */
    fun attempt(index: Int): Result {
        if (solved) return Result.SOLVED
        if (answer[done] != index) {
            done = 0
            failures++
            return Result.WRONG
        }
        done++
        return if (solved) Result.SOLVED else Result.RIGHT
    }

    /** From the start again, without it counting as a failure. */
    fun restart() {
        done = 0
    }

    /** A new game. */
    fun reset() {
        done = 0
        failures = 0
    }
}
