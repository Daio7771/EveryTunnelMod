package daio7771.everytunnel.chapter2.level2.platform

import daio7771.everytunnel.common.EveryTunnel
import daio7771.everytunnel.chapter2.level2.core.Ch2L2Data
import daio7771.everytunnel.chapter2.level2.core.Ch2L2Files
import daio7771.everytunnel.chapter2.platform.Ch2DataReload
import net.fabricmc.loader.api.FabricLoader
import java.io.IOException
import java.nio.file.Files

/**
 * Reads Chapter 2's Level 2 data ([Ch2L2Data]) and keeps the current copy, which every system of the level reads through [data]. Each
 * file comes from the config folder (`config/every_tunnel/ch2/l2/<name>.json`) if a usable one is there, else from the jar's defaults
 * (`data/every_tunnel/ch2/l2/`), as Level 1's do: editing a copy in the config folder and running `/et reload` changes the level
 * without a rebuild. Read at launch and on every reload (it joins `/et reload` through [Ch2DataReload]).
 */
object Ch2L2DataLoader {

    /** The data as last read. Replaced as a whole, so a reader never sees half of a reload. */
    @Volatile
    var data: Ch2L2Data = Ch2L2Data.EMPTY
        private set

    fun register() {
        reload()
        Ch2DataReload.register(::reload)
    }

    /** Reads every file again and makes the result current; the report's lines, the first a summary. */
    fun reload(): List<String> {
        val folder = FabricLoader.getInstance().configDir.resolve(Ch2L2Files.OVERRIDE_FOLDER)
        val readProblems = mutableListOf<String>()
        val overrides = linkedMapOf<String, String>()
        for (name in Ch2L2Files.ALL) {
            val file = folder.resolve("$name.json")
            if (!Files.isRegularFile(file)) continue
            try {
                overrides[name] = Files.readString(file)
            } catch (e: IOException) {
                readProblems += "$name.json (override): could not be read (${e.message}), using the jar's copy"
            }
        }
        val read = Ch2L2Data.parse(overrides, Ch2L2Files.readAllBundled())
        data = read
        val anchors = read.anchors
        val lines = buildList {
            add("Chapter 2 Level 2 data loaded: ${anchors.points.size} anchors and ${anchors.zones.size} zones (${anchors.provisionalCount()} provisional).")
            add(
                if (read.overridden.isEmpty()) "No overrides for Level 2 (put edited copies in $folder)."
                else "Level 2 overrides in use: ${read.overridden.joinToString(", ")} (from $folder).",
            )
            addAll(readProblems + read.problems)
        }
        EveryTunnel.LOGGER.info(lines.first())
        (readProblems + read.problems).forEach { EveryTunnel.LOGGER.warn("Chapter 2 Level 2 data: {}", it) }
        return lines
    }
}
