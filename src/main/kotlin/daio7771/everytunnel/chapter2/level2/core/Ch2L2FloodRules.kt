package daio7771.everytunnel.chapter2.level2.core

import daio7771.everytunnel.common.util.JsonFields
/**
 * The flood's tunables (`flood` in `level.json`; the stages' geometry and timing are `flood.json`'s): the pipe that bursts, its jet,
 * the camera's shake at the gate and at the burst, how loud the rumble is at each stage and through the station's door, and the door.
 *
 * - The vestibule's gate slams as the first stage starts; the stage's `delay_seconds` of creaking follow, then the pipe at [pipeAnchor]
 *   bursts: for the stage's seconds a jet of [jetParticles] particles a tick (thrown at [jetSpeed]), afterwards a trickle of
 *   [trickleParticles] (at [trickleSpeed]) for as long as the flood runs; [jetVolume] and [trickleVolume] are their sound's.
 * - [rumbleVolume] is the rumble's volume at stage 1, 2 and 3; through the shut [stationDoor] it is [mutedVolume].
 * - The [stationDoor] shuts once the player is [stationClearBlocks] or more inside it (on the side of the [stationInside] anchor).
 * - At most [cellsPerTick] cells of water are placed a tick.
 */
data class Ch2L2FloodRules(
    val pipeAnchor: String,
    val jetParticles: Int,
    val trickleParticles: Int,
    val jetSpeed: Double,
    val trickleSpeed: Double,
    val shakeGate: Double,
    val shakeBurst: Double,
    val shakeSeconds: Double,
    val rumbleVolume: List<Double>,
    val mutedVolume: Double,
    val jetVolume: Double,
    val trickleVolume: Double,
    val stationDoor: String,
    val stationInside: String,
    val stationClearBlocks: Double,
    val cellsPerTick: Int,
) {
    fun anchorNames(): List<String> = listOf(pipeAnchor, stationInside)

    /** The rumble's volume at [stage] (1 to 3; more than the list has takes its last). */
    fun rumbleAt(stage: Int): Double = rumbleVolume.getOrElse(stage - 1) { rumbleVolume.lastOrNull() ?: 1.0 }

    companion object {
        val DEFAULT = Ch2L2FloodRules(
            pipeAnchor = "pipe_burst", jetParticles = 6, trickleParticles = 2, jetSpeed = 0.32, trickleSpeed = 0.12, shakeGate = 0.5, shakeBurst = 0.4, shakeSeconds = 1.5,
            rumbleVolume = listOf(0.35, 0.6, 0.9), mutedVolume = 0.3, jetVolume = 1.0, trickleVolume = 0.4,
            stationDoor = "station_south", stationInside = "station_door_in", stationClearBlocks = 3.0, cellsPerTick = 500,
        )
    }
}

/**
 * The ending (`ending` in `level.json`), started when the reunion's scene ends (phase 6.1; before, the exit's door did): the screen fades to
 * black over [fadeSeconds], the card ([textKey], a lang key) stays [cardSeconds] and the game goes back to the main menu. [checkpoint] is
 * saved with the level finished: CONTINUE stands at its anchor, in the reunion room.
 */
data class Ch2L2Ending(
    val checkpoint: String,
    val fadeSeconds: Double,
    val cardSeconds: Double,
    val textKey: String,
) {
    companion object {
        val DEFAULT = Ch2L2Ending("chapter_end", 2.0, 4.0, "every_tunnel.ch2.l2.ending.continued")
    }
}

/** Reads the `flood` and `ending` blocks of `level.json`; the problems go to [JsonFields.problems]. */
object Ch2L2FloodParser {

    fun flood(root: JsonFields): Ch2L2FloodRules {
        val d = Ch2L2FloodRules.DEFAULT
        if (!root.has("flood")) return d
        val f = root.child("flood")
        val particles = f.child("particles")
        val shake = f.child("shake")
        val rumble = f.child("rumble")
        val door = f.child("station_door")
        val rules = Ch2L2FloodRules(
            pipeAnchor = f.string("pipe_anchor", d.pipeAnchor),
            jetParticles = particles.int("jet", d.jetParticles, min = 0, max = 40),
            trickleParticles = particles.int("trickle", d.trickleParticles, min = 0, max = 40),
            jetSpeed = particles.double("jet_speed", d.jetSpeed, min = 0.02, max = 2.0),
            trickleSpeed = particles.double("trickle_speed", d.trickleSpeed, min = 0.02, max = 2.0),
            shakeGate = shake.double("gate", d.shakeGate, min = 0.0, max = 3.0),
            shakeBurst = shake.double("burst", d.shakeBurst, min = 0.0, max = 3.0),
            shakeSeconds = shake.double("seconds", d.shakeSeconds, min = 0.2, max = 10.0),
            rumbleVolume = listOf(
                rumble.double("stage_1", d.rumbleAt(1), min = 0.0, max = 1.0),
                rumble.double("stage_2", d.rumbleAt(2), min = 0.0, max = 1.0),
                rumble.double("stage_3", d.rumbleAt(3), min = 0.0, max = 1.0),
            ),
            mutedVolume = rumble.double("muffled", d.mutedVolume, min = 0.0, max = 1.0),
            jetVolume = f.double("jet_volume", d.jetVolume, min = 0.0, max = 1.0),
            trickleVolume = f.double("trickle_volume", d.trickleVolume, min = 0.0, max = 1.0),
            stationDoor = door.string("gate", d.stationDoor),
            stationInside = door.string("inside", d.stationInside),
            stationClearBlocks = door.double("clear_blocks", d.stationClearBlocks, min = 0.0, max = 20.0),
            cellsPerTick = f.int("cells_per_tick", d.cellsPerTick, min = 10, max = 20000),
        )
        particles.reportUnknownKeys()
        shake.reportUnknownKeys()
        rumble.reportUnknownKeys()
        door.reportUnknownKeys()
        f.reportUnknownKeys()
        return rules
    }

    fun ending(root: JsonFields): Ch2L2Ending {
        val d = Ch2L2Ending.DEFAULT
        if (!root.has("ending")) return d
        val f = root.child("ending")
        val ending = Ch2L2Ending(
            checkpoint = f.string("checkpoint", d.checkpoint),
            fadeSeconds = f.double("fade_seconds", d.fadeSeconds, min = 0.1, max = 30.0),
            cardSeconds = f.double("card_seconds", d.cardSeconds, min = 0.5, max = 60.0),
            textKey = f.string("text", d.textKey),
        )
        f.reportUnknownKeys()
        return ending
    }
}
