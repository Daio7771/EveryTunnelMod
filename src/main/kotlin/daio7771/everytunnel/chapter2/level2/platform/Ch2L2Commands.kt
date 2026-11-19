package daio7771.everytunnel.chapter2.level2.platform

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import daio7771.everytunnel.chapter2.level2.core.Ch2L2Event
import daio7771.everytunnel.chapter2.level2.core.Ch2L2State
import daio7771.everytunnel.chapter2.platform.Ch2Progress
import daio7771.everytunnel.chapter2.platform.MapBuild
import daio7771.everytunnel.platform.DevWorlds
import daio7771.everytunnel.platform.LightMode
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.command.CommandSource
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.Identifier

/**
 * Developer commands for Chapter 2's Level 2 (operator level 2), **only in the development environment**, like Level 1's
 * (`/et ch2 l1 ...`), under the same `/et` (Brigadier joins the two trees).
 *
 * - `/et ch2 l2 enter`: you begin the level as if you had come down Level 1's manhole ([Ch2L2.enter]): built if it is not, your
 *   pockets emptied, the resources of `level.json`, at `sealed_entry`, its first checkpoint taken. `/et ch2 l2 leave` ends it (the
 *   saved game stays).
 * - `/et ch2 l2 state`: your state, the last checkpoint and the state it saved, the flood's stage and where you are in each encounter;
 *   `state <state>` puts you in another one (every system of the level is told, as if you had got there by playing).
 * - `/et ch2 l2 skip`: on to the next state; `skip <event>` past an encounter (`hunters`, `creature`, `guard`, `flood`).
 * - `/et ch2 l2 reset <event>`: the encounter to live again: the state before it, you at the checkpoint before it ([Ch2L2.reset]).
 *
 * - `/et ch2 l2 build`: builds the catacombs again over what stands ([Ch2L2Build]; a new plan is built by itself the next time
 *   `goto` needs it); `/et ch2 l2 unbuild` empties the whole area (before packing the world into a jar: it is Chapter 2's).
 * - `/et ch2 l2 hunters`: the level's Hunters put back whole at their posts, whatever your state; `/et ch2 l2 loot`: all the loot
 *   as if none had been taken (your counters forgotten; what is in your pockets stays) ([Ch2L2Spawns]).
 * - `/et ch2 l2 creature state|pump`: the action bar shows the creature's brain (state and target) or the pump's cycle (phase, tick of the
 *   cycle, whether it masks vibrations); again to turn it off ([Ch2L2Pump]).
 * - `/et ch2 l2 guard`: the action bar shows the Observant Guard's fight (phase, stuns, each trap's phase: S spent, C charging, A armed);
 *   again to turn it off ([Ch2L2Guard]). `skip guard` runs its finish at once; `reset guard` puts the wall whole again at the vestibule.
 * - `/et ch2 l2 flood <0|1|2|3>`: the water at once to that stage, whatever your state (0 drains it); `skip flood` fills all three and
 *   shuts the station's door, `reset flood` drains it and opens the vestibule's gate again at the collector ([Ch2L2FloodRun]).
 *   `/et ch2 l2 ending` plays the ending (fade, card, main menu; the level is finished only if you are in the reunion or past it) ([Ch2L2Ending]).
 * - `/et ch2 l2 maw`: Stone Maw's fight (phase 6.1): its phase, hits left and mode; `maw hit` counts a hit, `maw kill` plays its death. `skip maw`
 *   runs its death's end at once, `reset maw` puts the fight back at its start. `/et ch2 l2 reunion` plays the reunion (you are put at its spot) ([Ch2L2Maw], [Ch2L2Reunion]).
 * - `/et ch2 l2 perf`: what the level costs where you stand (phase 7): its entities by type (more than one where only one belongs is flagged), the
 *   flood's water cells, the lamps by mode and the mean and worst milliseconds a server tick spends on the level's own handlers over the last
 *   100 ticks, by handler ([Ch2L2Perf]).
 * - `/et ch2 l2 anchors`: every anchor with its coordinates, the provisional ones marked.
 * - `/et ch2 l2 goto <anchor>`: builds the level if it is not, then takes you to a point (the places of the route, `sealed_entry` to
 *   `level_exit`, and the rest), or to the middle of a zone (`zone_<place>`) one block over its floor.
 *
 * `/et reload` reads Level 2's files again too ([Ch2L2DataLoader]).
 */
object Ch2L2Commands {

    private val ANCHOR_NAMES = SuggestionProvider<ServerCommandSource> { _, builder ->
        CommandSource.suggestMatching(Ch2L2DataLoader.data.anchors.names(), builder)
    }

    private val STATE_NAMES = SuggestionProvider<ServerCommandSource> { _, builder ->
        CommandSource.suggestMatching(Ch2L2State.entries.map { it.name.lowercase() }, builder)
    }

    private val EVENT_NAMES = SuggestionProvider<ServerCommandSource> { _, builder ->
        CommandSource.suggestMatching(Ch2L2Event.entries.map { it.id }, builder)
    }

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            if (!DevWorlds.enabled) return@register
            dispatcher.register(
                CommandManager.literal("et")
                    .requires { it.hasPermissionLevel(2) }
                    .then(
                        CommandManager.literal("ch2").then(
                            CommandManager.literal("l2")
                                .then(CommandManager.literal("enter").executes(::enter))
                                .then(CommandManager.literal("leave").executes(::leave))
                                .then(
                                    CommandManager.literal("state").executes(::showState).then(
                                        CommandManager.argument("state", StringArgumentType.word()).suggests(STATE_NAMES).executes(::setState),
                                    ),
                                )
                                .then(
                                    CommandManager.literal("skip").executes(::skip).then(
                                        CommandManager.argument("event", StringArgumentType.word()).suggests(EVENT_NAMES).executes(::skipEvent),
                                    ),
                                )
                                .then(
                                    CommandManager.literal("reset").then(
                                        CommandManager.argument("event", StringArgumentType.word()).suggests(EVENT_NAMES).executes(::resetEvent),
                                    ),
                                )
                                .then(CommandManager.literal("build").executes(::build))
                                .then(CommandManager.literal("unbuild").executes(::unbuild))
                                .then(CommandManager.literal("hunters").executes(::hunters))
                                .then(CommandManager.literal("loot").executes(::loot))
                                .then(
                                    CommandManager.literal("creature")
                                        .then(CommandManager.literal("state").executes(::watchCreature))
                                        .then(CommandManager.literal("pump").executes(::watchPump)),
                                )
                                .then(CommandManager.literal("guard").executes(::watchGuard))
                                .then(
                                    CommandManager.literal("flood").then(
                                        CommandManager.argument("stage", IntegerArgumentType.integer(0, 3)).executes(::flood),
                                    ),
                                )
                                .then(CommandManager.literal("ending").executes(::ending))
                                .then(
                                    CommandManager.literal("maw")
                                        .executes(::mawState)
                                        .then(CommandManager.literal("hit").executes(::mawHit))
                                        .then(CommandManager.literal("kill").executes(::mawKill)),
                                )
                                .then(CommandManager.literal("reunion").executes(::reunion))
                                .then(CommandManager.literal("perf").executes(::perf))
                                .then(CommandManager.literal("anchors").executes(::anchors))
                                .then(
                                    CommandManager.literal("goto").then(
                                        CommandManager.argument("anchor", StringArgumentType.word()).suggests(ANCHOR_NAMES).executes(::goto),
                                    ),
                                ),
                        ),
                    ),
            )
        }
    }

    private fun enter(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.playerOrThrow
        val why = Ch2L2.enter(player)
        if (why != null) {
            reply(context, why)
            return 0
        }
        reply(context, "Into the catacombs (built first if they are not).")
        return 1
    }

    private fun leave(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.playerOrThrow
        if (!Ch2L2.active(player)) {
            reply(context, "You are not in Level 2.")
            return 0
        }
        Ch2L2.leave(player)
        reply(context, "Out of Level 2: the rest of the game's rules apply again. The saved game stays.")
        return 1
    }

    private fun showState(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.playerOrThrow
        val state = Ch2L2.state(player)
        val snapshot = Ch2Progress.ch2L2Snapshot(player)
        reply(context, "Level 2: $state${if (Ch2L2.active(player)) "" else " (not playing it: /et ch2 l2 enter)"}")
        reply(context, "  checkpoint: ${snapshot?.let { "${it.checkpoint}, saved at ${it.state}" } ?: "none"}")
        reply(context, "  water: stage ${state.floodStage} of 3 by state; ${Ch2L2FloodRun.describe()}${if (Ch2L2Ending.isRunning(player)) "; the ending is playing" else ""}")
        reply(context, "  encounters: ${Ch2L2Event.entries.joinToString { "${it.id} ${it.progress(state)}" }}")
        reply(context, "  counters: ${Ch2Progress.ch2L2Counters(player).ifEmpty { "none" }}")
        reply(context, "  states: ${Ch2L2State.entries.joinToString(" ") { it.name.lowercase() }}")
        return 1
    }

    private fun setState(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.playerOrThrow
        val text = StringArgumentType.getString(context, "state")
        val state = Ch2L2State.parse(text)
        if (state == null) {
            reply(context, "No state $text. States: ${Ch2L2State.entries.joinToString(" ") { it.name.lowercase() }}")
            return 0
        }
        reply(context, if (Ch2L2.setState(player, state, "command")) "Level 2: now $state." else "Already $state.")
        return 1
    }

    private fun skip(context: CommandContext<ServerCommandSource>): Int {
        val next = Ch2L2.skip(context.source.playerOrThrow)
        reply(context, if (next == null) "Already at the end." else "Level 2: now $next.")
        return if (next == null) 0 else 1
    }

    private fun event(context: CommandContext<ServerCommandSource>): Ch2L2Event? {
        val text = StringArgumentType.getString(context, "event")
        return Ch2L2Event.parse(text).also {
            if (it == null) reply(context, "No encounter $text. Encounters: ${Ch2L2Event.entries.joinToString(" ") { e -> e.id }}")
        }
    }

    private fun skipEvent(context: CommandContext<ServerCommandSource>): Int {
        val event = event(context) ?: return 0
        val player = context.source.playerOrThrow
        if (!Ch2L2.skip(player, event)) {
            reply(context, "Already past ${event.id} (${Ch2L2.state(player)}).")
            return 0
        }
        reply(context, "Past ${event.id}: now ${Ch2L2.state(player)}.")
        return 1
    }

    private fun resetEvent(context: CommandContext<ServerCommandSource>): Int {
        val event = event(context) ?: return 0
        val player = context.source.playerOrThrow
        val why = Ch2L2.reset(player, event)
        if (why != null) {
            reply(context, why)
            return 0
        }
        reply(context, "${event.id} to live again: ${Ch2L2.state(player)}, at the checkpoint ${Ch2L2.checkpoint(player)?.id}.")
        return 1
    }

    private fun build(context: CommandContext<ServerCommandSource>): Int {
        val asked = Ch2L2Build.ask(context.source.server, rebuild = true) { reply(context, "The catacombs are built.") }
        return answer(context, asked)
    }

    private fun answer(context: CommandContext<ServerCommandSource>, asked: MapBuild.Asked): Int = when (asked) {
        MapBuild.Asked.STARTED -> { reply(context, "Building the catacombs (a few seconds)..."); 1 }
        MapBuild.Asked.JOINED -> { reply(context, "They are being built already."); 1 }
        MapBuild.Asked.BUSY -> { reply(context, "The area is being cleared: try again in a moment."); 0 }
        MapBuild.Asked.FAILED -> { reply(context, "The plan cannot be built: ${Ch2L2Build.lastProblem}"); 0 }
        MapBuild.Asked.BUILT -> 1
    }

    private fun hunters(context: CommandContext<ServerCommandSource>): Int {
        val world = context.source.server.getWorld(
            RegistryKey.of(RegistryKeys.WORLD, Identifier.tryParse(Ch2L2DataLoader.data.anchors.dimension) ?: return 0),
        )
        if (world == null) {
            reply(context, "The anchors' dimension ${Ch2L2DataLoader.data.anchors.dimension} is not there.")
            return 0
        }
        Ch2L2Spawns.putHunters(world, null)
        reply(context, "The Hunters are back at their posts (${Ch2L2DataLoader.data.level.hunters.size} group(s)).")
        return 1
    }

    private fun loot(context: CommandContext<ServerCommandSource>): Int {
        val why = Ch2L2Spawns.restock(context.source.playerOrThrow)
        reply(context, why ?: "All the loot is back (${Ch2L2DataLoader.data.level.loot.size} pieces).")
        return if (why == null) 1 else 0
    }

    private fun flood(context: CommandContext<ServerCommandSource>): Int {
        val stage = IntegerArgumentType.getInteger(context, "stage")
        val asked = Ch2L2Build.ask(context.source.server, rebuild = false) {
            val n = Ch2L2FloodRun.setWater(context.source.server, stage)
            reply(context, if (n == 0) "The flood's water is drained." else "The flood's water is at stage $n.")
        }
        return if (asked == MapBuild.Asked.BUILT) 1 else answer(context, asked)
    }

    private fun ending(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.playerOrThrow
        if (!Ch2L2.active(player)) {
            reply(context, "You are not in Level 2: /et ch2 l2 enter first.")
            return 0
        }
        if (!Ch2L2Ending.start(player, force = true)) {
            reply(context, "The ending is playing already.")
            return 0
        }
        reply(context, "The ending: the fade, the card, the menu.${if (Ch2L2.state(player) < Ch2L2State.REUNION) " (You are before the reunion: the level does not finish.)" else ""}")
        return 1
    }

    private fun mawState(context: CommandContext<ServerCommandSource>): Int {
        reply(context, Ch2L2Maw.describe())
        return 1
    }

    private fun mawHit(context: CommandContext<ServerCommandSource>): Int {
        val ok = Ch2L2Maw.debugHit(context.source.server)
        reply(context, if (ok) "A hit on Stone Maw. ${Ch2L2Maw.describe()}" else "Stone Maw is not fighting (enter the arena first, or skip/reset maw).")
        return if (ok) 1 else 0
    }

    private fun mawKill(context: CommandContext<ServerCommandSource>): Int {
        val ok = Ch2L2Maw.debugKill()
        reply(context, if (ok) "Stone Maw's death begins." else "Stone Maw is not fighting.")
        return if (ok) 1 else 0
    }

    private fun reunion(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.playerOrThrow
        if (!Ch2L2.active(player)) {
            reply(context, "You are not in Level 2: /et ch2 l2 enter first.")
            return 0
        }
        val why = Ch2L2Reunion.debugPlay(player)
        reply(context, why ?: "The reunion plays.")
        return if (why == null) 1 else 0
    }

    private fun watchCreature(context: CommandContext<ServerCommandSource>): Int {
        val on = Ch2L2Pump.toggleWatchCreature(context.source.playerOrThrow)
        reply(context, if (on) "The creature's brain is in the action bar." else "The creature's readout is off.")
        return 1
    }

    private fun watchGuard(context: CommandContext<ServerCommandSource>): Int {
        val on = Ch2L2Guard.toggleWatch(context.source.playerOrThrow)
        reply(context, if (on) "The action bar shows the Guard's fight." else "The Guard's readout is off.")
        return 1
    }

    private fun watchPump(context: CommandContext<ServerCommandSource>): Int {
        val on = Ch2L2Pump.toggleWatchPump(context.source.playerOrThrow)
        reply(context, if (on) "The pump's cycle is in the action bar." else "The pump's readout is off.")
        return 1
    }

    private fun unbuild(context: CommandContext<ServerCommandSource>): Int {
        Ch2L2Spawns.clearHunters(context.source.server)
        Ch2L2Pump.creatureIn(context.source.server)?.discard()
        val asked = Ch2L2Build.clear(context.source.server) { reply(context, "The area is empty.") }
        if (asked == MapBuild.Asked.BUSY) {
            reply(context, "The catacombs are being built or cleared: try again in a moment.")
            return 0
        }
        reply(context, "Emptying the area (whoever stands in it will fall)...")
        return 1
    }

    private fun anchors(context: CommandContext<ServerCommandSource>): Int {
        val anchors = Ch2L2DataLoader.data.anchors
        if (anchors.points.isEmpty() && anchors.zones.isEmpty()) {
            reply(context, "No anchors: anchors.json has none (try /et reload).")
            return 0
        }
        reply(context, "Level 2 anchors in ${anchors.dimension}, ${anchors.provisionalCount()} of ${anchors.names().size} provisional:")
        for (point in anchors.points) {
            val p = point.position
            reply(context, "  ${point.id} [%.1f, %.1f, %.1f] yaw %.0f%s".format(p.x, p.y, p.z, point.yaw, mark(point.provisional)))
        }
        for (zone in anchors.zones) {
            val b = zone.box
            reply(context, "  zone ${zone.id} [${b.minX}, ${b.minY}, ${b.minZ}] to [${b.maxX}, ${b.maxY}, ${b.maxZ}]${mark(zone.provisional)}")
        }
        return anchors.names().size
    }

    private fun goto(context: CommandContext<ServerCommandSource>): Int {
        val id = StringArgumentType.getString(context, "anchor")
        val anchors = Ch2L2DataLoader.data.anchors
        val point = anchors.point(id)
        val zone = anchors.zone(id)
        if (point == null && zone == null) {
            reply(context, "No anchor called $id. Anchors: ${anchors.names().joinToString(", ").ifEmpty { "none" }}")
            return 0
        }
        val source = context.source
        val player = source.playerOrThrow
        val world = Identifier.tryParse(anchors.dimension)?.let { source.server.getWorld(RegistryKey.of(RegistryKeys.WORLD, it)) }
        if (world == null) {
            reply(context, "The anchors' dimension ${anchors.dimension} does not exist in this world.")
            return 0
        }
        val asked = Ch2L2Build.ask(source.server, rebuild = false) {
            player.stopRiding()
            if (point != null) {
                player.teleport(world, point.position.x, point.position.y, point.position.z, point.yaw, point.pitch)
                reply(context, "At $id${mark(point.provisional)}.")
            } else if (zone != null) {
                val spot = zone.standingPoint()
                player.teleport(world, spot.x, spot.y, spot.z, player.yaw, player.pitch)
                reply(context, "In the middle of zone $id${mark(zone.provisional)}.")
            }
        }
        return if (asked == MapBuild.Asked.BUILT) 1 else answer(context, asked)
    }

    private fun perf(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.playerOrThrow
        val world = player.serverWorld
        if (world.registryKey.value.toString() != Ch2L2DataLoader.data.anchors.dimension) {
            reply(context, "Level 2's world is ${Ch2L2DataLoader.data.anchors.dimension}; you are in ${world.registryKey.value}.")
            return 0
        }
        val entities = world.iterateEntities().filter { it !is net.minecraft.entity.player.PlayerEntity }.toList()
        val byType = entities.groupingBy { net.minecraft.registry.Registries.ENTITY_TYPE.getId(it.type).path }.eachCount().toSortedMap()
        reply(context, "entities (${entities.size}): ${byType.entries.joinToString(", ") { "${it.key} ${it.value}" }.ifEmpty { "none" }}")
        val onlyOne = listOf(
            "Observant Guard" to entities.count { it is ObservantGuardEntity },
            "pump creature" to entities.count { it is VibrationCreatureEntity && !it.inFight },
            "Stone Maw" to entities.count { it is VibrationCreatureEntity && it.inFight },
        ).filter { it.second > 1 }
        if (onlyOne.isNotEmpty()) reply(context, "  DUPLICATES: ${onlyOne.joinToString { "${it.first} x${it.second}" }}")
        val npcs = entities.count { it is daio7771.everytunnel.chapter2.level1.platform.FactoryNpcEntity && it.group == "ch2l2_reunion" }
        if (npcs > 2) reply(context, "  DUPLICATES: reunion NPCs x$npcs (two belong)")
        val (wet, cells) = Ch2L2FloodRun.wetCells(world)
        reply(context, "water: $wet of $cells flood cells hold water; ${Ch2L2FloodRun.describe()}")
        val lamps = Ch2L2Build.lampModes(world)
        reply(context, "lamps: ${lamps.values.sum()} (on ${lamps[LightMode.ON]}, flicker ${lamps[LightMode.FLICKER]}, off ${lamps[LightMode.OFF]})")
        val budget = Ch2L2Perf.budget
        reply(context, "level's tick work, last ${budget.ticks} ticks: mean %.3f ms, worst %.3f ms (a tick is 50 ms)".format(budget.meanMillis(), budget.worstMillis()))
        reply(context, "  by handler: ${budget.meanMillisByName().joinToString(", ") { "%s %.3f".format(it.first, it.second) }.ifEmpty { "none yet" }}")
        return 1
    }

    private fun mark(provisional: Boolean) = if (provisional) " (provisional)" else ""

    private fun reply(context: CommandContext<ServerCommandSource>, message: String) {
        context.source.sendFeedback({ Text.literal(message) }, false)
    }
}
