package daio7771.everytunnel.client

import daio7771.everytunnel.EveryTunnel
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.writeLines

/**
 * The recommended shader settings for Every Tunnel, put once into the settings of every
 * Complementary pack (Reimagined, Unbound) in `shaderpacks/`, as the client starts:
 *
 * - [OFF_KEYS] go back to their default (removed from `<pack>.txt`, Iris's settings file for
 *   that pack). Complementary's colored lighting lights the world from the pack's own list of
 *   glowing blocks, which has none of the mod's, so in its range the fluorescent lights (and
 *   the flashlight) light nothing; off, the pack uses the game's light, which has them.
 *   World space reflections go too: the pack refuses to run them without colored
 *   lighting ("Advanced Color Tracing") and shows an error screen instead.
 *
 * Each pack is done once per version of these settings ([APPLIED_FILE] in the config folder
 * remembers "<pack>|<[VERSION]>"): what the player changes afterwards is kept, until the
 * recommended settings themselves change. Without Iris or a Complementary pack, nothing
 * happens.
 */
object ShaderSettings {

    private val OFF_KEYS = listOf("COLORED_LIGHTING", "WORLD_SPACE_REFLECTIONS")
    /** Raise when [OFF_KEYS] change, so packs done before get the new settings too. */
    private const val VERSION = 2
    private const val APPLIED_FILE = "every_tunnel/shader_settings_applied.txt"

    fun apply() {
        try {
            val loader = FabricLoader.getInstance()
            val packs = loader.gameDir.resolve("shaderpacks")
            if (!packs.isDirectory()) return
            val appliedFile = loader.configDir.resolve(APPLIED_FILE)
            val applied = if (appliedFile.exists()) appliedFile.readLines().map(String::trim).toMutableSet() else mutableSetOf()
            var changed = false
            for (pack in packs.listDirectoryEntries()) {
                val name = pack.name
                val entry = "$name|$VERSION"
                if (name.endsWith(".txt") || !name.startsWith("Complementary", ignoreCase = true) || entry in applied) continue
                applyTo(packs.resolve("$name.txt"))
                applied.removeIf { it.substringBefore('|') == name }
                applied += entry
                changed = true
                EveryTunnel.LOGGER.info("Recommended shader settings applied to {}", name)
            }
            if (changed) {
                Files.createDirectories(appliedFile.parent)
                appliedFile.writeLines(applied.sorted())
            }
        } catch (e: Exception) {
            EveryTunnel.LOGGER.warn("Could not apply the recommended shader settings", e)
        }
    }

    /** Removes [OFF_KEYS] from a pack's settings file, keeping everything else as it was. */
    private fun applyTo(settings: Path) {
        if (!settings.exists()) return // no file: every option is at its default already
        val properties = Properties()
        Files.newBufferedReader(settings).use(properties::load)
        if (OFF_KEYS.none(properties::containsKey)) return
        // Line by line, so the rest of the file (and its order) stays as Iris wrote it.
        val kept = settings.readLines().filterNot { line -> OFF_KEYS.any { line.trimStart().startsWith("$it=") } }
        settings.writeLines(kept)
    }
}
