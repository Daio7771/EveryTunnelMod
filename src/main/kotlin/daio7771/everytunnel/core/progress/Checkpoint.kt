package daio7771.everytunnel.core.progress

/**
 * Where a saved game goes on, and a death restarts: a level's start, or a restart point inside it
 * (Level 5's Lab hall). [PlayerProgress] keeps it as text: the level id, or `level/point` with the id of
 * the level's spawn point.
 */
data class Checkpoint(val levelId: String, val pointId: String? = null) {

    override fun toString(): String = if (pointId == null) levelId else "$levelId/$pointId"

    companion object {
        /** The checkpoint in [text], or null for none (or nothing usable). */
        fun parse(text: String?): Checkpoint? {
            if (text.isNullOrBlank()) return null
            val parts = text.split('/', limit = 2)
            val level = parts[0].trim()
            if (level.isEmpty()) return null
            return Checkpoint(level, parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() })
        }
    }
}
