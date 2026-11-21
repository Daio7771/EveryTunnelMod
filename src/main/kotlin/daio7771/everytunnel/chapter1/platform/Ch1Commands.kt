package daio7771.everytunnel.chapter1.platform

import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import daio7771.everytunnel.common.config.ConfigManager
import daio7771.everytunnel.common.core.LevelManager
import daio7771.everytunnel.common.core.PlayerLocationTracker
import daio7771.everytunnel.common.systems.NoiseSystem
import daio7771.everytunnel.common.systems.ResourceSystem
import daio7771.everytunnel.common.world.WorldRules
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.server.command.CommandManager
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import daio7771.everytunnel.chapter1.level4.platform.LoungeHooks
import daio7771.everytunnel.chapter1.level5.platform.LabHooks
import daio7771.everytunnel.common.platform.DebugCommands
import daio7771.everytunnel.common.core.level.LevelDefinition
import daio7771.everytunnel.common.platform.DevWorlds
import net.minecraft.server.network.ServerPlayerEntity
import daio7771.everytunnel.chapter1.level2.platform.PaleManEntity
import daio7771.everytunnel.chapter1.level3.platform.BookHooks
import daio7771.everytunnel.chapter1.level3.platform.BoxHooks
import daio7771.everytunnel.chapter1.level3.platform.ValveHooks
import daio7771.everytunnel.chapter1.level4.platform.FilmSetHooks
import daio7771.everytunnel.chapter1.level5.platform.DirectorSceneHooks

/**
 * Chapter 1's part of the `/everytunnel` commands, registered by [daio7771.everytunnel.chapter1.Chapter1]: the ones that finish a
 * level's build in a dev world (`loungeready`, `labready`, `filmready`) and, in a dev world, the Pale man, the director, the valves, the boxes
 * and books and the film set. They are subcommands of the shared `everytunnel` root (Brigadier merges them).
 */
object Ch1Commands {

    fun register() {
        DebugCommands.levelBuilders += { player: ServerPlayerEntity, level: LevelDefinition ->
            if (LoungeHooks.notBuiltYet(level) && LoungeHooks.toCorridor(player)) {
                // Level 4 does not stand until the corridor before it is walked into, which builds it.
                "Level 4 is built from here: wait for the door at the end to open."
            } else if (LabHooks.notBuiltYet(level) && LabHooks.buildFor(player)) {
                // Level 5 is built in play too; this takes you to its start once it stands.
                "Level 5 is being built: you go to its start in a few seconds."
            } else {
                null
            }
        }
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            val root = CommandManager.literal("everytunnel")
                .requires { it.hasPermissionLevel(2) }
                .then(CommandManager.literal("loungeready").executes(::loungeReady))
                .then(CommandManager.literal("labready").executes(::labReady))
                .then(CommandManager.literal("filmready").executes(::filmReady))
            if (DevWorlds.enabled) {
                root
                    .then(CommandManager.literal("congrats").executes { EndingHooks.previewCongrats(it.source.playerOrThrow); 1 })
                    .then(CommandManager.literal("paleman").executes(::paleMen))
                    .then(CommandManager.literal("director").executes(::director))
                    .then(CommandManager.literal("valves").executes(::valves))
                    .then(filmCommands())
                    .then(
                        CommandManager.literal("boxes").executes(::boxes)
                            .then(CommandManager.literal("fill").executes(::fillBoxes)),
                    )
                    .then(
                        CommandManager.literal("books").executes(::books)
                            .then(CommandManager.literal("fill").executes(::fillBooks)),
                    )
            }
            dispatcher.register(root)
        }
    }


    /** Before the director's door, his scene ready to play again (Level 5 must stand: `setlevel 5` first). */
    private fun director(context: CommandContext<ServerCommandSource>): Int {
        if (!DirectorSceneHooks.again(context.source.playerOrThrow)) {
            DebugCommands.reply(context, "Level 5 has no director's door point.")
            return 0
        }
        DebugCommands.reply(context, "Before the director's door: open it to see his scene.")
        return 1
    }

    private fun paleMen(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.playerOrThrow
        val near = player.serverWorld.getEntitiesByClass(
            PaleManEntity::class.java, player.boundingBox.expand(PALE_MAN_LIST_RANGE.toDouble()),
        ) { true }
        if (near.isEmpty()) DebugCommands.reply(context, "No Pale man within $PALE_MAN_LIST_RANGE blocks.")
        for (paleMan in near) {
            val target = paleMan.brain.target?.let { "%.0f, %.0f, %.0f".format(it.x, it.y, it.z) } ?: "-"
            val territory = paleMan.homeKey?.substringBefore('/')?.let(LevelManager::get)
                ?.spawnPoint(paleMan.homeKey!!.substringAfter('/'))?.zone
                ?.takeIf { paleMan.territory() != null } ?: "anywhere"
            DebugCommands.reply(
                context,
                "%s at [%.0f, %.0f, %.0f], %.0f blocks away, target [%s], stays in %s".format(
                    paleMan.brain.state.name.lowercase(), paleMan.x, paleMan.y, paleMan.z, paleMan.distanceTo(player), target, territory,
                ),
            )
        }
        return near.size
    }

    private const val PALE_MAN_LIST_RANGE = 128

    private fun filmCommands() = CommandManager.literal("film")
        .then(CommandManager.literal("set").executes { context ->
            FilmSetHooks.begin(context.source.playerOrThrow)
            DebugCommands.reply(context, "The set is being built. F1 hides the HUD. /everytunnel film walk, /everytunnel film back for another take.")
            1
        })
        .then(CommandManager.literal("walk").executes { context ->
            val walking = FilmSetHooks.walk(context.source.server)
            if (!walking) DebugCommands.reply(context, "Nobody there: /everytunnel film set first.")
            if (walking) 1 else 0
        })
        .then(CommandManager.literal("back").executes { context ->
            FilmSetHooks.back(context.source.server)
            1
        })

    private fun filmReady(context: CommandContext<ServerCommandSource>): Int {
        FilmSetHooks.ready(context.source.server)
        return 1
    }

    private fun valves(context: CommandContext<ServerCommandSource>): Int {
        val levels = LevelManager.all().filter { level -> level.spawnPoints.any { ValveHooks.VALVE_TAG in it.tags } }
        if (levels.isEmpty()) {
            DebugCommands.reply(context, "No level has valves.")
            return 0
        }
        for (level in levels) {
            DebugCommands.reply(context, "${level.id}: " + ValveHooks.values(level).entries.joinToString(", ") { (letter, digit) -> "$letter $digit" })
        }
        return 1
    }

    private fun boxes(context: CommandContext<ServerCommandSource>): Int {
        val lines = BoxHooks.report(context.source.server)
        if (lines.isEmpty()) DebugCommands.reply(context, "No level has a box shelf.")
        lines.forEach { DebugCommands.reply(context, it) }
        return lines.size
    }

    private fun fillBoxes(context: CommandContext<ServerCommandSource>): Int {
        val shelves = BoxHooks.fillAllButOne(context.source.server)
        DebugCommands.reply(context, if (shelves == 0) "No level has a box shelf." else "Shelf filled but for one place; one box left lying about.")
        return shelves
    }

    private fun books(context: CommandContext<ServerCommandSource>): Int {
        val lines = BookHooks.report(context.source.server)
        if (lines.isEmpty()) DebugCommands.reply(context, "No level has a book counter.")
        lines.forEach { DebugCommands.reply(context, it) }
        return lines.size
    }

    private fun fillBooks(context: CommandContext<ServerCommandSource>): Int {
        val levels = BookHooks.fillAllButOne(context.source.server)
        DebugCommands.reply(context, if (levels == 0) "No level has a book desk." else "The boxes are done and the books out, all but one already on the desk.")
        return levels
    }

    private fun loungeReady(context: CommandContext<ServerCommandSource>): Int {
        LoungeHooks.chunksReady()
        return 1
    }

    private fun labReady(context: CommandContext<ServerCommandSource>): Int {
        LabHooks.chunksReady()
        return 1
    }
}
