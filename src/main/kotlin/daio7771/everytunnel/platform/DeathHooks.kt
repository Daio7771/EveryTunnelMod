package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.death.DeathCause
import daio7771.everytunnel.core.progress.Checkpoint
import daio7771.everytunnel.systems.ResourceSystem
import daio7771.everytunnel.world.LevelTriggers
import daio7771.everytunnel.world.WorldRules
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.damage.DamageSource
import net.minecraft.network.PacketByteBuf
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import java.util.UUID

/**
 * Dying in a level restarts it, without vanilla's death screen.
 *
 * When a player would die while the world rules apply (Survival or Adventure in a world
 * with levels), they stay alive instead: they hear `event.death`, the screen fades to
 * black ([FADE_OUT_TICKS]), they cannot move and nothing hurts them; on the black, but in Levels 1
 * and 4, the death screen says what killed them, with a tip ([DeathCause], sent to the client's
 * `DeathScreen`), and the black stays until they press its "Try again" (or Esc: [RETRY_PACKET_ID]);
 * in the dark they are
 * taken to the start of the level they were last in (its first spawn point tagged
 * `player`) with full health and resources, and the Pale men of that level go back to
 * their spawn points and start over (the Observers too, and Level 5's Hunters and Distortions,
 * [DistortionHooks.restart]). Then the screen fades back in ([FADE_IN_TICKS]).
 * The inventory is kept: what was picked up stays picked up.
 *
 * A player who never entered a level dies the vanilla way.
 */
object DeathHooks {

    /** Screen fade, sent to the client: target darkness (0 to 1) and seconds to get there. */
    val FADE_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "screen_fade")

    /** What killed the player ([DeathCause.id]), sent to the client when they die, for the death screen. */
    val CAUSE_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "death_cause")

    /** From the client: the player pressed the death screen's "Try again". */
    val RETRY_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "death_retry")

    const val FADE_OUT_TICKS = 30
    /** Taken to the level start in the middle of the darkness. */
    private const val RESTART_TICK = 50
    private const val FADE_IN_START_TICK = 60
    private const val FADE_IN_TICKS = 30

    private val FREEZE_ID: UUID = UUID.fromString("5c0b8e8e-2f4c-4d0a-9a51-6f2b8f3d7a10")

    /** [waits]: a death screen shows, and the black stays until the player has [retried]. */
    private class Dying(
        val levelId: String,
        val waits: Boolean,
        var ticks: Int = 0,
        var restarted: Boolean = false,
        var retried: Boolean = false,
    )

    // Server thread only.
    private val dying = HashMap<UUID, Dying>()
    private val lastLevel = HashMap<UUID, String>()

    fun register() {
        LevelTriggers.register { change -> change.current.levelId?.let { lastLevel[change.playerId] = it } }
        ServerLivingEntityEvents.ALLOW_DEATH.register { entity, source, _ ->
            val player = entity as? ServerPlayerEntity ?: return@register true
            if (player.uuid in dying) {
                player.health = 1f
                return@register false
            }
            val levelId = lastLevel[player.uuid]
            if (levelId == null || !WorldRules.appliesTo(player.isCreative, player.isSpectator) || startOf(levelId) == null) {
                return@register true
            }
            startDying(player, levelId, source)
            false
        }
        ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, _, _ -> entity.uuid !in dying }
        ServerTickEvents.END_SERVER_TICK.register(::tick)
        ServerPlayNetworking.registerGlobalReceiver(RETRY_PACKET_ID) { server, player, _, _, _ ->
            server.execute { dying[player.uuid]?.retried = true }
        }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            // Leaving while dying: finish now, so they come back alive at the level start.
            val player = handler.player
            dying.remove(player.uuid)?.let { state -> if (!state.restarted) restart(player, state.levelId) }
            unfreeze(player)
            lastLevel.remove(player.uuid)
        }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            dying.clear()
            lastLevel.clear()
        }
    }

    /** True from the fatal hit until the screen starts fading back in; a dying player makes no noise. */
    fun isDying(player: ServerPlayerEntity): Boolean = player.uuid in dying

    private fun startDying(player: ServerPlayerEntity, levelId: String, source: DamageSource) {
        CarryHooks.died(player)
        player.health = 1f
        player.isSprinting = false
        val cause = causeOf(levelId, source)
        val shown = cause != null && ServerPlayNetworking.canSend(player, CAUSE_PACKET_ID)
        dying[player.uuid] = Dying(levelId, waits = shown)
        player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED)?.let { speed ->
            if (speed.getModifier(FREEZE_ID) == null) {
                speed.addTemporaryModifier(
                    EntityAttributeModifier(FREEZE_ID, "Every Tunnel: dying", -1.0, EntityAttributeModifier.Operation.MULTIPLY_TOTAL),
                )
            }
        }
        player.playSound(ModSounds.DEATH, SoundCategory.PLAYERS, 1f, 1f)
        sendFade(player, 1f, FADE_OUT_TICKS / 20f)
        if (shown) ServerPlayNetworking.send(player, CAUSE_PACKET_ID, PacketByteBufs.create().writeString(cause!!.id))
        EveryTunnel.LOGGER.info("{} died in {}: restarting the level", player.name.string, levelId)
        PlaytestLog.died(player, levelId, source)
        AchievementHooks.died(player, source)
    }

    private fun tick(server: MinecraftServer) {
        val iterator = dying.entries.iterator()
        while (iterator.hasNext()) {
            val (id, state) = iterator.next()
            val player = server.playerManager.getPlayer(id)
            if (player == null) {
                iterator.remove()
                continue
            }
            state.ticks++
            if (state.ticks >= RESTART_TICK && !state.restarted) {
                state.restarted = true
                restart(player, state.levelId)
            }
            val done = if (state.waits) state.restarted && state.retried else state.ticks >= FADE_IN_START_TICK
            if (done) {
                iterator.remove()
                unfreeze(player)
                sendFade(player, 0f, FADE_IN_TICKS / 20f)
            }
        }
    }

    private fun restart(player: ServerPlayerEntity, levelId: String) {
        val level = LevelManager.get(levelId)
        // A restart point reached in this level (Level 5's Lab hall) comes before its start.
        val checkpoint = Checkpoint.parse(ProgressService.get().checkpoint(player))
        val start = checkpoint?.takeIf { it.levelId == levelId }?.pointId?.let { level?.spawnPoint(it) } ?: startOf(levelId)
        val world = level?.let { player.server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(it.dimension))) }
        if (start == null || world == null) {
            EveryTunnel.LOGGER.warn("Cannot restart {} for {}: the level or its start is gone", levelId, player.name.string)
            return
        }
        player.stopRiding()
        player.teleport(world, start.position.x, start.position.y, start.position.z, start.yaw, start.pitch)
        player.velocity = Vec3d.ZERO
        player.fallDistance = 0f
        player.extinguish()
        player.clearStatusEffects()
        player.health = player.maxHealth
        ResourceSystem.reset(player.uuid)

        for (paleMan in world.getEntitiesByType(ModEntities.PALE_MAN) { it.isAlive && it.homeKey?.startsWith("$levelId/") == true }) {
            val point = level.spawnPoint(paleMan.homeKey!!.substringAfter('/')) ?: continue
            paleMan.returnHome(point.position.x, point.position.y, point.position.z, point.yaw)
        }
        ObserverHooks.sendHome(world, levelId)
        DistortionHooks.restart(world, levelId)
        CollapseHooks.reset(player.server, levelId)
    }

    /** What the death screen names for a death in [levelId] by [source], or null for none. */
    private fun causeOf(levelId: String, source: DamageSource): DeathCause? {
        val order = LevelManager.get(levelId)?.order ?: return null
        val killer = (source.attacker ?: source.source)?.let { Registries.ENTITY_TYPE.getId(it.type).toString() }
        val damage = source.typeRegistryEntry.key.map { it.value.toString() }.orElse("")
        return DeathCause.of(order, killer, damage)
    }

    private fun unfreeze(player: ServerPlayerEntity) {
        player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED)?.removeModifier(FREEZE_ID)
    }

    private fun startOf(levelId: String) =
        LevelManager.get(levelId)?.spawnPoints?.firstOrNull { RespawnHooks.TAG in it.tags }

    fun sendFade(player: ServerPlayerEntity, darkness: Float, seconds: Float) {
        if (!ServerPlayNetworking.canSend(player, FADE_PACKET_ID)) return
        val buf = PacketByteBufs.create()
        buf.writeFloat(darkness)
        buf.writeFloat(seconds)
        ServerPlayNetworking.send(player, FADE_PACKET_ID, buf)
    }

    /** (darkness, seconds) */
    fun readFade(buf: PacketByteBuf): Pair<Float, Float> = buf.readFloat() to buf.readFloat()
}
