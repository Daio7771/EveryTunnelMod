package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.block.BlockState
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.network.PacketByteBuf
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.sound.SoundCategory
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * Level 5's computers ([LabTerminalBlock]): a right click shows what is on the screen, typed out line by line
 * on the client's `TerminalScreen`. Each is a spawn point tagged [TAG] at its block; its id says what it shows
 * ([text]). The Office's, `terminal_office`, tells the chemical puzzle's answer ([ChemicalHooks.mix]) in the lab
 * head's work log; Storage's, `terminal_storage`, lists what is kept there; the others are locked.
 */
object TerminalHooks {

    const val TAG = "terminal"

    /** To the client: the screen's lines, one string, lines split by '\n'. */
    val PACKET_ID = Identifier(EveryTunnel.MOD_ID, "terminal")

    fun open(player: ServerPlayerEntity, pos: BlockPos) {
        val level = LevelManager.levelAt(player.serverWorld.registryKey.value.toString(), pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
        val id = level?.spawnPoints?.firstOrNull { TAG in it.tags && BlockPos.ofFloored(it.position.x, it.position.y, it.position.z) == pos }?.id
        val lines = text(player, id)
        player.serverWorld.playSound(null, pos, ModSounds.TERMINAL_KEY, SoundCategory.BLOCKS, 0.6f, 1f)
        if (!ServerPlayNetworking.canSend(player, PACKET_ID)) return
        val buf = PacketByteBufs.create()
        buf.writeVarInt(lines.size)
        for (line in lines) {
            buf.writeString(line.key)
            buf.writeBoolean(line.red)
            buf.writeVarInt(line.args.size)
            line.args.forEach { buf.writeString(it) }
        }
        ServerPlayNetworking.send(player, PACKET_ID, buf)
    }

    /**
     * One line of a screen: [key] is a translation key (`terminal.every_tunnel.*`, empty for a blank line), filled
     * with [args]; the client translates it, so the screen follows the player's language. [red] draws it red.
     */
    data class Line(val key: String = "", val red: Boolean = false, val args: List<String> = emptyList())

    /**
     * What the computer [id] shows, line by line. The Office's is the lab head's work log, with the chemical
     * puzzle's answer; Storage's, its inventory (two gowns more than rooms); the rest are locked, asking for a
     * password, the last account in use left on screen.
     */
    fun text(player: ServerPlayerEntity, id: String?): List<Line> = when (id) {
        "terminal_office" -> {
            val mix = ChemicalHooks.mix(player.server)
            listOf(
                Line("terminal.every_tunnel.office.title"),
                Line(),
                Line("terminal.every_tunnel.office.day_1"),
                Line(),
                Line("terminal.every_tunnel.office.day_30"),
                Line(),
                Line("terminal.every_tunnel.office.day_41_drain"),
                Line("terminal.every_tunnel.office.day_41_mix", args = listOf(mix.first.toString(), mix.second.toString())),
                Line("terminal.every_tunnel.office.day_41_alarm"),
                Line(),
                Line("terminal.every_tunnel.office.day_77"),
            )
        }
        "terminal_storage" -> listOf(
            Line("terminal.every_tunnel.storage.title"),
            Line(),
            Line("terminal.every_tunnel.storage.gowns"),
            Line("terminal.every_tunnel.storage.subjects"),
            Line("terminal.every_tunnel.storage.trays"),
            Line("terminal.every_tunnel.storage.snacks"),
            Line("terminal.every_tunnel.storage.water"),
            Line("terminal.every_tunnel.storage.batteries"),
            Line("terminal.every_tunnel.storage.restraints"),
        )
        else -> listOf(
            Line("terminal.every_tunnel.locked.title", red = true),
            Line(),
            Line("terminal.every_tunnel.locked.log_in"),
            Line(),
            Line("terminal.every_tunnel.locked.user"),
            Line("terminal.every_tunnel.locked.password"),
        )
    }

    /** A line starting with this is drawn red (`TerminalScreen`). */
    const val RED = "!"

    /** The screen's lines as the client's language reads them, a red one marked with [RED]. */
    fun readText(buf: PacketByteBuf): List<String> = List(buf.readVarInt()) {
        val key = buf.readString()
        val red = buf.readBoolean()
        val args = Array<Any>(buf.readVarInt()) { buf.readString() }
        val text = if (key.isEmpty()) "" else Text.translatable(key, *args).string
        if (red) RED + text else text
    }
}

/** A lab computer on a desk or a bench (Level 5): a right click reads its screen ([TerminalHooks]). */
class LabTerminalBlock(settings: Settings, boxes: List<BlockShapes.Box>) : PropBlock(settings, boxes) {

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult {
        if (world.isClient) return ActionResult.SUCCESS
        TerminalHooks.open(player as ServerPlayerEntity, pos)
        return ActionResult.CONSUME
    }
}
