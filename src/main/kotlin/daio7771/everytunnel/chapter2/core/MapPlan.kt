package daio7771.everytunnel.chapter2.core

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import daio7771.everytunnel.chapter2.prologue.core.Cell

/**
 * One of Chapter 2's levels as a thing to build: what a map generator under `tools/map/` writes into the level's `map.json`
 * (`tools/map/ch2l1/generate.py` for Level 1, `tools/map/ch2l2/generate.py` for Level 2) and `platform.MapBuild` puts in the
 * world when the level is needed. Plain Kotlin.
 *
 * The plan's cells are its own (x east, z south, y up from the floor of the air; the floor's blocks are at y = -1);
 * [origin] is the world cell of plan cell 0, 0, 0. [clear] is the box emptied before building (both corners included),
 * [fills] are boxes in the order they are placed, each of one block (`every_tunnel:time_clock[facing=north,unlocked=false]`, as in
 * [daio7771.everytunnel.chapter2.prologue.core.BlockSpec]) or of a weighted mix of blocks, one picked for each cell by [pick] from
 * the fill's seed (the generator draws the same with the same function, so its preview is what the game builds). [lights] are the
 * cells of the ceiling lamps, for turning them down and up, [texts] what the plan's `wall_text` blocks say (Level 3's nameplates: a
 * lang key in a style, set on the block's entity once it is built), [messages] what its `wall_message` blocks say (chalk scrawls: a
 * lang key, a line's height in blocks, degrees off level, the chalk's colour), and [gates] the openings whose block changes with the
 * level's story (Level 2's grate: its cells and a block for each of its states; the build puts the `built` one). [fingerprint]
 * changes whenever the file does: a world that was built from another plan is built again.
 */
class MapPlan(
    val origin: Cell,
    val clear: Pair<Cell, Cell>,
    val fills: List<PlanFill>,
    val lights: List<Cell>,
    val fingerprint: Int,
    val texts: List<WallText> = emptyList(),
    val messages: List<WallMessage> = emptyList(),
    val gates: List<Gate> = emptyList(),
) {

    /** A block of a mix and how often it comes, against the others' weights. */
    data class Weighted(val block: String, val weight: Int)

    /** Every cell from [from] to [to], both included, set to one of [mix] (one block, or several picked by [seed]). */
    data class PlanFill(val from: Cell, val to: Cell, val mix: List<Weighted>, val seed: Int = 0) {
        /** The block of a fill of one block (the first of a mix). */
        val block: String get() = mix.first().block

        /** The block this fill puts at plan cell [x], [y], [z]. */
        fun blockAt(x: Int, y: Int, z: Int): String = if (mix.size == 1) mix[0].block else pick(mix, x, y, z, seed)
    }

    /**
     * A `wall_text` block's words: the lang key [text] in [style] (`lab`, `plaque`, `poster`... as the block entity's NBT names them),
     * [offset] blocks to the reader's right (a plate centred over a door two blocks wide).
     */
    data class WallText(val at: Cell, val text: String, val style: String, val offset: Float = 0f)

    /**
     * A `wall_message` block's scrawl: the lang key [text], a line [size] blocks high, [rotation] degrees off level, in [chalk] (`white` on a
     * dark wall, `black` on a white one); always there.
     */
    data class WallMessage(val at: Cell, val text: String, val size: Float, val rotation: Float, val chalk: String)

    /** An opening whose block depends on the story: its [cells], the block of each of its [states] (`minecraft:air` for open), the one [built]. */
    data class Gate(val id: String, val cells: List<Cell>, val states: Map<String, String>, val built: String)

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

    /** Every block the plan may put anywhere: each fill's, each mix's, each gate's states. */
    fun allBlocks(): Set<String> =
        fills.flatMap { f -> f.mix.map { it.block } }.toSet() + gates.flatMap { it.states.values }.toSet()

    fun gate(id: String): Gate? = gates.firstOrNull { it.id == id }

    companion object {
        /**
         * A well-mixed 32-bit number (as a non-negative Long) for a cell and a seed. `tools/map/ch2l2/level.py`'s `cell_hash` is the
         * same function, bit for bit: change one, change both (`MapPlanTest` holds the two to the same values).
         */
        fun cellHash(x: Int, y: Int, z: Int, seed: Int): Long {
            val mask = 0xFFFFFFFFL
            var h = ((x.toLong() * 73856093L) xor (y.toLong() * 19349663L) xor (z.toLong() * 83492791L) xor (seed.toLong() * 2654435761L)) and mask
            h = h xor (h ushr 16)
            h = (h * 0x45D9F3BL) and mask
            h = h xor (h ushr 16)
            h = (h * 0x45D9F3BL) and mask
            h = h xor (h ushr 16)
            return h
        }

        /** The block of a weighted [mix] for a cell, from [cellHash]. */
        fun pick(mix: List<Weighted>, x: Int, y: Int, z: Int, seed: Int): String {
            val total = mix.sumOf { it.weight }
            var r = cellHash(x, y, z, seed) % total
            for (w in mix) {
                if (r < w.weight) return w.block
                r -= w.weight
            }
            return mix.last().block
        }

        fun parse(json: String): MapPlan {
            val root = JsonParser.parseString(json).asJsonObject
            fun cell(e: JsonElement) = e.asJsonArray.let { Cell(it[0].asInt, it[1].asInt, it[2].asInt) }
            val clear = root.getAsJsonObject("clear")
            return MapPlan(
                cell(root["origin"]),
                cell(clear["from"]) to cell(clear["to"]),
                root.getAsJsonArray("fills").map {
                    val o = it.asJsonObject
                    val mix = o.getAsJsonArray("mix")?.map { w -> w.asJsonArray.let { a -> Weighted(a[0].asString, a[1].asInt) } }
                        ?: listOf(Weighted(o["block"].asString, 1))
                    PlanFill(cell(o["from"]), cell(o["to"]), mix, o["seed"]?.asInt ?: 0)
                },
                root.getAsJsonArray("lights").map(::cell),
                json.hashCode(),
                root.getAsJsonArray("texts")?.map {
                    val o = it.asJsonObject
                    WallText(cell(o["at"]), o["text"].asString, o["style"].asString, o["offset"]?.asFloat ?: 0f)
                } ?: emptyList(),
                root.getAsJsonArray("messages")?.map {
                    val o = it.asJsonObject
                    WallMessage(cell(o["at"]), o["text"].asString, o["size"].asFloat, o["rotation"].asFloat, o["chalk"]?.asString ?: "")
                } ?: emptyList(),
                root.getAsJsonArray("gates")?.map {
                    val o = it.asJsonObject
                    Gate(
                        o["id"].asString, o.getAsJsonArray("cells").map(::cell),
                        o.getAsJsonObject("states").entrySet().associate { (k, v) -> k to v.asString }, o["built"].asString,
                    )
                } ?: emptyList(),
            )
        }

        /** The plan at [resource] on the classpath (the mod's own resources). */
        fun load(resource: String): MapPlan {
            val stream = MapPlan::class.java.classLoader.getResourceAsStream(resource)
                ?: error("$resource is missing: run its map generator with --install")
            return stream.use { parse(it.reader().readText()) }
        }
    }
}
