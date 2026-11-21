package daio7771.everytunnel.chapter2.prologue.platform

import daio7771.everytunnel.common.platform.DevWorlds
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

/**
 * `/everytunnel prologue` (dev worlds only, operators): plays Chapter 2's opening cinematic from wherever you stand, building its room
 * out of the way and putting you back where you were when it ends ([PrologueSceneHooks]). It joins the base game's `/everytunnel`
 * (`common.platform.DebugCommands`), which Brigadier merges by name.
 */
object PrologueCommand {

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            if (!DevWorlds.enabled) return@register
            dispatcher.register(
                CommandManager.literal("everytunnel")
                    .requires { it.hasPermissionLevel(2) }
                    .then(CommandManager.literal("prologue").executes(::prologue)),
            )
        }
    }

    private fun prologue(context: CommandContext<ServerCommandSource>): Int {
        val why = PrologueSceneHooks.start(context.source.playerOrThrow)
        if (why != null) {
            context.source.sendFeedback({ Text.literal(why) }, false)
            return 0
        }
        context.source.sendFeedback({ Text.literal("The prologue begins: press jump twice to skip it.") }, false)
        return 1
    }
}
