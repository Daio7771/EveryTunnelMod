package daio7771.everytunnel.systems

/**
 * The timing of Level 3's tunnel collapse, in ticks from the moment it goes off, in plain
 * Kotlin (unit tested); `platform.CollapseHooks` runs it on the server, the client's camera
 * shake reads [shake]. It follows the sound (`event.collapse`, 6 s): a second of groaning and
 * cracks, then the fall.
 *
 * - **Rubble:** the stretch behind the player fills from [RUBBLE_START] to [RUBBLE_END], a
 *   steady share of its cells each tick ([rubbleBy]).
 * - **Lights:** the fluorescents round about flicker from [FLICKER_START] to [FLICKER_END].
 * - **Dust** falls from the ceiling until [DUST_END].
 * - **Shake:** builds through the groan, strongest while the slabs fall, then dies away by [LENGTH].
 */
object CollapseSchedule {

    const val RUBBLE_START = 24
    const val RUBBLE_END = 56
    const val FLICKER_START = 20
    const val FLICKER_END = 76
    const val DUST_END = 110
    const val LENGTH = 120

    /** How many of [total] rubble cells are filled once [tick] is reached. */
    fun rubbleBy(tick: Int, total: Int): Int = when {
        tick < RUBBLE_START -> 0
        tick >= RUBBLE_END -> total
        else -> (total.toLong() * (tick - RUBBLE_START + 1) / (RUBBLE_END - RUBBLE_START + 1)).toInt()
    }

    /** The camera shake's strength, 0..1, at [ticks] (fractional) since it went off. */
    fun shake(ticks: Float): Float = when {
        ticks <= 0f || ticks >= LENGTH -> 0f
        ticks < RUBBLE_START -> 0.35f * ticks / RUBBLE_START
        ticks < RUBBLE_START + 6 -> 0.35f + 0.65f * (ticks - RUBBLE_START) / 6f
        ticks < RUBBLE_END -> 1f
        else -> 1f - (ticks - RUBBLE_END) / (LENGTH - RUBBLE_END)
    }
}
