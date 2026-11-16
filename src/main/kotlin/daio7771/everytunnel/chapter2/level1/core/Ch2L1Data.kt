package daio7771.everytunnel.chapter2.level1.core

import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser

/**
 * Everything adjustable in Chapter 2's Level 1, as the JSON files that hold it: the places ([Ch2L1Anchors]) and one
 * document for each system that is still to be built (loudspeakers, tasks, patrols, NPCs and the two cinematics).
 * Plain Kotlin; `platform.Ch2L1DataLoader` reads the files and hands their texts to [Ch2L1DataParser].
 *
 * Each [documents] entry is the file as a JSON object. Only the anchors have a typed reader so far: every other
 * system gets its own, in the step that builds it, and reads its document from here. Changing a rhythm, a route
 * or a text means editing a file and running `/et reload`.
 */
class Ch2L1Data(
    val anchors: Ch2L1Anchors,
    /** File name ([Ch2L1Files.ALL]) to its content; `{}` for a file with no usable copy. */
    val documents: Map<String, JsonObject>,
    /** File name to which copy was used. */
    val sources: Map<String, Source>,
) {
    /** The document of [name], an empty object if it has none. */
    fun document(name: String): JsonObject = documents[name] ?: JsonObject()

    enum class Source {
        /** `config/every_tunnel/ch2/l1/<name>.json`, which the developer put there. */
        OVERRIDE,

        /** The copy inside the mod's jar (the defaults). */
        BUNDLED,

        /** No usable copy of either kind. */
        MISSING,
    }

    companion object {
        val EMPTY = Ch2L1Data(
            Ch2L1Anchors.EMPTY,
            Ch2L1Files.ALL.associateWith { JsonObject() },
            Ch2L1Files.ALL.associateWith { Source.MISSING },
        )
    }
}

/**
 * The level's data files: names (without `.json`, relative to the level's folder) and where each lives. The jar has
 * the defaults under `data/every_tunnel/ch2/l1/`; a file of the same name in the game's config folder, under
 * `every_tunnel/ch2/l1/`, takes its place while it is usable.
 */
object Ch2L1Files {
    const val ANCHORS = "anchors"

    /** In reading order: the anchors first, the rest in the order of the phases that use them. */
    val ALL: List<String> = listOf(
        ANCHORS, "speakers", "tasks", "patrols", "npcs", "cutscenes/meeting", "act3", "cutscenes/office",
    )

    /** Inside the jar. */
    const val BUNDLED_FOLDER = "data/every_tunnel/ch2/l1"

    /** Inside the game's config folder. */
    const val OVERRIDE_FOLDER = "every_tunnel/ch2/l1"

    fun bundledPath(name: String): String = "$BUNDLED_FOLDER/$name.json"

    /** The jar's copy of [name] from the classpath, or null if it has none. */
    fun readBundled(name: String): String? =
        Ch2L1Files::class.java.classLoader.getResourceAsStream(bundledPath(name))?.use { it.reader().readText() }

    /** The jar's copy of every file that has one. */
    fun readAllBundled(): Map<String, String> = ALL.mapNotNull { name -> readBundled(name)?.let { name to it } }.toMap()
}

/**
 * Chooses and parses the copy of each file. A copy in [overrides] wins over the jar's [bundled] one, but only if it
 * is usable (valid JSON, an object): a broken override is reported and the jar's copy is used, so a typo in a hand
 * edit never leaves the level without its data. A file with no usable copy gives an empty one and a problem.
 */
object Ch2L1DataParser {

    class Result(
        val data: Ch2L1Data,
        /** One line each, every one starting with the file it is about. */
        val problems: List<String>,
    )

    /** Both maps are file name ([Ch2L1Files.ALL]) to its text; a file with no copy of a kind is absent. */
    fun parse(overrides: Map<String, String>, bundled: Map<String, String>): Result {
        val problems = mutableListOf<String>()
        var anchors = Ch2L1Anchors.EMPTY
        val documents = linkedMapOf<String, JsonObject>()
        val sources = linkedMapOf<String, Ch2L1Data.Source>()

        for (name in Ch2L1Files.ALL) {
            val candidates = listOfNotNull(
                overrides[name]?.let { Ch2L1Data.Source.OVERRIDE to it },
                bundled[name]?.let { Ch2L1Data.Source.BUNDLED to it },
            )
            var used: Ch2L1Data.Source? = null
            for ((source, text) in candidates) {
                val where = "$name.json (${source.name.lowercase()})"
                if (name == Ch2L1Files.ANCHORS) {
                    val result = Ch2L1AnchorsParser.parse(text)
                    result.problems.mapTo(problems) { "$where: $it" }
                    val parsed = result.anchors
                    if (parsed != null) {
                        anchors = parsed
                        documents[name] = JsonObject()
                        used = source
                    }
                } else {
                    val document = parseObject(text, where, problems)
                    if (document != null) {
                        documents[name] = document
                        used = source
                    }
                }
                if (used != null) break
                if (source == Ch2L1Data.Source.OVERRIDE) problems += "$where: unusable, trying the copy in the jar"
            }
            if (used == null) {
                problems += "$name.json: no usable copy, using an empty one"
                documents[name] = JsonObject()
            }
            sources[name] = used ?: Ch2L1Data.Source.MISSING
        }
        return Result(Ch2L1Data(anchors, documents, sources), problems.toList())
    }

    private fun parseObject(text: String, where: String, problems: MutableList<String>): JsonObject? {
        val root = try {
            JsonParser.parseString(text)
        } catch (e: JsonParseException) {
            problems += "$where: invalid JSON: ${e.message}"
            return null
        }
        if (!root.isJsonObject) {
            problems += "$where: the file must contain a JSON object"
            return null
        }
        return root.asJsonObject
    }
}
