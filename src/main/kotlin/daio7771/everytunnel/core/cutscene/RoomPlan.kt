package daio7771.everytunnel.core.cutscene

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser

/** A cell of the room, from its corner (the room's own coordinates: x east, y up, z south). */
data class Cell(val x: Int, val y: Int, val z: Int)

/** Every cell from [from] to [to], both included, set to [block] (a block id with its states: `every_tunnel:lab_bench[facing=south]`). */
data class Fill(val from: Cell, val to: Cell, val block: String)

/** One cell set to [block]. */
data class Put(val at: Cell, val block: String)

/** A standing place: the floor at ([x], [y], [z]) in the room's coordinates and the way one faces ([yaw], Minecraft's). */
data class Spot(val x: Double, val y: Double, val z: Double, val yaw: Float)

/** A block as the plan names it: `namespace:id` with its states, `namespace:id[facing=south,left=true]`. */
data class BlockSpec(val id: String, val states: Map<String, String>) {

    companion object {
        /** Reads [spec]; fails on a state without a value or a missing bracket, naming it. */
        fun parse(spec: String): BlockSpec {
            val open = spec.indexOf('[')
            if (open < 0) return BlockSpec(spec, emptyMap())
            require(spec.endsWith("]")) { "block '$spec' has no closing bracket" }
            val states = spec.substring(open + 1, spec.length - 1).split(',').filter { it.isNotBlank() }.associate {
                val parts = it.split('=')
                require(parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) { "block '$spec' has a bad state '$it'" }
                parts[0].trim() to parts[1].trim()
            }
            return BlockSpec(spec.substring(0, open), states)
        }
    }
}

/**
 * The prologue's room as `tools/prologue/room.py` writes it into `assets/every_tunnel/cutscene/prologue_room.json`:
 * what to place, in order ([fills] then [puts], each over the one before), the [shell] it is built in (everything
 * in it is cleared when the scene is over) and the [interior] of air in it, the ceiling's [lights] (cells to turn
 * off and on), where the [scientist] stands, where the radiation [counter] sounds (the Geiger clicks) and where the [viewer] (the player, not drawn) is held. Plain Kotlin;
 * `platform.PrologueSceneHooks` places it at its origin in the world.
 */
class RoomPlan(
    val shell: Pair<Cell, Cell>,
    val interior: Pair<Cell, Cell>,
    val fills: List<Fill>,
    val puts: List<Put>,
    val lights: List<Cell>,
    val scientist: Spot,
    val counter: Spot,
    val viewer: Spot,
) {

    /** Is the room's own point ([x], [y], [z]) in the air of the room? */
    fun insideInterior(x: Double, y: Double, z: Double): Boolean {
        val (a, b) = interior
        return x >= a.x && x <= b.x + 1 && y >= a.y && y <= b.y + 1 && z >= a.z && z <= b.z + 1
    }

    companion object {
        const val RESOURCE = "assets/every_tunnel/cutscene/prologue_room.json"

        fun parse(json: String): RoomPlan {
            val root = JsonParser.parseString(json).asJsonObject
            fun cell(e: JsonElement) = e.asJsonArray.let { Cell(it[0].asInt, it[1].asInt, it[2].asInt) }
            fun box(name: String) = root.getAsJsonObject(name).let { cell(it["from"]) to cell(it["to"]) }
            fun spot(name: String) = root.getAsJsonObject(name).let {
                val at: JsonArray = it.getAsJsonArray("at")
                Spot(at[0].asDouble, at[1].asDouble, at[2].asDouble, it["yaw"].asFloat)
            }
            return RoomPlan(
                box("shell"),
                box("interior"),
                root.getAsJsonArray("fills").map { Fill(cell(it.asJsonObject["from"]), cell(it.asJsonObject["to"]), it.asJsonObject["block"].asString) },
                root.getAsJsonArray("blocks").map { Put(cell(it.asJsonObject["at"]), it.asJsonObject["block"].asString) },
                root.getAsJsonArray("lights").map(::cell),
                spot("scientist"),
                spot("counter"),
                spot("viewer"),
            )
        }

        /** The file from the classpath (the mod's own resources). */
        fun load(): RoomPlan {
            val stream = RoomPlan::class.java.classLoader.getResourceAsStream(RESOURCE)
                ?: error("$RESOURCE is missing: run tools/prologue/room.py")
            return stream.use { parse(it.reader().readText()) }
        }
    }
}
