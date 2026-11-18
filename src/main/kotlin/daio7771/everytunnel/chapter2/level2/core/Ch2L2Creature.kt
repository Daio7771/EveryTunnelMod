package daio7771.everytunnel.chapter2.level2.core

import daio7771.everytunnel.util.JsonFields

/**
 * The pump's cycle (`creature.pump` in `level.json`): [runSeconds] running, the last [slowSeconds] of it slowing down, then [silentSeconds]
 * of silence. The pump sounds at [anchor]; every cell of [beacons] holds an amber beacon, all lit together.
 */
data class Ch2L2PumpConfig(
    /** Where the pump sounds (the main beacon's block cell). */
    val anchor: String,
    val beacons: List<String>,
    val runSeconds: Double,
    val silentSeconds: Double,
    val slowSeconds: Double,
) {
    val cycleSeconds: Double get() = runSeconds + silentSeconds
}

/** The pipe that falls in the first silence (`creature.pipe`): released over [from] and [delaySeconds] into the silence. */
data class Ch2L2PipeConfig(val from: String, val delaySeconds: Double)

/**
 * The vibration creature and the pump it hides behind (`creature` in `level.json`; phases 4 and 4.1). It stays in the pump room and
 * feels what is stepped on within [floorZones] (the anchors' zones that carry vibrations: not the overlook, which is a raised, caged block).
 *
 * - A soft step (sneaking) makes it stalk at [stalkSpeed] and listen [stalkListenSeconds] where it ends.
 * - Any other takes [orientSeconds] to turn to its point, then a charge there at [chargeSpeed] ([chargeSpeedLoud] for a run, jump or landing).
 * - A blow takes [windupSeconds] to land (the escape) and hurts the player within [contactRange] of its centre then, for [damageFraction]
 *   of their maximum health, at most once every [hitIntervalSeconds]. Touching a player ([touchRange]) also starts a blow.
 * - Afterwards [recoverSeconds] still, then a search: [searchPoints] points [searchRadius] round where it struck, walked at [searchSpeed],
 *   [searchListenSeconds] at each; then home at [walkSpeed].
 *
 * Speeds are blocks per second. A step within [graceAfterStopSeconds] of the pump stopping is still masked.
 */
data class Ch2L2Creature(
    val home: String,
    val floorZones: List<String>,
    val orientSeconds: Double,
    val windupSeconds: Double,
    val touchRange: Double,
    val contactRange: Double,
    val damageFraction: Double,
    val hitIntervalSeconds: Double,
    val recoverSeconds: Double,
    val stalkListenSeconds: Double,
    val searchPoints: Int,
    val searchRadius: Double,
    val searchListenSeconds: Double,
    val stalkSpeed: Double,
    val searchSpeed: Double,
    val walkSpeed: Double,
    val chargeSpeed: Double,
    val chargeSpeedLoud: Double,
    val graceAfterStopSeconds: Double,
    val pump: Ch2L2PumpConfig,
    val pipe: Ch2L2PipeConfig,
) {
    fun anchorNames(): List<String> = listOf(home, pump.anchor, pipe.from) + pump.beacons
    fun zoneNames(): List<String> = floorZones

    companion object {
        val DEFAULT = Ch2L2Creature(
            home = "creature_home", floorZones = emptyList(), orientSeconds = 1.0, windupSeconds = 0.9, touchRange = 1.6,
            contactRange = 2.0, damageFraction = 0.5, hitIntervalSeconds = 2.0, recoverSeconds = 1.5, stalkListenSeconds = 1.5,
            searchPoints = 3, searchRadius = 4.0, searchListenSeconds = 1.2,
            stalkSpeed = 1.3, searchSpeed = 1.6, walkSpeed = 2.0, chargeSpeed = 4.6, chargeSpeedLoud = 5.2,
            graceAfterStopSeconds = 0.25,
            pump = Ch2L2PumpConfig("pump_beacon", emptyList(), runSeconds = 6.0, silentSeconds = 6.0, slowSeconds = 1.0),
            pipe = Ch2L2PipeConfig("pipe_drop", delaySeconds = 0.0),
        )
    }
}

/** Reads the `creature` block of `level.json`; the problems go to [JsonFields.problems]. */
object Ch2L2CreatureParser {

    fun parse(root: JsonFields): Ch2L2Creature {
        val d = Ch2L2Creature.DEFAULT
        if (!root.has("creature")) return d
        val f = root.child("creature")
        fun speed(key: String, default: Double) = f.double(key, default, min = 0.1, max = 12.0)
        fun seconds(key: String, default: Double) = f.double(key, default, min = 0.0, max = 60.0)
        val creature = Ch2L2Creature(
            home = f.string("home", d.home),
            floorZones = f.stringList("floor_zones"),
            orientSeconds = f.double("orient_seconds", d.orientSeconds, min = 0.05, max = 10.0),
            windupSeconds = f.double("windup_seconds", d.windupSeconds, min = 0.05, max = 10.0),
            touchRange = f.double("touch_range", d.touchRange, min = 0.5, max = 6.0),
            contactRange = f.double("contact_range", d.contactRange, min = 0.5, max = 6.0),
            damageFraction = f.double("damage_fraction", d.damageFraction, min = 0.0, max = 1.0),
            hitIntervalSeconds = seconds("hit_interval_seconds", d.hitIntervalSeconds),
            recoverSeconds = seconds("recover_seconds", d.recoverSeconds),
            stalkListenSeconds = seconds("stalk_listen_seconds", d.stalkListenSeconds),
            searchPoints = f.int("search_points", d.searchPoints, min = 0, max = 8),
            searchRadius = f.double("search_radius", d.searchRadius, min = 0.5, max = 16.0),
            searchListenSeconds = seconds("search_listen_seconds", d.searchListenSeconds),
            stalkSpeed = speed("stalk_speed", d.stalkSpeed),
            searchSpeed = speed("search_speed", d.searchSpeed),
            walkSpeed = speed("walk_speed", d.walkSpeed),
            chargeSpeed = speed("charge_speed", d.chargeSpeed),
            chargeSpeedLoud = speed("charge_speed_loud", d.chargeSpeedLoud),
            graceAfterStopSeconds = seconds("grace_after_stop_seconds", d.graceAfterStopSeconds),
            pump = parsePump(f, d.pump),
            pipe = parsePipe(f, d.pipe),
        )
        f.reportUnknownKeys()
        return creature
    }

    private fun parsePump(f: JsonFields, d: Ch2L2PumpConfig): Ch2L2PumpConfig {
        if (!f.has("pump")) return d
        val p = f.child("pump")
        val anchor = p.string("anchor", d.anchor)
        val beacons = if (p.has("beacons")) p.stringList("beacons") else listOf(anchor)
        val run = p.double("run_seconds", d.runSeconds, min = 1.0)
        val silent = p.double("silent_seconds", d.silentSeconds, min = 1.0)
        val slow = p.double("slow_seconds", d.slowSeconds, min = 0.0)
        p.reportUnknownKeys()
        if (slow > run) {
            f.problems += "creature.pump: slow_seconds ($slow) is longer than run_seconds ($run), using the defaults"
            return d.copy(anchor = anchor, beacons = beacons)
        }
        return Ch2L2PumpConfig(anchor, beacons, run, silent, slow)
    }

    private fun parsePipe(f: JsonFields, d: Ch2L2PipeConfig): Ch2L2PipeConfig {
        if (!f.has("pipe")) return d
        val q = f.child("pipe")
        val pipe = Ch2L2PipeConfig(q.string("from", d.from), q.double("delay_seconds", d.delaySeconds, min = 0.0))
        q.reportUnknownKeys()
        return pipe
    }
}
