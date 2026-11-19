package daio7771.everytunnel.chapter2.level2.core

import daio7771.everytunnel.util.JsonFields

/**
 * Stone Maw's last stand (`maw` in `level.json`; phase 6.1), the arena beyond the exit's heavy door. The creature is phase 4's
 * ([Ch2L2Creature] still sets how it rests, stalks, charges and strikes); these are the fight's own tunables, read by [StoneMawFight],
 * [PipeSupply], [MawDeathScene] and `platform.Ch2L2Maw`.
 *
 * - **The pieces of pipe**: [piecesAtOnce] lie in the arena or in the player's hand at a time; [respawnSeconds] after one is gone (it hit,
 *   or broke on the floor) another falls from one of [pipePoints] (anchors under the ceiling); the first falls [firstPieceOffset] blocks
 *   in front of the player. A throw leaves the hand at [throwSpeed] blocks a tick with [throwGravity] a tick of fall.
 * - **A hit** (any impact of a thrown piece on the creature) makes it orient on the player for [ramOrientSeconds], then charge in a straight
 *   line at [ramSpeed] blocks a second, turning at most [ramTurnDegreesPerSecond] towards the player, until it hits a wall (or after
 *   [ramMaxSeconds]); then it is stunned [stunSeconds]. The [hits]th kills it.
 * - **The death** (phase 6.2): it limps to [centre] at [deathWalkSpeed] (at most [deathWalkSeconds]) and kneels [crouchSeconds] (the
 *   ceiling cracks, its sound first); then [bitCount] small bits of [collapseBlocks] (a mix) fall within [collapseRadius] of the centre
 *   and, [firstSlabDelayTicks] after, a 3 x 3 slab falling at [slabStartSpeed] blocks a tick lands on it at [crushHeight] blocks above the
 *   floor, followed by [slabCount] - 1 more over [slabSpreadTicks] ticks; the mound is at most [maxHeight] high ([MawCollapse]). The scene
 *   lasts [collapseSeconds] from the first bit, then [afterSeconds] of calm.
 * - [arenaZone] (the jump is blocked in it), [middleZone] (reaching it shuts [entryGate] and lets it out of [mouthGate]), [farGate]
 *   (opens when it dies), the [exitDoor]'s gate, zone and sound anchor (the heavy door of the exit room, which opens when reached).
 */
data class Ch2L2Maw(
    val hits: Int,
    val stunSeconds: Double,
    val ramOrientSeconds: Double,
    val ramSpeed: Double,
    val ramTurnDegreesPerSecond: Double,
    val ramMaxSeconds: Double,
    val throwSpeed: Double,
    val throwGravity: Double,
    val piecesAtOnce: Int,
    val respawnSeconds: Double,
    val firstPieceOffset: Double,
    val pipePoints: List<String>,
    val home: String,
    val centre: String,
    val arenaZone: String,
    val middleZone: String,
    val entryGate: String,
    val mouthGate: String,
    val farGate: String,
    val mouthAnchor: String,
    val entryAnchor: String,
    val farAnchor: String,
    val exitGate: String,
    val exitZone: String,
    val exitAnchor: String,
    val deathWalkSpeed: Double,
    val deathWalkSeconds: Double,
    val crouchSeconds: Double,
    val collapseSeconds: Double,
    val afterSeconds: Double,
    val collapseRadius: Int,
    val collapseBlocks: List<String>,
    val bitCount: Int,
    val firstSlabDelayTicks: Int,
    val slabCount: Int,
    val slabSpreadTicks: Int,
    val slabStartSpeed: Double,
    val crushHeight: Double,
    val maxHeight: Int,
    val burstSeconds: Double,
) {
    fun anchorNames(): List<String> = pipePoints + listOf(home, centre, mouthAnchor, entryAnchor, farAnchor, exitAnchor)
    fun zoneNames(): List<String> = listOf(arenaZone, middleZone, exitZone)

    companion object {
        val DEFAULT = Ch2L2Maw(
            hits = 5, stunSeconds = 3.0, ramOrientSeconds = 0.8, ramSpeed = 6.0, ramTurnDegreesPerSecond = 30.0, ramMaxSeconds = 6.0,
            throwSpeed = 1.1, throwGravity = 0.05, piecesAtOnce = 1, respawnSeconds = 2.0, firstPieceOffset = 4.0,
            pipePoints = emptyList(), home = "maw_home", centre = "maw_centre", arenaZone = "zone_maw_arena", middleZone = "zone_maw_middle",
            entryGate = "maw_entry", mouthGate = "maw_pipe_mouth", farGate = "maw_far", mouthAnchor = "maw_mouth", entryAnchor = "maw_entry_door",
            farAnchor = "maw_far_door", exitGate = "exit_hatch", exitZone = "zone_exit_door", exitAnchor = "exit_hatch",
            deathWalkSpeed = 1.6, deathWalkSeconds = 6.0, crouchSeconds = 3.5, collapseSeconds = 3.6, afterSeconds = 2.0,
            collapseRadius = 4, collapseBlocks = listOf("every_tunnel:catacomb_brick_cracked", "every_tunnel:catacomb_brick", "every_tunnel:catacomb_brick_damp"),
            bitCount = 6, firstSlabDelayTicks = 12, slabCount = 5, slabSpreadTicks = 20, slabStartSpeed = 0.35, crushHeight = 2.0, maxHeight = 3, burstSeconds = 2.6,
        )
    }
}

/** Reads the `maw` block of `level.json`; the problems go to [JsonFields.problems]. */
object Ch2L2MawParser {

    fun parse(root: JsonFields): Ch2L2Maw {
        val d = Ch2L2Maw.DEFAULT
        if (!root.has("maw")) return d
        val f = root.child("maw")
        val throwing = f.child("throw")
        val pipes = f.child("pipes")
        val ram = f.child("ram")
        val death = f.child("death")
        val gates = f.child("gates")
        val exit = f.child("exit_door")
        val maw = Ch2L2Maw(
            hits = f.int("hits", d.hits, min = 1, max = 30),
            stunSeconds = f.double("stun_seconds", d.stunSeconds, min = 0.2, max = 30.0),
            ramOrientSeconds = ram.double("orient_seconds", d.ramOrientSeconds, min = 0.05, max = 10.0),
            ramSpeed = ram.double("speed", d.ramSpeed, min = 0.5, max = 12.0),
            ramTurnDegreesPerSecond = ram.double("turn_degrees_per_second", d.ramTurnDegreesPerSecond, min = 0.0, max = 360.0),
            ramMaxSeconds = ram.double("max_seconds", d.ramMaxSeconds, min = 0.5, max = 30.0),
            throwSpeed = throwing.double("speed", d.throwSpeed, min = 0.1, max = 4.0),
            throwGravity = throwing.double("gravity", d.throwGravity, min = 0.0, max = 0.5),
            piecesAtOnce = pipes.int("at_once", d.piecesAtOnce, min = 1, max = 8),
            respawnSeconds = pipes.double("respawn_seconds", d.respawnSeconds, min = 0.0, max = 60.0),
            firstPieceOffset = pipes.double("first_piece_offset", d.firstPieceOffset, min = 0.0, max = 12.0),
            pipePoints = pipes.stringList("points"),
            home = f.string("home", d.home),
            centre = f.string("centre", d.centre),
            arenaZone = f.string("arena_zone", d.arenaZone),
            middleZone = f.string("middle_zone", d.middleZone),
            entryGate = gates.string("entry", d.entryGate),
            mouthGate = gates.string("mouth", d.mouthGate),
            farGate = gates.string("far", d.farGate),
            mouthAnchor = gates.string("mouth_anchor", d.mouthAnchor),
            entryAnchor = gates.string("entry_anchor", d.entryAnchor),
            farAnchor = gates.string("far_anchor", d.farAnchor),
            exitGate = exit.string("gate", d.exitGate),
            exitZone = exit.string("zone", d.exitZone),
            exitAnchor = exit.string("anchor", d.exitAnchor),
            deathWalkSpeed = death.double("walk_speed", d.deathWalkSpeed, min = 0.3, max = 8.0),
            deathWalkSeconds = death.double("walk_seconds", d.deathWalkSeconds, min = 0.5, max = 30.0),
            crouchSeconds = death.double("crouch_seconds", d.crouchSeconds, min = 0.2, max = 30.0),
            collapseSeconds = death.double("collapse_seconds", d.collapseSeconds, min = 0.5, max = 30.0),
            afterSeconds = death.double("after_seconds", d.afterSeconds, min = 0.0, max = 30.0),
            collapseRadius = death.int("collapse_radius", d.collapseRadius, min = 1, max = 12),
            collapseBlocks = death.stringList("collapse_blocks").ifEmpty { d.collapseBlocks },
            bitCount = death.int("bit_count", d.bitCount, min = 0, max = 40),
            firstSlabDelayTicks = death.int("first_slab_delay_ticks", d.firstSlabDelayTicks, min = 1, max = 200),
            slabCount = death.int("slab_count", d.slabCount, min = 1, max = 12),
            slabSpreadTicks = death.int("slab_spread_ticks", d.slabSpreadTicks, min = 0, max = 200),
            slabStartSpeed = death.double("slab_start_speed", d.slabStartSpeed, min = 0.0, max = 2.0),
            crushHeight = death.double("crush_height", d.crushHeight, min = 0.5, max = 4.0),
            maxHeight = death.int("max_height", d.maxHeight, min = 1, max = 5),
            burstSeconds = f.double("burst_seconds", d.burstSeconds, min = 0.2, max = 30.0),
        )
        for (child in listOf(throwing, pipes, ram, death, gates, exit)) child.reportUnknownKeys()
        f.reportUnknownKeys()
        if (maw.pipePoints.size < 3) f.problems += "maw.pipes.points: at least three anchors are needed (the supply never drops on the creature, the player or the last one)"
        return maw
    }
}

/** One line of the reunion: who says it (a skin: `coworker` or `worker_b`), its lang key (subtitle `<text>.<key>`), its sound (if any) and how long it lasts. */
data class Ch2L2ReunionLine(val who: String, val key: String, val sound: String?, val seconds: Double)

/**
 * The reunion with the coworker and worker B in the room beyond the arena (`reunion` in `level.json`; phase 6.1): the player is held at
 * [playerAnchor], the two stand at their anchors, the camera is the player's eyes turning to whoever speaks [lines] (each lasts its
 * seconds; the user's voices, when they come, are the `sound`s). Afterwards the three turn to [leaveAnchor] and the NPCs walk that way
 * for [leaveSeconds]; the scene flies in over [flyInSeconds], turns take [turnSeconds], a [gapSeconds] pause follows each line, and with
 * no lines [silentSeconds] of them just standing there. [textKey] is the subtitles' lang prefix.
 */
data class Ch2L2Reunion(
    val zone: String,
    val playerAnchor: String,
    val coworkerAnchor: String,
    val workerBAnchor: String,
    val leaveAnchor: String,
    val lines: List<Ch2L2ReunionLine>,
    val textKey: String,
    val flyInSeconds: Double,
    val flyOutSeconds: Double,
    val turnSeconds: Double,
    val gapSeconds: Double,
    val silentSeconds: Double,
    val leaveSeconds: Double,
    val walkSpeed: Double,
) {
    fun anchorNames(): List<String> = listOf(playerAnchor, coworkerAnchor, workerBAnchor, leaveAnchor)
    fun zoneNames(): List<String> = listOf(zone)

    companion object {
        val DEFAULT = Ch2L2Reunion(
            zone = "zone_reunion", playerAnchor = "reunion_player", coworkerAnchor = "reunion_coworker", workerBAnchor = "reunion_worker_b",
            leaveAnchor = "reunion_leave", lines = emptyList(), textKey = "every_tunnel.ch2.l2.reunion", flyInSeconds = 1.5, flyOutSeconds = 1.0,
            turnSeconds = 0.7, gapSeconds = 0.5, silentSeconds = 3.0, leaveSeconds = 4.0, walkSpeed = 1.6,
        )
        val SPEAKERS = setOf("coworker", "worker_b")
    }
}

/** Reads the `reunion` block of `level.json`. */
object Ch2L2ReunionParser {

    fun parse(root: JsonFields): Ch2L2Reunion {
        val d = Ch2L2Reunion.DEFAULT
        if (!root.has("reunion")) return d
        val f = root.child("reunion")
        val anchors = f.child("anchors")
        val pace = f.child("pace")
        val lines = (f.array("lines")?.toList() ?: emptyList()).mapIndexedNotNull { i, e ->
            if (!e.isJsonObject) {
                f.problems += "reunion.lines[$i]: expected an object"
                return@mapIndexedNotNull null
            }
            val l = JsonFields(e.asJsonObject, "reunion.lines[$i]", f.problems)
            val who = l.requiredString("who")
            val key = l.requiredString("key")
            val sound = if (l.has("sound")) l.string("sound", "") else null
            val seconds = l.double("seconds", 3.0, min = 0.2, max = 120.0)
            l.reportUnknownKeys()
            if (who != null && who !in Ch2L2Reunion.SPEAKERS) f.problems += "reunion.lines[$i].who: \"$who\" is not one of ${Ch2L2Reunion.SPEAKERS.joinToString()}"
            if (who == null || key == null) null else Ch2L2ReunionLine(who, key, sound, seconds)
        }
        val reunion = Ch2L2Reunion(
            zone = f.string("zone", d.zone),
            playerAnchor = anchors.string("player", d.playerAnchor),
            coworkerAnchor = anchors.string("coworker", d.coworkerAnchor),
            workerBAnchor = anchors.string("worker_b", d.workerBAnchor),
            leaveAnchor = anchors.string("leave", d.leaveAnchor),
            lines = lines,
            textKey = f.string("text", d.textKey),
            flyInSeconds = pace.double("fly_in_seconds", d.flyInSeconds, min = 0.2, max = 10.0),
            flyOutSeconds = pace.double("fly_out_seconds", d.flyOutSeconds, min = 0.2, max = 10.0),
            turnSeconds = pace.double("turn_seconds", d.turnSeconds, min = 0.1, max = 5.0),
            gapSeconds = pace.double("gap_seconds", d.gapSeconds, min = 0.0, max = 5.0),
            silentSeconds = pace.double("silent_seconds", d.silentSeconds, min = 0.0, max = 30.0),
            leaveSeconds = pace.double("leave_seconds", d.leaveSeconds, min = 0.5, max = 30.0),
            walkSpeed = pace.double("walk_speed", d.walkSpeed, min = 0.3, max = 6.0),
        )
        anchors.reportUnknownKeys()
        pace.reportUnknownKeys()
        f.reportUnknownKeys()
        return reunion
    }
}
