package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.config.ConfigManager
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.minecraft.client.item.TooltipContext
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemGroups
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.TypedActionResult
import net.minecraft.util.UseAction
import net.minecraft.world.World

/**
 * The mod's items, listed in the Tools creative tab for map building.
 *
 * - Battery (`/give @s every_tunnel:battery`): hold use for
 *   `flashlight.battery_change_seconds` to refill the flashlight battery; consumed
 *   outside Creative. The flashlight itself is not an item: every player has it,
 *   invisible (see [FlashlightHooks]).
 * - Water bottle (`/give @s every_tunnel:water_bottle`): hold use for
 *   `thirst.drink_seconds` to drink it (see [DrinkHooks]); consumed outside Creative.
 *
 * In the levels both are found lying around ([LootSpawner]).
 */
object ModItems {

    @JvmField
    val BATTERY: Item = BatteryItem(Item.Settings().maxCount(16))
    @JvmField
    val WATER_BOTTLE: Item = WaterBottleItem(Item.Settings().maxCount(16))

    fun register() {
        Registry.register(Registries.ITEM, Identifier(EveryTunnel.MOD_ID, "battery"), BATTERY)
        Registry.register(Registries.ITEM, Identifier(EveryTunnel.MOD_ID, "water_bottle"), WATER_BOTTLE)
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register { entries ->
            entries.add(BATTERY)
            entries.add(WATER_BOTTLE)
        }
    }
}

private class BatteryItem(settings: Settings) : Item(settings) {

    /** Read once per use from the config (which only changes on restart). */
    private fun changeTicks(): Int = Math.round(ConfigManager.gameplay.flashlight.batteryChangeSeconds * 20.0).toInt()

    override fun use(world: World, user: PlayerEntity, hand: Hand): TypedActionResult<ItemStack> {
        val stack = user.getStackInHand(hand)
        if (!FlashlightHooks.canInsertBattery(user)) {
            if (user is ServerPlayerEntity) FlashlightHooks.explainBatteryRefused(user)
            return TypedActionResult.fail(stack)
        }
        if (changeTicks() <= 0) {
            if (user is ServerPlayerEntity) FlashlightHooks.insertBattery(user, stack)
            return TypedActionResult.success(stack, world.isClient)
        }
        user.setCurrentHand(hand)
        if (user is ServerPlayerEntity) FlashlightHooks.batteryChangeStarted(user)
        return TypedActionResult.consume(stack)
    }

    override fun getMaxUseTime(stack: ItemStack): Int = changeTicks().coerceAtLeast(1)

    override fun getUseAction(stack: ItemStack): UseAction = UseAction.NONE

    override fun finishUsing(stack: ItemStack, world: World, user: LivingEntity): ItemStack {
        if (user is ServerPlayerEntity) FlashlightHooks.insertBattery(user, stack)
        return stack
    }

    override fun appendTooltip(stack: ItemStack, world: World?, tooltip: MutableList<Text>, context: TooltipContext) {
        tooltip += Text.translatable("item.every_tunnel.battery.tooltip").formatted(Formatting.GRAY)
    }
}

private class WaterBottleItem(settings: Settings) : Item(settings) {

    /** Read once per use from the config (which only changes on restart). */
    private fun drinkTicks(): Int = Math.round(ConfigManager.gameplay.thirst.drinkSeconds * 20.0).toInt().coerceAtLeast(1)

    override fun use(world: World, user: PlayerEntity, hand: Hand): TypedActionResult<ItemStack> {
        val stack = user.getStackInHand(hand)
        if (!DrinkHooks.canDrink(user)) {
            if (user is ServerPlayerEntity) DrinkHooks.explainRefused(user)
            return TypedActionResult.fail(stack)
        }
        user.setCurrentHand(hand)
        return TypedActionResult.consume(stack)
    }

    override fun getMaxUseTime(stack: ItemStack): Int = drinkTicks()

    // The drinking pose; vanilla's own drinking sound is switched off for this item (LivingEntityMixin).
    override fun getUseAction(stack: ItemStack): UseAction = UseAction.DRINK

    override fun usageTick(world: World, user: LivingEntity, stack: ItemStack, remainingUseTicks: Int) {
        val used = drinkTicks() - remainingUseTicks
        if (user is ServerPlayerEntity && used >= FIRST_GULP_TICK && (used - FIRST_GULP_TICK) % GULP_TICKS == 0) DrinkHooks.gulp(user)
    }

    override fun finishUsing(stack: ItemStack, world: World, user: LivingEntity): ItemStack {
        if (user is ServerPlayerEntity) DrinkHooks.drink(user, stack)
        return stack
    }

    override fun appendTooltip(stack: ItemStack, world: World?, tooltip: MutableList<Text>, context: TooltipContext) {
        tooltip += Text.translatable("item.every_tunnel.water_bottle.tooltip").formatted(Formatting.GRAY)
    }

    private companion object {
        const val FIRST_GULP_TICK = 3
        const val GULP_TICKS = 11
    }
}
