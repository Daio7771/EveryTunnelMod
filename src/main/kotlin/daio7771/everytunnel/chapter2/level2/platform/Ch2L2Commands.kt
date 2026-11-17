package daio7771.everytunnel.chapter2.level2.platform

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
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
 * (`/et ch2 l1 ...`), under the same `/et` (Brigadier joins the two trees). Phase 1 walks the map; the state, `skip` and `reset` of
 * the level's events come with phase 2.
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

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            if (!DevWorlds.enabled) return@register
            dispatcher.register(
                CommandManager.literal("et")
                    .requires { it.hasPermissionLevel(2) }
                    .then(
                        CommandManager.literal("ch2").then(
                            CommandManager.literal("l2")
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
