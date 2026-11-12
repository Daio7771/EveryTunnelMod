package daio7771.everytunnel.core.profile

import java.util.Locale

/**
 * What the tick profiler ([daio7771.everytunnel.platform.TickProfiler]) has measured since the last
 * [reset]: the whole server tick, and the time each of the mod's tick hooks took inside it. Plain Kotlin,
 * fed in nanoseconds; [summary] is the one line that goes to the log. Server thread only.
 */
class TickStats(private val slowTickNanos: Long = SLOW_TICK_NANOS) {

    private class Hook {
        var total = 0L
        var max = 0L
    }

    private val hooks = HashMap<String, Hook>()
    private var tickTotal = 0L
    private var tickMax = 0L
    private var slowTicks = 0

    /** Ticks recorded since the last [reset]. */
    var ticks = 0
        private set

    fun recordHook(name: String, nanos: Long) {
        val hook = hooks.getOrPut(name) { Hook() }
        hook.total += nanos
        if (nanos > hook.max) hook.max = nanos
    }

    fun recordTick(nanos: Long) {
        ticks++
        tickTotal += nanos
        if (nanos > tickMax) tickMax = nanos
        if (nanos > slowTickNanos) slowTicks++
    }

    fun reset() {
        hooks.clear()
        tickTotal = 0
        tickMax = 0
        slowTicks = 0
        ticks = 0
    }

    /**
     * `600 ticks | tick avg 2.40 ms, max 51.20 ms, 3 over 50 ms | mod hooks avg 0.31 ms (13%) | top: A 0.12/4.10, ...`:
     * for each of the [top] costliest hooks, its average per tick and its slowest single call, in ms.
     */
    fun summary(top: Int = 6): String {
        if (ticks == 0) return "no ticks recorded"
        val hooksTotal = hooks.values.sumOf { it.total }
        val share = if (tickTotal > 0) (100.0 * hooksTotal / tickTotal).toInt() else 0
        val worst = hooks.entries.sortedByDescending { it.value.total }.take(top)
            .joinToString(", ") { "${it.key} ${ms(it.value.total.toDouble() / ticks)}/${ms(it.value.max.toDouble())}" }
        return "$ticks ticks | tick avg ${ms(tickTotal.toDouble() / ticks)} ms, max ${ms(tickMax.toDouble())} ms, " +
            "$slowTicks over ${slowTickNanos / 1_000_000} ms | mod hooks avg ${ms(hooksTotal.toDouble() / ticks)} ms ($share%) | top: $worst"
    }

    private fun ms(nanos: Double): String = String.format(Locale.ROOT, "%.2f", nanos / 1_000_000.0)

    companion object {
        /** A tick over this is a lag spike: the server's budget is 50 ms. */
        const val SLOW_TICK_NANOS = 50_000_000L
    }
}
