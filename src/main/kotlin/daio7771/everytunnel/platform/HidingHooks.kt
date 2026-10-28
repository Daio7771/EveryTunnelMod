package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.world.WorldRules
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.block.BlockState
import net.minecraft.entity.Entity
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import java.util.UUID

/**
 * Hiding in a locker ([LockerBlock]), in worlds with levels.
 *
 * - **In:** right-clicking a locker from in front of its door, standing, not riding anything.
 *   The player is put on an invisible seat on the floor of the locker's lower block
 *   ([LockerSeatEntity]), facing out through the door, and the door's sound plays
 *   (`block.locker.door`).
 * - **Inside:** the head turns only a little (`core.hiding.LookLimit`), the player makes no
 *   noise at all ([NoiseHooks.makesNoise]), and the client draws the inside of the door with
 *   its vent over the screen (`client.hud.LockerView`). Enemies can ask [isHidden].
 * - **Out:** right-clicking again or sneaking; the player steps out in front of the door, with
 *   the door's sound again.
 *
 * Getting in and out are at least [USE_COOLDOWN_TICKS] apart, so holding the use key does not
 * go in and straight out again. A new game takes everyone out, quietly ([newGame]).
 */
object HidingHooks {

    const val USE_COOLDOWN_TICKS = 10

    // Server thread only. Player -> the server tick of their last time in or out.
    private val lastUse = mutableMapOf<UUID, Int>()

    fun register() {
        ServerLifecycleEvents.SERVER_STOPPED.register { lastUse.clear() }
    }

    fun isHidden(player: Entity): Boolean = player.vehicle is LockerSeatEntity

    /** [player] right-clicked the locker block at [pos]: in or out. True if something happened. */
    fun use(player: ServerPlayerEntity, pos: BlockPos, state: BlockState): Boolean {
        val now = player.server.ticks
        val last = lastUse[player.uuid]
        if (last != null && now - last in 0 until USE_COOLDOWN_TICKS) return isHidden(player)
        if (isHidden(player)) {
            lastUse[player.uuid] = now
            player.stopRiding()
            return true
        }
        if (!canHide(player)) return false
        val world = player.serverWorld
        var base = pos
        while (world.getBlockState(base.down()).isOf(ModBlocks.LOCKER)) base = base.down()
        val facing = state.get(StackFurnitureBlock.FACING)
        // Only from in front of the door.
        val toPlayer = player.pos.subtract(Vec3d.ofBottomCenter(base))
        if (toPlayer.x * facing.offsetX + toPlayer.z * facing.offsetZ <= 0.0) return false
        if (world.getEntitiesByClass(LockerSeatEntity::class.java, Box(base)) { true }.isNotEmpty()) return false

        val seat = LockerSeatEntity(ModEntities.LOCKER_SEAT, world)
        seat.refreshPositionAndAngles(base.x + 0.5, base.y.toDouble(), base.z + 0.5, facing.asRotation(), 0f)
        if (!world.spawnEntity(seat)) return false
        player.refreshPositionAndAngles(seat.x, seat.y, seat.z, seat.yaw, 0f)
        player.headYaw = seat.yaw
        player.bodyYaw = seat.yaw
        if (!player.startRiding(seat, true)) {
            seat.discard()
            return false
        }
        lastUse[player.uuid] = now
        world.playSound(null, base.up(), ModSounds.LOCKER_DOOR, SoundCategory.BLOCKS, 1f, 0.95f + world.random.nextFloat() * 0.1f)
        EveryTunnel.LOGGER.debug("{} hides in the locker at {}", player.name.string, base)
        return true
    }

    /** In a world with levels, alive, not a spectator, on their feet and not riding anything. */
    private fun canHide(player: ServerPlayerEntity): Boolean =
        WorldRules.active && player.isAlive && !player.isSpectator && !player.hasVehicle() && !player.isSneaking &&
            !LoadingHooks.isLoading(player) && !DeathHooks.isDying(player)

    /** A new game: everyone out of the lockers, without the door's sound. */
    fun newGame(server: MinecraftServer) {
        lastUse.clear()
        for (player in server.playerManager.playerList) {
            val seat = player.vehicle as? LockerSeatEntity ?: continue
            seat.quiet = true
            player.stopRiding()
            seat.discard()
        }
    }
}
