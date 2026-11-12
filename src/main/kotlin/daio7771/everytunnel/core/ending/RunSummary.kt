package daio7771.everytunnel.core.ending

/**
 * What the congratulations window says about the playthrough that has just ended: the time played and the
 * deaths, summed over all its sessions (`PlayerProgress`, kept by `platform.RunStatsHooks`). Plain data;
 * it goes from the server to the client with the ending packet.
 */
data class RunSummary(val playTicks: Long, val deaths: Int) {

    /** The time played as `H:MM:SS`, or `M:SS` under an hour (the server ticks 20 times a second). */
    fun time(): String {
        val seconds = playTicks.coerceAtLeast(0) / TICKS_PER_SECOND
        val hours = seconds / 3600
        val minutes = seconds / 60 % 60
        val rest = seconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, rest) else "%d:%02d".format(minutes, rest)
    }

    companion object {
        const val TICKS_PER_SECOND = 20L
        val EMPTY = RunSummary(0, 0)
    }
}
