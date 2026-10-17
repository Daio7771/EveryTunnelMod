package daio7771.everytunnel.platform

import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.context.CommandContext
import daio7771.everytunnel.config.ConfigManager
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.PlayerLocationTracker
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
 * - `/everytunnel resources`: your stamina and thirst.
 * - `/everytunnel resources set (stamina|thirst) <value>`: changes one of them.
 */
object DebugCommands {

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                CommandManager.literal("everytunnel")
                    .requires { it.hasPermissionLevel(2) }
                    .then(CommandManager.literal("levels").executes(::levels))
                    .then(CommandManager.literal("where").executes(::where))
                    .then(
                        CommandManager.literal("resources").executes(::resources)
                            .then(
                                CommandManager.literal("set")
                                    .then(setResource("stamina"))
                                    .then(setResource("thirst")),
                            ),
                    ),
            )
        }
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
                    else -> state.copy(thirst = value.coerceAtMost(config.thirst.max))
                },
            )
            resources(context)
        },
    )

    private fun reply(context: CommandContext<ServerCommandSource>, message: String) {
        context.source.sendFeedback({ Text.literal(message) }, false)
    }
}
