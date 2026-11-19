package daio7771.everytunnel.chapter2.level2.core

/**
 * What Level 2's own server tick work costs, for `/et ch2 l2 perf`: each handler's time ([add], by name) summed per server tick, kept for
 * the last [window] ticks. Plain Kotlin; `platform.Ch2L2Perf` feeds it. A tick is closed when the next tick's first measure arrives.
 */
class TickBudget(private val window: Int = 100) {

    private val finished = ArrayDeque<Map<String, Long>>()
    private var currentTick = Long.MIN_VALUE
    private var current = HashMap<String, Long>()

    /** [nanos] spent by [name] in server tick [tick]. */
    fun add(tick: Long, name: String, nanos: Long) {
        if (tick != currentTick) {
            close()
            currentTick = tick
        }
        current.merge(name, nanos, Long::plus)
    }

    private fun close() {
        if (current.isEmpty()) return
        finished.addLast(current)
        while (finished.size > window) finished.removeFirst()
        current = HashMap()
    }

    /** How many whole ticks are held (at most the window). */
    val ticks: Int get() = finished.size

    /** The mean of the ticks' total, in milliseconds; 0 with none held. */
    fun meanMillis(): Double = if (finished.isEmpty()) 0.0 else finished.sumOf { it.values.sum() } / finished.size / NANOS_PER_MILLI

    /** The costliest tick's total, in milliseconds. */
    fun worstMillis(): Double = (finished.maxOfOrNull { it.values.sum() } ?: 0L) / NANOS_PER_MILLI

    /** Each handler's mean per tick, in milliseconds, the costliest first. */
    fun meanMillisByName(): List<Pair<String, Double>> {
        if (finished.isEmpty()) return emptyList()
        val sums = HashMap<String, Long>()
        for (tick in finished) for ((name, nanos) in tick) sums.merge(name, nanos, Long::plus)
        return sums.map { (name, nanos) -> name to nanos / finished.size / NANOS_PER_MILLI }.sortedByDescending { it.second }
    }

    fun clear() {
        finished.clear()
        current = HashMap()
        currentTick = Long.MIN_VALUE
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000.0
    }
}
