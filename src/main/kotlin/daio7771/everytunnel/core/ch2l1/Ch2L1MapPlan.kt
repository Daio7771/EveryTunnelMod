package daio7771.everytunnel.core.ch2l1

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import daio7771.everytunnel.core.cutscene.Cell
import daio7771.everytunnel.core.cutscene.Fill

/**
 * Chapter 2's Level 1 as a thing to build: what `tools/map/ch2l1/generate.py` writes into
 * `data/every_tunnel/ch2/l1/map.json` and `platform.Ch2L1Build` puts in the world when the level is needed. Plain Kotlin.
 *
 * The plan's cells are its own (x east, z south, y up from the floor of the air; the floor's blocks are at y = -1);
 * [origin] is the world cell of plan cell 0, 0, 0. [clear] is the box emptied before building (both corners included),
 * [fills] are boxes of one block each, in the order they are placed (a block is a name with its states,
 * `every_tunnel:time_clock[facing=north,unlocked=false]`, as in [daio7771.everytunnel.core.cutscene.BlockSpec]), and
 * [lights] the cells of the ceiling lamps, for turning them down and up. [fingerprint] changes whenever the file does:
 * a world that was built from another plan is built again.
 */
class Ch2L1MapPlan(
    val origin: Cell,
    val clear: Pair<Cell, Cell>,
    val fills: List<Fill>,
    val lights: List<Cell>,
    val fingerprint: Int,
) {

    /** The world's cell for plan cell [x], [y], [z]. */
    fun worldX(x: Int) = origin.x + x
    fun worldY(y: Int) = origin.y + y
    fun worldZ(z: Int) = origin.z + z

    /** How many cells the fills set, counting each cell of each box. */
    fun filledCells(): Long = fills.sumOf { f ->
        (f.to.x - f.from.x + 1).toLong() * (f.to.y - f.from.y + 1) * (f.to.z - f.from.z + 1)
    }

    /** How many cells [clear] covers. */
    fun clearedCells(): Long {
        val (a, b) = clear
        return (b.x - a.x + 1).toLong() * (b.y - a.y + 1) * (b.z - a.z + 1)
    }

    companion object {
        const val RESOURCE = "data/every_tunnel/ch2/l1/map.json"

        fun parse(json: String): Ch2L1MapPlan {
            val root = JsonParser.parseString(json).asJsonObject
            fun cell(e: JsonElement) = e.asJsonArray.let { Cell(it[0].asInt, it[1].asInt, it[2].asInt) }
            val clear = root.getAsJsonObject("clear")
            return Ch2L1MapPlan(
                cell(root["origin"]),
                cell(clear["from"]) to cell(clear["to"]),
                root.getAsJsonArray("fills").map { Fill(cell(it.asJsonObject["from"]), cell(it.asJsonObject["to"]), it.asJsonObject["block"].asString) },
                root.getAsJsonArray("lights").map(::cell),
                json.hashCode(),
            )
        }

        /** The file from the classpath (the mod's own resources). */
        fun load(): Ch2L1MapPlan {
            val stream = Ch2L1MapPlan::class.java.classLoader.getResourceAsStream(RESOURCE)
                ?: error("$RESOURCE is missing: run tools/map/ch2l1/generate.py --install")
            return stream.use { parse(it.reader().readText()) }
        }
    }
}
