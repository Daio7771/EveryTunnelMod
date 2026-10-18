package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.config.ConfigManager
import daio7771.everytunnel.systems.BatteryInsert
import daio7771.everytunnel.systems.FlashlightToggle
import daio7771.everytunnel.systems.ResourceSystem
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text
import net.minecraft.util.Identifier

/**
 * Server side of the flashlight: the toggle request from the Flashlight key,
 * battery items, and the feedback (sounds, action bar messages).
 *
 * The on/off state and the battery charge live in `systems.ResourceSystem` (saved
 * and synced with the other resources by [ResourceHooks]); the light itself is drawn
 * on the client. The sounds are vanilla placeholders until the audio phase.
 */
object FlashlightHooks {

    /** Client -> server, empty payload: the Flashlight key was pressed. */
    val TOGGLE_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "toggle_flashlight")

    /**
     * Client side of [canInsertBattery]: whether the synced battery is below full.
     * Set by the client entrypoint; common code cannot read the client's copy directly.
     */
    @Volatile
    var clientBatteryNotFull: () -> Boolean = { true }

    fun register() {
        ServerPlayNetworking.registerGlobalReceiver(TOGGLE_PACKET_ID) { server, player, _, _, _ ->
            server.execute { toggle(player) }
        }
    }

    /** True if a flashlight is anywhere in the player's inventory (hotbar, main, armor or offhand slots). */
    fun hasFlashlight(player: PlayerEntity): Boolean {
        val inventory = player.inventory
        for (slot in 0 until inventory.size()) {
            if (inventory.getStack(slot).isOf(ModItems.FLASHLIGHT)) return true
        }
        return false
    }

    fun toggle(player: ServerPlayerEntity) {
        if (!player.isAlive || player.isSpectator) return
        val config = ConfigManager.gameplay
        val (state, result) = ResourceSystem.toggleFlashlight(ResourceSystem.stateOf(player.uuid, config), hasFlashlight(player))
        ResourceSystem.set(player.uuid, state)
        when (result) {
            FlashlightToggle.TURNED_ON -> play(player, SoundEvents.BLOCK_STONE_BUTTON_CLICK_ON, 0.4f, 1.8f)
            FlashlightToggle.TURNED_OFF -> play(player, SoundEvents.BLOCK_STONE_BUTTON_CLICK_OFF, 0.4f, 1.6f)
            FlashlightToggle.BATTERY_EMPTY -> {
                play(player, SoundEvents.BLOCK_STONE_BUTTON_CLICK_OFF, 0.3f, 0.9f)
                actionBar(player, "message.every_tunnel.battery_empty")
            }
            FlashlightToggle.NO_FLASHLIGHT -> actionBar(player, "message.every_tunnel.no_flashlight")
        }
    }

    /** Called by [ResourceHooks] when the flashlight switched off because the battery ran out. */
    fun batteryDied(player: ServerPlayerEntity) {
        play(player, SoundEvents.BLOCK_STONE_BUTTON_CLICK_OFF, 0.4f, 0.7f)
        actionBar(player, "message.every_tunnel.battery_empty")
    }

    /** True if using a battery item would add charge. Works on both sides (client: synced copy). */
    fun canInsertBattery(player: PlayerEntity): Boolean {
        val hasFlashlight = hasFlashlight(player)
        if (player.world.isClient) return hasFlashlight && clientBatteryNotFull()
        val config = ConfigManager.gameplay
        return ResourceSystem.canInsertBattery(ResourceSystem.stateOf(player.uuid, config), hasFlashlight, config)
    }

    /** Refills the battery from [stack] (one battery item, kept in Creative). */
    fun insertBattery(player: ServerPlayerEntity, stack: ItemStack) {
        val config = ConfigManager.gameplay
        val (state, result) = ResourceSystem.insertBattery(ResourceSystem.stateOf(player.uuid, config), hasFlashlight(player), config)
        if (result != BatteryInsert.REFILLED) {
            explainBatteryRefused(player)
            return
        }
        ResourceSystem.set(player.uuid, state)
        if (!player.abilities.creativeMode) stack.decrement(1)
        play(player, SoundEvents.BLOCK_LEVER_CLICK, 0.5f, 1.4f)
        actionBar(player, "message.every_tunnel.battery_replaced")
    }

    fun explainBatteryRefused(player: ServerPlayerEntity) {
        actionBar(
            player,
            if (hasFlashlight(player)) "message.every_tunnel.battery_full" else "message.every_tunnel.battery_needs_flashlight",
        )
    }

    private fun play(player: ServerPlayerEntity, sound: SoundEvent, volume: Float, pitch: Float) {
        player.world.playSound(null, player.x, player.eyeY, player.z, sound, SoundCategory.PLAYERS, volume, pitch)
    }

    private fun actionBar(player: ServerPlayerEntity, key: String) {
        player.sendMessage(Text.translatable(key), true)
    }
}
