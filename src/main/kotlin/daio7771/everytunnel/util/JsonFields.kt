package daio7771.everytunnel.util

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

/**
 * Typed, forgiving reader over a [JsonObject].
 *
 * Instead of throwing, every problem (wrong type, value out of range, unknown key)
 * is appended to [problems] as a readable message that includes the JSON path, and
 * the caller's default is used. Absent keys are recorded in [missingKeys].
 * Readers created with [child] share the same lists, so one parse collects
 * everything in one place.
 *
 * Uses only Gson (no Minecraft classes), so it works in unit tests and does not
 * change between Minecraft versions.
 */
class JsonFields(
    private val obj: JsonObject,
    private val path: String,
    val problems: MutableList<String> = mutableListOf(),
    val missingKeys: MutableList<String> = mutableListOf(),
) {
    private val readKeys = mutableSetOf<String>()

    /** Full JSON path of [key] inside this object, e.g. `stamina.max` or `zones[2].from`. */
    fun pathOf(key: String): String = if (path.isEmpty()) key else "$path.$key"

    /** True if [key] is present and not `null`. Does not mark the key as read. */
    fun has(key: String): Boolean = obj.has(key) && !obj.get(key).isJsonNull

    private fun take(key: String): JsonElement? {
        readKeys += key
        val element = obj.get(key)
        if (element == null || element.isJsonNull) {
            missingKeys += pathOf(key)
            return null
        }
        return element
    }

    fun double(
        key: String,
        default: Double,
        min: Double = -Double.MAX_VALUE,
        max: Double = Double.MAX_VALUE,
    ): Double {
        val element = take(key) ?: return default
        val value = element.numberOrNull()
        if (value == null || !value.isFinite()) {
            problems += "${pathOf(key)}: expected a number but got $element, using default $default"
            return default
        }
        if (value < min || value > max) {
            problems += "${pathOf(key)}: $value is outside the allowed range [$min, $max], using default $default"
            return default
        }
        return value
    }

    fun int(
        key: String,
        default: Int,
        min: Int = Int.MIN_VALUE,
        max: Int = Int.MAX_VALUE,
    ): Int {
        val element = take(key) ?: return default
        val value = element.numberOrNull()
        if (value == null || value != Math.floor(value) || value < Int.MIN_VALUE || value > Int.MAX_VALUE) {
            problems += "${pathOf(key)}: expected a whole number but got $element, using default $default"
            return default
        }
        val intValue = value.toInt()
        if (intValue < min || intValue > max) {
            problems += "${pathOf(key)}: $intValue is outside the allowed range [$min, $max], using default $default"
            return default
        }
        return intValue
    }

    fun string(key: String, default: String): String {
        val element = take(key) ?: return default
        if (element is JsonPrimitive && element.isString) return element.asString
        problems += "${pathOf(key)}: expected a string but got $element, using default \"$default\""
        return default
    }

    /** Reads a required string. Returns null (and records a problem) if missing or not a string. */
    fun requiredString(key: String): String? {
        if (!has(key)) {
            readKeys += key
            problems += "${pathOf(key)}: missing required value"
            return null
        }
        val element = take(key)
        if (element is JsonPrimitive && element.isString) return element.asString
        problems += "${pathOf(key)}: expected a string but got $element"
        return null
    }

    /** Reads an optional list of strings; invalid entries are skipped with a problem. */
    fun stringList(key: String): List<String> {
        if (!has(key)) {
            readKeys += key
            return emptyList()
        }
        val array = array(key) ?: return emptyList()
        val result = mutableListOf<String>()
        array.forEachIndexed { index, element ->
            if (element is JsonPrimitive && element.isString) {
                result += element.asString
            } else {
                problems += "${pathOf(key)}[$index]: expected a string but got $element, ignored"
            }
        }
        return result
    }

    /** Returns the array at [key], or null (with a problem if it is present but not an array). */
    fun array(key: String): JsonArray? {
        val element = take(key) ?: return null
        if (element.isJsonArray) return element.asJsonArray
        problems += "${pathOf(key)}: expected an array but got $element"
        return null
    }

    /**
     * Reader for the nested object at [key]. A missing or invalid object gives a
     * reader over an empty object, so every nested value falls back to its default.
     */
    fun child(key: String): JsonFields {
        val element = take(key)
        val childObject = when {
            element == null -> JsonObject()
            element.isJsonObject -> element.asJsonObject
            else -> {
                problems += "${pathOf(key)}: expected an object but got $element, using defaults"
                JsonObject()
            }
        }
        return JsonFields(childObject, pathOf(key), problems, missingKeys)
    }

    /** Records a problem for every key in this object that was never read (usually a typo). */
    fun reportUnknownKeys() {
        for (key in obj.keySet()) {
            if (key !in readKeys) problems += "${pathOf(key)}: unknown key, ignored (typo?)"
        }
    }

    companion object {
        /** Returns this element as a Double if it is a JSON number, otherwise null. */
        fun JsonElement.numberOrNull(): Double? =
            (this as? JsonPrimitive)?.takeIf { it.isNumber }?.asDouble
    }
}
