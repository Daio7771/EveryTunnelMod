package daio7771.everytunnel.client

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.achievement.Achievement
import net.fabricmc.loader.api.FabricLoader
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * The achievements this player has earned, for good: kept on this computer in
 * `config/every_tunnel/achievements.json` (`{"unlocked": ["first_day", ...]}`), the same for
 * every world and every new game. Read the first time it is needed, written on each new one.
 * A missing or broken file counts as none earned (and is logged); ids this version does not know
 * are kept in the file but not shown.
 *
 * Client thread only. The server says what was earned (`platform.AchievementHooks`); the ACHIEVEMENTS
 * screen (`client.screen.AchievementsScreen`) reads [unlocked].
 */
object AchievementStore {

    private val file: Path get() = FabricLoader.getInstance().configDir.resolve(EveryTunnel.MOD_ID).resolve("achievements.json")

    private var ids: MutableSet<String>? = null

    private fun ids(): MutableSet<String> = ids ?: load().also { ids = it }

    /** The achievements earned, in list order. */
    val unlocked: Set<Achievement> get() = Achievement.fromIds(ids())

    fun isUnlocked(achievement: Achievement): Boolean = achievement.id in ids()

    /** Remembers [achievement]; true if it is new. */
    fun unlock(achievement: Achievement): Boolean {
        if (!ids().add(achievement.id)) return false
        save()
        return true
    }

    private fun load(): MutableSet<String> {
        val path = file
        if (!Files.exists(path)) return linkedSetOf()
        return try {
            val json = JsonParser.parseString(Files.readString(path)).asJsonObject
            val list = json.getAsJsonArray("unlocked") ?: JsonArray()
            list.mapNotNullTo(linkedSetOf()) { element -> element.takeIf { it.isJsonPrimitive }?.asString }
        } catch (e: Exception) {
            EveryTunnel.LOGGER.warn("Could not read {}: no achievements loaded ({})", path, e.message)
            linkedSetOf()
        }
    }

    private fun save() {
        val path = file
        try {
            Files.createDirectories(path.parent)
            val json = JsonObject().apply { add("unlocked", JsonArray().apply { ids().forEach(::add) }) }
            Files.writeString(path, json.toString() + "\n")
        } catch (e: IOException) {
            EveryTunnel.LOGGER.warn("Could not save {}: {}", path, e.message)
        }
    }
}
