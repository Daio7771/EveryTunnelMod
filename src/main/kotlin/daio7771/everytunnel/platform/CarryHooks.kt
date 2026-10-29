package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.world.WorldRules
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.block.BlockState
import net.minecraft.entity.ItemEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import java.util.UUID

/**
 * Carrying a puzzle thing in Level 3: a purple box ([BoxHooks]) or a book ([BookHooks]), picked
 * up from where it lay ([take]). One thing at a time, for everything carried:
 *
 * - it stays in the hand: the selected hotbar slot; if the slot changes, the thing moves to it
 *   ([tick]);
 * - it cannot be thrown away: dropped (Q, or out of the inventory), it comes straight back;
 * - the player cannot sprint (here, and on the client: `client.ClientResources`);
 * - hiding in a locker with it is fine;
 * - dying ([died]), or losing it any other way, puts it back where it was picked up, as it lay.
 *
 * Putting it where it belongs is the puzzle's own ([release] then forgets it). A new game or a
 * rebuild of the map forgets everything carried ([forgetAll]). A player who joins with one still
 * in the inventory (they left carrying it, and no new game took it away: Creative, Spectator)
 * loses it: the rebuild already took its place away, so it would be one box or book too many.
 */
object CarryHooks {

    /** A thing in someone's hands, and where (and how) it lay before. */
    private class Carried(val dimension: String, val pos: BlockPos, val state: BlockState)

    // Server thread only.
    private val carried = mutableMapOf<UUID, Carried>()

    /** The items that are carried things. */
    private val items: Set<Item> get() = setOf(ModBlocks.PURPLE_BOX.asItem(), ModBlocks.OFFICE_BOOK.asItem())

    fun register() {
        ServerLifecycleEvents.SERVER_STOPPED.register { carried.clear() }
        ServerTickEvents.END_SERVER_TICK.register(::tick)
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            val player = handler.player
            if (WorldRules.active && player.uuid !in carried && isCarrying(player)) {
                removeAll(player)
                EveryTunnel.LOGGER.info("{} joined carrying a box or book from an earlier game: taken away", player.name.string)
            }
        }
        // A carried thing never lies about as a dropped item: it goes back to whoever carried it
        // (the thrower, or the nearest carrier who no longer has it), or else [tick] puts it back
        // where it was picked up.
        ServerEntityEvents.ENTITY_LOAD.register { entity, world ->
            val item = entity as? ItemEntity ?: return@register
            if (!WorldRules.active || item.stack.item !in items) return@register
            val stack = item.stack.copy()
            item.discard()
            val carrier = (item.owner as? ServerPlayerEntity)?.takeIf { it.uuid in carried && !isCarrying(it) }
                ?: world.players.filter { it.uuid in carried && !isCarrying(it) }.minByOrNull { it.squaredDistanceTo(item) }
                ?: return@register
            val inventory = carrier.inventory
            if (inventory.main[inventory.selectedSlot].isEmpty) inventory.main[inventory.selectedSlot] = stack else inventory.insertStack(stack)
            inventory.markDirty()
        }
    }

    /** Everything in someone's hands: (dimension, where it was picked up, how it lay), for the save. */
    fun carriedThings(): List<Triple<String, BlockPos, BlockState>> = carried.values.map { Triple(it.dimension, it.pos, it.state) }

    /** Anything carried in the inventory? */
    fun isCarrying(player: ServerPlayerEntity): Boolean = player.inventory.containsAny { it.item in items }

    /** Carrying [item] in particular? */
    fun isCarrying(player: ServerPlayerEntity, item: Item): Boolean = player.inventory.containsAny { it.isOf(item) }

    private fun tick(server: MinecraftServer) {
        for (player in server.playerManager.playerList) {
            if (!isCarrying(player)) {
                // It left the inventory some other way (a command, Creative): back to its place.
                if (player.uuid in carried && !DeathHooks.isDying(player)) putBack(server, player)
                continue
            }
            if (player.isSprinting) player.isSprinting = false
            keepInHand(player)
        }
    }

    /** The thing always in the selected hotbar slot: if the slot changed, it moves to it. */
    private fun keepInHand(player: ServerPlayerEntity) {
        val inventory = player.inventory
        val selected = inventory.selectedSlot
        if (inventory.main[selected].item in items) return
        val offHand = inventory.offHand
        if (offHand[0].item in items) {
            val thing = offHand[0]
            offHand[0] = inventory.main[selected]
            inventory.main[selected] = thing
        } else {
            val at = inventory.main.indexOfFirst { it.item in items }
            if (at < 0) return
            val thing = inventory.main[at]
            inventory.main[at] = inventory.main[selected]
            inventory.main[selected] = thing
        }
        inventory.markDirty()
    }

    /**
     * [player] picks up the block at [pos] as [item], with [sound]. True if the click was used:
     * taken, or refused with a message (hands full, no room).
     */
    fun take(player: ServerPlayerEntity, pos: BlockPos, item: Item, sound: SoundEvent): Boolean {
        if (!WorldRules.active || player.isSpectator || DeathHooks.isDying(player)) return false
        if (isCarrying(player)) {
            player.sendMessage(Text.translatable("message.every_tunnel.carry_hands_full"), true)
            return true
        }
        val world = player.serverWorld
        val state = world.getBlockState(pos)
        val inventory = player.inventory
        val selected = inventory.selectedSlot
        if (!inventory.main[selected].isEmpty) {
            val free = inventory.emptySlot
            if (free < 0) {
                player.sendMessage(Text.translatable("message.every_tunnel.carry_no_room"), true)
                return true
            }
            inventory.main[free] = inventory.main[selected]
        }
        inventory.main[selected] = ItemStack(item)
        inventory.markDirty()
        world.removeBlock(pos, false)
        world.playSound(null, pos, sound, SoundCategory.BLOCKS, 0.8f, 1.1f)
        carried[player.uuid] = Carried(world.registryKey.value.toString(), pos.toImmutable(), state)
        return true
    }

    /** What [player] carried went where it belongs: out of the hands, forgotten. */
    fun release(player: ServerPlayerEntity) {
        removeAll(player)
        carried.remove(player.uuid)
    }

    /** [player] is dying: what they carry goes back where it was picked up. */
    fun died(player: ServerPlayerEntity) {
        if (player.uuid !in carried && !isCarrying(player)) return
        removeAll(player)
        putBack(player.server, player)
    }

    /** A new game or a rebuild: nothing is in anyone's hands any more. */
    fun forgetAll() {
        carried.clear()
    }

    private fun removeAll(player: ServerPlayerEntity) {
        val inventory = player.inventory
        for (list in listOf(inventory.main, inventory.offHand)) {
            for (i in list.indices) if (list[i].item in items) list[i] = ItemStack.EMPTY
        }
        inventory.markDirty()
    }

    private fun putBack(server: MinecraftServer, player: ServerPlayerEntity) {
        val thing = carried.remove(player.uuid) ?: return
        val world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(thing.dimension))) ?: return
        if (world.getBlockState(thing.pos).isAir) world.setBlockState(thing.pos, thing.state)
    }
}
