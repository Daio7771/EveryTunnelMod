package daio7771.everytunnel.chapter2.level1.platform

import daio7771.everytunnel.chapter2.level1.core.Ch2L1Act3
import daio7771.everytunnel.chapter2.platform.Ch2Blocks
import daio7771.everytunnel.chapter2.platform.Ch2Progress
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Skins
import daio7771.everytunnel.chapter2.level1.core.Ch2L1State
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.command.CommandSource
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import daio7771.everytunnel.platform.CardReaderBlock
import daio7771.everytunnel.platform.DevWorlds

/**
 * Developer commands for Chapter 2's Level 1 (operator level 2), so that every step of the level can be tried
 * without playing the ones before it. **Only in the development environment**, like [DebugCommands]: a release
 * has none of them.
 *
 * - `/et reload`: reads the level's JSON files again ([Ch2L1DataLoader]) and says what is wrong with them.
 * - `/et ch2 l1 enter`: you become a new hire of the factory ([Ch2L1.enter]): the level is built if it is not ([Ch2L1Build]), the
 *   level's rules apply to you (no flashlight, nothing hurts, free silent sprint, no bars; in Survival or Adventure), your
 *   pockets are emptied, the state is the start and you are at `spawn_clockin`. `/et ch2 l1 leave` ends it: the rules of the
 *   rest of the game apply again.
 * - `/et ch2 l1 build`: builds the level again over what stands (a new plan is built by itself the next time someone enters);
 *   `/et ch2 l1 unbuild` empties the whole area (do it before packing the world into a jar: it is Chapter 2's, not Chapter 1's).
 * - `/et ch2 l1 clock`: puts a time clock ([TimeClockBlock]) on the wall you look at, until the map places its own.
 * - `/et ch2 l1 anchors`: every anchor with its coordinates, the provisional ones marked.
 * - `/et ch2 l1 goto <anchor>`: takes you to a point, to the middle of a zone one block over its floor or to the first point of a path.
 * - `/et ch2 l1 state [<state>]`: shows your state, or puts you in another one (every system of the level
 *   is told, as if you had got there by playing).
 * - `/et ch2 l1 skip`: moves you on to the next state, as if you had done what ends the current one.
 * - `/et ch2 l1 npc <skin>`: puts a person of the factory in front of you (`rowan`, `coworker`, `worker_a`, `worker_b`,
 *   `generic_1` to `generic_3`), to see its skin; `/et ch2 l1 npc clear` takes them all away ([Ch2L1Npcs]).
 *
 * `/et ch2 l1 tasks` shows how far the player is in the three tasks (and what the data names that is missing), `tasks reset` starts them over
 * ([Ch2L1TaskHooks]); `intro` plays the first announcement's cinematic and `say <rowan_loyal_a1..a6>` an announcement, from the speaker of
 * the zone the player stands in ([Ch2L1IntroHooks], [Ch2L1SpeakerHooks]). `act3` shows what the player has done in Act 3 (the lab opened, the
 * files read, the office opened and entered), `act3 reset` shuts the auxiliary lab and the office again and forgets it ([Ch2L1Act3Hooks]);
 * `patrols` says where each guard is and what he thinks of the player ([Ch2L1PatrolHooks]).
 * The other voices have no command:
 * `/playsound every_tunnel:voice.<id> voice @s` plays any of them, with its subtitles.
 */
object Ch2L1Commands {

    private val ANCHOR_NAMES = SuggestionProvider<ServerCommandSource> { _, builder ->
        CommandSource.suggestMatching(Ch2L1DataLoader.data.anchors.names(), builder)
    }

    private val SKIN_NAMES = SuggestionProvider<ServerCommandSource> { _, builder ->
        CommandSource.suggestMatching(Ch2L1Skins.ALL, builder)
    }

    private val LOUDSPEAKER_LINES = SuggestionProvider<ServerCommandSource> { _, builder ->
        CommandSource.suggestMatching((1..6).map { "rowan_loyal_a$it" }, builder)
    }

    private val GROUP_NAMES = SuggestionProvider<ServerCommandSource> { _, builder ->
        CommandSource.suggestMatching(Ch2L1DataLoader.npcs.groups.keys + Ch2L1DataLoader.npcs.workers.map { it.group }.toSet(), builder)
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
                                .then(CommandManager.literal("enter").executes(::enter))
                                .then(CommandManager.literal("leave").executes(::leave))
                                .then(CommandManager.literal("clock").executes(::clock))
                                .then(CommandManager.literal("build").executes(::build))
                                .then(CommandManager.literal("unbuild").executes(::unbuild))
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
                                    CommandManager.literal("tasks").executes(::tasks)
                                        .then(CommandManager.literal("reset").executes(::resetTasks)),
                                )
                                .then(CommandManager.literal("patrols").executes(::patrols))
                                .then(
                                    CommandManager.literal("act3").executes(::act3)
                                        .then(CommandManager.literal("reset").executes(::resetAct3)),
                                )
                                .then(CommandManager.literal("intro").executes(::intro))
                                .then(
                                    CommandManager.literal("say").then(
                                        CommandManager.argument("line", StringArgumentType.word())
                                            .suggests(LOUDSPEAKER_LINES)
                                            .executes(::say),
                                    ),
                                )
                                .then(
                                    CommandManager.literal("npc")
                                        .then(CommandManager.literal("clear").executes(::clearNpcs))
                                        .then(CommandManager.literal("ambient").executes(::ambientNpcs))
                                        .then(
                                            CommandManager.literal("group").then(
                                                CommandManager.argument("name", StringArgumentType.word()).suggests(GROUP_NAMES).executes(::spawnGroup),
                                            ),
                                        )
                                        .then(
                                            CommandManager.literal("remove").then(
                                                CommandManager.argument("name", StringArgumentType.word()).suggests(GROUP_NAMES).executes(::removeGroup),
                                            ),
                                        )
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
        for (path in anchors.paths) {
            reply(context, "  path ${path.id}: ${path.points.size} points, ${if (path.closed) "closed" else "there and back"}${mark(path.provisional)}")
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
        val path = anchors.path(id)
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
            path != null -> {
                val first = path.points.first()
                player.stopRiding()
                player.teleport(world, first.x, first.y, first.z, player.yaw, player.pitch)
                reply(context, "At the first point of path $id${mark(path.provisional)}.")
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

    /** `/et ch2 l1 npc ambient`: the factory's ordinary people are put in now, the ones that are missing. */
    private fun ambientNpcs(context: CommandContext<ServerCommandSource>): Int {
        val count = Ch2L1Npcs.ensureAmbient(context.source.server)
        reply(context, "$count of the ${Ch2L1DataLoader.npcs.workers.size} workers put in (the level must be built, you must be playing it and be in it).")
        return 1
    }

    /** `/et ch2 l1 npc group <name>`: a group of `npcs.json` standing at its anchors. */
    private fun spawnGroup(context: CommandContext<ServerCommandSource>): Int {
        val name = StringArgumentType.getString(context, "name")
        val count = Ch2L1Npcs.spawnGroup(context.source.server, name)
        if (count == null) {
            reply(context, "npcs.json has no group called $name.")
            return 0
        }
        reply(context, "$count of the group $name put in.")
        return 1
    }

    /** `/et ch2 l1 npc remove <group>`: every person of a group (the workers' groups too: lab, main, dining) gone. */
    private fun removeGroup(context: CommandContext<ServerCommandSource>): Int {
        val name = StringArgumentType.getString(context, "name")
        reply(context, "${Ch2L1Npcs.despawnGroup(context.source.server, name)} of $name taken away.")
        return 1
    }

    /** `/et ch2 l1 tasks`: how far the player is in the three tasks of Act 1, and anything `tasks.json` or `speakers.json` names that is missing. */
    private fun tasks(context: CommandContext<ServerCommandSource>): Int {
        Ch2L1TaskHooks.status(context.source.playerOrThrow).forEach { reply(context, it) }
        val problems = daio7771.everytunnel.chapter2.level1.core.Ch2L1Wiring.problems(
            Ch2L1DataLoader.data.anchors, Ch2L1DataLoader.tasks, Ch2L1DataLoader.speakers,
        )
        problems.forEach { reply(context, "Problem: $it") }
        return if (problems.isEmpty()) 1 else 0
    }

    private fun resetTasks(context: CommandContext<ServerCommandSource>): Int {
        Ch2L1TaskHooks.reset(context.source.playerOrThrow)
        reply(context, "The three tasks start over (the tubes, the broom and the dirt are back where the state says).")
        return 1
    }

    /** `/et ch2 l1 patrols`: where each guard is and what he thinks. */
    private fun patrols(context: CommandContext<ServerCommandSource>): Int {
        Ch2L1PatrolHooks.status().forEach { reply(context, it) }
        return 1
    }

    /** `/et ch2 l1 act3`: what the player has done in Act 3. */
    private fun act3(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.playerOrThrow
        val act3 = Ch2L1DataLoader.act3
        val counter = { key: String -> Ch2Progress.ch2L1Counter(player, key) }
        reply(context, "Act 3: objective ${act3.objective(counter)}; lab ${if (counter(Ch2L1Act3.LAB_OPEN) > 0) "open" else "shut"}, " +
            "office ${if (counter(Ch2L1Act3.OFFICE_OPEN) > 0) "open" else "shut"}${if (counter(Ch2L1Act3.IN_OFFICE) > 0) " (entered)" else ""}.")
        reply(context, "Files read: " + act3.allFiles.joinToString(", ") { "$it ${if (act3.isRead(it, counter)) "yes" else "no"}" } +
            if (counter(Ch2L1Act3.TRIGGERED) > 0) "; the office cinematic's moment is reached." else ".")
        return 1
    }

    private fun resetAct3(context: CommandContext<ServerCommandSource>): Int {
        Ch2L1Act3Hooks.relock(context.source.playerOrThrow)
        reply(context, "The auxiliary lab and the office are shut again, the reader and the keypad red, Act 3 forgotten (the state is unchanged).")
        return 1
    }

    /** `/et ch2 l1 intro`: the first announcement's cinematic, now. */
    private fun intro(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.playerOrThrow
        val speakers = Ch2L1DataLoader.speakers
        val emitter = speakers.emitters.values.firstOrNull()?.let { Ch2L1DataLoader.data.anchors.point(it) }?.position
        val from = emitter?.let { net.minecraft.util.math.Vec3d(it.x, it.y, it.z) } ?: player.pos.add(0.0, 2.0, 0.0)
        if (!Ch2L1IntroHooks.start(player, from)) {
            reply(context, "The cinematic cannot play: another one is running, or speakers.json has no \"intro\" or one of its anchors is missing (see the log).")
            return 0
        }
        return 1
    }

    /** `/et ch2 l1 say <line>`: Rowan's announcement now (the chime, then the voice), from the speaker of the zone the player is in. */
    private fun say(context: CommandContext<ServerCommandSource>): Int {
        val line = StringArgumentType.getString(context, "line")
        if (!Ch2L1SpeakerHooks.say(context.source.playerOrThrow, line)) {
            reply(context, "No voice line called $line (try one of rowan_loyal_a1 to a6).")
            return 0
        }
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

    private fun enter(context: CommandContext<ServerCommandSource>): Int {
        val problem = Ch2L1.enter(context.source.playerOrThrow)
        if (problem != null) {
            reply(context, problem)
            return 0
        }
        return 1
    }

    private fun build(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val asked = Ch2L1Build.ask(source.server, rebuild = true) { reply(context, "The factory is built.") }
        return when (asked) {
            Ch2L1Build.Asked.STARTED -> { reply(context, "Building the factory (a second or two)..."); 1 }
            Ch2L1Build.Asked.JOINED -> { reply(context, "It is being built already."); 1 }
            Ch2L1Build.Asked.BUSY -> { reply(context, "The area is being cleared: try again in a moment."); 0 }
            Ch2L1Build.Asked.FAILED -> { reply(context, "The plan cannot be built: ${Ch2L1Build.lastProblem}"); 0 }
            Ch2L1Build.Asked.BUILT -> 1
        }
    }

    private fun unbuild(context: CommandContext<ServerCommandSource>): Int {
        val asked = Ch2L1Build.clear(context.source.server) { reply(context, "The area is empty.") }
        if (asked == Ch2L1Build.Asked.BUSY) {
            reply(context, "The factory is being built or cleared: try again in a moment.")
            return 0
        }
        reply(context, "Emptying the area (whoever stands in it will fall)...")
        return 1
    }

    private fun leave(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.playerOrThrow
        if (!Ch2L1.active(player)) {
            reply(context, "You are not in the level.")
            return 0
        }
        Ch2L1.leave(player)
        reply(context, "You are out of the level: the rules of the rest of the game apply again.")
        return 1
    }

    private fun clock(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.playerOrThrow
        val hit = player.raycast(6.0, 1f, false)
        if (hit !is BlockHitResult || hit.type != HitResult.Type.BLOCK) {
            reply(context, "Look at a wall, within 6 blocks.")
            return 0
        }
        val side = hit.side
        if (!side.axis.isHorizontal) {
            reply(context, "Look at the side of a wall, not at its top or its bottom.")
            return 0
        }
        val pos = hit.blockPos.offset(side)
        val world = player.serverWorld
        if (!world.getBlockState(pos).isAir) {
            reply(context, "There is no room for a clock there.")
            return 0
        }
        world.setBlockState(pos, Ch2Blocks.TIME_CLOCK.defaultState.with(CardReaderBlock.FACING, side))
        reply(context, "A time clock hangs on the wall: right-click it to clock in (at the clock-in step).")
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
