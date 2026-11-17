package daio7771.everytunnel.chapter2.level1.core

import com.google.gson.JsonObject
import daio7771.everytunnel.util.JsonFields

/** One sample tube of task 1: the anchor of its block and the code the terminal's registry shows for it. */
data class Ch2L1TubeSpec(val anchor: String, val code: String)

/**
 * The guide arrow (`client.render.TaskArrow`, [Ch2L1Guide]): whether it shows, how high over the anchor it floats ([height], blocks)
 * and how near the player can come before it hides itself ([hideWithin], blocks along the floor).
 */
data class Ch2L1GuideSpec(val enabled: Boolean, val height: Double, val hideWithin: Double)

/**
 * The three tasks of Act 1, from `tasks.json`: which anchors hold what, and how long each takes. Plain Kotlin.
 *
 * ```json
 * {
 *   "task1": { "tubes": [ { "anchor": "task1_tube_1", "code": "S-0412" } ], "terminal": "task1_terminal_block", "register_seconds": 1.2 },
 *   "task2": { "analyzer": "task2_analyzer_block", "calibrate_seconds": 4.0 },
 *   "task3": { "broom": "task3_broom_block", "dirt": "task3_dirt_spots", "sweep_seconds": 1.5 },
 *   "guide": { "enabled": true, "height": 1.1, "hide_within": 1.5 }
 * }
 * ```
 *
 * Every key is optional (the defaults are the above, three tubes S-0412 to S-0414). The tubes and the dirt are a bit each in
 * the player's progress, so there are at most [MAX_TUBES] tubes and [MAX_DIRT] spots. An anchor named here that
 * `anchors.json` does not have makes its task impossible, which `/et ch2 l1 tasks` reports.
 */
/** A reagent bottle of the reagents' task: its block's anchor on the cabinet and its colour (`blue`, `yellow`, `red`: its block and item). */
data class Ch2L1BottleSpec(val anchor: String, val colour: String)

/**
 * The LAB's two tasks of phase 10b. The reagents: the [bottles] on their cabinet, the mixing tank ([tankAnchor]) they are poured into, a
 * click held [pourSeconds] for each, in any order. The gauge: the centrifuge ([centrifugeAnchor]) shows a speed, drawn for each player
 * between [minRpm] and [maxRpm] in steps of ten ([Ch2L1Gauge]), to be typed at the terminal beside it ([gaugeTerminalAnchor]).
 */
data class Ch2L1LabTasksSpec(
    val bottles: List<Ch2L1BottleSpec> = listOf(
        Ch2L1BottleSpec("reagent_blue_block", "blue"), Ch2L1BottleSpec("reagent_yellow_block", "yellow"),
        Ch2L1BottleSpec("reagent_red_block", "red"),
    ),
    val tankAnchor: String = "mixing_tank_block",
    val pourSeconds: Double = 2.0,
    val centrifugeAnchor: String = "centrifuge_block",
    val gaugeTerminalAnchor: String = "gauge_terminal_block",
    val minRpm: Int = 1200,
    val maxRpm: Int = 4800,
) {
    val pourTicks: Int get() = (pourSeconds * Ch2L1Tasks.TICKS_PER_SECOND).toInt().coerceAtLeast(1)

    companion object {
        val COLOURS = listOf("blue", "yellow", "red")
    }
}

/** The gauge's speed: drawn once a game for each player, and what counts as typing it. Plain Kotlin, unit tested. */
object Ch2L1Gauge {

    /** A speed between [spec]'s bounds, a multiple of ten, from [roll] (a random int, any value). */
    fun reading(spec: Ch2L1LabTasksSpec, roll: Int): Int {
        val steps = (spec.maxRpm - spec.minRpm) / 10 + 1
        return spec.minRpm + Math.floorMod(roll, steps) * 10
    }

    /** True if [typed] (its digits only) is [rpm]. */
    fun accepts(typed: String, rpm: Int): Boolean = rpm > 0 && typed.filter(Char::isDigit).trimStart('0') == rpm.toString()

    /** The longest reading the terminal takes. */
    const val MAX_DIGITS = 5
}

data class Ch2L1Tasks(
    val tubes: List<Ch2L1TubeSpec>,
    val terminalAnchor: String,
    val registerSeconds: Double,
    val analyzerAnchor: String,
    val calibrateSeconds: Double,
    val broomAnchor: String,
    val dirtPath: String,
    val sweepSeconds: Double,
    val guide: Ch2L1GuideSpec,
    /** The overtime's task (Rowan's three hours, after the sealed door): its broom and its dirt, in the basement; swept as task 3's is. */
    val basementBroomAnchor: String = "basement_broom_block",
    val basementDirtPath: String = "basement_dirt_spots",
    val lab: Ch2L1LabTasksSpec = Ch2L1LabTasksSpec(),
) {
    /** Ticks the registry's bar takes for one sample. */
    val registerTicks: Int get() = (registerSeconds * TICKS_PER_SECOND).toInt().coerceAtLeast(1)

    val calibrateTicks: Int get() = (calibrateSeconds * TICKS_PER_SECOND).toInt().coerceAtLeast(1)

    val sweepTicks: Int get() = (sweepSeconds * TICKS_PER_SECOND).toInt().coerceAtLeast(1)

    companion object {
        const val MAX_TUBES = 8
        const val MAX_DIRT = 30
        const val TICKS_PER_SECOND = 20

        val DEFAULT: Ch2L1Tasks = Ch2L1TasksParser.parse(JsonObject()).tasks

        /** The ids of the player's progress counters (`Ch2Progress.ch2L1Counter`). */
        const val TUBES = "tubes"
        const val REGISTERED = "registered"
        const val SWEPT = "swept"
        const val BROOM = "broom"
        const val BASEMENT_SWEPT = "basement_swept"
        const val BASEMENT_BROOM = "basement_broom"
        /** The reagents' task: the bottles taken and the bottles poured (bits, in [Ch2L1LabTasksSpec.bottles]' order). */
        const val REAGENTS_TAKEN = "reagents_taken"
        const val REAGENTS_POURED = "reagents_poured"
        /** The gauge's task: the speed the centrifuge shows this player (0: not drawn yet). */
        const val GAUGE_RPM = "gauge_rpm"
    }
}

object Ch2L1TasksParser {

    data class Result(val tasks: Ch2L1Tasks, val problems: List<String>)

    fun parse(root: JsonObject): Result {
        val fields = JsonFields(root, "")
        val one = fields.child("task1")
        val two = fields.child("task2")
        val three = fields.child("task3")
        val overtime = fields.child("overtime")
        val reagents = fields.child("reagents")
        val gauge = fields.child("gauge")
        val guide = fields.child("guide")

        val tubes = parseTubes(one, fields.problems)
        val tasks = Ch2L1Tasks(
            tubes = tubes,
            terminalAnchor = one.string("terminal", "task1_terminal_block"),
            registerSeconds = one.double("register_seconds", 1.2, min = 0.1, max = 30.0),
            analyzerAnchor = two.string("analyzer", "task2_analyzer_block"),
            calibrateSeconds = two.double("calibrate_seconds", 4.0, min = 0.5, max = 60.0),
            broomAnchor = three.string("broom", "task3_broom_block"),
            dirtPath = three.string("dirt", "task3_dirt_spots"),
            sweepSeconds = three.double("sweep_seconds", 1.5, min = 0.2, max = 30.0),
            guide = Ch2L1GuideSpec(
                enabled = guide.boolean("enabled", true),
                height = guide.double("height", 1.1, min = 0.0, max = 10.0),
                hideWithin = guide.double("hide_within", 1.5, min = 0.0, max = 50.0),
            ),
            basementBroomAnchor = overtime.string("broom", "basement_broom_block"),
            basementDirtPath = overtime.string("dirt", "basement_dirt_spots"),
            lab = parseLab(reagents, gauge, fields.problems),
        )
        overtime.reportUnknownKeys()
        reagents.reportUnknownKeys()
        gauge.reportUnknownKeys()
        guide.reportUnknownKeys()
        one.reportUnknownKeys()
        two.reportUnknownKeys()
        three.reportUnknownKeys()
        fields.reportUnknownKeys()
        return Result(tasks, fields.problems.toList())
    }

    private fun parseLab(reagents: JsonFields, gauge: JsonFields, problems: MutableList<String>): Ch2L1LabTasksSpec {
        val d = Ch2L1LabTasksSpec()
        val bottles = reagents.array("bottles")?.mapIndexedNotNull { i, element ->
            val path = "${reagents.pathOf("bottles")}[$i]"
            if (!element.isJsonObject) {
                problems += "$path: expected an object, skipped"
                return@mapIndexedNotNull null
            }
            val entry = JsonFields(element.asJsonObject, path, problems)
            val anchor = entry.requiredString("anchor")
            val colour = entry.string("colour", "")
            entry.reportUnknownKeys()
            if (colour !in Ch2L1LabTasksSpec.COLOURS) {
                problems += "$path.colour: \"$colour\" is not one of ${Ch2L1LabTasksSpec.COLOURS}"
                null
            } else {
                anchor?.let { Ch2L1BottleSpec(it, colour) }
            }
        }?.ifEmpty { null } ?: d.bottles
        val min = gauge.int("min_rpm", d.minRpm, min = 10, max = 99_990)
        val max = gauge.int("max_rpm", d.maxRpm, min = 10, max = 99_990)
        if (max < min) problems += "${gauge.pathOf("max_rpm")}: under min_rpm"
        return Ch2L1LabTasksSpec(
            bottles = bottles.take(8),
            tankAnchor = reagents.string("tank", d.tankAnchor),
            pourSeconds = reagents.double("pour_seconds", d.pourSeconds, min = 0.2, max = 30.0),
            centrifugeAnchor = gauge.string("centrifuge", d.centrifugeAnchor),
            gaugeTerminalAnchor = gauge.string("terminal", d.gaugeTerminalAnchor),
            minRpm = min,
            maxRpm = maxOf(min, max),
        )
    }

    private fun defaultTubes() = (1..3).map { Ch2L1TubeSpec("task1_tube_$it", "S-%04d".format(411 + it)) }

    private fun parseTubes(one: JsonFields, problems: MutableList<String>): List<Ch2L1TubeSpec> {
        val array = one.array("tubes") ?: return defaultTubes()
        val tubes = mutableListOf<Ch2L1TubeSpec>()
        array.forEachIndexed { i, element ->
            if (!element.isJsonObject) {
                problems += "${one.pathOf("tubes")}[$i]: expected an object, skipped"
                return@forEachIndexed
            }
            val entry = JsonFields(element.asJsonObject, "${one.pathOf("tubes")}[$i]", problems)
            val anchor = entry.requiredString("anchor")
            val code = entry.string("code", "S-%04d".format(412 + i))
            entry.reportUnknownKeys()
            if (anchor != null) tubes += Ch2L1TubeSpec(anchor, code)
        }
        if (tubes.size > Ch2L1Tasks.MAX_TUBES) {
            problems += "${one.pathOf("tubes")}: at most ${Ch2L1Tasks.MAX_TUBES} tubes, the rest are ignored"
        }
        return tubes.take(Ch2L1Tasks.MAX_TUBES).ifEmpty {
            problems += "${one.pathOf("tubes")}: no usable tube, using the default three"
            defaultTubes()
        }
    }
}

/** Sets of up to 30 things as the bits of an Int: which tubes are taken, which spots are swept. */
object Ch2L1Bits {
    fun has(mask: Int, index: Int): Boolean = mask and (1 shl index) != 0

    fun with(mask: Int, index: Int): Int = mask or (1 shl index)

    fun count(mask: Int): Int = Integer.bitCount(mask)

    /** The mask with the first [n] things in it. */
    fun first(n: Int): Int = if (n >= 31) Int.MAX_VALUE else (1 shl n) - 1
}

/**
 * How long a player has held their use on one target: the game repeats a held right-click every 4 ticks, so a use
 * within [maxGapTicks] of the last on the same target goes on holding, and anything else starts again. Plain
 * Kotlin: the caller passes the server's tick.
 */
class HoldTracker(private val maxGapTicks: Int = 8) {

    private var target: String? = null
    private var last = 0L
    private var held = 0L

    /** A use of [on] at tick [now]; the ticks it has been held, 0 at the first use. */
    fun press(on: String, now: Long): Int {
        if (on != target || now - last > maxGapTicks) {
            target = on
            held = 0
        } else {
            held += now - last
        }
        last = now
        return held.toInt()
    }

    fun reset() {
        target = null
        held = 0
    }
}
