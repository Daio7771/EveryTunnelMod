package daio7771.everytunnel.client.effects

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.client.ClientResources
import daio7771.everytunnel.config.ConfigManager
import daio7771.everytunnel.core.detector.Detector
import daio7771.everytunnel.platform.DistortionEntity
import daio7771.everytunnel.platform.ModSounds
import daio7771.everytunnel.platform.ResourceSyncPacket
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.client.sound.SoundInstance
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Identifier
import net.minecraft.util.hit.HitResult
import net.minecraft.world.RaycastContext
import java.util.Random
import kotlin.math.sqrt

/**
 * Level 5's radiation detector, which the player always has on them there (no item: [ResourceSyncPacket.detector],
 * set by the server from the laboratory's airlock on): it notices the nearest
 * Distortion Cloud (an entity of the type tag `every_tunnel:radiation_sources`) within
 * `detector.range` blocks, never through a wall (nothing solid between the player's eyes and it), and
 * clicks in the player's head like a Geiger counter, more counts as it comes nearer ([Detector]).
 * Minecraft starts sounds once a tick, so each tick draws how many counts fall in it and plays one
 * sound holding that many: `item.detector.click` (one), `.clicks` (two to four) or `.crackle` (more).
 * [client.hud.DetectorHud] draws what it reads. Only while the world rules apply.
 */
object DetectorEffect {

    private val SOURCES: TagKey<EntityType<*>> = TagKey.of(RegistryKeys.ENTITY_TYPE, Identifier(EveryTunnel.MOD_ID, "radiation_sources"))
    private const val VOLUME = 0.6f

    /** The detector works here: the server says so ([ResourceSyncPacket.detector]). */
    var working = false
        private set

    /** Distance to the Cloud it reads, or null for none. */
    var distance: Double? = null
        private set

    private val random = Random()

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> stop() }
    }

    private fun stop() {
        working = false
        distance = null
    }

    private fun tick(client: MinecraftClient) {
        val player = client.player
        val world = client.world
        if (player == null || world == null || !ClientResources.current.active || player.isSpectator) return stop()
        if (client.isPaused) return
        working = ClientResources.current.detector
        if (!working) {
            distance = null
            return
        }
        val detector = Detector(ConfigManager.gameplay.detector)
        distance = nearest(world, player, ConfigManager.gameplay.detector.range)
        val sound = when (detector.countsThisTick(detector.level(distance), random::nextDouble)) {
            0 -> return
            1 -> ModSounds.DETECTOR_CLICK
            in 2..4 -> ModSounds.DETECTOR_CLICKS
            else -> ModSounds.DETECTOR_CRACKLE
        }
        client.soundManager.play(
            PositionedSoundInstance(
                sound.id, SoundCategory.PLAYERS, VOLUME, 1.0f, SoundInstance.createRandom(),
                false, 0, SoundInstance.AttenuationType.NONE, 0.0, 0.0, 0.0, true,
            ),
        )
    }

    /** Distance from the player's eyes to the nearest source in range with nothing solid in between. */
    private fun nearest(world: ClientWorld, player: ClientPlayerEntity, range: Double): Double? {
        val eyes = player.eyePos
        return world.getEntitiesByClass(Entity::class.java, player.boundingBox.expand(range)) { it.isAlive && it.type.isIn(SOURCES) && (it as? DistortionEntity)?.ending != true }
            .map { it to sqrt(it.squaredDistanceTo(eyes)) }
            .filter { (_, d) -> d <= range }
            .sortedBy { it.second }
            .firstOrNull { (entity, _) ->
                val target = entity.boundingBox.center
                world.raycast(RaycastContext(eyes, target, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player)).type == HitResult.Type.MISS
            }
            ?.second
    }
}
