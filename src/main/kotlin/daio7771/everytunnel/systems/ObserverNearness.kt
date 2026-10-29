package daio7771.everytunnel.systems

/**
 * Level 3's book phase: how one player notices the Observer coming, in plain Kotlin (unit
 * tested). `platform.BookPhaseHooks` calls [tick] every server tick with the distance to the
 * nearest Observer and plays what it says.
 *
 * - **The clock:** within [NearnessSettings.clockRange] blocks the library's clock ticks in the
 *   player's head, tick and tock in turn: every [NearnessSettings.slowIntervalSeconds] at the
 *   edge, quickening to every [NearnessSettings.fastIntervalSeconds] at
 *   [NearnessSettings.closeRange] or nearer, and louder as he nears.
 * - **The warning:** the first time he comes within [NearnessSettings.warnRange], "He's near.
 *   Hide."; again only after he has been farther than [NearnessSettings.rearmRange]. Not while
 *   he is already after this player (it would come too late to help).
 */
class ObserverNearness(private val settings: NearnessSettings = NearnessSettings()) {

    enum class Beat { TICK, TOCK }

    /** What to play this tick: a [beat] (or none) at [volume], and whether to [warn]. */
    data class Step(val beat: Beat?, val volume: Float, val warn: Boolean)

    private var sinceBeat = Int.MAX_VALUE
    private var tock = false
    private var armed = true

    /** [distance] to the nearest Observer (null: none about); [hunting]: he is chasing or searching for this player. */
    fun tick(distance: Double?, hunting: Boolean = false): Step {
        var warn = false
        if (distance == null || distance > settings.rearmRange) {
            armed = true
        } else if (armed && distance <= settings.warnRange) {
            armed = false
            warn = !hunting
        }
        if (distance == null || distance > settings.clockRange) {
            sinceBeat = Int.MAX_VALUE
            return Step(null, 0f, warn)
        }
        val near = ((settings.clockRange - distance) / (settings.clockRange - settings.closeRange)).coerceIn(0.0, 1.0)
        val volume = (settings.quietVolume + (settings.loudVolume - settings.quietVolume) * near).toFloat()
        val seconds = settings.slowIntervalSeconds + (settings.fastIntervalSeconds - settings.slowIntervalSeconds) * near
        if (sinceBeat != Int.MAX_VALUE) sinceBeat++
        if (sinceBeat < (seconds * 20).toInt()) return Step(null, volume, warn)
        sinceBeat = 0
        val beat = if (tock) Beat.TOCK else Beat.TICK
        tock = !tock
        return Step(beat, volume, warn)
    }
}

/** The book phase's clock and warning ([ObserverNearness]); distances in blocks. */
data class NearnessSettings(
    val clockRange: Double = 24.0,
    val closeRange: Double = 4.0,
    val slowIntervalSeconds: Double = 0.75,
    val fastIntervalSeconds: Double = 0.25,
    val quietVolume: Float = 0.35f,
    val loudVolume: Float = 1.0f,
    val warnRange: Double = 16.0,
    val rearmRange: Double = 24.0,
)
