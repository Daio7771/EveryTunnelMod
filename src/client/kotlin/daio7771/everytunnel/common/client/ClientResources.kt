package daio7771.everytunnel.common.client

import daio7771.everytunnel.common.platform.DrinkHooks
import daio7771.everytunnel.common.platform.ModBlocks
import daio7771.everytunnel.common.platform.FlashlightHooks
import daio7771.everytunnel.common.platform.ResourceSyncPacket
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import daio7771.everytunnel.common.EveryTunnel
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier

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
        FlashlightHooks.clientBatteryNotFull = { current.battery < current.batteryMax && !current.deadBattery }
        DrinkHooks.clientThirstNotFull = { current.thirst < current.thirstMax }
    }

    /**
     * True while the player is exhausted, or carries a box or a book, or stands where sprinting is not allowed
     * (Level 5's entry corridor), and must not sprint. Called from `ClientPlayerEntityMixin`.
     */
    @JvmStatic
    fun isSprintBlocked(): Boolean = current.active && (current.exhausted || current.noSprint || carryingBox())

    /** A Level 3 box or book in the inventory: no sprinting while carrying it (`chapter1.platform.CarryHooks`). */
    private fun carryingBox(): Boolean {
        val player = MinecraftClient.getInstance().player ?: return false
        val things = setOf("purple_box", "office_book").map { Registries.ITEM.get(Identifier(EveryTunnel.MOD_ID, it)) }.toSet()
        return player.inventory.containsAny { it.item in things }
    }
}
