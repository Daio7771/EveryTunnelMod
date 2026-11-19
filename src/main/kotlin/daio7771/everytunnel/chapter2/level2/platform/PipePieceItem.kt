package daio7771.everytunnel.chapter2.level2.platform

import daio7771.everytunnel.chapter2.platform.Ch2Sounds
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.world.World

/**
 * A piece of pipe of Stone Maw's arena (phase 6.1): it lies where it fell and is picked up by walking over it; a right click throws it
 * ([ThrownPipeEntity]) in a heavy, short arc (`maw.throw`), and it is gone. Only meant for that fight: [Ch2L2Maw] takes the ones in
 * pockets away when the fight is left.
 */
class PipePieceItem(settings: Settings) : Item(settings) {

    override fun use(world: World, player: PlayerEntity, hand: Hand): TypedActionResult<ItemStack> {
        val stack = player.getStackInHand(hand)
        if (!world.isClient) {
            val rules = Ch2L2DataLoader.data.level.maw
            val piece = ThrownPipeEntity(world, player)
            piece.setItem(stack)
            piece.setVelocity(player, player.pitch, player.yaw, 0.0f, rules.throwSpeed.toFloat(), THROW_DIVERGENCE)
            world.spawnEntity(piece)
            world.playSound(null, player.x, player.eyeY, player.z, Ch2Sounds.CH2L2_MAW_THROW, SoundCategory.PLAYERS, 1.0f, 0.95f + world.random.nextFloat() * 0.1f)
            if (!player.abilities.creativeMode) stack.decrement(1)
        }
        return TypedActionResult.success(stack, world.isClient)
    }

    private companion object {
        /** Almost straight: the arc is the fall, not the aim. */
        const val THROW_DIVERGENCE = 0.4f
    }
}
