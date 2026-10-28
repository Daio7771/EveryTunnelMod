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
 * A locker (Level 3's offices): a stack of two blocks, like the other tall furniture
 * ([StackFurnitureBlock]), that a player can hide in. Right-clicking it from in front gets in,
 * right-clicking again (from inside it is the block under the crosshair) gets out
 * ([HidingHooks]). In a world without levels it is only furniture.
 */
class LockerBlock(settings: Settings, boxes: List<BlockShapes.Box>) : StackFurnitureBlock(settings, boxes) {

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult {
        if (world.isClient) return ActionResult.SUCCESS
        val server = player as? ServerPlayerEntity ?: return ActionResult.PASS
        return if (HidingHooks.use(server, pos, state)) ActionResult.CONSUME else ActionResult.PASS
    }
}
