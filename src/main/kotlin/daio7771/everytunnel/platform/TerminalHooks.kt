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
        buf.writeString(lines.joinToString("\n"))
        ServerPlayNetworking.send(player, PACKET_ID, buf)
    }

    /**
     * What the computer [id] shows, line by line; a line starting with [RED] is drawn red. The Office's is
     * the lab head's work log, with the chemical puzzle's answer; Storage's, its inventory (two gowns more
     * than rooms); the rest are locked, asking for a password, the last account in use left on screen.
     */
    fun text(player: ServerPlayerEntity, id: String?): List<String> = when (id) {
        "terminal_office" -> {
            val mix = ChemicalHooks.mix(player.server)
            listOf(
                "LOWER LABS - WORK LOG",
                "",
                "Day 1. Head office came down with a tablet. The budget for the lower labs is gone. End of the month. Staff to leave.",
                "",
                "Day 30. They have left. I have kept the schedule. It seemed rude to stop in the middle.",
                "",
                "Day 41. The chemical room's drain backed up. I sealed it with a grate.",
                "To open it: R-${mix.first} with R-${mix.second} in the mixer. Nothing else.",
                "The wrong pair sets off the alarm. The alarm brings them.",
                "",
                "Day 77. Something in the Lab hall again. Do not look at it. Turn round, walk back the way you came. It goes.",
            )
        }
        "terminal_storage" -> listOf(
            "STORAGE - INVENTORY",
            "",
            "Patient gowns, issued: 6",
            "Subjects in rooms: 4",
            "Meal trays, daily: 4",
            "Snacks: 2",
            "Water: 2",
            "Batteries: 2",
            "Restraints: 4",
        )
        else -> listOf("${RED}SESSION LOCKED", "", "Log in to continue.", "", "User: RTA", "Password: ________")
    }

    /** A line starting with this is drawn red (`TerminalScreen`). */
    const val RED = "!"

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
