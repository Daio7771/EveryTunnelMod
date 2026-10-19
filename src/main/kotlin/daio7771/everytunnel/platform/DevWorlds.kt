package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.MinecraftServer
import net.minecraft.util.WorldSavePath
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * Development only (never active in a player's game): worlds that start from the same
 * point on every launch, so nothing done while testing is kept.
 *
 * - `/everytunnel devworld save`: when the open world closes, it is copied to
 *   `run/dev_templates/<world>`. Stand where you want to start before quitting.
 * - On every launch of the dev client, each world with a template is replaced by it
 *   ([restoreAll], called by the client entrypoint before the quick play world opens).
 * - `/everytunnel devworld forget`: deletes the open world's template; it is kept
 *   between launches again.
 *
 * `tools/map/map_generator.py` installs the level into the template too, so it is not
 * undone by the next launch.
 */
object DevWorlds {

    val enabled: Boolean get() = FabricLoader.getInstance().isDevelopmentEnvironment

    private val templates: Path get() = FabricLoader.getInstance().gameDir.resolve("dev_templates")
    private val saves: Path get() = FabricLoader.getInstance().gameDir.resolve("saves")

    /** World folder to copy to its template when the server stops. */
    private var saveOnStop: Path? = null

    fun register() {
        if (!enabled) return
        ServerLifecycleEvents.SERVER_STOPPED.register {
            val world = saveOnStop ?: return@register
            saveOnStop = null
            val template = templates.resolve(world.name)
            deleteTree(template)
            copyTree(world, template)
            EveryTunnel.LOGGER.info("Dev world template saved: {} -> {}", world, template)
        }
    }

    /** Puts back every world that has a template. Call before any world is opened. */
    fun restoreAll() {
        if (!enabled || !templates.isDirectory()) return
        for (template in templates.listDirectoryEntries().filter { it.resolve("level.dat").exists() }) {
            val world = saves.resolve(template.name)
            deleteTree(world)
            copyTree(template, world)
            EveryTunnel.LOGGER.info("Dev world {} reset from its template", template.name)
        }
    }

    /** Marks the open world to become its template when it closes; returns its name. */
    fun saveOnStop(server: MinecraftServer): String {
        val world = worldFolder(server)
        saveOnStop = world
        return world.name
    }

    /** Deletes the open world's template; returns false if it had none. */
    fun forget(server: MinecraftServer): Boolean {
        saveOnStop = null
        val template = templates.resolve(worldFolder(server).name)
        if (!template.exists()) return false
        deleteTree(template)
        return true
    }

    private fun worldFolder(server: MinecraftServer): Path =
        server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize()

    private fun copyTree(from: Path, to: Path) {
        Files.walk(from).use { paths ->
            for (path in paths) {
                if (path.name == "session.lock") continue
                val target = to.resolve(from.relativize(path).toString())
                if (path.isDirectory()) Files.createDirectories(target)
                else Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private fun deleteTree(root: Path) {
        if (!root.exists()) return
        Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
    }
}
