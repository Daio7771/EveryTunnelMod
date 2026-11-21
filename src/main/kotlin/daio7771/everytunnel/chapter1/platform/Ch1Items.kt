package daio7771.everytunnel.chapter1.platform

import daio7771.everytunnel.common.EveryTunnel
import daio7771.everytunnel.chapter1.level4.platform.DartEntity
import daio7771.everytunnel.chapter1.level4.platform.DartHooks
import daio7771.everytunnel.common.platform.ModBlocks
import daio7771.everytunnel.common.platform.ModSounds
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.TypedActionResult
import net.minecraft.world.World

/**
 * Chapter 1's own items: the dart of Level 4's Game room ([DartHooks]), registered by [daio7771.everytunnel.chapter1.Chapter1].
 * The shared items (battery, water, food, the keycard, the loot of its levels) stay in `ModItems`.
 */
object Ch1Items {

    @JvmField
    val DART: Item = DartItem(Item.Settings().maxCount(DartHooks.DART_COUNT))

    fun register() {
        Registry.register(Registries.ITEM, Identifier(EveryTunnel.MOD_ID, "dart"), DART)
        ItemGroupEvents.modifyEntriesEvent(ModBlocks.TAB).register { entries -> entries.add(DART) }
    }
}

private class DartItem(settings: Settings) : Item(settings) {

    override fun use(world: World, user: PlayerEntity, hand: Hand): TypedActionResult<ItemStack> {
        val stack = user.getStackInHand(hand)
        if (!world.isClient) {
            val dart = DartEntity(world, user)
            // From the eyes, not vanilla's a little under them: it goes where the crosshair says, but for the drop.
            dart.setPosition(user.x, user.eyeY, user.z)
            dart.setVelocity(user, user.pitch, user.yaw, 0f, THROW_SPEED, 0f)
            world.spawnEntity(dart)
            world.playSound(null, user.x, user.y, user.z, ModSounds.DART_THROW, SoundCategory.PLAYERS, 0.8f, 1f)
        }
        user.itemCooldownManager.set(this, COOLDOWN_TICKS)
        stack.decrement(1)
        return TypedActionResult.success(stack, world.isClient)
    }

    private companion object {
        /** Blocks per tick: from the line, 5 blocks away, it drops about a tenth of a block. */
        const val THROW_SPEED = 2.5f
        const val COOLDOWN_TICKS = 8
    }
}
