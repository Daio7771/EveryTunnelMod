package daio7771.everytunnel.chapter2.level2.core

import daio7771.everytunnel.util.JsonFields

/**
 * A group of three Hunters (Level 5's) put at [anchor] when the level starts at a state no later than [until]; they face [yaw]
 * (the anchor's own if null). Several entries give several groups.
 */
data class Ch2L2HunterGroup(val id: String, val anchor: String, val yaw: Float?, val until: Ch2L2State)

/**
 * A piece of loot lying at the block [dx], [dy], [dz] from the block of [anchor] (a key of `platform.LootSpawner.LOOT`: `battery`,
 * `water`, `food_snack`, `wrench`...), turned [yaw] degrees. Once taken it stays taken (the player's Level 2 counter [takenKey]), and what
 * a saved game comes back to is what had been taken at its checkpoint.
 */
data class Ch2L2Loot(val id: String, val item: String, val anchor: String, val dx: Int, val dy: Int, val dz: Int, val yaw: Float) {
    val takenKey: String get() = "$TAKEN_PREFIX$id"

    companion object {
        const val TAKEN_PREFIX = "loot."
    }
}

/**
 * The note pinned on the board at [anchor] (the board's own block): `assets/every_tunnel/notes/<lang>/[noteId].txt`, which
 * `tools/lang/langfiles.py` writes from the lang keys. Reading one is optional, and remembered ([readKey]).
 */
data class Ch2L2Note(val id: String, val anchor: String) {
    val noteId: String get() = "ch2_l2_$id"
    val readKey: String get() = "$READ_PREFIX$id"

    companion object {
        const val READ_PREFIX = "read."
    }
}

/** Reads the `hunters`, `loot` and `notes` lists of `level.json`; the problems go to [problems]. */
object Ch2L2PlacementsParser {

    private fun entries(f: JsonFields, key: String): List<JsonFields> {
        if (!f.has(key)) return emptyList()
        return (f.array(key)?.toList() ?: emptyList()).mapIndexedNotNull { i, e ->
            if (e.isJsonObject) JsonFields(e.asJsonObject, "$key[$i]", f.problems)
            else {
                f.problems += "$key[$i]: expected an object"
                null
            }
        }
    }

    fun hunters(f: JsonFields): List<Ch2L2HunterGroup> = entries(f, "hunters").mapNotNull { e ->
        val id = e.requiredString("id")
        val anchor = e.requiredString("anchor")
        val yaw = if (e.has("yaw")) e.double("yaw", 0.0).toFloat() else null
        val untilText = e.string("until", "pump_room")
        val until = Ch2L2State.parse(untilText)
        if (until == null) f.problems += "${e.pathOf("until")}: no state \"$untilText\""
        e.reportUnknownKeys()
        if (id == null || anchor == null || until == null) null else Ch2L2HunterGroup(id, anchor, yaw, until)
    }

    fun loot(f: JsonFields): List<Ch2L2Loot> = entries(f, "loot").mapNotNull { e ->
        val id = e.requiredString("id")
        val item = e.requiredString("item")
        val anchor = e.requiredString("anchor")
        val offset = (if (e.has("offset")) e.array("offset")?.toList() else null)?.map { runCatching { it.asInt }.getOrNull() } ?: listOf<Int?>(0, 0, 0)
        val offsetOk = offset.size == 3 && offset.none { it == null }
        if (!offsetOk) f.problems += "${e.pathOf("offset")}: expected three whole numbers (dx, dy, dz)"
        val yaw = e.double("yaw", 0.0).toFloat()
        e.reportUnknownKeys()
        if (id == null || item == null || anchor == null || !offsetOk) null
        else Ch2L2Loot(id, item, anchor, offset[0]!!, offset[1]!!, offset[2]!!, yaw)
    }

    fun notes(f: JsonFields): List<Ch2L2Note> = entries(f, "notes").mapNotNull { e ->
        val id = e.requiredString("id")
        val anchor = e.requiredString("anchor")
        e.reportUnknownKeys()
        if (id == null || anchor == null) null else Ch2L2Note(id, anchor)
    }

    /** Two entries of one list with the same id (a loot piece would count as taken for both). */
    fun duplicates(what: String, ids: List<String>): List<String> =
        ids.groupBy { it }.filter { it.value.size > 1 }.keys.map { "$what: two have the id \"$it\"" }
}
