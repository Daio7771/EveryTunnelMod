package daio7771.everytunnel.chapter2.level2.platform

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import daio7771.everytunnel.chapter2.level2.core.Ch2L2Event
import daio7771.everytunnel.chapter2.level2.core.Ch2L2State
import daio7771.everytunnel.chapter2.platform.Ch2Progress
import daio7771.everytunnel.chapter2.platform.MapBuild
import daio7771.everytunnel.platform.DevWorlds
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
        reply(context, "  water: stage ${state.floodStage} of 3")
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

    private fun unbuild(context: CommandContext<ServerCommandSource>): Int {
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

    private fun mark(provisional: Boolean) = if (provisional) " (provisional)" else ""

    private fun reply(context: CommandContext<ServerCommandSource>, message: String) {
        context.source.sendFeedback({ Text.literal(message) }, false)
    }
}
