package daio7771.everytunnel.core.ch2l1

import com.google.gson.JsonArray
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import daio7771.everytunnel.core.level.BlockBox
import daio7771.everytunnel.core.level.Position
import daio7771.everytunnel.util.JsonFields
import daio7771.everytunnel.util.JsonFields.Companion.numberOrNull

/**
 * Parses `anchors.json`. Example:
 *
 * ```json
 * {
 *   "dimension": "minecraft:overworld",
 *   "anchors": [
 *     { "id": "spawn_clockin", "pos": [-48.5, 65, -135.5], "yaw": -90, "pitch": 0, "provisional": true }
 *   ],
 *   "zones": [
 *     { "id": "speaker_lab", "from": [-40, 64, -146], "to": [3, 69, -127], "provisional": true }
 *   ]
 * }
 * ```
 *
 * The shapes are those of a level's file ([daio7771.everytunnel.core.level.LevelParser]): `pos` has three numbers,
 * `from` and `to` three whole numbers, the corners in any order (both included, as in `/fill`). `dimension` defaults
 * to the overworld; `yaw` (-360 to 360), `pitch` (-90 to 90) and `provisional` (marks a place not yet measured on
 * the final map) to 0, 0 and false. Both lists are optional. An anchor id (a-z, 0-9, `_`) is unique across points
 * and zones, since `goto` takes either; a broken or repeated entry is skipped with a problem. The file is rejected
 * only when it is not a JSON object.
 */
object Ch2L1AnchorsParser {

    data class Result(
        /** The parsed anchors, or null if the file was unusable. */
        val anchors: Ch2L1Anchors?,
        val problems: List<String>,
    )

    private val ID_PATTERN = Regex("[a-z0-9_]+")
    private val DIMENSION_PATTERN = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")

    fun parse(text: String): Result {
        val root = try {
            JsonParser.parseString(text)
        } catch (e: JsonParseException) {
            return Result(null, listOf("invalid JSON: ${e.message}"))
        }
        if (!root.isJsonObject) return Result(null, listOf("the file must contain a JSON object"))

        val fields = JsonFields(root.asJsonObject, "")
        val problems = fields.problems

        var dimension = fields.string("dimension", "minecraft:overworld")
        if (!DIMENSION_PATTERN.matches(dimension)) {
            problems += "dimension: \"$dimension\" is not a valid dimension id, using minecraft:overworld"
            dimension = "minecraft:overworld"
        }

        val taken = mutableSetOf<String>()
        val points = parsePoints(fields, taken, problems)
        val zones = parseZones(fields, taken, problems)
        fields.reportUnknownKeys()
        return Result(Ch2L1Anchors(dimension, points, zones), problems.toList())
    }

    private fun parsePoints(fields: JsonFields, taken: MutableSet<String>, problems: MutableList<String>): List<Anchor> {
        val array = fields.array("anchors") ?: return emptyList()
        val points = mutableListOf<Anchor>()
        forEachObject(array, fields.pathOf("anchors"), problems) { entry ->
            val id = validId(entry, taken, problems) ?: return@forEachObject
            val pos = doubleVec3(entry, "pos", problems)
            val yaw = entry.double("yaw", 0.0, min = -360.0, max = 360.0).toFloat()
            val pitch = entry.double("pitch", 0.0, min = -90.0, max = 90.0).toFloat()
            val provisional = entry.boolean("provisional", false)
            entry.reportUnknownKeys()
            if (pos == null) {
                problems += "${entry.pathOf("id")} \"$id\": anchor skipped"
                taken -= id
            } else {
                points += Anchor(id, Position(pos[0], pos[1], pos[2]), yaw, pitch, provisional)
            }
        }
        return points
    }

    private fun parseZones(fields: JsonFields, taken: MutableSet<String>, problems: MutableList<String>): List<AnchorZone> {
        val array = fields.array("zones") ?: return emptyList()
        val zones = mutableListOf<AnchorZone>()
        forEachObject(array, fields.pathOf("zones"), problems) { entry ->
            val id = validId(entry, taken, problems) ?: return@forEachObject
            val from = intVec3(entry, "from", problems)
            val to = intVec3(entry, "to", problems)
            val provisional = entry.boolean("provisional", false)
            entry.reportUnknownKeys()
            if (from == null || to == null) {
                problems += "${entry.pathOf("id")} \"$id\": zone skipped"
                taken -= id
            } else {
                zones += AnchorZone(id, BlockBox.ofCorners(from[0], from[1], from[2], to[0], to[1], to[2]), provisional)
            }
        }
        return zones
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

    /** The entry's id, or null (with a problem) if it is missing, malformed or already taken; a good one is added to [taken]. */
    private fun validId(entry: JsonFields, taken: MutableSet<String>, problems: MutableList<String>): String? {
        val id = entry.requiredString("id") ?: return null
        if (!ID_PATTERN.matches(id)) {
            problems += "${entry.pathOf("id")}: \"$id\" may only contain a-z, 0-9 and _, entry skipped"
            return null
        }
        if (!taken.add(id)) {
            problems += "${entry.pathOf("id")}: duplicate anchor id \"$id\", skipped"
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
