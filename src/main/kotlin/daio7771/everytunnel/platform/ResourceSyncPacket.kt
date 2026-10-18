package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import net.minecraft.network.PacketByteBuf
import net.minecraft.util.Identifier

/**
 * Server -> client copy of the player's resources, for the HUD, for sprint
 * prediction (the client refuses to sprint while [exhausted]) and for the
 * flashlight's light (drawn on the client).
 *
 * Values are rounded to 0.1 so the server only resends when something visible changed.
 * [active] is false when the survival resources do not apply (no levels,
 * Creative/Spectator); the stamina and thirst values are meaningless then. The
 * flashlight fields are always valid: the flashlight also works in Creative, it just
 * does not drain the battery there.
 */
data class ResourceSyncPacket(
    val active: Boolean,
    val stamina: Float,
    val staminaMax: Float,
    val thirst: Float,
    val thirstMax: Float,
    val exhausted: Boolean,
    /** True if a flashlight is anywhere in the player's inventory. */
    val hasFlashlight: Boolean,
    val flashlightOn: Boolean,
    /** Seconds of light left. */
    val battery: Float,
    val batteryMax: Float,
) {
    fun write(buf: PacketByteBuf) {
        buf.writeBoolean(active)
        buf.writeFloat(stamina)
        buf.writeFloat(staminaMax)
        buf.writeFloat(thirst)
        buf.writeFloat(thirstMax)
        buf.writeBoolean(exhausted)
        buf.writeBoolean(hasFlashlight)
        buf.writeBoolean(flashlightOn)
        buf.writeFloat(battery)
        buf.writeFloat(batteryMax)
    }

    /** True while the battery is below the low battery warning percentage. */
    fun batteryLow(warningPercent: Double): Boolean =
        batteryMax > 0f && battery < batteryMax * warningPercent / 100.0

    companion object {
        val ID = Identifier(EveryTunnel.MOD_ID, "resources")
        val NONE = ResourceSyncPacket(false, 0f, 0f, 0f, 0f, false, false, false, 0f, 0f)

        fun of(
            active: Boolean,
            stamina: Double,
            staminaMax: Double,
            thirst: Double,
            thirstMax: Double,
            exhausted: Boolean,
            hasFlashlight: Boolean,
            flashlightOn: Boolean,
            battery: Double,
            batteryMax: Double,
        ) = ResourceSyncPacket(
            active,
            if (active) round(stamina) else 0f,
            if (active) round(staminaMax) else 0f,
            if (active) round(thirst) else 0f,
            if (active) round(thirstMax) else 0f,
            active && exhausted,
            hasFlashlight,
            flashlightOn,
            round(battery),
            round(batteryMax),
        )

        fun read(buf: PacketByteBuf) = ResourceSyncPacket(
            active = buf.readBoolean(),
            stamina = buf.readFloat(),
            staminaMax = buf.readFloat(),
            thirst = buf.readFloat(),
            thirstMax = buf.readFloat(),
            exhausted = buf.readBoolean(),
            hasFlashlight = buf.readBoolean(),
            flashlightOn = buf.readBoolean(),
            battery = buf.readFloat(),
            batteryMax = buf.readFloat(),
        )

        private fun round(value: Double): Float = Math.round(value * 10.0) / 10f
    }
}
