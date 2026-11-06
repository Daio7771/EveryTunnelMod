package daio7771.everytunnel.core.audio

/**
 * How Level 4's jazz is heard, by where the player stands (`platform.LoungeMusicHooks`): [main] the piece as it
 * is, [far] the same one muffled, for the Calm room. Plain Kotlin, unit tested.
 */
data class LoungeMix(val main: Float, val far: Float) {

    companion object {
        val SILENT = LoungeMix(0f, 0f)
        private val FULL = LoungeMix(1f, 0f)
        private val SUITES = LoungeMix(0.25f, 0f)
        private val CALM = LoungeMix(0f, 1f)

        private val QUIET = setOf("ch1_l4_maintenance", "ch1_l4_door_maintenance", "ch1_l4_exit", "ch1_l4_exit_far", "ch1_l4_door_big")
        private val CALM_ZONES = setOf("ch1_l4_calm", "ch1_l4_steps")

        /** [zones]: the ids of the level's zones the player is in. During the Cinema's film nothing plays. */
        fun of(zones: Set<String>, filmRunning: Boolean): LoungeMix = when {
            filmRunning || zones.isEmpty() || zones.any { it in QUIET } -> SILENT
            zones.any { it in CALM_ZONES } -> CALM
            "ch1_l4_suites" in zones -> SUITES
            else -> FULL
        }
    }
}
