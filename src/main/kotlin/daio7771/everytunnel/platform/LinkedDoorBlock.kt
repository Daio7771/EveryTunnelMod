package daio7771.everytunnel.platform

import net.minecraft.block.Block
import net.minecraft.block.BlockSetType
import net.minecraft.block.BlockState
import net.minecraft.block.DoorBlock
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * One leaf of a pair of doors that open and shut together by hand (Chapter 2's factory doors): using one does the same
 * to the leaf beside it, the same block facing the same way, hinged on the other side. A door with no such neighbour is
 * an ordinary one. What the leaves do beyond that is vanilla's, and they carry no scene (unlike [PairedDoorBlock]).
 */
class LinkedDoorBlock(settings: Settings, type: BlockSetType) : DoorBlock(settings, type) {

    @Deprecated("Deprecated in Java")
    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult {
        @Suppress("DEPRECATION")
        val result = super.onUse(state, world, pos, player, hand, hit)
        if (!result.isAccepted) return result
        val now = world.getBlockState(pos)
        if (!now.isOf(this)) return result
        val facing = now.get(FACING)
        for (side in listOf(facing.rotateYClockwise(), facing.rotateYCounterclockwise())) {
            val other = pos.offset(side)
            val leaf = world.getBlockState(other)
            if (!leaf.isOf(this) || leaf.get(FACING) != facing || leaf.get(HINGE) == now.get(HINGE) || leaf.get(HALF) != now.get(HALF)) continue
            if (leaf.get(OPEN) != now.get(OPEN)) {
                world.setBlockState(other, leaf.with(OPEN, now.get(OPEN)), Block.NOTIFY_LISTENERS or Block.REDRAW_ON_MAIN_THREAD)
            }
        }
        return result
    }
}
