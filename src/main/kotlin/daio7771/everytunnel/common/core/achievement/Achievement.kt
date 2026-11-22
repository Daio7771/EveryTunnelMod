package daio7771.everytunnel.common.core.achievement

/**
 * The mod's achievements, in the order the ACHIEVEMENTS screen lists them (roughly the order a
 * playthrough earns them). The server says when one is earned (`common.platform.AchievementHooks`);
 * the client shows the "Achievement Unlocked!" toast the first time (`common.client.hud.AchievementToast`)
 * and remembers it for good, across games and worlds (`common.client.AchievementStore`).
 *
 * [id] names an achievement in the packet, in the saved file and in its lang keys:
 * `achievement.every_tunnel.<id>` (the title, a little tongue-in-cheek) and
 * `achievement.every_tunnel.<id>.description` (one line on how it was earned, shown once it is).
 * [puzzle] marks the ones a puzzle ends with; every player in the world gets those.
 *
 * These fourteen are Chapter 1's, the first tab of the screen ([chapter] 1). A later chapter adds its own as [AchievementDef]s to the
 * [AchievementRegistry]; everything that earns, stores or lists an achievement works with the interface.
 */
enum class Achievement(override val id: String, override val puzzle: Boolean = false, override val hidden: Boolean = false) : AchievementDef {
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
    BOOKS("books", puzzle = true),

    /** Level 4: the first piece of the master card picked up. */
    BIG_BRAIN("big_brain"),

    /** Level 4: a safe in the Suites opened. */
    PRIVACY_POLICY("privacy_policy"),

    /** Level 5: the game finished, down the Director's corridor that had "nothing in there". Earned by the client when the congratulations window opens. */
    NOTHING_THERE("nothing_there"),

    /** Level 1: stood still until something whispered, a whisper that says nothing (hidden). */
    WHISPER("whisper", hidden = true),

    /** Level 3: read the note of the room behind the spiral stairs that was not there before the valves (hidden). */
    GUARD_NOTE("guard_note", hidden = true),

    /** Level 5: quit to the menu before the Director's office, and the logo read EVERY FINAL (hidden). Earned by the client when the menu shows it. */
    EVERY_FINAL("every_final", hidden = true);

    override val chapter: Int get() = 1

    companion object {
        /** The achievement named [id], or null for an id this version does not know. */
        fun byId(id: String): Achievement? = entries.firstOrNull { it.id == id }

        /** Chapter 1's achievements named in [ids], in list order; unknown ids are left out (see [AchievementRegistry.fromIds] for every chapter's). */
        fun fromIds(ids: Iterable<String>): Set<Achievement> {
            val known = ids.mapNotNull(::byId).toSet()
            return entries.filterTo(linkedSetOf()) { it in known }
        }
    }
}
