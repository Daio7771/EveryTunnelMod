package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import net.minecraft.network.PacketByteBuf
import net.minecraft.util.Identifier

/**
 * Server -> client copy of the player's resources, for the HUD and for sprint
 * prediction (the client refuses to sprint while [exhausted]).
 *
 * Values are rounded to 0.1 so the server only resends when something visible changed.
 * [active] is false when resources do not apply (no levels, Creative/Spectator).
 */
data class ResourceSyncPacket(
    val active: Boolean,
    val stamina: Float,
    val staminaMax: Float,
    val thirst: Float,
    val thirstMax: Float,
    val exhausted: Boolean,
) {
    fun write(buf: PacketByteBuf) {
        buf.writeBoolean(active)
        buf.writeFloat(stamina)
        buf.writeFloat(staminaMax)
        buf.writeFloat(thirst)
        buf.writeFloat(thirstMax)
        buf.writeBoolean(exhausted)
    }

    companion object {
        val ID = Identifier(EveryTunnel.MOD_ID, "resources")
        val INACTIVE = ResourceSyncPacket(false, 0f, 0f, 0f, 0f, false)

        fun of(stamina: Double, staminaMax: Double, thirst: Double, thirstMax: Double, exhausted: Boolean) =
            ResourceSyncPacket(true, round(stamina), round(staminaMax), round(thirst), round(thirstMax), exhausted)

        fun read(buf: PacketByteBuf) = ResourceSyncPacket(
            active = buf.readBoolean(),
            stamina = buf.readFloat(),
            staminaMax = buf.readFloat(),
            thirst = buf.readFloat(),
            thirstMax = buf.readFloat(),
            exhausted = buf.readBoolean(),
        )

        private fun round(value: Double): Float = Math.round(value * 10.0) / 10f
    }
}
