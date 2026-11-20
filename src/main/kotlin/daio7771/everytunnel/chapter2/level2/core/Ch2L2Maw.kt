package daio7771.everytunnel.chapter2.level2.core

import daio7771.everytunnel.util.JsonFields

/** What Stone Maw's ram is like with [Ch2L2Maw.stage]'s number of pillars broken: faster and sharper with each, and the stun shorter. */
data class MawStage(val ramSpeed: Double, val turnDegreesPerSecond: Double, val orientSeconds: Double, val stunSeconds: Double)

/**
 * The ceiling pieces that fall round a pillar that breaks ([count] of them over [spreadTicks] ticks within [radius] blocks of it): each is
 * announced on the floor [warnTicks] before it lands (dust, a small crumble) and hurts the player under it for [damageFraction] of their health.
 */
data class MawDebris(val count: Int, val radius: Double, val spreadTicks: Int, val warnTicks: Int, val damageFraction: Double)

/**
 * The escape after the last pillar: the arena caves in for [seconds]; [debrisPerSecond] pieces a second fall over it (fewer within
 * [doorClearRadius] of the far door); whoever is in [zone] when the time is up dies. [anchor] is where CONTINUE puts the player.
 */
data class MawEscapeRules(val seconds: Double, val debrisPerSecond: Double, val doorClearRadius: Double, val anchor: String, val zone: String)

/**
 * Stone Maw's last stand (`maw` in `level.json`; phase 6.1, remade in 9), the arena beyond the exit's heavy door. The creature is phase 4's
 * ([Ch2L2Creature] still sets how it rests, stalks, charges and strikes); these are the fight's own tunables, read by [StoneMawFight],
 * [PipeSupply], [MawEscape], [MawCollapse] and `platform.Ch2L2Maw`.
 *
 * - **The pieces of pipe**: [piecesAtOnce] lie in the arena or in the player's hand at a time; [respawnSeconds] after one is gone (it hit,
 *   or broke on the floor) another falls from one of [pipePoints] (anchors under the ceiling); the first falls [firstPieceOffset] blocks
 *   in front of the player. A throw leaves the hand at [throwSpeed] blocks a tick with [throwGravity] a tick of fall.
 * - **A hit** (any impact of a thrown piece on the creature) makes it orient on the player, then charge in a straight line, until it hits
 *   something solid (or after [ramMaxSeconds]); then it is stunned. It does the same **on its own** when it hears something and a player is
 *   in the arena, at most once every [autoRamCooldownSeconds] after the last stun (the pipes only annoy it; hiding behind a pillar is the game). How fast, how sharp and for how long is the [stage] it is at: the number of
 *   pillars it has broken ([stages]).
 * - **The pillars** ([pillars]: anchors at their centres): a charge that ends against one damages it; [pillarHits] charges break it
 *   ([pillarHitRadius]: how close to a pillar's face the stop must be to count). Breaking one drops [debris]; breaking the last brings the
 *   ceiling down on the creature ([MawCollapse], from `death`: [bitCount] small bits of [collapseBlocks] (a mix) within [collapseRadius] of it
 *   and, [firstSlabDelayTicks] after, a 3 x 3 slab falling at [slabStartSpeed] blocks a tick, lands on it at [crushHeight] blocks above
 *   the floor, followed by [slabCount] - 1 more over [slabSpreadTicks] ticks; the mound is at most [maxHeight] high) and starts the [escape].
 * - [arenaZone] (the jump is blocked in it), [middleZone] (reaching it shuts [entryGate] and lets it out of [mouthGate]), [farGate]
 *   (opens when it dies), the [exitDoor]'s gate, zone and sound anchor (the heavy door of the exit room, which opens when reached).
 */
data class Ch2L2Maw(
    val stages: List<MawStage>,
    val ramMaxSeconds: Double,
    val autoRamCooldownSeconds: Double,
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
    val pillars: List<String>,
    val pillarHits: Int,
    val pillarHitRadius: Double,
    val debris: MawDebris,
    val escape: MawEscapeRules,
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
    fun anchorNames(): List<String> = pipePoints + pillars + listOf(home, centre, mouthAnchor, entryAnchor, farAnchor, exitAnchor, escape.anchor)
    fun zoneNames(): List<String> = listOf(arenaZone, middleZone, exitZone, escape.zone)

    /** The ram's numbers with [broken] pillars down (the last stage holds past the end of the list). */
    fun stage(broken: Int): MawStage = stages[broken.coerceIn(0, stages.lastIndex)]

    companion object {
        val DEFAULT_STAGES = listOf(MawStage(6.0, 30.0, 0.8, 3.0), MawStage(6.8, 40.0, 0.7, 2.6), MawStage(7.6, 50.0, 0.6, 2.2))

        val DEFAULT = Ch2L2Maw(
            stages = DEFAULT_STAGES, ramMaxSeconds = 6.0, autoRamCooldownSeconds = 4.0,
            throwSpeed = 1.1, throwGravity = 0.05, piecesAtOnce = 1, respawnSeconds = 2.0, firstPieceOffset = 4.0,
            pipePoints = emptyList(), home = "maw_home", centre = "maw_centre", arenaZone = "zone_maw_arena", middleZone = "zone_maw_middle",
            entryGate = "maw_entry", mouthGate = "maw_pipe_mouth", farGate = "maw_far", mouthAnchor = "maw_mouth", entryAnchor = "maw_entry_door",
            farAnchor = "maw_far_door", exitGate = "exit_hatch", exitZone = "zone_exit_door", exitAnchor = "exit_hatch",
            pillars = listOf("maw_pillar_1", "maw_pillar_2", "maw_pillar_3"), pillarHits = 2, pillarHitRadius = 1.6,
            debris = MawDebris(6, 4.0, 40, 20, 0.25),
            escape = MawEscapeRules(8.0, 3.0, 4.0, "maw_escape", "zone_maw_escape"),
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
        val pillars = f.child("pillars")
        val debris = f.child("debris")
        val escape = f.child("escape")
        val stages = (f.array("stages")?.toList() ?: emptyList()).mapIndexedNotNull { i, e ->
            if (!e.isJsonObject) {
                f.problems += "maw.stages[$i]: expected an object"
                return@mapIndexedNotNull null
            }
            val s = JsonFields(e.asJsonObject, "maw.stages[$i]", f.problems)
            val base = d.stage(i)
            val stage = MawStage(
                ramSpeed = s.double("ram_speed", base.ramSpeed, min = 0.5, max = 12.0),
                turnDegreesPerSecond = s.double("turn_degrees_per_second", base.turnDegreesPerSecond, min = 0.0, max = 360.0),
                orientSeconds = s.double("orient_seconds", base.orientSeconds, min = 0.05, max = 10.0),
                stunSeconds = s.double("stun_seconds", base.stunSeconds, min = 0.2, max = 30.0),
            )
            s.reportUnknownKeys()
            stage
        }.ifEmpty { d.stages }
        val maw = Ch2L2Maw(
            stages = stages,
            ramMaxSeconds = ram.double("max_seconds", d.ramMaxSeconds, min = 0.5, max = 30.0),
            autoRamCooldownSeconds = ram.double("auto_cooldown_seconds", d.autoRamCooldownSeconds, min = 0.0, max = 60.0),
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
            pillars = pillars.stringList("anchors"),
            pillarHits = pillars.int("hits", d.pillarHits, min = 1, max = 10),
            pillarHitRadius = pillars.double("hit_radius", d.pillarHitRadius, min = 0.5, max = 6.0),
            debris = MawDebris(
                count = debris.int("count", d.debris.count, min = 0, max = 40),
                radius = debris.double("radius", d.debris.radius, min = 1.0, max = 12.0),
                spreadTicks = debris.int("spread_ticks", d.debris.spreadTicks, min = 1, max = 400),
                warnTicks = debris.int("warn_ticks", d.debris.warnTicks, min = 1, max = 100),
                damageFraction = debris.double("damage_fraction", d.debris.damageFraction, min = 0.0, max = 1.0),
            ),
            escape = MawEscapeRules(
                seconds = escape.double("seconds", d.escape.seconds, min = 2.0, max = 60.0),
                debrisPerSecond = escape.double("debris_per_second", d.escape.debrisPerSecond, min = 0.0, max = 20.0),
                doorClearRadius = escape.double("door_clear_radius", d.escape.doorClearRadius, min = 0.0, max = 20.0),
                anchor = escape.string("anchor", d.escape.anchor),
                zone = escape.string("zone", d.escape.zone),
            ),
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
        for (child in listOf(throwing, pipes, ram, death, gates, exit, pillars, debris, escape)) child.reportUnknownKeys()
        f.reportUnknownKeys()
        if (maw.pipePoints.size < 3) f.problems += "maw.pipes.points: at least three anchors are needed (the supply never drops on the creature, the player or the last one)"
        if (maw.pillars.isEmpty()) f.problems += "maw.pillars.anchors: at least one pillar is needed (the fight ends when the last one breaks)"
        return maw
    }
}

/** One line of the reunion: who says it (a skin: `coworker` or `worker_b`), its lang key (subtitle `<text>.<key>`), its sound (if any) and how long it lasts. */
data class Ch2L2ReunionLine(val who: String, val key: String, val sound: String?, val seconds: Double)

/**
 * What happens after the reunion's last line (phase 9): the two lead the player to the service lift. They walk [waypoints] (anchors, one at
 * each turn of the corridor) at [speed] blocks a second and wait, looking back, when the player is more than [waitDistance] behind; the
 * coworker opens [doorGate] (Level 3's door, its sound at [doorAnchor]) as they set off; they go into the lift's cab ([coworkerAnchor],
 * [workerBAnchor]) and face its gate. When the player is in [zone], [gate] shuts (its sound at [gateAnchor]); after [gatePauseSeconds] the
 * motor runs for [motorSeconds] (the shake) and the ending starts.
 */
data class Ch2L2Lead(
    val speed: Double,
    val waitDistance: Double,
    val waypoints: List<String>,
    val doorGate: String,
    val doorAnchor: String,
    val coworkerAnchor: String,
    val workerBAnchor: String,
    val zone: String,
    val gate: String,
    val gateAnchor: String,
    val gatePauseSeconds: Double,
    val motorSeconds: Double,
) {
    fun anchorNames(): List<String> = waypoints + listOf(doorAnchor, coworkerAnchor, workerBAnchor, gateAnchor)
    fun zoneNames(): List<String> = listOf(zone)

    companion object {
        val DEFAULT = Ch2L2Lead(
            speed = 2.2, waitDistance = 6.0, waypoints = listOf("lead_1", "lead_2", "lead_3"), doorGate = "l3_door", doorAnchor = "reunion_leave",
            coworkerAnchor = "lift_coworker", workerBAnchor = "lift_worker_b", zone = "zone_lift", gate = "lift_gate", gateAnchor = "lift_gate",
            gatePauseSeconds = 1.5, motorSeconds = 3.0,
        )
    }
}

/**
 * The reunion with the coworker and worker B in the room beyond the arena (`reunion` in `level.json`; phase 6.1): the player is held at
 * [playerAnchor], the two stand at their anchors, the camera is the player's eyes turning to whoever speaks [lines] (each lasts its
 * seconds; the user's voices, when they come, are the `sound`s). The scene ends after the last line and the player has control again with
 * the NPCs setting off ([lead]); the scene flies in over [flyInSeconds], turns take [turnSeconds], a [gapSeconds] pause follows each line,
 * and with no lines [silentSeconds] of them just standing there. [leaveAnchor] is the door the listener glances at. [textKey] is the
 * subtitles' lang prefix.
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
    val lead: Ch2L2Lead = Ch2L2Lead.DEFAULT,
) {
    fun anchorNames(): List<String> = listOf(playerAnchor, coworkerAnchor, workerBAnchor, leaveAnchor) + lead.anchorNames()
    fun zoneNames(): List<String> = listOf(zone) + lead.zoneNames()

    companion object {
        val DEFAULT = Ch2L2Reunion(
            zone = "zone_reunion", playerAnchor = "reunion_player", coworkerAnchor = "reunion_coworker", workerBAnchor = "reunion_worker_b",
            leaveAnchor = "reunion_leave", lines = emptyList(), textKey = "every_tunnel.ch2.l2.reunion", flyInSeconds = 1.5, flyOutSeconds = 1.0,
            turnSeconds = 0.7, gapSeconds = 0.5, silentSeconds = 3.0,
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
        val leadFields = f.child("lead")
        val leadDoor = leadFields.child("door")
        val leadLift = leadFields.child("lift")
        val ld = d.lead
        val lead = Ch2L2Lead(
            speed = leadFields.double("speed", ld.speed, min = 0.3, max = 6.0),
            waitDistance = leadFields.double("wait_distance", ld.waitDistance, min = 2.0, max = 30.0),
            waypoints = if (leadFields.has("waypoints")) leadFields.stringList("waypoints") else ld.waypoints,
            doorGate = leadDoor.string("gate", ld.doorGate),
            doorAnchor = leadDoor.string("anchor", ld.doorAnchor),
            coworkerAnchor = leadLift.string("coworker", ld.coworkerAnchor),
            workerBAnchor = leadLift.string("worker_b", ld.workerBAnchor),
            zone = leadLift.string("zone", ld.zone),
            gate = leadLift.string("gate", ld.gate),
            gateAnchor = leadLift.string("gate_anchor", ld.gateAnchor),
            gatePauseSeconds = leadLift.double("gate_pause_seconds", ld.gatePauseSeconds, min = 0.0, max = 20.0),
            motorSeconds = leadLift.double("motor_seconds", ld.motorSeconds, min = 0.5, max = 30.0),
        )
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
            lead = lead,
        )
        for (child in listOf(leadDoor, leadLift, leadFields)) child.reportUnknownKeys()
        anchors.reportUnknownKeys()
        pace.reportUnknownKeys()
        f.reportUnknownKeys()
        return reunion
    }
}
