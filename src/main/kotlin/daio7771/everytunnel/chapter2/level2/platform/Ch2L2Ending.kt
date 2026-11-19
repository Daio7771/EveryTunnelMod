package daio7771.everytunnel.chapter2.level2.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.chapter2.level2.core.Ch2L2Flood
import daio7771.everytunnel.chapter2.level2.core.Ch2L2State
import daio7771.everytunnel.chapter2.platform.Ch2Sounds
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Identifier
import java.util.UUID
import kotlin.math.roundToInt

/**
 * The end of Chapter 2's Level 2 (phase 6): the exit's heavy door. A player from the station on ([Ch2L2Flood.endingAllowed]) who
 * walks into `ending.zone` is held still, the door's sound plays (a wheel turned, two bolts), after `door_sound_seconds` the client is told to
 * fade to black over `fade_seconds`, show the card (`ending.text`) for `card_seconds` and leave for the main menu
 * (`client.Ch2L2EndingOverlay`, [PACKET]).
 *
 * [start] is the one, idempotent end: the first time it also puts the player in [Ch2L2State.DONE] and saves the `ending.checkpoint`
 * with it, so that CONTINUE stands in front of the door, where walking in plays it again. Nothing else changes in the world.
 * A start of the level, leaving the world or a death clears it. Server thread only.
 */
object Ch2L2Ending {

    /** To the client: the fade's ticks, the card's ticks and the card's lang key; the client leaves for the menu when it is over. */
    val PACKET = Identifier(EveryTunnel.MOD_ID, "ch2l2_ending")

    private val FREEZE_ID: UUID = UUID.fromString("0d6c1f3a-77e2-4c0b-9a1b-5e2f8a9d4b21")
    private const val CHECK_EVERY = 5

    private val data get() = Ch2L2DataLoader.data

    /** Players at the door and the ticks since they got there; [Running.told] once the client has been sent the fade. */
    private class Running {
        var ticks = 0
        var told = false
    }

    private val running = HashMap<UUID, Running>()

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register(::tick)
        Ch2L2.onStart { player, _ -> release(player) }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> running.remove(handler.player.uuid) }
        ServerLifecycleEvents.SERVER_STOPPED.register { running.clear() }
    }

    fun isRunning(player: ServerPlayerEntity): Boolean = player.uuid in running

    /**
     * The ending for [player]. Unless [force] (the debug command), only from the station on. False, and nothing happens, if it is not
     * allowed or already running.
     */
    fun start(player: ServerPlayerEntity, force: Boolean = false): Boolean {
        val state = Ch2L2.state(player)
        val allowed = Ch2L2Flood.endingAllowed(state)
        if ((!allowed && !force) || player.uuid in running) return false
        val ending = data.level.ending
        running[player.uuid] = Running()
        hold(player)
        if (allowed) {
            if (state < Ch2L2State.DONE) Ch2L2.setState(player, Ch2L2State.DONE, "the exit door")
            Ch2L2.takeCheckpoint(player, ending.checkpoint)
        }
        val world = player.serverWorld
        val at = data.anchors.point(DOOR_ANCHOR)?.position
        if (at != null) world.playSound(null, at.x, at.y, at.z, Ch2Sounds.CH2L2_EXIT_DOOR, SoundCategory.BLOCKS, 1.5f, 1.0f)
        else world.playSound(null, player.blockPos, Ch2Sounds.CH2L2_EXIT_DOOR, SoundCategory.BLOCKS, 1.5f, 1.0f)
        EveryTunnel.LOGGER.info("Chapter 2 Level 2: {} reaches the exit door: the ending", player.gameProfile.name)
        return true
    }

    private fun tick(server: MinecraftServer) {
        val ending = data.level.ending
        if (server.ticks % CHECK_EVERY == 0) {
            val dimension = data.anchors.dimension
            val zone = data.anchors.zone(ending.zone)?.box
            if (zone != null) {
                for (player in server.playerManager.playerList) {
                    if (!Ch2L2.active(player) || !player.isAlive || player.isSpectator || player.uuid in running) continue
                    if (player.serverWorld.registryKey.value.toString() != dimension) continue
                    if (zone.contains(player.x, player.y, player.z)) start(player)
                }
            }
        }
        if (running.isEmpty()) return
        val iterator = running.entries.iterator()
        while (iterator.hasNext()) {
            val (id, run) = iterator.next()
            val player = server.playerManager.getPlayer(id)
            if (player == null) {
                iterator.remove()
                continue
            }
            run.ticks++
            if (!run.told && run.ticks >= (ending.doorSoundSeconds * 20).roundToInt()) {
                run.told = true
                if (ServerPlayNetworking.canSend(player, PACKET)) {
                    val buf = PacketByteBufs.create()
                    buf.writeVarInt((ending.fadeSeconds * 20).roundToInt())
                    buf.writeVarInt((ending.cardSeconds * 20).roundToInt())
                    buf.writeString(ending.textKey)
                    ServerPlayNetworking.send(player, PACKET, buf)
                } else {
                    release(player)
                    iterator.remove()
                }
            }
        }
    }

    /** Held still where they stand (their speed taken away; they can still look). */
    private fun hold(player: ServerPlayerEntity) {
        player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED)?.let { speed ->
            if (speed.getModifier(FREEZE_ID) == null) {
                speed.addTemporaryModifier(EntityAttributeModifier(FREEZE_ID, "Every Tunnel: the exit door", -1.0, EntityAttributeModifier.Operation.MULTIPLY_TOTAL))
            }
        }
        player.velocity = player.velocity.multiply(0.0, 1.0, 0.0)
    }

    private fun release(player: ServerPlayerEntity) {
        running.remove(player.uuid)
        player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED)?.removeModifier(FREEZE_ID)
    }

    private const val DOOR_ANCHOR = "exit_hatch"
}
