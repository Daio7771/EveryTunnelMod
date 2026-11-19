package daio7771.everytunnel.chapter2.level2.core

import com.google.gson.JsonObject
import daio7771.everytunnel.util.JsonFields

/**
 * A place the level saves at ([Ch2L2Level.checkpoints]): walking into [zone] takes it, once, if it lies ahead of the last one taken; a
 * death, or the menu's CONTINUE, brings the player back to [anchor]. [before]: the encounter it comes before, where `/et ch2 l2 reset
 * <event>` puts the player.
 */
data class Ch2L2Checkpoint(val id: String, val anchor: String, val zone: String, val before: Ch2L2Event?)

/** Walking into [zone] moves the player on to [state], if it lies ahead of theirs ([Ch2L2Level.reaches]). */
data class Ch2L2Reach(val zone: String, val state: Ch2L2State)

/**
 * How Chapter 2's Level 2 is played, as `level.json` says (the plan's "lighting, resources and flashlight" are here): the rules, what the
 * player has on arriving, the lamps, where the level saves and how the player moves on through its states. Plain Kotlin, parsed by
 * [parse]; the anchors and zones it names are checked against `anchors.json` by [Ch2L2Data].
 */
data class Ch2L2Level(
    /** The flashlight works (Rowan gave it in Level 1). */
    val flashlight: Boolean,
    /** The player's steps make noise the creatures hear. */
    val noise: Boolean,
    /** On arriving from Level 1, each resource as a share of its full amount (0 to 1); a death or CONTINUE gives them all full. */
    val startBattery: Double,
    val startThirst: Double,
    val startStamina: Double,
    /** The lamps of each zone ("on", "flicker" or "off"), set whenever the level is started; zones not named keep the map's (on). */
    val lights: Map<String, String>,
    /** In the order of the way. */
    val checkpoints: List<Ch2L2Checkpoint>,
    val reaches: List<Ch2L2Reach>,
    /** The groups of Hunters, the loot lying about and the notes on boards (phase 3). */
    val hunters: List<Ch2L2HunterGroup> = emptyList(),
    val loot: List<Ch2L2Loot> = emptyList(),
    val notes: List<Ch2L2Note> = emptyList(),
    /** The vibration creature and the pump (phase 4). */
    val creature: Ch2L2Creature = Ch2L2Creature.DEFAULT,
    /** The Observant Guard and its four traps (phase 5). */
    val guard: Ch2L2Guard = Ch2L2Guard.DEFAULT,
    /** The flood's tunables and the ending (phase 6). */
    val flood: Ch2L2FloodRules = Ch2L2FloodRules.DEFAULT,
    val ending: Ch2L2Ending = Ch2L2Ending.DEFAULT,
) {
    fun checkpoint(id: String?): Ch2L2Checkpoint? = checkpoints.firstOrNull { it.id == id }

    /** The checkpoint ahead of [current] (null: none taken) whose zone the player is in ([inZone]), the furthest one; null if none. */
    fun checkpointAhead(current: String?, inZone: (String) -> Boolean): Ch2L2Checkpoint? {
        val taken = checkpoints.indexOfFirst { it.id == current }
        return checkpoints.withIndex().lastOrNull { (i, c) -> i > taken && inZone(c.zone) }?.value
    }

    /** The furthest state ahead of [state] whose zone the player is in ([inZone]); null if none. */
    fun stateAhead(state: Ch2L2State, inZone: (String) -> Boolean): Ch2L2State? =
        reaches.filter { it.state > state && inZone(it.zone) }.maxOfOrNull { it.state }

    /** The checkpoint that comes before [event], if one says so. */
    fun checkpointBefore(event: Ch2L2Event): Ch2L2Checkpoint? = checkpoints.firstOrNull { it.before == event }

    /** Every anchor and zone the file names, to check against the anchors. */
    fun anchorNames(): List<String> = checkpoints.map { it.anchor } + hunters.map { it.anchor } + loot.map { it.anchor } + notes.map { it.anchor } + creature.anchorNames() + guard.anchorNames() + flood.anchorNames()
    fun zoneNames(): List<String> = (lights.keys + checkpoints.map { it.zone } + reaches.map { it.zone } + creature.zoneNames() + guard.zoneNames() + ending.zone).distinct()

    class Result(val level: Ch2L2Level, val problems: List<String>)

    companion object {
        val LIGHT_MODES = setOf("on", "flicker", "off")

        /** What the level does without a usable file: no checkpoint but the entry, nothing reached by walking. */
        val DEFAULT = Ch2L2Level(
            flashlight = true, noise = true, startBattery = 1.0, startThirst = 1.0, startStamina = 1.0, lights = emptyMap(),
            checkpoints = listOf(Ch2L2Checkpoint("entry", "sealed_entry", "zone_sealed_entry", Ch2L2Event.HUNTERS)),
            reaches = emptyList(),
        )

        fun parse(root: JsonObject): Result {
            val f = JsonFields(root, "")
            val d = DEFAULT
            val rules = f.child("rules")
            val flashlight = rules.boolean("flashlight", d.flashlight)
            val noise = rules.boolean("noise", d.noise)
            rules.reportUnknownKeys()
            val start = f.child("start")
            val battery = start.double("battery", d.startBattery, min = 0.0, max = 1.0)
            val thirst = start.double("thirst", d.startThirst, min = 0.05, max = 1.0)
            val stamina = start.double("stamina", d.startStamina, min = 0.0, max = 1.0)
            start.reportUnknownKeys()

            val lights = linkedMapOf<String, String>()
            if (f.has("lights")) {
                val l = f.child("lights")
                for (zone in root.get("lights").takeIf { it.isJsonObject }?.asJsonObject?.keySet().orEmpty()) {
                    val mode = l.string(zone, "on")
                    if (mode in LIGHT_MODES) lights[zone] = mode else f.problems += "lights.$zone: \"$mode\" is not one of ${LIGHT_MODES.joinToString()}"
                }
            }

            val checkpoints = if (!f.has("checkpoints")) d.checkpoints else (f.array("checkpoints")?.toList() ?: emptyList()).mapIndexedNotNull { i, e ->
                if (!e.isJsonObject) {
                    f.problems += "checkpoints[$i]: expected an object"
                    return@mapIndexedNotNull null
                }
                val c = JsonFields(e.asJsonObject, "checkpoints[$i]", f.problems)
                val id = c.requiredString("id")
                val anchor = c.requiredString("anchor")
                val zone = c.requiredString("zone")
                val beforeText = if (c.has("before")) c.string("before", "") else null
                val before = beforeText?.let(Ch2L2Event::parse)
                if (beforeText != null && before == null) {
                    f.problems += "checkpoints[$i].before: no encounter \"$beforeText\" (${Ch2L2Event.entries.joinToString { it.id }})"
                }
                c.reportUnknownKeys()
                if (id == null || anchor == null || zone == null) null else Ch2L2Checkpoint(id, anchor, zone, before)
            }

            val reaches = (if (f.has("progress")) f.array("progress")?.toList() ?: emptyList() else emptyList()).mapIndexedNotNull { i, e ->
                if (!e.isJsonObject) {
                    f.problems += "progress[$i]: expected an object"
                    return@mapIndexedNotNull null
                }
                val r = JsonFields(e.asJsonObject, "progress[$i]", f.problems)
                val zone = r.requiredString("zone")
                val stateText = r.requiredString("state")
                val state = stateText?.let { Ch2L2State.parse(it) }
                if (stateText != null && state == null) f.problems += "progress[$i].state: no state \"$stateText\""
                r.reportUnknownKeys()
                if (zone == null || state == null) null else Ch2L2Reach(zone, state)
            }
            val hunters = Ch2L2PlacementsParser.hunters(f)
            val loot = Ch2L2PlacementsParser.loot(f)
            val notes = Ch2L2PlacementsParser.notes(f)
            val creature = Ch2L2CreatureParser.parse(f)
            val guard = Ch2L2GuardParser.parse(f)
            val flood = Ch2L2FloodParser.flood(f)
            val ending = Ch2L2FloodParser.ending(f)
            f.reportUnknownKeys()

            val problems = f.problems.toMutableList()
            problems += Ch2L2PlacementsParser.duplicates("hunters", hunters.map { it.id })
            problems += Ch2L2PlacementsParser.duplicates("loot", loot.map { it.id })
            problems += Ch2L2PlacementsParser.duplicates("notes", notes.map { it.id })
            if (checkpoints.isEmpty()) problems += "checkpoints: none; at least the entry's is needed"
            if (checkpoints.map { it.id }.toSet().size != checkpoints.size) problems += "checkpoints: two have the same id"
            val befores = checkpoints.mapNotNull { it.before }
            if (befores.toSet().size != befores.size) problems += "checkpoints: two come before the same encounter"
            val level = Ch2L2Level(
                flashlight, noise, battery, thirst, stamina, lights,
                checkpoints.ifEmpty { d.checkpoints }, reaches, hunters, loot, notes, creature, guard, flood, ending,
            )
            return Result(level, problems)
        }
    }
}
