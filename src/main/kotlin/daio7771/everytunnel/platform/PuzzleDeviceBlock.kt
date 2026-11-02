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
 * What a puzzle's item is put into in Level 4: the record player and the projector. Right-clicked
 * with the item in hand, [LoungePuzzleHooks] says what happens.
 */
class PuzzleDeviceBlock(settings: Settings, boxes: List<BlockShapes.Box>) : PropBlock(settings, boxes) {

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult {
        if (world.isClient) return ActionResult.SUCCESS
        LoungePuzzleHooks.useDevice(player as ServerPlayerEntity, state)
        return ActionResult.CONSUME
    }
}
