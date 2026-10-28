package daio7771.everytunnel.core.achievement

/**
 * The mod's achievements, in the order the ACHIEVEMENTS screen lists them (roughly the order a
 * playthrough earns them). The server says when one is earned (`platform.AchievementHooks`);
 * the client shows the "Achievement Unlocked!" toast every time (`client.hud.AchievementToast`)
 * and remembers it for good, across games and worlds (`client.AchievementStore`).
 *
 * [id] names an achievement in the packet, in the saved file and in its lang keys:
 * `achievement.every_tunnel.<id>` (the title, a little tongue-in-cheek) and
 * `achievement.every_tunnel.<id>.description` (one line on how it was earned, shown once it is).
 * [puzzle] marks the ones a puzzle ends with; every player in the world gets those.
 */
enum class Achievement(val id: String, val puzzle: Boolean = false) {
    /** Level 1: stepping out of the service elevator after the intro. */
    FIRST_DAY("first_day"),

    /** The first water bottle drunk. */
    STAY_HYDRATED("stay_hydrated"),

    /** Walking down into Level 2. */
    TALL_MEN("tall_men"),

    /** Killed by a Pale man. */
    OUCH("ouch"),

    /** Level 2: the keycard opens the EXIT door's card reader. */
    KEYCARD("keycard", puzzle = true),

    /** Level 3: the five valves set right, the checkpoint door opens. */
    VALVES("valves", puzzle = true),

    /** Level 3: the ten purple boxes stacked on the empty shelf in Storage. */
    BOXES("boxes", puzzle = true),

    /** Level 3: the five books back on the librarian's counter, the bunker hatch opens. */
    BOOKS("books", puzzle = true);

    companion object {
        /** The achievement named [id], or null for an id this version does not know. */
        fun byId(id: String): Achievement? = entries.firstOrNull { it.id == id }

        /** The achievements named in [ids], in list order; unknown ids are left out. */
        fun fromIds(ids: Iterable<String>): Set<Achievement> {
            val known = ids.mapNotNull(::byId).toSet()
            return entries.filterTo(linkedSetOf()) { it in known }
        }
    }
}
