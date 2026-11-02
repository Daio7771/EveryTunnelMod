package daio7771.everytunnel.platform

import daio7771.everytunnel.config.ConfigManager
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

/**
 * Eating (the food items, `ModItems`): hold use for `food.eat_seconds` and `food.heal` health comes
 * back. Eating is a noise for the Pale man, as loud as drinking. Only in worlds with levels; a
 * player at full health cannot eat.
 */
object FoodHooks {

    fun canEat(player: PlayerEntity): Boolean = FlashlightHooks.available(player) && player.health < player.maxHealth

    fun explainRefused(player: ServerPlayerEntity) {
        if (FlashlightHooks.available(player)) player.sendMessage(Text.translatable("message.every_tunnel.not_hurt"), true)
    }

    /** Eats [stack] (one food, kept in Creative). */
    fun eat(player: ServerPlayerEntity, stack: ItemStack) {
        if (!canEat(player)) {
            explainRefused(player)
            return
        }
        if (!player.abilities.creativeMode) stack.decrement(1)
        player.heal(ConfigManager.gameplay.food.heal.toFloat())
        NoiseHooks.drink(player)
    }
}
