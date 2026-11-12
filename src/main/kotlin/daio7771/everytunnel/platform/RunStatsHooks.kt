package daio7771.everytunnel.platform

import net.minecraft.server.MinecraftServer
import daio7771.everytunnel.world.WorldRules

/**
 * Counts the time played, for the congratulations window ([EndingHooks]): a tick of the server is a tick played by
 * each player the world rules apply to who is past the loading screen. The server does not tick while the game
 * is paused or closed, so the count is the time actually played. It goes into the player's progress
 * ([ProgressService.addPlayTicks], saved with the world), so it adds up over the sessions of one game and
 * starts again with a new one. Deaths are counted in [DeathHooks].
 */
object RunStatsHooks {

    fun register() {
        TickProfiler.endServerTick.register(::tick)
    }

    private fun tick(server: MinecraftServer) {
        val progress = ProgressService.get()
        for (player in server.playerManager.playerList) {
            if (!WorldRules.appliesTo(player.isCreative, player.isSpectator) || LoadingHooks.isLoading(player)) continue
            progress.addPlayTicks(player, 1)
        }
    }
}
