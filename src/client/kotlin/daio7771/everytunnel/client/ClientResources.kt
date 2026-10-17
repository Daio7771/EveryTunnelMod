package daio7771.everytunnel.client

import daio7771.everytunnel.platform.ResourceSyncPacket
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking

/**
 * The local player's stamina and thirst as last sent by the server
 * ([ResourceSyncPacket]). Read by the sprint mixin and, later, the HUD.
 */
object ClientResources {

    @Volatile
    var current: ResourceSyncPacket = ResourceSyncPacket.INACTIVE
        private set

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(ResourceSyncPacket.ID) { client, _, buf, _ ->
            val packet = ResourceSyncPacket.read(buf)
            client.execute { current = packet }
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> current = ResourceSyncPacket.INACTIVE }
    }

    /** True while the player is exhausted and must not sprint. Called from `ClientPlayerEntityMixin`. */
    @JvmStatic
    fun isSprintBlocked(): Boolean = current.active && current.exhausted
}
