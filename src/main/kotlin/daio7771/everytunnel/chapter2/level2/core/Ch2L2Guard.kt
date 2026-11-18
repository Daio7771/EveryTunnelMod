package daio7771.everytunnel.chapter2.level2.core

import daio7771.everytunnel.util.JsonFields

/** One of the Guard's four traps (`guard.plates`): the plate is the box of [zone] (its feet inside it is "on the plate"), [breaker] the anchor of its breaker's block. */
data class Ch2L2Plate(val id: String, val zone: String, val breaker: String)

/**
 * The Observant Guard and its four traps (`guard` in `level.json`; phase 5). It breaks out of the bricked-up patch [wall] (a gate of the
 * map's plan) at [nest], and from then on, while it lives, the way is barred: [barred] are the plan's gates that drop when it comes
 * out (the service corridor's far end, the maintenance room's east exit, the vestibule's south door and the grate to the collector) and
 * rise when it dies. [arena] are the zones where it always knows where the player is.
 *
 * - **Coming out**: a jumpscare after a warning. For [crackSeconds] the vestibule's lamps ([lightsZone]) flicker and, the last second, die,
 *   the grates fall and the wall's patch ([wall], [wallFace]) cracks. Then an [introSeconds] cinematic with bands, skippable: the Guard,
 *   in its nest behind the wall, punches the wall open, steps out, points at the player and beats its chest with both fists (it has no
 *   voice: it speaks with signs). The lamps come back at the blow. Control returns, it stands still [stareSeconds] and hunts.
 * - **Hunting**: at [chaseSpeed] blocks a second straight at the player. Within [hitRange] it raises an arm for [windupSeconds] and, if the
 *   player is still within range, hurts them for [damageFraction] of their maximum health (a real loss: two hits kill), at least
 *   [hitIntervalSeconds] after the last hit.
 * - **Traps** ([TrapBoard]): a pulled breaker charges [rearmSeconds], then arms its plate; the Guard stepping on an armed plate rolls once:
 *   [failChance] it fizzles, else it is stunned [stunSeconds]. [stunsToKill] stuns kill it: it comes apart over [deathSeconds] and the
 *   finish runs ([GuardFight]), after a cinematic that watches it fall to the floor and come apart; BOO! goes up at [boo].
 */
data class Ch2L2Guard(
    val nest: String,
    val wall: String,
    val wallFace: String,
    val boo: String,
    /** The zone whose lamps flicker and die before the Guard appears (the vestibule). */
    val lightsZone: String,
    val barred: List<String>,
    val arena: List<String>,
    val plates: List<Ch2L2Plate>,
    val damageFraction: Double,
    val failChance: Double,
    val stunSeconds: Double,
    val rearmSeconds: Double,
    val hitIntervalSeconds: Double,
    val stunsToKill: Int,
    val deathSeconds: Double,
    val crackSeconds: Double,
    val introSeconds: Double,
    val stareSeconds: Double,
    val windupSeconds: Double,
    val hitRange: Double,
    val chaseSpeed: Double,
) {
    fun anchorNames(): List<String> = listOf(nest, wallFace, boo) + plates.map { it.breaker }
    fun zoneNames(): List<String> = arena + plates.map { it.zone } + lightsZone

    companion object {
        val DEFAULT = Ch2L2Guard(
            nest = "guard_nest", wall = "guard_wall", wallFace = "observer_wall_face", boo = "boo_wall", lightsZone = "zone_observer_wall",
            barred = listOf("service_far_gate", "chamber_exit", "return_door", "vestibule_gate"),
            arena = emptyList(), plates = emptyList(),
            damageFraction = 0.5, failChance = 0.25, stunSeconds = 4.0, rearmSeconds = 2.0, hitIntervalSeconds = 2.0, stunsToKill = 4,
            deathSeconds = 14.0, crackSeconds = 5.0, introSeconds = 10.0, stareSeconds = 0.5, windupSeconds = 0.6, hitRange = 2.4, chaseSpeed = 3.8,
        )
    }
}

/** Reads the `guard` block of `level.json`; the problems go to [JsonFields.problems]. */
object Ch2L2GuardParser {

    fun parse(root: JsonFields): Ch2L2Guard {
        val d = Ch2L2Guard.DEFAULT
        if (!root.has("guard")) return d
        val f = root.child("guard")
        fun seconds(key: String, default: Double) = f.double(key, default, min = 0.0, max = 60.0)
        val plates = if (!f.has("plates")) emptyList() else (f.array("plates")?.toList() ?: emptyList()).mapIndexedNotNull { i, e ->
            if (!e.isJsonObject) {
                root.problems += "guard.plates[$i]: expected an object"
                return@mapIndexedNotNull null
            }
            val p = JsonFields(e.asJsonObject, "guard.plates[$i]", root.problems)
            val id = p.requiredString("id")
            val zone = p.requiredString("zone")
            val breaker = p.requiredString("breaker")
            p.reportUnknownKeys()
            if (id == null || zone == null || breaker == null) null else Ch2L2Plate(id, zone, breaker)
        }
        val guard = Ch2L2Guard(
            nest = f.string("nest", d.nest),
            wall = f.string("wall", d.wall),
            wallFace = f.string("wall_face", d.wallFace),
            boo = f.string("boo", d.boo),
            lightsZone = f.string("lights_zone", d.lightsZone),
            barred = if (f.has("barred")) f.stringList("barred") else d.barred,
            arena = f.stringList("arena"),
            plates = plates,
            damageFraction = f.double("damage_fraction", d.damageFraction, min = 0.0, max = 1.0),
            failChance = f.double("fail_chance", d.failChance, min = 0.0, max = 1.0),
            stunSeconds = seconds("stun_seconds", d.stunSeconds),
            rearmSeconds = seconds("rearm_seconds", d.rearmSeconds),
            hitIntervalSeconds = seconds("hit_interval_seconds", d.hitIntervalSeconds),
            stunsToKill = f.int("stuns_to_kill", d.stunsToKill, min = 1, max = 20),
            deathSeconds = seconds("death_seconds", d.deathSeconds),
            crackSeconds = seconds("crack_seconds", d.crackSeconds),
            introSeconds = seconds("intro_seconds", d.introSeconds),
            stareSeconds = seconds("stare_seconds", d.stareSeconds),
            windupSeconds = f.double("windup_seconds", d.windupSeconds, min = 0.05, max = 10.0),
            hitRange = f.double("hit_range", d.hitRange, min = 0.5, max = 8.0),
            chaseSpeed = f.double("chase_speed", d.chaseSpeed, min = 0.1, max = 12.0),
        )
        f.reportUnknownKeys()
        if (plates.map { it.id }.toSet().size != plates.size) root.problems += "guard.plates: two have the same id"
        return guard
    }
}
