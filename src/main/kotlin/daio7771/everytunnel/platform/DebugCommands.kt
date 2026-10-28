package daio7771.everytunnel.platform

import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.context.CommandContext
import daio7771.everytunnel.config.ConfigManager
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.PlayerLocationTracker
import daio7771.everytunnel.systems.NoiseSystem
import daio7771.everytunnel.systems.ResourceSystem
import daio7771.everytunnel.world.WorldRules
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

/**
 * Developer commands for building and checking the map (operator level 2,
 * i.e. "Allow Commands" in singleplayer):
 *
 * - `/everytunnel levels`: loaded levels with their zones and spawn points.
 * - `/everytunnel where`: the level and zones you are standing in, and whether
 *   the world rules are active.
 * - `/everytunnel resources`: your stamina, thirst and flashlight battery.
 * - `/everytunnel resources set (stamina|thirst|battery) <value>`: changes one of
 *   them (battery in seconds of light).
 * - `/everytunnel noise`: the noises of the last 5 seconds near you.
 * - `/everytunnel noise watch`: shows each noise you make in the action bar (toggle).
 * - `/everytunnel paleman`: the Pale men near you, what they are doing and where they are going.
 * - `/everytunnel intro`: plays the new-game intro (the elevator) again for you (see [IntroHooks]).
 * - `/everytunnel newgame`: starts the game over in this world (see [NewGame]).
 * - `/everytunnel valves`: this game's valve digits, per level with valves (see [ValveHooks]).
 * - `/everytunnel rebuilt`: run by the map's `garage:build` after rebuilding the levels:
 *   puts the loot back ([NewGame.levelsRebuilt]).
 * - `/everytunnel devworld save|forget` (development only): make this world start from
 *   how it is when you quit, on every launch, or stop doing that (see [DevWorlds]).
 */
object DebugCommands {

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            val root = CommandManager.literal("everytunnel")
                .requires { it.hasPermissionLevel(2) }
                .then(CommandManager.literal("levels").executes(::levels))
                .then(CommandManager.literal("where").executes(::where))
                .then(
                    CommandManager.literal("resources").executes(::resources)
                        .then(
                            CommandManager.literal("set")
                                .then(setResource("stamina"))
                                .then(setResource("thirst"))
                                .then(setResource("battery")),
                        ),
                )
                .then(
                    CommandManager.literal("noise").executes(::noise)
                        .then(CommandManager.literal("watch").executes(::watchNoise)),
                )
                .then(CommandManager.literal("paleman").executes(::paleMen))
                .then(CommandManager.literal("intro").executes(::intro))
                .then(CommandManager.literal("newgame").executes(::newGame))
                .then(CommandManager.literal("valves").executes(::valves))
                .then(
                    CommandManager.literal("boxes").executes(::boxes)
                        .then(CommandManager.literal("fill").executes(::fillBoxes)),
                )
                .then(
                    CommandManager.literal("books").executes(::books)
                        .then(CommandManager.literal("fill").executes(::fillBooks)),
                )
                .then(CommandManager.literal("rebuilt").executes(::rebuilt))
            if (DevWorlds.enabled) {
                root.then(
                    CommandManager.literal("devworld")
                        .then(CommandManager.literal("save").executes(::saveDevWorld))
                        .then(CommandManager.literal("forget").executes(::forgetDevWorld)),
                )
            }
            dispatcher.register(root)
        }
    }

    private fun saveDevWorld(context: CommandContext<ServerCommandSource>): Int {
        val name = DevWorlds.saveOnStop(context.source.server)
        reply(context, "When you quit, \"$name\" is saved as it is then, and every launch starts from there.")
        return 1
    }

    private fun forgetDevWorld(context: CommandContext<ServerCommandSource>): Int {
        val had = DevWorlds.forget(context.source.server)
        reply(context, if (had) "Template deleted: this world is kept between launches again." else "This world has no template.")
        return if (had) 1 else 0
    }

    private fun levels(context: CommandContext<ServerCommandSource>): Int {
        val levels = LevelManager.all()
        if (levels.isEmpty()) {
            reply(context, "No Every Tunnel levels in this world's datapacks.")
            return 0
        }
        for (level in levels) {
            reply(context, "${level.id} \"${level.name}\" (order ${level.order}, ${level.dimension})")
            for (zone in level.zones) {
                val b = zone.box
                val tags = if (zone.tags.isEmpty()) "" else " tags ${zone.tags}"
                reply(context, "  zone ${zone.id}: [${b.minX}, ${b.minY}, ${b.minZ}] to [${b.maxX}, ${b.maxY}, ${b.maxZ}]$tags")
            }
            for (spawn in level.spawnPoints) {
                val p = spawn.position
                val tags = if (spawn.tags.isEmpty()) "" else " tags ${spawn.tags}"
                reply(context, "  spawn ${spawn.id}: [${p.x}, ${p.y}, ${p.z}] yaw ${spawn.yaw}$tags")
            }
        }
        return levels.size
    }

    private fun where(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.playerOrThrow
        val location = PlayerLocationTracker.locationOf(player.uuid)
        val pos = "[%.1f, %.1f, %.1f]".format(player.x, player.y, player.z)
        reply(context, "Position $pos in ${LevelTrackingHooks.dimensionOf(player)}")
        if (location.levelId == null) {
            reply(context, "Not inside any level.")
        } else {
            reply(context, "Level: ${location.levelId}")
            reply(context, "Zones: ${location.zones.joinToString { "${it.levelId}/${it.zoneId}" }}")
        }
        reply(context, "World rules: ${if (WorldRules.active) "active" else "inactive (no levels)"}")
        return 1
    }

    private fun resources(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.playerOrThrow
        val config = ConfigManager.gameplay
        val state = ResourceSystem.stateOf(player.uuid, config)
        reply(context, "Stamina: %.1f / %.1f%s".format(state.stamina, config.stamina.max, if (state.exhausted) " (exhausted)" else ""))
        reply(context, "Thirst: %.1f / %.1f".format(state.thirst, config.thirst.max))
        val flashlight = when {
            !FlashlightHooks.available(player) -> "not available here"
            state.flashlightOn -> "on"
            else -> "off"
        }
        reply(context, "Battery: %.1f / %.1f s (flashlight %s)".format(state.battery, config.flashlight.batteryMaxSeconds, flashlight))
        if (!ResourceHooks.appliesTo(player)) {
            reply(context, "Resources are paused (no levels in this world, or Creative/Spectator).")
        }
        return 1
    }

    private fun setResource(name: String) = CommandManager.literal(name).then(
        CommandManager.argument("value", DoubleArgumentType.doubleArg(0.0)).executes { context ->
            val player = context.source.playerOrThrow
            val config = ConfigManager.gameplay
            val value = DoubleArgumentType.getDouble(context, "value")
            val state = ResourceSystem.stateOf(player.uuid, config)
            ResourceSystem.set(
                player.uuid,
                when (name) {
                    "stamina" -> state.copy(stamina = value.coerceAtMost(config.stamina.max))
                    "thirst" -> state.copy(thirst = value.coerceAtMost(config.thirst.max))
                    else -> state.copy(battery = value.coerceAtMost(config.flashlight.batteryMaxSeconds))
                },
            )
            resources(context)
        },
    )

    private fun noise(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.playerOrThrow
        val now = player.server.ticks.toLong()
        val dimension = LevelTrackingHooks.dimensionOf(player)
        val near = NoiseSystem.recent().filter {
            it.dimension == dimension && it.distanceSqTo(player.x, player.y, player.z) <= NOISE_LIST_RANGE * NOISE_LIST_RANGE
        }
        if (near.isEmpty()) reply(context, "No noise within $NOISE_LIST_RANGE blocks in the last 5 seconds.")
        for (event in near.take(15)) {
            reply(
                context,
                "%s %.1f blocks at [%.0f, %.0f, %.0f], %.1f s ago".format(
                    event.source.id, event.radius, event.x, event.y, event.z, (now - event.tick) / 20.0,
                ),
            )
        }
        if (!NoiseHooks.makesNoise(player)) reply(context, "You make no noise (no levels in this world, or Creative/Spectator).")
        return near.size
    }

    private fun watchNoise(context: CommandContext<ServerCommandSource>): Int {
        val on = NoiseHooks.toggleWatch(context.source.playerOrThrow)
        reply(context, "Noise readout in the action bar: ${if (on) "on" else "off"}")
        return 1
    }

    private const val NOISE_LIST_RANGE = 32

    private fun paleMen(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.playerOrThrow
        val near = player.serverWorld.getEntitiesByClass(
            PaleManEntity::class.java, player.boundingBox.expand(PALE_MAN_LIST_RANGE.toDouble()),
        ) { true }
        if (near.isEmpty()) reply(context, "No Pale man within $PALE_MAN_LIST_RANGE blocks.")
        for (paleMan in near) {
            val target = paleMan.brain.target?.let { "%.0f, %.0f, %.0f".format(it.x, it.y, it.z) } ?: "-"
            val territory = paleMan.homeKey?.substringBefore('/')?.let(LevelManager::get)
                ?.spawnPoint(paleMan.homeKey!!.substringAfter('/'))?.zone
                ?.takeIf { paleMan.territory() != null } ?: "anywhere"
            reply(
                context,
                "%s at [%.0f, %.0f, %.0f], %.0f blocks away, target [%s], stays in %s".format(
                    paleMan.brain.state.name.lowercase(), paleMan.x, paleMan.y, paleMan.z, paleMan.distanceTo(player), target, territory,
                ),
            )
        }
        return near.size
    }

    private const val PALE_MAN_LIST_RANGE = 128

    private fun intro(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.playerOrThrow
        val level = IntroHooks.replay(player)
        if (level == null) {
            reply(context, "No level has an intro.")
            return 0
        }
        reply(context, "Intro of ${level.id} again: it starts in the elevator (in Survival or Adventure).")
        return 1
    }

    private fun newGame(context: CommandContext<ServerCommandSource>): Int {
        val level = NewGame.start(context.source.playerOrThrow)
        if (level == null) {
            reply(context, "No level with a start in this world.")
            return 0
        }
        reply(context, "New game: shutters open, Pale men home, inventory cleared, back at the start of ${level.id}.")
        return 1
    }

    private fun valves(context: CommandContext<ServerCommandSource>): Int {
        val levels = LevelManager.all().filter { level -> level.spawnPoints.any { ValveHooks.VALVE_TAG in it.tags } }
        if (levels.isEmpty()) {
            reply(context, "No level has valves.")
            return 0
        }
        for (level in levels) {
            reply(context, "${level.id}: " + ValveHooks.values(level).entries.joinToString(", ") { (letter, digit) -> "$letter $digit" })
        }
        return 1
    }

    private fun boxes(context: CommandContext<ServerCommandSource>): Int {
        val lines = BoxHooks.report(context.source.server)
        if (lines.isEmpty()) reply(context, "No level has a box shelf.")
        lines.forEach { reply(context, it) }
        return lines.size
    }

    private fun fillBoxes(context: CommandContext<ServerCommandSource>): Int {
        val shelves = BoxHooks.fillAllButOne(context.source.server)
        reply(context, if (shelves == 0) "No level has a box shelf." else "Shelf filled but for one place; one box left lying about.")
        return shelves
    }

    private fun books(context: CommandContext<ServerCommandSource>): Int {
        val lines = BookHooks.report(context.source.server)
        if (lines.isEmpty()) reply(context, "No level has a book counter.")
        lines.forEach { reply(context, it) }
        return lines.size
    }

    private fun fillBooks(context: CommandContext<ServerCommandSource>): Int {
        val levels = BookHooks.fillAllButOne(context.source.server)
        reply(context, if (levels == 0) "No level has a book counter." else "The books are out, all but one already on the counter.")
        return levels
    }

    private fun rebuilt(context: CommandContext<ServerCommandSource>): Int {
        NewGame.levelsRebuilt(context.source.server)
        reply(context, "Levels rebuilt: loot back in place.")
        return 1
    }

    private fun reply(context: CommandContext<ServerCommandSource>, message: String) {
        context.source.sendFeedback({ Text.literal(message) }, false)
    }
}
