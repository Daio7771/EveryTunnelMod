package daio7771.everytunnel.chapter2.level2.core

import com.google.gson.JsonParser
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A cell of the flood's water, in the map's own coordinates (plan cells: x east, z south, y up from the exit hall's floor). */
data class FloodCell(val x: Int, val y: Int, val z: Int)

/** The cells of one stage's water at one height ([y]): a stage rises a layer at a time, bottom up. */
class FloodLayer(val y: Int, val cells: List<FloodCell>)

/**
 * One stage of the flood (`flood.json`, written by `tools/map/chapter2/ch2l2/generate.py`): it starts once, when [trigger]'s zone is reached,
 * waits [delaySeconds], then fills the air of its spaces under [level] over [seconds]. [layers] are only the cells this stage adds to
 * the one before's, bottom up; [gates] are the gates' states while it holds water.
 */
class FloodStage(
    val id: Int,
    val trigger: String,
    val delaySeconds: Double,
    val seconds: Double,
    val level: Int,
    val gates: Map<String, String>,
    val layers: List<FloodLayer>,
) {
    val delayTicks: Int get() = (delaySeconds * 20).roundToInt()
    val totalTicks: Int get() = max(1, (seconds * 20).roundToInt())
    val cellCount: Int get() = layers.sumOf { it.cells.size }

    /** How many of [layers] the water has reached [tick] ticks into the fill: the first at once, the last by the end, evenly between. */
    fun layersDue(tick: Int): Int {
        if (layers.isEmpty() || tick < 0) return 0
        return min(layers.size, (tick.toLong() * layers.size / totalTicks).toInt() + 1)
    }

    /** The cells of the first [layerCount] layers, bottom up. */
    fun cellsOf(layerCount: Int): List<FloodCell> = layers.take(layerCount).flatMap { it.cells }
}

/**
 * The flood of Chapter 2's Level 2 as `flood.json` holds it ([stages] in order; each stage's boxes are cumulative in the file, here a
 * stage only keeps what it adds), and what the level does with it at its start ([resume]). Plain Kotlin; the world's blocks are the
 * platform's business (`Ch2L2FloodRun`).
 */
class Ch2L2Flood(val stages: List<FloodStage>) {

    /** The stage numbered [id] (1 to [stages]), or null. */
    fun stage(id: Int): FloodStage? = stages.firstOrNull { it.id == id }

    /** Every cell any stage fills: what draining has to look at. */
    val allCells: List<FloodCell> by lazy { stages.flatMap { s -> s.layers.flatMap { it.cells } } }

    /** The stage whose [FloodStage.trigger] is [zone], or null. */
    fun stageOf(zone: String): FloodStage? = stages.firstOrNull { it.trigger == zone }

    /**
     * What the level does with the water when it is started (entering, a death, CONTINUE, `reset`) at [state]: [Resume.water] stages of it
     * put at once (0: all drained), and the state the player is brought back to ([Resume.state]). The flood is lived again from the
     * collector (its checkpoint), so any state inside it goes back to the one before; from the station on, the whole flood stays.
     */
    fun resume(state: Ch2L2State): Resume = when {
        state < Ch2L2State.FLOOD_1 -> Resume(0, state)
        state < Ch2L2State.STATION -> Resume(0, Ch2L2State.GUARD_DEAD)
        else -> Resume(stages.size, state)
    }

    class Resume(val water: Int, val state: Ch2L2State)

    class Result(val flood: Ch2L2Flood, val problems: List<String>)

    companion object {
        val EMPTY = Ch2L2Flood(emptyList())

        /** The exit's heavy door opens when it is reached, from the station on only (phase 6.1: it no longer ends the level). */
        fun exitDoorAllowed(state: Ch2L2State): Boolean = state >= Ch2L2State.STATION

        /** The ending (the fade, the card, the menu) follows the reunion, so it is only for a player who has reached it. */
        fun endingAllowed(state: Ch2L2State): Boolean = state >= Ch2L2State.REUNION

        fun parse(text: String): Result {
            val problems = mutableListOf<String>()
            val root = runCatching { JsonParser.parseString(text) }.getOrNull()
            if (root == null || !root.isJsonObject) return Result(EMPTY, listOf("not a JSON object"))
            val array = root.asJsonObject.getAsJsonArray("stages")
            if (array == null) return Result(EMPTY, listOf("no \"stages\""))
            val stages = mutableListOf<FloodStage>()
            var before = HashSet<FloodCell>()
            for ((i, element) in array.withIndex()) {
                val o = element.takeIf { it.isJsonObject }?.asJsonObject
                if (o == null) {
                    problems += "stages[$i]: expected an object"
                    continue
                }
                val water = HashSet<FloodCell>()
                for (b in o.getAsJsonArray("water") ?: emptyList()) {
                    val a = b.asJsonArray.map { it.asInt }
                    if (a.size != 6) {
                        problems += "stages[$i].water: a box needs six numbers"
                        continue
                    }
                    for (x in a[0]..a[3]) for (y in a[1]..a[4]) for (z in a[2]..a[5]) water += FloodCell(x, y, z)
                }
                val added = water.filter { it !in before }
                val layers = added.groupBy { it.y }.toSortedMap().map { (y, cells) -> FloodLayer(y, cells.sortedWith(compareBy({ it.x }, { it.z }))) }
                val gates = o.getAsJsonObject("gates")?.entrySet()?.associate { (k, v) -> k to v.asString } ?: emptyMap()
                stages += FloodStage(
                    o["id"]?.asInt ?: (i + 1), o["trigger"]?.asString ?: "", o["delay_seconds"]?.asDouble ?: 0.0,
                    o["seconds"]?.asDouble ?: 1.0, o["level"]?.asInt ?: 0, gates, layers,
                )
                before = water
            }
            if (stages.map { it.id } != (1..stages.size).toList()) problems += "stages: the ids are not 1, 2, 3, ... in order"
            return Result(Ch2L2Flood(stages), problems)
        }
    }
}
