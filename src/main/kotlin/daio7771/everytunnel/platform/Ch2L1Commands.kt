package daio7771.everytunnel.platform

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import daio7771.everytunnel.core.ch2l1.Ch2L1Skins
import daio7771.everytunnel.core.ch2l1.Ch2L1State
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.command.CommandSource
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.Identifier

/**
 * Developer commands for Chapter 2's Level 1 (operator level 2), so that every step of the level can be tried
 * without playing the ones before it. **Only in the development environment**, like [DebugCommands]: a release
 * has none of them.
 *
 * - `/et reload`: reads the level's JSON files again ([Ch2L1DataLoader]) and says what is wrong with them.
 * - `/et ch2 l1 anchors`: every anchor with its coordinates, the provisional ones marked.
 * - `/et ch2 l1 goto <anchor>`: takes you to a point, or to the middle of a zone one block over its floor.
 * - `/et ch2 l1 state [<state>]`: shows your state, or puts you in another one (every system of the level
 *   is told, as if you had got there by playing).
 * - `/et ch2 l1 skip`: moves you on to the next state, as if you had done what ends the current one.
 * - `/et ch2 l1 npc <skin>`: puts a person of the factory in front of you (`rowan`, `coworker`, `worker_a`, `worker_b`,
 *   `generic_1` to `generic_3`), to see its skin; `/et ch2 l1 npc clear` takes them all away ([Ch2L1Npcs]).
 *
 * The level's voices have no command: `/playsound every_tunnel:voice.<id> voice @s` plays any of them, with its subtitles.
 */
object Ch2L1Commands {

    private val ANCHOR_NAMES = SuggestionProvider<ServerCommandSource> { _, builder ->
        CommandSource.suggestMatching(Ch2L1DataLoader.data.anchors.names(), builder)
    }

    private val SKIN_NAMES = SuggestionProvider<ServerCommandSource> { _, builder ->
        CommandSource.suggestMatching(Ch2L1Skins.ALL, builder)
    }

    private val STATE_NAMES = SuggestionProvider<ServerCommandSource> { _, builder ->
        CommandSource.suggestMatching(Ch2L1State.entries.map { it.name }, builder)
    }

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            if (!DevWorlds.enabled) return@register
            dispatcher.register(
                CommandManager.literal("et")
                    .requires { it.hasPermissionLevel(2) }
                    .then(CommandManager.literal("reload").executes(::reload))
                    .then(
                        CommandManager.literal("ch2").then(
                            CommandManager.literal("l1")
                                .then(CommandManager.literal("anchors").executes(::anchors))
                                .then(
                                    CommandManager.literal("goto").then(
                                        CommandManager.argument("anchor", StringArgumentType.word())
                                            .suggests(ANCHOR_NAMES)
                                            .executes(::goto),
                                    ),
                                )
                                .then(
                                    CommandManager.literal("state").executes(::showState).then(
                                        CommandManager.argument("state", StringArgumentType.word())
                                            .suggests(STATE_NAMES)
                                            .executes(::setState),
                                    ),
                                )
                                .then(CommandManager.literal("skip").executes(::skip))
                                .then(
                                    CommandManager.literal("npc")
                                        .then(CommandManager.literal("clear").executes(::clearNpcs))
                                        .then(
                                            CommandManager.argument("skin", StringArgumentType.word())
                                                .suggests(SKIN_NAMES)
                                                .executes(::spawnNpc),
                                        ),
                                ),
                        ),
                    ),
            )
        }
    }

    private fun reload(context: CommandContext<ServerCommandSource>): Int {
        val report = Ch2L1DataLoader.reload()
        report.lines().forEach { reply(context, it) }
        return if (report.problems.isEmpty()) 1 else 0
    }

    private fun anchors(context: CommandContext<ServerCommandSource>): Int {
        val anchors = Ch2L1DataLoader.data.anchors
        if (anchors.points.isEmpty() && anchors.zones.isEmpty()) {
            reply(context, "No anchors: anchors.json has none (try /et reload).")
            return 0
        }
        reply(context, "Anchors in ${anchors.dimension}, ${anchors.provisionalCount()} of ${anchors.names().size} provisional:")
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
        val anchors = Ch2L1DataLoader.data.anchors
        val player = context.source.playerOrThrow
        val dimension = Identifier.tryParse(anchors.dimension)
        val world = dimension?.let { context.source.server.getWorld(RegistryKey.of(RegistryKeys.WORLD, it)) }
        if (world == null) {
            reply(context, "The anchors' dimension ${anchors.dimension} does not exist in this world.")
            return 0
        }
        val point = anchors.point(id)
        val zone = anchors.zone(id)
        when {
            point != null -> {
                player.stopRiding()
                player.teleport(world, point.position.x, point.position.y, point.position.z, point.yaw, point.pitch)
                reply(context, "At $id${mark(point.provisional)}.")
            }
            zone != null -> {
                val spot = zone.standingPoint()
                player.stopRiding()
                player.teleport(world, spot.x, spot.y, spot.z, player.yaw, player.pitch)
                reply(context, "In the middle of zone $id${mark(zone.provisional)}.")
            }
            else -> {
                reply(context, "No anchor called $id. Anchors: ${anchors.names().joinToString(", ").ifEmpty { "none" }}")
                return 0
            }
        }
        return 1
    }

    private fun showState(context: CommandContext<ServerCommandSource>): Int {
        val state = Ch2L1.state(context.source.playerOrThrow)
        reply(context, "Chapter 2 Level 1: you are in $state (next: ${state.next() ?: "none, the level is over"}).")
        reply(context, "States: ${Ch2L1State.entries.joinToString(", ")}")
        return 1
    }

    private fun setState(context: CommandContext<ServerCommandSource>): Int {
        val name = StringArgumentType.getString(context, "state")
        val state = Ch2L1State.parse(name)
        if (state == null) {
            reply(context, "No state called $name. States: ${Ch2L1State.entries.joinToString(", ")}")
            return 0
        }
        val player = context.source.playerOrThrow
        if (!Ch2L1.setState(player, state, "command")) {
            reply(context, "You are in $state already.")
            return 0
        }
        reply(context, "Chapter 2 Level 1: now in $state.")
        return 1
    }

    private fun skip(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.playerOrThrow
        val from = Ch2L1.state(player)
        val to = Ch2L1.skip(player)
        if (to == null) {
            reply(context, "You are in $from: there is nothing after it.")
            return 0
        }
        reply(context, "Skipped $from: now in $to.")
        return 1
    }

    private fun spawnNpc(context: CommandContext<ServerCommandSource>): Int {
        val skin = StringArgumentType.getString(context, "skin")
        if (skin !in Ch2L1Skins.ALL) {
            reply(context, "No skin called $skin. Skins: ${Ch2L1Skins.ALL.joinToString(", ")}")
            return 0
        }
        if (Ch2L1Npcs.spawnInFront(context.source.playerOrThrow, skin) == null) {
            reply(context, "No room for a person in front of you.")
            return 0
        }
        reply(context, "$skin stands in front of you (/et ch2 l1 npc clear takes them away).")
        return 1
    }

    private fun clearNpcs(context: CommandContext<ServerCommandSource>): Int {
        val count = Ch2L1Npcs.clear(context.source.server)
        reply(context, if (count == 0) "There were none." else "$count gone.")
        return count
    }

    private fun mark(provisional: Boolean) = if (provisional) " (provisional)" else ""

    private fun reply(context: CommandContext<ServerCommandSource>, message: String) {
        context.source.sendFeedback({ Text.literal(message) }, false)
    }
}
