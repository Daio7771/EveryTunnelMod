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
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
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
 * - Food (a plate, popcorn, a snack; `/give @s every_tunnel:food_plate`): hold use for
 *   `food.eat_seconds` to eat it and get `food.heal` health back (see [FoodHooks]); the same
 *   effect in all three. Consumed outside Creative.
 *
 * - Level 4's puzzle items ([LoungePuzzleHooks]): four records, four film reels, and the four
 *   corners of the golden master card. One each.
 *
 * - Darts ([DartHooks]): three from the Game room's tray; right-click throws one straight away.
 *
 * - Keycard: the night guard's security card. Lost in Level 2; the exit's card reader
 *   ([CardReaderBlock]) only opens for a player carrying it.
 *
 * In the levels they are found lying around ([LootSpawner]).
 */
object ModItems {

    @JvmField
    val BATTERY: Item = BatteryItem(Item.Settings().maxCount(16))
    @JvmField
    val WATER_BOTTLE: Item = WaterBottleItem(Item.Settings().maxCount(16))
    @JvmField
    val KEYCARD: Item = KeycardItem(Item.Settings().maxCount(1))
    @JvmField
    val FOOD_PLATE: Item = FoodItem(Item.Settings().maxCount(16))
    @JvmField
    val FOOD_POPCORN: Item = FoodItem(Item.Settings().maxCount(16))
    @JvmField
    val FOOD_SNACK: Item = FoodItem(Item.Settings().maxCount(16))

    /** In the order the Bar's menu lists them, which is the order they are played in. */
    val RECORDS: List<Item> = List(4) { Item(Item.Settings().maxCount(1)) }
    private val RECORD_IDS = listOf("record_old_fashioned", "record_sparkling_water", "record_still_water", "record_chefs_plate")

    /** By running time: 87, 94, 102 and 120 minutes. */
    val REELS: List<Item> = List(4) { Item(Item.Settings().maxCount(1)) }
    private val REEL_IDS = listOf("reel_87", "reel_94", "reel_102", "reel_120")

    /** The golden master card's corners: Bar, Cinema, Spa, Game room. */
    val CARD_NW: Item = Item(Item.Settings().maxCount(1))
    val CARD_NE: Item = Item(Item.Settings().maxCount(1))
    val CARD_SW: Item = Item(Item.Settings().maxCount(1))
    val CARD_SE: Item = Item(Item.Settings().maxCount(1))

    @JvmField
    val DART: Item = DartItem(Item.Settings().maxCount(DartHooks.DART_COUNT))

    fun register() {
        Registry.register(Registries.ITEM, Identifier(EveryTunnel.MOD_ID, "dart"), DART)
        Registry.register(Registries.ITEM, Identifier(EveryTunnel.MOD_ID, "battery"), BATTERY)
        Registry.register(Registries.ITEM, Identifier(EveryTunnel.MOD_ID, "water_bottle"), WATER_BOTTLE)
        Registry.register(Registries.ITEM, Identifier(EveryTunnel.MOD_ID, "keycard"), KEYCARD)
        Registry.register(Registries.ITEM, Identifier(EveryTunnel.MOD_ID, "food_plate"), FOOD_PLATE)
        Registry.register(Registries.ITEM, Identifier(EveryTunnel.MOD_ID, "food_popcorn"), FOOD_POPCORN)
        Registry.register(Registries.ITEM, Identifier(EveryTunnel.MOD_ID, "food_snack"), FOOD_SNACK)
        val puzzleItems = RECORD_IDS.zip(RECORDS) + REEL_IDS.zip(REELS) + listOf(
            "master_card_nw" to CARD_NW, "master_card_ne" to CARD_NE, "master_card_sw" to CARD_SW, "master_card_se" to CARD_SE,
        )
        for ((id, item) in puzzleItems) Registry.register(Registries.ITEM, Identifier(EveryTunnel.MOD_ID, id), item)
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register { entries ->
            entries.add(BATTERY)
            entries.add(WATER_BOTTLE)
            entries.add(KEYCARD)
            entries.add(FOOD_PLATE)
            entries.add(FOOD_POPCORN)
            entries.add(FOOD_SNACK)
            entries.add(DART)
            for ((_, item) in puzzleItems) entries.add(item)
        }
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
            world.playSound(null, user.x, user.y, user.z, SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 0.5f, 1.4f)
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

private class FoodItem(settings: Settings) : Item(settings) {

    /** Read once per use from the config (which only changes on restart). */
    private fun eatTicks(): Int = Math.round(ConfigManager.gameplay.food.eatSeconds * 20.0).toInt().coerceAtLeast(1)

    override fun use(world: World, user: PlayerEntity, hand: Hand): TypedActionResult<ItemStack> {
        val stack = user.getStackInHand(hand)
        if (!FoodHooks.canEat(user)) {
            if (user is ServerPlayerEntity) FoodHooks.explainRefused(user)
            return TypedActionResult.fail(stack)
        }
        user.setCurrentHand(hand)
        return TypedActionResult.consume(stack)
    }

    override fun getMaxUseTime(stack: ItemStack): Int = eatTicks()

    // The eating pose, with vanilla's chewing sound and crumbs.
    override fun getUseAction(stack: ItemStack): UseAction = UseAction.EAT

    override fun finishUsing(stack: ItemStack, world: World, user: LivingEntity): ItemStack {
        if (user is ServerPlayerEntity) FoodHooks.eat(user, stack)
        return stack
    }

    override fun appendTooltip(stack: ItemStack, world: World?, tooltip: MutableList<Text>, context: TooltipContext) {
        tooltip += Text.translatable("item.every_tunnel.food.tooltip").formatted(Formatting.GRAY)
    }
}

private class KeycardItem(settings: Settings) : Item(settings) {
    override fun appendTooltip(stack: ItemStack, world: World?, tooltip: MutableList<Text>, context: TooltipContext) {
        tooltip += Text.translatable("item.every_tunnel.keycard.tooltip").formatted(Formatting.GRAY)
    }
}
