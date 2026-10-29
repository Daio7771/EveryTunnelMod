package daio7771.everytunnel.platform

import daio7771.everytunnel.config.ConfigManager
import daio7771.everytunnel.core.achievement.Achievement
import daio7771.everytunnel.systems.ResourceSystem
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.text.Text

/**
 * Drinking a water bottle (`ModItems.WATER_BOTTLE`): thirst goes up by
 * `thirst.drink_restore`, a gulp is heard about every half second while drinking, and the
 * drink is a noise for the Pale man (`noise.drink_radius`). Only where the flashlight and
 * thirst exist (worlds with levels); a full player cannot drink. [quench] is shared with the
 * water dispensers ([WaterDispenserHooks]).
 */
object DrinkHooks {

    /** Set by the client: whether the local player's thirst is below full (for prediction). */
    var clientThirstNotFull: () -> Boolean = { true }

    fun canDrink(player: PlayerEntity): Boolean {
        if (!FlashlightHooks.available(player)) return false
        if (player.world.isClient) return clientThirstNotFull()
        val config = ConfigManager.gameplay
        return ResourceSystem.canDrink(ResourceSystem.stateOf(player.uuid, config), config)
    }

    fun explainRefused(player: ServerPlayerEntity) {
        if (FlashlightHooks.available(player)) player.sendMessage(Text.translatable("message.every_tunnel.not_thirsty"), true)
    }

    fun gulp(player: ServerPlayerEntity) {
        player.world.playSoundFromEntity(
            null, player, ModSounds.DRINK, SoundCategory.PLAYERS, 1f, 0.95f + player.random.nextFloat() * 0.1f,
        )
    }

    /** Drinks [stack] (one water bottle, kept in Creative). */
    fun drink(player: ServerPlayerEntity, stack: ItemStack) {
        if (!canDrink(player)) {
            explainRefused(player)
            return
        }
        if (!player.abilities.creativeMode) stack.decrement(1)
        quench(player)
        AchievementHooks.earned(player, Achievement.STAY_HYDRATED)
    }

    /** A drink's worth of water (a bottle, or a drink at a water dispenser, [WaterDispenserHooks]). */
    fun quench(player: ServerPlayerEntity) {
        val config = ConfigManager.gameplay
        val before = ResourceSystem.stateOf(player.uuid, config)
        val after = ResourceSystem.drink(before, config)
        ResourceSystem.set(player.uuid, after)
        PlaytestLog.drank(player, before, after)
        NoiseHooks.drink(player)
    }
}
