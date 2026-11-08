package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.block.BlockState
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.network.PacketByteBuf
import net.minecraft.server.network.ServerPlayerEntity
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
 * ([text]). The Office's, `terminal_office`, tells the chemical puzzle's answer ([ChemicalHooks.mix]); the
 * lore round it comes later, so do the other screens' texts.
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
        buf.writeString(lines.joinToString("\n"))
        ServerPlayNetworking.send(player, PACKET_ID, buf)
    }

    /** What the computer [id] shows. */
    fun text(player: ServerPlayerEntity, id: String?): List<String> = when (id) {
        "terminal_office" -> {
            val mix = ChemicalHooks.mix(player.server)
            listOf(
                "LOWER LABS - CHEMICAL STORE",
                "",
                "Drain grate, solvent: R-${mix.first} with R-${mix.second}.",
                "",
                "[Placeholder: the rest of this log comes later.]",
            )
        }
        else -> listOf("SESSION LOCKED", "", "[Placeholder: this screen's text comes later.]")
    }

    fun readText(buf: PacketByteBuf): List<String> = buf.readString().split('\n')
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
