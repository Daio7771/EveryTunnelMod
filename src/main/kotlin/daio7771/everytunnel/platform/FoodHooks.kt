package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.config.ConfigManager
import daio7771.everytunnel.systems.FoodEnergy
import daio7771.everytunnel.systems.ResourceSystem
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity

/**
 * Eating (the food items, `ModItems`): hold use for `food.eat_seconds` and two things happen. `food.heal`
 * health comes back (nothing, if the player is at full health), and the player gets `food.energy_seconds`
 * (10) of unlimited energy: sprinting and jumping cost no stamina, which stays full ([ResourceSystem.eat]);
 * the HUD's stamina row turns gold meanwhile. A food can be eaten at full health, and in a safe level (where
 * the energy changes nothing); one eaten while the last one's energy still lasts is eaten all the same and
 * wasted: no more time. Eating is a noise for the Pale man, as loud as drinking. Only in worlds with levels.
 */
object FoodHooks {

    fun canEat(player: PlayerEntity): Boolean = FlashlightHooks.available(player)

    /** Eats [stack] (one food, kept in Creative). */
    fun eat(player: ServerPlayerEntity, stack: ItemStack) {
        if (!canEat(player)) return
        if (!player.abilities.creativeMode) stack.decrement(1)
        player.heal(ConfigManager.gameplay.food.heal.toFloat())
        giveEnergy(player)
        NoiseHooks.drink(player)
    }

    /** The energy of a food, where the survival resources apply (not in Creative or behind the loading screen). */
    private fun giveEnergy(player: ServerPlayerEntity) {
        if (!ResourceHooks.appliesTo(player)) return
        val config = ConfigManager.gameplay
        val (state, result) = ResourceSystem.eat(ResourceSystem.stateOf(player.uuid, config), config)
        ResourceSystem.set(player.uuid, state)
        if (result == FoodEnergy.WASTED) EveryTunnel.LOGGER.info("{}: a food eaten while the last one's energy lasts: wasted", player.name.string)
    }
}
