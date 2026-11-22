package daio7771.everytunnel.chapter1.client

import daio7771.everytunnel.chapter1.level5.platform.EveryFinalHooks
import daio7771.everytunnel.common.client.MenuLogoOverride
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking

/**
 * The menu logo's EVERY FINAL easter egg, the client's half: the server says whether the player is in Level 5 before the Director's office
 * ([EveryFinalHooks]); leaving the game while that is so sets [MenuLogoOverride], and the menu then reads EVERY FINAL until the game is closed.
 */
object EveryFinalClient {

    private var armed = false

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(EveryFinalHooks.ARMED_PACKET_ID) { client, _, buf, _ ->
            val now = buf.readBoolean()
            client.execute { armed = now }
        }
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> armed = false }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            if (armed) MenuLogoOverride.set()
            armed = false
        }
    }
}
