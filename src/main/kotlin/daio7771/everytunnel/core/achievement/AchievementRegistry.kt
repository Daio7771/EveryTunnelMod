package daio7771.everytunnel.core.achievement

/**
 * What the base game needs to know of an achievement, so that a later chapter can have its own without the base naming it: the id (the
 * packet, the saved file and the lang keys `achievement.every_tunnel.<id>` and `.description`), the [chapter] whose tab of the
 * ACHIEVEMENTS screen lists it, whether a [puzzle] ends with it (every player in the world earns it) and its [icon].
 */
interface AchievementDef {
    val id: String
    val chapter: Int
    val puzzle: Boolean

    /** Its 16x16 icon: `item:<namespace>:<path>` for an item drawn as it is, `sprite:<n>` for the n-th 16-pixel column of `achievement_icons.png`; empty for Chapter 1's, which [Achievement] draws by itself. */
    val icon: String get() = ""

    /** Until it is earned the screen says only that it is hidden, not that it is yet to be unlocked. */
    val hidden: Boolean get() = false
}

/**
 * Every achievement of the game in list order: Chapter 1's ([Achievement]) first, then what each chapter [register]s at start-up (its
 * entrypoint, on both sides). The ACHIEVEMENTS screen makes a tab of each chapter number ([chapters]); the server earns and the client
 * remembers by id ([byId]). Ids are unique across the whole game.
 */
object AchievementRegistry {

    private val added = mutableListOf<AchievementDef>()

    /** Adds a chapter's achievements; the same definition twice is ignored, another with a known id is an error. */
    fun register(defs: Iterable<AchievementDef>) {
        for (def in defs) {
            val known = byId(def.id)
            if (known === def) continue
            require(known == null) { "Achievement id \"${def.id}\" is registered twice" }
            require(def.chapter > 1) { "Achievement \"${def.id}\" is of chapter ${def.chapter}: only the base game's are Chapter 1's" }
            added += def
        }
    }

    /** Every achievement, Chapter 1's first, then in registration order. */
    fun all(): List<AchievementDef> = Achievement.entries + added

    fun byId(id: String): AchievementDef? = all().firstOrNull { it.id == id }

    /** The chapter numbers that have achievements, ascending: the tabs. */
    fun chapters(): List<Int> = all().map { it.chapter }.distinct().sorted()

    fun inChapter(chapter: Int): List<AchievementDef> = all().filter { it.chapter == chapter }

    /** The achievements named in [ids], in list order; ids this version does not know are left out. */
    fun fromIds(ids: Iterable<String>): Set<AchievementDef> {
        val known = ids.toSet()
        return all().filterTo(linkedSetOf()) { it.id in known }
    }

    /** The chapters' own additions forgotten (tests). */
    internal fun clearAdded() {
        added.clear()
    }
}
