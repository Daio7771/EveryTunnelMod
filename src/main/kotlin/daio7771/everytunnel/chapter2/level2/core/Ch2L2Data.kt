package daio7771.everytunnel.chapter2.level2.core

import com.google.gson.JsonParser
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Anchors
import daio7771.everytunnel.chapter2.level1.core.Ch2L1AnchorsParser
/**
 * The data of Chapter 2's Level 2, the catacombs under the factory, as last read: its named places ([anchors], `anchors.json`, the same
 * format as Level 1's: [Ch2L1AnchorsParser], points, zones and paths, in world coordinates) and how it is played ([level],
 * `level.json`: [Ch2L2Level]). Plain Kotlin.
 *
 * Every later phase adds its file to [Ch2L2Files.ALL] and its parsed shape here. The map itself (`map.json`) is written by `tools/map/chapter2/ch2l2/generate.py`, with the anchors and the flood's stages (`flood.json`, read as [flood]).
 */
class Ch2L2Data(
    val anchors: Ch2L1Anchors,
    val level: Ch2L2Level,
    /** The flood's stages (`flood.json`, written by the generator with the anchors). */
    val flood: Ch2L2Flood,
    /** What is wrong with the files, one line each, naming the file. */
    val problems: List<String>,
    /** The files read from the config folder's copy. */
    val overridden: Set<String>,
) {
    companion object {
        val EMPTY = Ch2L2Data(Ch2L1Anchors.EMPTY, Ch2L2Level.DEFAULT, Ch2L2Flood.EMPTY, emptyList(), emptySet())

        /**
         * Chooses and parses each file's copy: one in [overrides] (the config folder's) wins over the jar's [bundled] one while it is
         * usable (valid JSON, an object); a broken one is reported and the jar's is used, so a typo in a hand edit never leaves the
         * level without its places. A file with no usable copy gives its defaults and a problem. Then what `level.json` names is
         * looked for in the anchors.
         */
        fun parse(overrides: Map<String, String>, bundled: Map<String, String>): Ch2L2Data {
            val problems = mutableListOf<String>()
            val overridden = mutableSetOf<String>()

            /** The first usable copy of [name], parsed by [read] (a result, or null and its problems). */
            fun <T> choose(name: String, read: (String) -> Pair<T?, List<String>>): T? {
                overrides[name]?.let { text ->
                    val (value, found) = read(text)
                    if (value != null) {
                        overridden += name
                        problems += found.map { "$name.json (override): $it" }
                        return value
                    }
                    problems += found.map { "$name.json (override): $it; using the jar's copy" }
                }
                val text = bundled[name]
                if (text == null) {
                    problems += "$name.json: the jar has no copy (run tools/map/chapter2/ch2l2/generate.py --install)"
                    return null
                }
                val (value, found) = read(text)
                problems += found.map { "$name.json: $it" }
                return value
            }

            val anchors = choose(Ch2L2Files.ANCHORS) { text ->
                val result = Ch2L1AnchorsParser.parse(text)
                result.anchors to result.problems
            } ?: Ch2L1Anchors.EMPTY
            val level = choose(Ch2L2Files.LEVEL) { text ->
                val root = runCatching { JsonParser.parseString(text) }.getOrNull()
                if (root == null || !root.isJsonObject) {
                    null to listOf("not a JSON object")
                } else {
                    val result = Ch2L2Level.parse(root.asJsonObject)
                    result.level to result.problems
                }
            } ?: Ch2L2Level.DEFAULT
            val flood = choose(Ch2L2Files.FLOOD) { text ->
                val result = Ch2L2Flood.parse(text)
                (if (result.problems.any { it == "not a JSON object" }) null else result.flood) to result.problems
            } ?: Ch2L2Flood.EMPTY

            if (anchors.points.isNotEmpty()) {
                for (id in level.anchorNames()) if (anchors.point(id) == null) problems += "level.json: no anchor \"$id\" in anchors.json"
                for (id in level.zoneNames()) if (anchors.zone(id) == null) problems += "level.json: no zone \"$id\" in anchors.json"
                for (stage in flood.stages) if (anchors.zone(stage.trigger) == null) problems += "flood.json: no zone \"${stage.trigger}\" in anchors.json"
            }
            return Ch2L2Data(anchors, level, flood, problems, overridden)
        }
    }
}

/**
 * The level's data files: names (without `.json`) and where each lives. The jar has the defaults under `data/every_tunnel/ch2/l2/`;
 * a file of the same name in the game's config folder, under `every_tunnel/ch2/l2/`, takes its place while it is usable.
 */
object Ch2L2Files {
    const val ANCHORS = "anchors"
    const val LEVEL = "level"
    const val FLOOD = "flood"

    /** The files the level reads, in reading order. */
    val ALL: List<String> = listOf(ANCHORS, LEVEL, FLOOD)

    /** Inside the jar. */
    const val BUNDLED_FOLDER = "data/every_tunnel/ch2/l2"

    /** Inside the game's config folder. */
    const val OVERRIDE_FOLDER = "every_tunnel/ch2/l2"

    /** The map plan the mod builds (not a data file: it is rebuilt from the generator, never overridden). */
    const val PLAN = "$BUNDLED_FOLDER/map.json"

    fun bundledPath(name: String): String = "$BUNDLED_FOLDER/$name.json"

    /** The jar's copy of [name] from the classpath, or null if it has none. */
    fun readBundled(name: String): String? =
        Ch2L2Files::class.java.classLoader.getResourceAsStream(bundledPath(name))?.use { it.reader().readText() }

    /** The jar's copy of every file that has one. */
    fun readAllBundled(): Map<String, String> = ALL.mapNotNull { name -> readBundled(name)?.let { name to it } }.toMap()
}
