package daio7771.everytunnel.platform

import daio7771.everytunnel.core.hiding.LookLimit
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityPose
import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.listener.ClientPlayPacketListener
import net.minecraft.network.packet.Packet
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket
import net.minecraft.sound.SoundCategory
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World

/**
 * The invisible seat that holds a player hiding in a locker ([HidingHooks]): it stands on the
 * floor of the locker's lower block, facing the way the locker's door does, and the player
 * rides it. Riding keeps them inside a block they could not otherwise stand in, with no
 * walking, and lets vanilla do the rest: sneaking gets off, and getting off puts them back on
 * their feet in front of the door ([updatePassengerForDismount]).
 *
 * - The head turns at most as far as [LookLimit] allows ([onPassengerLookAround]; vanilla calls
 *   it on both sides each time the mouse moves the head, as for a boat).
 * - The door's sound plays whenever the player gets out alive, by whatever way, unless the
 *   game takes them out itself ([quiet], a new game).
 * - It is gone as soon as it has no passenger or its locker is gone, and is never saved
 *   ([ModEntities.LOCKER_SEAT] disables saving).
 *
 * It draws nothing (`EmptyEntityRenderer` on the client).
 */
class LockerSeatEntity(type: EntityType<out LockerSeatEntity>, world: World) : Entity(type, world) {

    /** True when the game takes the player out itself: no door sound. */
    var quiet = false

    init {
        noClip = true
        setNoGravity(true)
    }

    /** The way the locker's door faces (and the player looks). */
    val facing: Direction get() = Direction.fromRotation(yaw.toDouble())

    override fun initDataTracker() {}
    override fun readCustomDataFromNbt(nbt: NbtCompound) {}
    override fun writeCustomDataToNbt(nbt: NbtCompound) {}
    override fun createSpawnPacket(): Packet<ClientPlayPacketListener> = EntitySpawnS2CPacket(this)

    override fun tick() {
        super.tick()
        if (world.isClient) return
        if (!hasPassengers() || !world.getBlockState(blockPos).isOf(ModBlocks.LOCKER)) {
            removeAllPassengers()
            discard()
        }
    }

    override fun canAddPassenger(passenger: Entity): Boolean = !hasPassengers() && passenger is PlayerEntity

    /** The passenger's feet on the seat (on the locker's floor), not above it as on a mount. */
    override fun updatePassengerPosition(passenger: Entity, positionUpdater: PositionUpdater) {
        if (!hasPassenger(passenger)) return
        positionUpdater.accept(passenger, x, y, z)
    }

    override fun onPassengerLookAround(passenger: Entity) {
        val correction = LookLimit.yawCorrection(passenger.yaw, yaw)
        if (correction != 0f) {
            passenger.yaw += correction
            passenger.prevYaw += correction
        }
        passenger.headYaw = passenger.yaw
        if (passenger is LivingEntity) passenger.bodyYaw = yaw
        passenger.pitch = LookLimit.pitch(passenger.pitch)
        passenger.prevPitch = LookLimit.pitch(passenger.prevPitch)
    }

    /** Out on the floor in front of the door, if there is room; vanilla's choice otherwise. */
    override fun updatePassengerForDismount(passenger: LivingEntity): Vec3d {
        val front = blockPos.offset(facing)
        val target = Vec3d(front.x + 0.5, front.y.toDouble(), front.z + 0.5)
        val box = passenger.getDimensions(EntityPose.STANDING).getBoxAt(target)
        return if (world.isSpaceEmpty(passenger, box)) target else super.updatePassengerForDismount(passenger)
    }

    override fun removePassenger(passenger: Entity) {
        super.removePassenger(passenger)
        if (!world.isClient && !quiet && passenger.isAlive) {
            world.playSound(null, blockPos.up(), ModSounds.LOCKER_DOOR, SoundCategory.BLOCKS, 1f, 0.95f + random.nextFloat() * 0.1f)
        }
    }
}
