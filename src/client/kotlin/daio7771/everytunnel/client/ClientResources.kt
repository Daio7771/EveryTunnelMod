package daio7771.everytunnel.client

import daio7771.everytunnel.platform.DrinkHooks
import daio7771.everytunnel.platform.ModBlocks
import daio7771.everytunnel.platform.FlashlightHooks
import daio7771.everytunnel.platform.ResourceSyncPacket
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient

/**
 * The local player's stamina, thirst and flashlight as last sent by the server
 * ([ResourceSyncPacket]). Read by the sprint mixin, the HUD and the flashlight effect.
 */
object ClientResources {

    @Volatile
    var current: ResourceSyncPacket = ResourceSyncPacket.NONE
        private set

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(ResourceSyncPacket.ID) { client, _, buf, _ ->
            val packet = ResourceSyncPacket.read(buf)
            client.execute { current = packet }
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> current = ResourceSyncPacket.NONE }
        // Lets the battery item predict on the client whether it can be used.
        FlashlightHooks.clientBatteryNotFull = { current.battery < current.batteryMax }
        DrinkHooks.clientThirstNotFull = { current.thirst < current.thirstMax }
    }

    /** True while the player is exhausted, or carries a box or a book, and must not sprint. Called from `ClientPlayerEntityMixin`. */
    @JvmStatic
    fun isSprintBlocked(): Boolean = current.active && (current.exhausted || carryingBox())

    /** A Level 3 box or book in the inventory: no sprinting while carrying it (`platform.CarryHooks`). */
    private fun carryingBox(): Boolean {
        val player = MinecraftClient.getInstance().player ?: return false
        val things = setOf(ModBlocks.PURPLE_BOX.asItem(), ModBlocks.OFFICE_BOOK.asItem())
        return player.inventory.containsAny { it.item in things }
    }
}
