package daio7771.everytunnel.client

import daio7771.everytunnel.platform.DrinkHooks
import daio7771.everytunnel.platform.FlashlightHooks
import daio7771.everytunnel.platform.ResourceSyncPacket
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking

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

    /** True while the player is exhausted and must not sprint. Called from `ClientPlayerEntityMixin`. */
    @JvmStatic
    fun isSprintBlocked(): Boolean = current.active && current.exhausted
}
