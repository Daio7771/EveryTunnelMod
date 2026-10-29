package daio7771.everytunnel.platform

import net.minecraft.block.BlockState
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * A loose typed sheet lying on a desk or a cabinet: Level 3's company documents (the lore).
 * Right-clicked, it opens on paper like the note board's note ([NoteHooks]); which note is the
 * level's spawn point tagged `note` at the sheet. The model is in tools/blocks/generate.py.
 */
class NotePaperBlock(settings: Settings, boxes: List<BlockShapes.Box>) : PropBlock(settings, boxes) {

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult {
        if (world.isClient) return ActionResult.SUCCESS
        NoteHooks.read(player as ServerPlayerEntity, pos)
        return ActionResult.CONSUME
    }
}
