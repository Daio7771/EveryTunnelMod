package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.level.LevelDefinition
import daio7771.everytunnel.core.level.LevelParser
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.resource.ResourceManager
import net.minecraft.resource.ResourceType
import net.minecraft.util.Identifier
import java.io.IOException

/**
 * Loads `data/every_tunnel/levels/<id>.json` from the enabled datapacks (the map
 * ships them in `<world>/datapacks/`) into [LevelManager]. Runs when a world loads
 * and on `/reload`. A broken file is logged and skipped; it never stops loading.
 */
object LevelReloadListener : SimpleSynchronousResourceReloadListener {

    private const val FOLDER = "levels"
    private val ID = Identifier(EveryTunnel.MOD_ID, FOLDER)

    fun register() {
        ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(this)
    }

    override fun getFabricId(): Identifier = ID

    override fun reload(manager: ResourceManager) {
        val log = EveryTunnel.LOGGER
        val levels = mutableListOf<LevelDefinition>()
        val files = manager.findResources(FOLDER) { it.path.endsWith(".json") }
            .filterKeys { it.namespace == EveryTunnel.MOD_ID }
            .toSortedMap(compareBy { it.path })

        for ((fileId, resource) in files) {
            val levelId = fileId.path.removePrefix("$FOLDER/").removeSuffix(".json")
            val text = try {
                resource.reader.use { it.readText() }
            } catch (e: IOException) {
                log.error("Could not read level file {}", fileId, e)
                continue
            }
            val result = LevelParser.parse(levelId, text)
            result.problems.forEach { log.warn("{}: {}", fileId, it) }
            result.level?.let(levels::add)
        }

        LevelManager.replaceAll(levels).forEach { log.warn("Levels: {}", it) }
        if (levels.isEmpty()) {
            log.info("No Every Tunnel levels in this world's datapacks")
        } else {
            log.info(
                "Loaded {} level(s): {}",
                levels.size,
                LevelManager.all().joinToString { "${it.id} (order ${it.order}, ${it.zones.size} zones, ${it.spawnPoints.size} spawn points)" },
            )
        }
    }
}
