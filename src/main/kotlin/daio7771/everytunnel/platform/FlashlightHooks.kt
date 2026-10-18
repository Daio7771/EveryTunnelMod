package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.config.ConfigManager
import daio7771.everytunnel.systems.BatteryInsert
import daio7771.everytunnel.systems.FlashlightToggle
import daio7771.everytunnel.systems.ResourceSystem
import daio7771.everytunnel.world.WorldRules
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.text.Text
import net.minecraft.util.Identifier

/**
 * Server side of the flashlight: the toggle request from the Flashlight key,
 * battery items, and the feedback (sounds, action bar messages).
 *
 * The flashlight is built-in equipment, not an item: every player has it, nothing is
 * shown in the hand. It exists only in worlds with levels (vanilla worlds stay vanilla)
 * and not for spectators.
 *
 * The on/off state and the battery charge live in `systems.ResourceSystem` (saved
 * and synced with the other resources by [ResourceHooks]); the light itself is drawn
 * on the client. Sounds are positioned at the player (see [ModSounds]).
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

    /** True if the player can use the flashlight right now. */
    fun available(player: PlayerEntity): Boolean =
        WorldRules.active && player.isAlive && !player.isSpectator

    fun toggle(player: ServerPlayerEntity) {
        if (!available(player)) return
        val config = ConfigManager.gameplay
        val (state, result) = ResourceSystem.toggleFlashlight(ResourceSystem.stateOf(player.uuid, config))
        ResourceSystem.set(player.uuid, state)
        when (result) {
            FlashlightToggle.TURNED_ON -> play(player, ModSounds.FLASHLIGHT_ON, 0.6f)
            FlashlightToggle.TURNED_OFF -> play(player, ModSounds.FLASHLIGHT_OFF, 0.6f)
            FlashlightToggle.BATTERY_EMPTY -> {
                play(player, ModSounds.FLASHLIGHT_DEAD, 0.6f)
                actionBar(player, "message.every_tunnel.battery_empty")
            }
        }
    }

    /** Called by [ResourceHooks] when the flashlight switched off because the battery ran out. */
    fun batteryDied(player: ServerPlayerEntity) {
        play(player, ModSounds.FLASHLIGHT_FLICKER, 0.5f)
        actionBar(player, "message.every_tunnel.battery_empty")
    }

    /** True if using a battery item would add charge. Works on both sides (client: synced copy). */
    fun canInsertBattery(player: PlayerEntity): Boolean {
        if (!available(player)) return false
        if (player.world.isClient) return clientBatteryNotFull()
        val config = ConfigManager.gameplay
        return ResourceSystem.canInsertBattery(ResourceSystem.stateOf(player.uuid, config), config)
    }

    /** Refills the battery from [stack] (one battery item, kept in Creative). */
    fun insertBattery(player: ServerPlayerEntity, stack: ItemStack) {
        val config = ConfigManager.gameplay
        if (!available(player)) return
        val (state, result) = ResourceSystem.insertBattery(ResourceSystem.stateOf(player.uuid, config), config)
        if (result != BatteryInsert.REFILLED) {
            explainBatteryRefused(player)
            return
        }
        ResourceSystem.set(player.uuid, state)
        if (!player.abilities.creativeMode) stack.decrement(1)
        play(player, ModSounds.BATTERY_CLOSE, 0.6f)
        actionBar(player, "message.every_tunnel.battery_replaced")
    }

    /** Only says something where the flashlight exists (in vanilla worlds batteries just do nothing). */
    fun explainBatteryRefused(player: ServerPlayerEntity) {
        if (available(player)) actionBar(player, "message.every_tunnel.battery_full")
    }

    /** Battery change started (the cap is being unscrewed). */
    fun batteryChangeStarted(player: ServerPlayerEntity) {
        play(player, ModSounds.BATTERY_OPEN, 0.6f)
    }

    private fun play(player: ServerPlayerEntity, sound: SoundEvent, volume: Float) {
        player.world.playSound(null, player.x, player.eyeY, player.z, sound, SoundCategory.PLAYERS, volume, 1.0f)
    }

    private fun actionBar(player: ServerPlayerEntity, key: String) {
        player.sendMessage(Text.translatable(key), true)
    }
}
