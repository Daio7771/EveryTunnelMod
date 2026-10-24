package daio7771.everytunnel.client.compat

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.client.ShaderMode
import daio7771.everytunnel.client.effects.FlashlightEffect
import daio7771.everytunnel.systems.FlashlightBeam
import dev.lambdaurora.lambdynlights.api.DynamicLightsContext
import dev.lambdaurora.lambdynlights.api.DynamicLightsInitializer
import dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehavior
import dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehaviorManager
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.world.ClientWorld
import net.minecraft.util.math.BlockPos

/**
 * LambDynamicLights integration: turns [FlashlightEffect.beam] into dynamic light.
 *
 * Only LambDynamicLights (4.x API) calls this entrypoint (`lambdynlights:initializer`
 * in fabric.mod.json), so without that mod nothing here is loaded and the flashlight
 * simply gives no light (the player is told once, see `FlashlightControls`).
 *
 * The beam is added as a custom light behavior while the flashlight is on and removed
 * when it goes off. It is re-added when the world changes, in case LambDynamicLights
 * dropped its light sources on the switch.
 */
object LambDynamicLightsCompat : DynamicLightsInitializer {

    private val behavior = FlashlightLightBehavior()
    private var manager: DynamicLightBehaviorManager? = null
    private var added = false
    private var addedIn: ClientWorld? = null

    override fun onInitializeDynamicLights(context: DynamicLightsContext) {
        manager = context.dynamicLightBehaviorManager()
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
        EveryTunnel.LOGGER.info("LambDynamicLights found: the flashlight casts dynamic light")
    }

    /** Pre-4.0 entrypoint method, still abstract in the API; LambDynamicLights 4.x calls the one with the context. */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onInitializeDynamicLights() {}

    private fun tick(client: MinecraftClient) {
        val manager = manager ?: return
        val beam = FlashlightEffect.beam
        val world = client.world
        if (added && (beam == null || world !== addedIn)) {
            manager.remove(behavior)
            added = false
            addedIn = null
        }
        if (!added && beam != null && world != null) {
            behavior.start(beam)
            manager.add(behavior)
            added = true
            addedIn = world
        }
    }
}

/**
 * The flashlight beam as a LambDynamicLights light behavior.
 *
 * LambDynamicLights may evaluate [lightAtPos] off the render thread, so it only reads
 * the immutable [applied] snapshot. [hasChanged] (called once per tick) takes the
 * latest beam only when it looks different, because every change rebuilds the chunk
 * sections around the beam. Without shaders ([ShaderMode]) it is stricter: bigger changes
 * count as the same, and at most one change every [NO_SHADER_MIN_TICKS] ticks (the
 * on-screen spot follows the camera at once, so the light may lag a little).
 */
private class FlashlightLightBehavior : DynamicLightBehavior {

    @Volatile
    private var applied: FlashlightBeam? = null

    /** Called just before the behavior is added, so its first bounding box is right. */
    fun start(beam: FlashlightBeam) {
        applied = beam
    }

    override fun lightAtPos(pos: BlockPos, falloffRatio: Double): Double {
        val beam = applied ?: return 0.0
        return beam.lightAt(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, falloffRatio)
    }

    override fun getBoundingBox(): DynamicLightBehavior.BoundingBox {
        val beam = applied ?: return DynamicLightBehavior.BoundingBox(0, 0, 0, 0, 0, 0)
        val b = beam.bounds()
        return DynamicLightBehavior.BoundingBox(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ)
    }

    /** Ticks since the light last changed (hasChanged runs once per tick). */
    private var ticksSinceChange = 0

    override fun hasChanged(): Boolean {
        ticksSinceChange++
        val latest = FlashlightEffect.beam ?: return false
        val current = applied
        val coarse = !ShaderMode.shadersOn
        if (current != null && (latest.looksLike(current, coarse) || (coarse && ticksSinceChange < NO_SHADER_MIN_TICKS))) return false
        applied = latest
        ticksSinceChange = 0
        return true
    }

    private companion object {
        /** Without shaders, at most 5 light changes a second. */
        const val NO_SHADER_MIN_TICKS = 4
    }
}
