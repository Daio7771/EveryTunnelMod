package daio7771.everytunnel.chapter2.level2.core

import daio7771.everytunnel.chapter2.level1.core.Ch2L1Anchors
import daio7771.everytunnel.chapter2.level1.core.Ch2L1AnchorsParser

/**
 * The data of Chapter 2's Level 2, the catacombs under the factory, as last read: its named places ([anchors], `anchors.json`), the
 * same format as Level 1's ([Ch2L1AnchorsParser]: points, zones and paths, in world coordinates). Plain Kotlin.
 *
 * Phase 1 reads only the anchors; every later phase adds its file to [Ch2L2Files.ALL] and its parsed shape here. The map itself
 * (`map.json`) and the flood's stages (`flood.json`) are written by `tools/map/ch2l2/generate.py` with the anchors.
 */
class Ch2L2Data(
    val anchors: Ch2L1Anchors,
    /** What is wrong with the files, one line each, naming the file. */
    val problems: List<String>,
    /** The files read from the config folder's copy. */
    val overridden: Set<String>,
) {
    companion object {
        val EMPTY = Ch2L2Data(Ch2L1Anchors.EMPTY, emptyList(), emptySet())

        /**
         * Chooses and parses each file's copy: one in [overrides] (the config folder's) wins over the jar's [bundled] one while it is
         * usable (valid JSON, an object); a broken one is reported and the jar's is used, so a typo in a hand edit never leaves the
         * level without its places. A file with no usable copy gives an empty one and a problem.
         */
        fun parse(overrides: Map<String, String>, bundled: Map<String, String>): Ch2L2Data {
            val problems = mutableListOf<String>()
            val overridden = mutableSetOf<String>()
            var anchors: Ch2L1Anchors? = null
            overrides[Ch2L2Files.ANCHORS]?.let { text ->
                val result = Ch2L1AnchorsParser.parse(text)
                if (result.anchors != null) {
                    anchors = result.anchors
                    overridden += Ch2L2Files.ANCHORS
                    problems += result.problems.map { "anchors.json (override): $it" }
                } else {
                    problems += result.problems.map { "anchors.json (override): $it; using the jar's copy" }
                }
            }
            if (anchors == null) {
                val text = bundled[Ch2L2Files.ANCHORS]
                if (text == null) {
                    problems += "anchors.json: the jar has no copy (run tools/map/ch2l2/generate.py --install)"
                } else {
                    val result = Ch2L1AnchorsParser.parse(text)
                    anchors = result.anchors
                    problems += result.problems.map { "anchors.json: $it" }
                }
            }
            return Ch2L2Data(anchors ?: Ch2L1Anchors.EMPTY, problems, overridden)
        }
    }
}

/**
 * The level's data files: names (without `.json`) and where each lives. The jar has the defaults under `data/every_tunnel/ch2/l2/`;
 * a file of the same name in the game's config folder, under `every_tunnel/ch2/l2/`, takes its place while it is usable.
 */
object Ch2L2Files {
    const val ANCHORS = "anchors"

    /** The files the level reads, in reading order. */
    val ALL: List<String> = listOf(ANCHORS)

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
