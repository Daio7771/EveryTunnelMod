package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.world.WorldRules
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.MinecraftServer
import net.minecraft.util.ActionResult
import net.minecraft.world.GameMode
import net.minecraft.world.GameRules

/**
 * Enforces [WorldRules] through Fabric events and vanilla mechanics.
 *
 * - Breaking: block attacks are cancelled (no mining progress), and breaking is
 *   refused on the server as a safety net.
 * - Placing and changing blocks with items (blocks, buckets, flint and steel,
 *   bone meal, hoes, ...): survival players get the `allowModifyWorld` ability set
 *   to false, the same flag Adventure mode uses. Doors, buttons and levers still
 *   work.
 * - Mob spawning: spawn related gamerules are set to false when a world with
 *   levels starts and after `/reload`. They are not restored automatically if the
 *   levels are removed (change them with /gamerule).
 */
object WorldRulesHooks {

    private val SPAWN_GAMERULES = listOf(
        GameRules.DO_MOB_SPAWNING,
        GameRules.DO_PATROL_SPAWNING,
        GameRules.DO_TRADER_SPAWNING,
        GameRules.DO_INSOMNIA,
        GameRules.DO_WARDEN_SPAWNING,
    )

    fun register() {
        // Fires on both client and server, so the client does not even start mining.
        AttackBlockCallback.EVENT.register { player, _, _, _, _ ->
            if (isRestricted(player)) ActionResult.FAIL else ActionResult.PASS
        }
        PlayerBlockBreakEvents.BEFORE.register { _, player, _, _, _ -> !isRestricted(player) }

        ServerTickEvents.END_SERVER_TICK.register { server ->
            for (player in server.playerManager.playerList) {
                syncAbilities(player, player.interactionManager.gameMode)
            }
        }
        ServerLifecycleEvents.SERVER_STARTED.register(::applyGameRules)
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register { server, _, success ->
            if (success) applyGameRules(server)
        }
    }

    fun isRestricted(player: PlayerEntity): Boolean =
        WorldRules.blocksRestrictedFor(player.isCreative, player.isSpectator)

    /**
     * Sets `allowModifyWorld` for survival players to match the rules. Called every
     * tick on the server and, for prediction, on the client (see EveryTunnelClient),
     * because vanilla resets the flag on game mode changes and does not sync it.
     * Other game modes keep their vanilla value.
     */
    fun syncAbilities(player: PlayerEntity, gameMode: GameMode?) {
        if (gameMode != GameMode.SURVIVAL) return
        val allow = !WorldRules.active
        if (player.abilities.allowModifyWorld != allow) {
            player.abilities.allowModifyWorld = allow
        }
    }

    private fun applyGameRules(server: MinecraftServer) {
        if (!WorldRules.active) return
        val changed = mutableListOf<String>()
        for (key in SPAWN_GAMERULES) {
            val rule = server.gameRules.get(key)
            if (rule.get()) {
                rule.set(false, server)
                changed += key.name
            }
        }
        if (changed.isNotEmpty()) {
            EveryTunnel.LOGGER.info("World rules active: set gamerule(s) {} to false", changed.joinToString())
        }
    }
}
