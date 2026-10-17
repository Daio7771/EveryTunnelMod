package daio7771.everytunnel.core.level

import com.google.gson.JsonArray
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import daio7771.everytunnel.util.JsonFields
import daio7771.everytunnel.util.JsonFields.Companion.numberOrNull

/**
 * Parses one level file. Example:
 *
 * ```json
 * {
 *   "name": "Level 1 - Parking Garage",
 *   "order": 1,
 *   "dimension": "minecraft:overworld",
 *   "zones": [
 *     { "id": "garage", "from": [0, -60, 0], "to": [31, -55, 31] },
 *     { "id": "ramp_down", "from": [28, -60, 28], "to": [31, -57, 31], "tags": ["exit"] }
 *   ],
 *   "spawn_points": [
 *     { "id": "player_start", "pos": [4.5, -60, 4.5], "yaw": 180, "tags": ["player"] }
 *   ]
 * }
 * ```
 *
 * `name` (defaults to the id), `dimension` (defaults to the overworld), `tags`,
 * `yaw`, `pitch` and `spawn_points` are optional. `order` and at least one valid
 * zone are required. A broken zone or spawn point is skipped with a problem; the
 * level is rejected only when it cannot be used at all.
 */
object LevelParser {

    data class Result(
        /** The parsed level, or null if the file was unusable. */
        val level: LevelDefinition?,
        val problems: List<String>,
    )

    private val ID_PATTERN = Regex("[a-z0-9_]+")
    private val DIMENSION_PATTERN = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")

    fun parse(levelId: String, text: String): Result {
        val root = try {
            JsonParser.parseString(text)
        } catch (e: JsonParseException) {
            return Result(null, listOf("invalid JSON: ${e.message}; level skipped"))
        }
        if (!root.isJsonObject) return Result(null, listOf("the file must contain a JSON object; level skipped"))

        val fields = JsonFields(root.asJsonObject, "")
        val problems = fields.problems

        val name = fields.string("name", levelId)
        val hasOrder = fields.has("order")
        val order = fields.int("order", 0, min = 0)
        if (!hasOrder) problems += "order: missing required value"

        var dimension = fields.string("dimension", "minecraft:overworld")
        if (!DIMENSION_PATTERN.matches(dimension)) {
            problems += "dimension: \"$dimension\" is not a valid dimension id, using minecraft:overworld"
            dimension = "minecraft:overworld"
        }

        val zones = parseZones(fields, problems)
        val spawnPoints = parseSpawnPoints(fields, problems)
        fields.reportUnknownKeys()

        if (!hasOrder || zones.isEmpty()) {
            if (zones.isEmpty()) problems += "zones: the level needs at least one valid zone"
            problems += "level skipped"
            return Result(null, problems.toList())
        }
        return Result(LevelDefinition(levelId, name, order, dimension, zones, spawnPoints), problems.toList())
    }

    private fun parseZones(fields: JsonFields, problems: MutableList<String>): List<Zone> {
        val array = fields.array("zones") ?: return emptyList()
        val zones = mutableListOf<Zone>()
        forEachObject(array, fields.pathOf("zones"), problems) { entry ->
            val id = validId(entry, problems) ?: return@forEachObject
            val from = intVec3(entry, "from", problems)
            val to = intVec3(entry, "to", problems)
            val tags = entry.stringList("tags").toSet()
            entry.reportUnknownKeys()
            when {
                from == null || to == null -> problems += "${entry.pathOf("id")} \"$id\": zone skipped"
                zones.any { it.id == id } -> problems += "${entry.pathOf("id")}: duplicate zone id \"$id\", zone skipped"
                else -> zones += Zone(id, BlockBox.ofCorners(from[0], from[1], from[2], to[0], to[1], to[2]), tags)
            }
        }
        return zones
    }

    private fun parseSpawnPoints(fields: JsonFields, problems: MutableList<String>): List<SpawnPoint> {
        if (!fields.has("spawn_points")) {
            fields.stringList("spawn_points") // marks the key as read; absent is fine
            return emptyList()
        }
        val array = fields.array("spawn_points") ?: return emptyList()
        val spawnPoints = mutableListOf<SpawnPoint>()
        forEachObject(array, fields.pathOf("spawn_points"), problems) { entry ->
            val id = validId(entry, problems) ?: return@forEachObject
            val pos = doubleVec3(entry, "pos", problems)
            val yaw = entry.double("yaw", 0.0, min = -360.0, max = 360.0).toFloat()
            val pitch = entry.double("pitch", 0.0, min = -90.0, max = 90.0).toFloat()
            val tags = entry.stringList("tags").toSet()
            entry.reportUnknownKeys()
            when {
                pos == null -> problems += "${entry.pathOf("id")} \"$id\": spawn point skipped"
                spawnPoints.any { it.id == id } -> problems += "${entry.pathOf("id")}: duplicate spawn point id \"$id\", skipped"
                else -> spawnPoints += SpawnPoint(id, Position(pos[0], pos[1], pos[2]), yaw, pitch, tags)
            }
        }
        return spawnPoints
    }

    private inline fun forEachObject(
        array: JsonArray,
        path: String,
        problems: MutableList<String>,
        action: (JsonFields) -> Unit,
    ) {
        array.forEachIndexed { index, element ->
            if (element.isJsonObject) {
                action(JsonFields(element.asJsonObject, "$path[$index]", problems))
            } else {
                problems += "$path[$index]: expected an object but got $element, skipped"
            }
        }
    }

    private fun validId(entry: JsonFields, problems: MutableList<String>): String? {
        val id = entry.requiredString("id") ?: return null
        if (!ID_PATTERN.matches(id)) {
            problems += "${entry.pathOf("id")}: \"$id\" may only contain a-z, 0-9 and _, entry skipped"
            return null
        }
        return id
    }

    private fun doubleVec3(entry: JsonFields, key: String, problems: MutableList<String>): DoubleArray? {
        if (!entry.has(key)) {
            entry.array(key)
            problems += "${entry.pathOf(key)}: missing required value"
            return null
        }
        val array = entry.array(key) ?: return null
        val values = array.mapNotNull { it.numberOrNull()?.takeIf(Double::isFinite) }
        if (array.size() != 3 || values.size != 3) {
            problems += "${entry.pathOf(key)}: expected [x, y, z] with 3 numbers but got $array"
            return null
        }
        return values.toDoubleArray()
    }

    private fun intVec3(entry: JsonFields, key: String, problems: MutableList<String>): IntArray? {
        val values = doubleVec3(entry, key, problems) ?: return null
        if (values.any { it != Math.floor(it) || it < Int.MIN_VALUE || it > Int.MAX_VALUE }) {
            problems += "${entry.pathOf(key)}: block coordinates must be whole numbers but got ${values.toList()}"
            return null
        }
        return IntArray(3) { values[it].toInt() }
    }
}
