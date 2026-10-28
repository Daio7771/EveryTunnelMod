package daio7771.everytunnel.platform

import daio7771.everytunnel.config.ConfigManager
import daio7771.everytunnel.systems.NoiseEvent
import daio7771.everytunnel.systems.NoiseSource
import daio7771.everytunnel.systems.NoiseSystem
import daio7771.everytunnel.systems.ResourceSystem
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.math.Vec3d
import net.minecraft.world.event.GameEvent
import java.util.UUID
import kotlin.math.sqrt

/**
 * Feeds [NoiseSystem] with what players do, every server tick:
 *
 * - Footsteps: one noise per block walked on the ground; radius by sneaking,
 *   walking or sprinting. Moving more than [MAX_WALK_PER_TICK] in one tick is a
 *   teleport, not walking.
 * - Jumps (vanilla jump statistic) and landings (radius scaled by the fall height).
 * - Panting once per second while out of stamina.
 * - Doors, trapdoors, fence gates, levers, buttons, chests, note blocks: the vanilla
 *   game events these emit (the vibrations the Warden and sculk sensors hear), caught
 *   by `mixin.ServerWorldMixin`, at the block's position.
 * - Flashlight clicks and battery changes, reported by [FlashlightHooks].
 *
 * Only Survival/Adventure players in worlds with levels make noise (Creative and
 * Spectator are silent, like vanilla mobs ignoring them), and none while hiding in a
 * locker ([HidingHooks]).
 * `/everytunnel noise watch` shows each of your noises in the action bar.
 */
object NoiseHooks {

    private const val MAX_WALK_PER_TICK = 2.0
    private const val BREATH_INTERVAL_TICKS = 20

    /** Vanilla game events that count as interacting with a block. */
    private val INTERACT_EVENTS = setOf(
        GameEvent.BLOCK_OPEN, GameEvent.BLOCK_CLOSE,
        GameEvent.BLOCK_ACTIVATE, GameEvent.BLOCK_DEACTIVATE,
        GameEvent.CONTAINER_OPEN, GameEvent.CONTAINER_CLOSE,
        GameEvent.NOTE_BLOCK_PLAY,
    )

    private class Tracked {
        val steps = NoiseSystem.StepCounter()
        var lastX = 0.0
        var lastZ = 0.0
        var hasPosition = false
        var wasOnGround = true
        var lastFallDistance = 0.0
        var breathCooldown = 0
    }

    // Server thread only.
    private val tracked = HashMap<UUID, Tracked>()
    private val jumps = JumpCounter()
    private val watching = HashSet<UUID>()

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register(::tick)
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            tracked.remove(handler.player.uuid)
            jumps.forget(handler.player.uuid)
            watching.remove(handler.player.uuid)
        }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            tracked.clear()
            jumps.clear()
            watching.clear()
            NoiseSystem.clear()
        }
    }

    fun makesNoise(player: ServerPlayerEntity): Boolean =
        ResourceHooks.appliesTo(player) && !DeathHooks.isDying(player) && !HidingHooks.isHidden(player)

    private fun tick(server: MinecraftServer) {
        NoiseSystem.prune(server.ticks.toLong())
        val config = ConfigManager.gameplay
        for (player in server.playerManager.playerList) {
            val jumpCount = jumps.jumpsSince(player)
            if (!makesNoise(player)) {
                tracked.remove(player.uuid)
                continue
            }
            val t = tracked.getOrPut(player.uuid) { Tracked() }

            if (t.hasPosition) {
                val dx = player.x - t.lastX
                val dz = player.z - t.lastZ
                val distance = sqrt(dx * dx + dz * dz)
                if (distance > MAX_WALK_PER_TICK) {
                    t.steps.reset()
                } else if (player.isOnGround) {
                    repeat(t.steps.add(distance)) {
                        emit(player, NoiseSystem.movementSource(player.isSneaking, player.isSprinting))
                    }
                }
            }
            if (jumpCount > 0) emit(player, NoiseSource.JUMP)
            if (!t.wasOnGround && player.isOnGround && t.lastFallDistance >= NoiseSystem.MIN_LANDING_FALL) {
                emit(player, NoiseSource.LAND, NoiseSystem.landingRadius(config.noise, t.lastFallDistance))
            }
            if (ResourceSystem.stateOf(player.uuid, config).exhausted) {
                if (--t.breathCooldown <= 0) {
                    emit(player, NoiseSource.BREATHING)
                    t.breathCooldown = BREATH_INTERVAL_TICKS
                }
            } else {
                t.breathCooldown = 0
            }

            t.lastX = player.x
            t.lastZ = player.z
            t.hasPosition = true
            t.wasOnGround = player.isOnGround
            t.lastFallDistance = player.fallDistance.toDouble()
        }
    }

    /** Flashlight click or battery change (called by [FlashlightHooks]). */
    fun drink(player: ServerPlayerEntity) {
        if (makesNoise(player)) emit(player, NoiseSource.DRINK)
    }

    fun equipment(player: ServerPlayerEntity) {
        if (makesNoise(player)) emit(player, NoiseSource.EQUIPMENT)
    }

    /** Called by `mixin.ServerWorldMixin` for every vanilla game event. */
    @JvmStatic
    fun onGameEvent(world: ServerWorld, event: GameEvent, position: Vec3d, emitter: GameEvent.Emitter) {
        if (event !in INTERACT_EVENTS) return
        val player = emitter.sourceEntity() as? ServerPlayerEntity ?: return
        if (makesNoise(player)) emit(player, NoiseSource.INTERACT, at = position)
    }

    /** Turns the action bar noise readout on or off for this player; returns the new setting. */
    fun toggleWatch(player: ServerPlayerEntity): Boolean =
        if (watching.remove(player.uuid)) false else watching.add(player.uuid)

    private fun emit(
        player: ServerPlayerEntity,
        source: NoiseSource,
        radius: Double = NoiseSystem.radiusOf(source, ConfigManager.gameplay.noise),
        at: Vec3d = player.pos,
    ) {
        val event = NoiseEvent(
            source, LevelTrackingHooks.dimensionOf(player), at.x, at.y, at.z, radius,
            player.server.ticks.toLong(), player.uuid,
        )
        NoiseSystem.emit(event)
        if (player.uuid in watching && radius > 0.0) {
            player.sendMessage(Text.literal("Noise: %s, %.1f blocks".format(source.id, radius)), true)
        }
    }
}
