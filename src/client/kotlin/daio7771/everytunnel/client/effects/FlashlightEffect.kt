package daio7771.everytunnel.client.effects

import daio7771.everytunnel.client.ClientResources
import daio7771.everytunnel.config.ConfigManager
import daio7771.everytunnel.config.FlashlightConfig
import daio7771.everytunnel.platform.ResourceSyncPacket
import daio7771.everytunnel.systems.FlashlightBeam
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.util.hit.HitResult
import java.util.Random

/**
 * Computes the flashlight's beam every client tick from the local player's eyes and
 * look direction, while the server says the flashlight is on.
 *
 * The beam stops at the first block the player looks at (see [FlashlightBeam]). Below
 * the low battery warning the light flickers now and then, more often as the battery
 * runs out. The light itself is applied by `client.compat.LambDynamicLightsCompat`.
 */
object FlashlightEffect {

    /** Light level multiplier during a flicker. */
    private const val FLICKER_DIMMING = 0.25
    /** Flicker chance per tick at the warning threshold and with an empty battery. */
    private const val FLICKER_CHANCE_AT_WARNING = 0.01
    private const val FLICKER_CHANCE_AT_EMPTY = 0.08

    /** The beam to light now, or null while the flashlight is off. Read from any thread. */
    @Volatile
    var beam: FlashlightBeam? = null
        private set

    private val random = Random()
    private var flickerTicksLeft = 0

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> beam = null }
    }

    private fun tick(client: MinecraftClient) {
        val player = client.player
        val resources = ClientResources.current
        if (player == null || client.world == null || player.isSpectator || !resources.flashlightOn) {
            beam = null
            flickerTicksLeft = 0
            return
        }
        val config = ConfigManager.gameplay.flashlight
        val eye = player.eyePos
        val look = player.getRotationVec(1.0f)
        val hit = player.raycast(config.beamRange, 1.0f, false)
        val hitDistance = if (hit.type == HitResult.Type.BLOCK) hit.pos.distanceTo(eye) else null
        beam = FlashlightBeam.create(eye.x, eye.y, eye.z, look.x, look.y, look.z, hitDistance, config, dimming(resources, config))
    }

    private fun dimming(resources: ResourceSyncPacket, config: FlashlightConfig): Double {
        if (flickerTicksLeft > 0) {
            flickerTicksLeft--
            return FLICKER_DIMMING
        }
        if (!resources.batteryLow(config.lowBatteryWarningPercent)) return 1.0
        val warningLevel = resources.batteryMax * config.lowBatteryWarningPercent / 100.0
        val emptiness = (1.0 - resources.battery / warningLevel).coerceIn(0.0, 1.0)
        val chance = FLICKER_CHANCE_AT_WARNING + (FLICKER_CHANCE_AT_EMPTY - FLICKER_CHANCE_AT_WARNING) * emptiness
        if (random.nextDouble() < chance) {
            flickerTicksLeft = random.nextInt(4) // 1-4 ticks dark including this one
            return FLICKER_DIMMING
        }
        return 1.0
    }
}
