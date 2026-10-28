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
 *     { "id": "ramp_down", "from": [28, -60, 28], "to": [31, -57, 31], "tags": ["exit"] },
 *     { "id": "boiler_room", "from": [0, -60, 20], "to": [8, -55, 31], "ambience": { "loop": "every_tunnel:ambient.vent" } }
 *   ],
 *   "spawn_points": [
 *     { "id": "player_start", "pos": [4.5, -60, 4.5], "yaw": 180, "tags": ["player"] },
 *     { "id": "pale_man_1", "pos": [16.5, -60, 16.5], "tags": ["pale_man"], "zone": "garage" }
 *   ],
 *   "ambience": {
 *     "loop": "every_tunnel:ambient.garage.drone",
 *     "loop_volume": 0.6,
 *     "random": [
 *       { "sound": "every_tunnel:ambient.drip", "interval": [6, 18], "distance": [3, 12], "volume": 0.5 }
 *     ]
 *   },
 *   "entry": { "title": "Level 1", "subtitle": "", "sound": "every_tunnel:event.level_transition" },
 *   "intro": { "seconds": 25, "music": "every_tunnel:music.elevator" },
 *   "build": "garage:build"
 * }
 * ```
 *
 * `name` (defaults to the id), `dimension` (defaults to the overworld), `tags`,
 * `yaw`, `pitch`, a spawn point's `zone` (the zone what spawns there stays in),
 * `spawn_points`, `ambience` (the level's, and a zone's own, which replaces it inside that zone;
 * `{}` there means silence), `entry`, `intro` (and everything inside them) and `build`
 * (the function that builds the level's blocks, run every time the world opens) are
 * optional; `intro.seconds` defaults to 25. `order` and at least one valid zone are required. A broken zone, spawn
 * point or random sound is skipped with a problem; the level is rejected only when
 * it cannot be used at all.
 */
object LevelParser {

    data class Result(
        /** The parsed level, or null if the file was unusable. */
        val level: LevelDefinition?,
        val problems: List<String>,
    )

    private val ID_PATTERN = Regex("[a-z0-9_]+")
    private val DIMENSION_PATTERN = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
    private val SOUND_PATTERN = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
    private val FUNCTION_PATTERN = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")

    /** Minecraft does not play positioned sounds farther away than this (at volume 1 or less). */
    const val MAX_SOUND_DISTANCE: Double = 16.0

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
        val spawnPoints = parseSpawnPoints(fields, zones, problems)
        val ambience = parseAmbience(fields, problems)
        val entry = parseEntry(fields, problems)
        val intro = parseIntro(fields, problems)
        val build = parseBuild(fields, problems)
        fields.reportUnknownKeys()

        if (!hasOrder || zones.isEmpty()) {
            if (zones.isEmpty()) problems += "zones: the level needs at least one valid zone"
            problems += "level skipped"
            return Result(null, problems.toList())
        }
        return Result(LevelDefinition(levelId, name, order, dimension, zones, spawnPoints, ambience, entry, intro, build), problems.toList())
    }

    private fun parseBuild(fields: JsonFields, problems: MutableList<String>): String? {
        if (!fields.has("build")) {
            fields.stringList("build") // marks the key as read; absent is fine
            return null
        }
        val id = fields.requiredString("build") ?: return null
        if (!FUNCTION_PATTERN.matches(id)) {
            problems += "${fields.pathOf("build")}: \"$id\" is not a function id like garage:build"
            return null
        }
        return id
    }

    private fun parseIntro(fields: JsonFields, problems: MutableList<String>): LevelIntro? {
        if (!fields.has("intro")) {
            fields.stringList("intro") // marks the key as read; absent is fine
            return null
        }
        val intro = fields.child("intro")
        val seconds = intro.double("seconds", 25.0, min = 0.0, max = 600.0)
        val music = if (intro.has("music")) {
            soundId(intro, "music", problems)
        } else {
            intro.stringList("music") // marks the key as read; absent is fine
            null
        }
        intro.reportUnknownKeys()
        return LevelIntro(seconds, music)
    }

    private fun parseEntry(fields: JsonFields, problems: MutableList<String>): LevelEntry? {
        if (!fields.has("entry")) {
            fields.stringList("entry") // marks the key as read; absent is fine
            return null
        }
        val entry = fields.child("entry")
        val title = entry.string("title", "")
        val subtitle = entry.string("subtitle", "")
        val sound = if (entry.has("sound")) {
            soundId(entry, "sound", problems)
        } else {
            entry.stringList("sound") // marks the key as read; absent is fine
            null
        }
        entry.reportUnknownKeys()
        return LevelEntry(title, subtitle, sound)
    }

    private fun parseAmbience(fields: JsonFields, problems: MutableList<String>): Ambience {
        if (!fields.has("ambience")) {
            fields.stringList("ambience") // marks the key as read; absent is fine
            return Ambience.NONE
        }
        val ambience = fields.child("ambience")
        val loop = if (ambience.has("loop")) {
            soundId(ambience, "loop", problems)
        } else {
            ambience.stringList("loop") // marks the key as read; absent is fine
            null
        }
        val loopVolume = ambience.double("loop_volume", 1.0, min = 0.0, max = 1.0)
        val random = mutableListOf<RandomSound>()
        if (ambience.has("random")) {
            ambience.array("random")?.let { array ->
                forEachObject(array, ambience.pathOf("random"), problems) { entry ->
                    val sound = soundId(entry, "sound", problems)
                    val interval = range(entry, "interval", 0.5, 3600.0, problems)
                    val distance = range(entry, "distance", 0.0, MAX_SOUND_DISTANCE, problems)
                    val volume = entry.double("volume", 1.0, min = 0.0, max = 1.0)
                    entry.reportUnknownKeys()
                    if (sound == null || interval == null || distance == null) {
                        problems += "${entry.pathOf("sound")}: random sound skipped"
                    } else {
                        random += RandomSound(sound, interval.first, interval.second, distance.first, distance.second, volume)
                    }
                }
            }
        } else {
            ambience.stringList("random")
        }
        ambience.reportUnknownKeys()
        return Ambience(loop, loopVolume, random)
    }

    private fun soundId(entry: JsonFields, key: String, problems: MutableList<String>): String? {
        val id = entry.requiredString(key) ?: return null
        if (!SOUND_PATTERN.matches(id)) {
            problems += "${entry.pathOf(key)}: \"$id\" is not a sound id like every_tunnel:ambient.drip"
            return null
        }
        return id
    }

    /** `[min, max]` with min <= max inside [lowest, highest]. */
    private fun range(entry: JsonFields, key: String, lowest: Double, highest: Double, problems: MutableList<String>): Pair<Double, Double>? {
        if (!entry.has(key)) {
            entry.array(key)
            problems += "${entry.pathOf(key)}: missing required value [min, max]"
            return null
        }
        val array = entry.array(key) ?: return null
        val values = array.mapNotNull { it.numberOrNull()?.takeIf(Double::isFinite) }
        if (array.size() != 2 || values.size != 2) {
            problems += "${entry.pathOf(key)}: expected [min, max] with 2 numbers but got $array"
            return null
        }
        val (low, high) = values
        if (low > high || low < lowest || high > highest) {
            problems += "${entry.pathOf(key)}: expected $lowest <= min <= max <= $highest but got $array"
            return null
        }
        return low to high
    }

    private fun parseZones(fields: JsonFields, problems: MutableList<String>): List<Zone> {
        val array = fields.array("zones") ?: return emptyList()
        val zones = mutableListOf<Zone>()
        forEachObject(array, fields.pathOf("zones"), problems) { entry ->
            val id = validId(entry, problems) ?: return@forEachObject
            val from = intVec3(entry, "from", problems)
            val to = intVec3(entry, "to", problems)
            val tags = entry.stringList("tags").toSet()
            val ambience = if (entry.has("ambience")) parseAmbience(entry, problems) else null
            entry.reportUnknownKeys()
            when {
                from == null || to == null -> problems += "${entry.pathOf("id")} \"$id\": zone skipped"
                zones.any { it.id == id } -> problems += "${entry.pathOf("id")}: duplicate zone id \"$id\", zone skipped"
                else -> zones += Zone(id, BlockBox.ofCorners(from[0], from[1], from[2], to[0], to[1], to[2]), tags, ambience)
            }
        }
        return zones
    }

    private fun parseSpawnPoints(fields: JsonFields, zones: List<Zone>, problems: MutableList<String>): List<SpawnPoint> {
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
            var zone = if (entry.has("zone")) entry.requiredString("zone") else null.also { entry.stringList("zone") }
            if (zone != null && zones.none { it.id == zone }) {
                problems += "${entry.pathOf("zone")}: no zone \"$zone\" in this level, ignored"
                zone = null
            }
            entry.reportUnknownKeys()
            when {
                pos == null -> problems += "${entry.pathOf("id")} \"$id\": spawn point skipped"
                spawnPoints.any { it.id == id } -> problems += "${entry.pathOf("id")}: duplicate spawn point id \"$id\", skipped"
                else -> spawnPoints += SpawnPoint(id, Position(pos[0], pos[1], pos[2]), yaw, pitch, tags, zone)
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
