package daio7771.everytunnel.chapter2.level1.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Overtime
import daio7771.everytunnel.chapter2.level1.core.Ch2L1State
import daio7771.everytunnel.chapter2.platform.Ch2Progress
import daio7771.everytunnel.platform.ModSounds
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Identifier
import java.util.UUID

/**
 * The end of Chapter 2's Level 1 ([Ch2L1State.DONE]: the basement's last dirt swept, [Ch2L1TaskHooks]). The level is marked finished in the
 * player's progress ([Ch2L1Overtime.COMPLETED]), the basement's lights go out with a thud, and after [Ch2L1Overtime.darkTicks] in the dark
 * the client is told to end ([ENDING_PACKET]: the fade, the closing text, then the main menu; `client.Ch2L1EndingOverlay`). A player who
 * left the world before the menu and comes back is still at the end: the dark and the ending again. The level is not published alone, so
 * for now nothing follows it. Leaving [Ch2L1State.DONE] (a command) lights the basement again. Server thread only.
 */
object Ch2L1EndingHooks {

    /** To the client: the fade's ticks, the text's ticks and the text's lang key; when it is over, back to the menu. */
    val ENDING_PACKET = Identifier(EveryTunnel.MOD_ID, "ch2l1_ending")

    /** Players in the dark, and the ticks left before their ending. */
    private val pending = HashMap<UUID, Int>()

    private val overtime: Ch2L1Overtime get() = Ch2L1DataLoader.act3.overtime

    /** On joining at the end, this much more before the dark's count starts (the client's world loading). */
    private const val REJOIN_TICKS = 40

    fun register() {
        Ch2L1.onStateChange(::onState)
        ServerTickEvents.END_SERVER_TICK.register(::tick)
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> pending.remove(handler.player.uuid) }
        // Back in the world after the end (it was left in the dark, before the menu): the dark, and the end again.
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            val player = handler.player
            if (Ch2L1.active(player) && Ch2L1.state(player) == Ch2L1State.DONE) {
                if (Ch2L1Build.isBuilt(player.server)) Ch2L1Build.zoneLights(player.serverWorld, overtime.basementZone, true)
                pending[player.uuid] = overtime.darkTicks + REJOIN_TICKS
            }
        }
    }

    private fun onState(player: ServerPlayerEntity, from: Ch2L1State, to: Ch2L1State) {
        val world = player.serverWorld
        val built = Ch2L1Build.isBuilt(player.server)
        if (to == Ch2L1State.DONE) {
            Ch2Progress.setCh2L1Counter(player, Ch2L1Overtime.COMPLETED, 1)
            if (built) Ch2L1Build.zoneLights(world, overtime.basementZone, true)
            world.playSound(null, player.blockPos, ModSounds.LOUNGE_BLACKOUT, SoundCategory.BLOCKS, 1f, 1f)
            pending[player.uuid] = overtime.darkTicks
            EveryTunnel.LOGGER.info("Chapter 2 Level 1: {} has finished the level: the basement goes dark", player.gameProfile.name)
        } else if (from == Ch2L1State.DONE) {
            pending.remove(player.uuid)
            if (built) Ch2L1Build.zoneLights(world, overtime.basementZone, false)
        }
    }

    private fun tick(server: MinecraftServer) {
        if (pending.isEmpty()) return
        val iterator = pending.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value > 0) {
                entry.setValue(entry.value - 1)
                continue
            }
            iterator.remove()
            val player = server.playerManager.getPlayer(entry.key) ?: continue
            if (!ServerPlayNetworking.canSend(player, ENDING_PACKET)) continue
            val buf = PacketByteBufs.create()
            buf.writeVarInt(overtime.fadeTicks)
            buf.writeVarInt(overtime.textTicks)
            buf.writeString(overtime.text)
            ServerPlayNetworking.send(player, ENDING_PACKET, buf)
        }
    }
}
