package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.world.WorldRules
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import java.util.Locale
import java.util.UUID

/**
 * Opening a world with levels: the map is rebuilt and the player starts a new game, behind
 * the loading screen (`client.screen.LoadingScreen`).
 *
 * - When the server starts, every level's `build` function runs once (the map's
 *   `garage:build`; levels built together name the same one). It force-loads the map,
 *   waits for its chunks, builds, and ends with `/everytunnel rebuilt` ([levelsRebuilt]).
 *   Until then the world is building.
 * - A player who joins a world with levels is loading: the client shows the loading screen
 *   ([STATE_LOADING]), and the player cannot move, be hurt or make a noise, their
 *   resources do not drain, and no intro starts.
 * - Once the world is built, a Survival or Adventure player starts a new game
 *   ([NewGame.start]): every level is reset and they wait in the closed elevator. Creative
 *   and Spectator players stay where they are. The client is told ([STATE_READY]); it waits
 *   until the terrain around the player is drawn, fades the screen out and answers
 *   ([DONE_PACKET_ID]). Then the player is free and the intro starts ([IntroHooks]).
 * - Safety nets: a build that never reports back is given up after [BUILD_TIMEOUT_TICKS],
 *   and a client that never answers is let go after [READY_TIMEOUT_TICKS].
 */
object LoadingHooks {

    /** To the client, one byte: [STATE_LOADING] or [STATE_READY]. */
    val STATE_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "loading")

    /** From the client, no payload: the loading screen has faded out. */
    val DONE_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "loading_done")

    /** Show the loading screen: the world is being prepared. */
    const val STATE_LOADING: Byte = 0

    /** The world is ready: fade the loading screen out once the terrain is drawn. */
    const val STATE_READY: Byte = 1

    private const val BUILD_TIMEOUT_TICKS = 20L * 120
    private const val READY_TIMEOUT_TICKS = 20L * 30
    /** How long to wait for the client to register the loading channel before going on without it. */
    private const val CHANNEL_WAIT_TICKS = 20L * 5

    private val FREEZE_ID: UUID = UUID.fromString("0f6c2d4a-7b1e-4f39-8c55-2a9e61d3b7c4")

    private class Loading(val joinedTick: Long) {
        var sentLoading = false
        var prepared = false
        /** Server tick [STATE_READY] was sent, or -1. */
        var readyTick = -1L
        var done = false
    }

    // Server thread only.
    private val loading = HashMap<UUID, Loading>()
    private var building = false
    private var buildStartTick = 0L

    fun register() {
        ServerLifecycleEvents.SERVER_STARTED.register(::startBuilds)
        ServerLifecycleEvents.SERVER_STOPPED.register {
            loading.clear()
            building = false
        }
        ServerPlayConnectionEvents.JOIN.register { handler, _, server -> join(handler.player, server) }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            if (loading.remove(handler.player.uuid) != null) unfreeze(handler.player)
        }
        ServerPlayNetworking.registerGlobalReceiver(DONE_PACKET_ID) { server, player, _, _, _ ->
            server.execute { loading[player.uuid]?.done = true }
        }
        ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, _, _ -> entity.uuid !in loading }
        ServerTickEvents.END_SERVER_TICK.register(::tick)
    }

    /** True from joining until the loading screen is gone. */
    fun isLoading(player: ServerPlayerEntity): Boolean = player.uuid in loading

    /** The map's build finished (`/everytunnel rebuilt`, its last command). */
    fun levelsRebuilt(server: MinecraftServer) {
        if (!building) return
        building = false
        EveryTunnel.LOGGER.info("Map built in {} s", "%.1f".format(Locale.ROOT, (server.ticks - buildStartTick) / 20.0))
    }

    private fun startBuilds(server: MinecraftServer) {
        building = false
        val ids = LevelManager.all().sortedBy { it.order }.mapNotNull { it.build }.distinct()
        if (ids.isEmpty()) return
        val functions = server.commandFunctionManager
        // Silent: the build's own messages and the commands' feedback are not for the player.
        val source = server.commandSource.withSilent()
        buildStartTick = server.ticks.toLong()
        for (id in ids) {
            val function = Identifier.tryParse(id)?.let { functions.getFunction(it).orElse(null) }
            if (function == null) {
                EveryTunnel.LOGGER.warn("Level build function {} not found: that part of the map is not rebuilt", id)
                continue
            }
            functions.execute(function, source)
            building = true
        }
        if (building) EveryTunnel.LOGGER.info("Building the map: {}", ids.joinToString())
    }

    private fun join(player: ServerPlayerEntity, server: MinecraftServer) {
        if (!WorldRules.active) return
        loading[player.uuid] = Loading(server.ticks.toLong())
        freeze(player)
    }

    private fun tick(server: MinecraftServer) {
        val now = server.ticks.toLong()
        if (building && now - buildStartTick > BUILD_TIMEOUT_TICKS) {
            EveryTunnel.LOGGER.warn(
                "The map build did not finish (no /everytunnel rebuilt) in {} s: going on without it",
                BUILD_TIMEOUT_TICKS / 20,
            )
            building = false
        }
        val iterator = loading.entries.iterator()
        while (iterator.hasNext()) {
            val (id, state) = iterator.next()
            val player = server.playerManager.getPlayer(id)
            if (player == null) {
                iterator.remove()
                continue
            }
            val canSend = ServerPlayNetworking.canSend(player, STATE_PACKET_ID)
            if (!state.sentLoading && canSend) {
                send(player, STATE_LOADING)
                state.sentLoading = true
            }
            if (building) continue

            if (!state.prepared) {
                state.prepared = true
                if (WorldRules.appliesTo(player.isCreative, player.isSpectator)) NewGame.start(player)
            }
            if (state.readyTick < 0) {
                if (canSend) {
                    send(player, STATE_READY)
                    state.readyTick = now
                } else if (now - state.joinedTick > CHANNEL_WAIT_TICKS) {
                    // A client without the loading screen: nothing to wait for.
                    iterator.remove()
                    unfreeze(player)
                }
                continue
            }
            val timedOut = now - state.readyTick > READY_TIMEOUT_TICKS
            if (state.done || timedOut) {
                if (timedOut) EveryTunnel.LOGGER.warn("{}: the loading screen did not report back, going on", player.name.string)
                iterator.remove()
                unfreeze(player)
                EveryTunnel.LOGGER.info("{}: loaded", player.name.string)
            }
        }
    }

    private fun send(player: ServerPlayerEntity, state: Byte) {
        val buf = PacketByteBufs.create()
        buf.writeByte(state.toInt())
        ServerPlayNetworking.send(player, STATE_PACKET_ID, buf)
    }

    private fun freeze(player: ServerPlayerEntity) {
        player.isSprinting = false
        player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED)?.let { speed ->
            if (speed.getModifier(FREEZE_ID) == null) {
                speed.addTemporaryModifier(
                    EntityAttributeModifier(FREEZE_ID, "Every Tunnel: loading", -1.0, EntityAttributeModifier.Operation.MULTIPLY_TOTAL),
                )
            }
        }
    }

    private fun unfreeze(player: ServerPlayerEntity) {
        player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED)?.removeModifier(FREEZE_ID)
    }
}
