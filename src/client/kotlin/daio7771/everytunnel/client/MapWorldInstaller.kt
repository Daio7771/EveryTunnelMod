package daio7771.everytunnel.client

import daio7771.everytunnel.EveryTunnel
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.nbt.NbtIo
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Comparator
import java.util.zip.ZipInputStream
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Puts the map world, packed inside the mod's jar (`every_tunnel/world/noname_complex.zip`,
 * see `packMapWorld` in build.gradle.kts), into the game's `saves/` folder, so it is in the
 * Singleplayer list as "Noname Complex" without installing anything else.
 *
 * - Missing: unpacked as `saves/noname_complex`. Its `level.dat` loses the player stored in
 *   it (whoever packed it), so a new player starts in Survival and the new game takes them
 *   to the elevator; its name becomes [WORLD_NAME], and cheats are off ([noCheats]).
 * - Already there, installed by an older version of the mod ([VERSION_FILE]): only its
 *   datapacks and `resources.zip` are replaced (the levels, their build and textures), and
 *   cheats are turned off (0.3 and older shipped the world with them on).
 * - A jar without the packed world (a dev build without `run/saves/noname_complex`), or a
 *   world that was not installed by the mod (the dev world), is left alone.
 *
 * Runs once, as the client starts, before quick play could open the world.
 */
object MapWorldInstaller {

    private const val RESOURCE = "/every_tunnel/world/noname_complex.zip"
    private const val FOLDER = "noname_complex"
    private const val WORLD_NAME = "Noname Complex"
    /** Inside the installed world: the mod version that installed or last updated it. */
    private const val VERSION_FILE = "every_tunnel_world_version.txt"

    fun install() {
        try {
            val saves = FabricLoader.getInstance().gameDir.resolve("saves")
            val world = saves.resolve(FOLDER)
            val version = FabricLoader.getInstance().getModContainer(EveryTunnel.MOD_ID)
                .map { it.metadata.version.friendlyString }.orElse("unknown")
            if (javaClass.getResource(RESOURCE) == null) return
            when {
                !world.resolve("level.dat").exists() -> installFresh(saves, world, version)
                world.resolve(VERSION_FILE).exists() && world.resolve(VERSION_FILE).readText().trim() != version ->
                    updateLevels(saves, world, version)
            }
        } catch (e: Exception) {
            EveryTunnel.LOGGER.error("Could not install the map world", e)
        }
    }

    private fun installFresh(saves: Path, world: Path, version: String) {
        val temp = saves.resolve("$FOLDER.installing")
        deleteTree(temp)
        unpack(temp) { true }
        val levelDat = temp.resolve("level.dat").toFile()
        val root = NbtIo.readCompressed(levelDat)
        val data = root.getCompound("Data")
        data.remove("Player")
        data.putString("LevelName", WORLD_NAME)
        data.putInt("GameType", 0)
        root.put("Data", data)
        NbtIo.writeCompressed(root, levelDat)
        noCheats(temp)
        temp.resolve(VERSION_FILE).writeText(version)
        deleteTree(world)
        Files.move(temp, world, StandardCopyOption.ATOMIC_MOVE)
        EveryTunnel.LOGGER.info("Installed the map world into {} (version {})", world, version)
    }

    private fun updateLevels(saves: Path, world: Path, version: String) {
        val temp = saves.resolve("$FOLDER.updating")
        deleteTree(temp)
        unpack(temp) { it.startsWith("datapacks/") || it == "resources.zip" }
        deleteTree(world.resolve("datapacks"))
        Files.move(temp.resolve("datapacks"), world.resolve("datapacks"))
        Files.move(temp.resolve("resources.zip"), world.resolve("resources.zip"), StandardCopyOption.REPLACE_EXISTING)
        deleteTree(temp)
        noCheats(world)
        world.resolve(VERSION_FILE).writeText(version)
        EveryTunnel.LOGGER.info("Updated the levels of the map world {} to version {}", world, version)
    }

    /**
     * "Allow Cheats" off in [world]'s `level.dat`: no `/tp`, `/gamemode` or `/give` for the
     * player. The map's own functions still run (they do not depend on it).
     */
    private fun noCheats(world: Path) {
        val levelDat = world.resolve("level.dat").toFile()
        val root = NbtIo.readCompressed(levelDat)
        val data = root.getCompound("Data")
        data.putBoolean("allowCommands", false)
        root.put("Data", data)
        NbtIo.writeCompressed(root, levelDat)
    }

    /** Unpacks the entries of the packed world that [wanted] accepts into [into]. */
    private fun unpack(into: Path, wanted: (String) -> Boolean) {
        Files.createDirectories(into)
        val stream = javaClass.getResourceAsStream(RESOURCE) ?: error("$RESOURCE is missing")
        ZipInputStream(stream.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!wanted(entry.name)) continue
                val target = into.resolve(entry.name).normalize()
                require(target.startsWith(into)) { "bad entry ${entry.name}" }
                if (entry.isDirectory) {
                    Files.createDirectories(target)
                } else {
                    Files.createDirectories(target.parent)
                    Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    private fun deleteTree(path: Path) {
        if (!path.exists()) return
        if (!path.isDirectory()) return Files.delete(path)
        Files.walk(path).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
    }
}
