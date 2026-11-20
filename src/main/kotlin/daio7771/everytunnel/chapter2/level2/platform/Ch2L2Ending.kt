package daio7771.everytunnel.chapter2.level2.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.chapter2.level2.core.Ch2L2Flood
import daio7771.everytunnel.chapter2.level2.core.Ch2L2State
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import java.util.UUID
import kotlin.math.roundToInt

/**
 * The end of Chapter 2's Level 2, in the service lift after the reunion (phase 9; in the reunion room in 6.1, at the exit's door before): [start] holds the player where they stand,
 * tells the client to fade to black over `ending.fade_seconds`, show the card (`ending.text`) for `card_seconds` and leave for the main
 * menu (`client.Ch2L2EndingOverlay`, [PACKET]). [Ch2L2Reunion] calls it when the lift's motor has run.
 *
 * [start] is the one, idempotent end: the first time it also puts the player in [Ch2L2State.DONE] and saves the `ending.checkpoint`
 * with it, so that CONTINUE stands in the lift. Nothing else changes in the world. A start of the level, leaving the world or a
 * death clears it. Server thread only.
 */
object Ch2L2Ending {

    /** To the client: the fade's ticks, the card's ticks and the card's lang key; the client leaves for the menu when it is over. */
    val PACKET = Identifier(EveryTunnel.MOD_ID, "ch2l2_ending")

    private val FREEZE_ID: UUID = UUID.fromString("0d6c1f3a-77e2-4c0b-9a1b-5e2f8a9d4b21")

    private val data get() = Ch2L2DataLoader.data

    private val running = HashSet<UUID>()

    fun register() {
        Ch2L2.onStart { player, _ -> release(player) }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> running.remove(handler.player.uuid) }
        ServerLifecycleEvents.SERVER_STOPPED.register { running.clear() }
    }

    fun isRunning(player: ServerPlayerEntity): Boolean = player.uuid in running

    /**
     * The ending for [player]. Unless [force] (the debug command), only from the reunion on. False, and nothing happens, if it is not
     * allowed or already running.
     */
    fun start(player: ServerPlayerEntity, force: Boolean = false): Boolean {
        val state = Ch2L2.state(player)
        val allowed = Ch2L2Flood.endingAllowed(state)
        if ((!allowed && !force) || player.uuid in running) return false
        val ending = data.level.ending
        running += player.uuid
        hold(player)
        if (allowed) {
            if (state < Ch2L2State.DONE) Ch2L2.setState(player, Ch2L2State.DONE, "the reunion is over")
            Ch2L2.takeCheckpoint(player, ending.checkpoint)
        }
        if (ServerPlayNetworking.canSend(player, PACKET)) {
            val buf = PacketByteBufs.create()
            buf.writeVarInt((ending.fadeSeconds * 20).roundToInt())
            buf.writeVarInt((ending.cardSeconds * 20).roundToInt())
            buf.writeString(ending.textKey)
            ServerPlayNetworking.send(player, PACKET, buf)
        } else {
            release(player)
        }
        EveryTunnel.LOGGER.info("Chapter 2 Level 2: {} reaches the end of the level: the card", player.gameProfile.name)
        return true
    }

    /** Held still where they stand (their speed taken away; they can still look). */
    private fun hold(player: ServerPlayerEntity) {
        player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED)?.let { speed ->
            if (speed.getModifier(FREEZE_ID) == null) {
                speed.addTemporaryModifier(EntityAttributeModifier(FREEZE_ID, "Every Tunnel: the end of the level", -1.0, EntityAttributeModifier.Operation.MULTIPLY_TOTAL))
            }
        }
        player.velocity = player.velocity.multiply(0.0, 1.0, 0.0)
    }

    private fun release(player: ServerPlayerEntity) {
        running.remove(player.uuid)
        player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED)?.removeModifier(FREEZE_ID)
    }
}
