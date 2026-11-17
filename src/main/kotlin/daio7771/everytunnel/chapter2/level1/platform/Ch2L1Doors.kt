package daio7771.everytunnel.chapter2.level1.platform

import daio7771.everytunnel.chapter2.platform.Ch2Blocks
import net.minecraft.block.DoorBlock
import net.minecraft.block.enums.DoubleBlockHalf
import net.minecraft.entity.Entity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World

/**
 * The factory's doors as its people use them: someone a scene walks somewhere opens the factory doors (`factory_door`, never the locked
 * grey ones) in their way ([openNear]), and a scene shuts or opens the leaves of a doorway round a place ([set]), with the door's own sound.
 * The grey authorized personnel doors that hands do not open (the auxiliary lab's, the office's, the sealed zone's) open and shut only this way, with
 * [locked]: a badge, a code or a card did it.
 */
object Ch2L1Doors {

    /** How near someone walking opens a door, blocks. */
    private const val REACH = 1.4

    /** Opens the closed factory doors within [REACH] of [entity], at its feet's height. */
    fun openNear(entity: Entity) {
        set(entity.world, entity.pos, REACH, true, entity)
    }

    /**
     * Opens ([open]) or shuts the factory doors whose lower half is within [radius] of [at] (along the floor, at its height or a block
     * below or above), each with its sound; how many changed. With [locked] the grey authorized personnel doors instead.
     */
    fun set(world: World, at: Vec3d, radius: Double, open: Boolean, by: Entity? = null, locked: Boolean = false): Int {
        val block = if (locked) Ch2Blocks.SECURE_DOOR else Ch2Blocks.FACTORY_DOOR
        val base = BlockPos.ofFloored(at)
        val r = Math.ceil(radius).toInt()
        var changed = 0
        for (dy in -1..1) for (dx in -r..r) for (dz in -r..r) {
            val pos = base.add(dx, dy, dz)
            val state = world.getBlockState(pos)
            if (!state.isOf(block) || state.get(DoorBlock.HALF) != DoubleBlockHalf.LOWER) continue
            val cx = pos.x + 0.5 - at.x
            val cz = pos.z + 0.5 - at.z
            if (cx * cx + cz * cz > radius * radius || state.get(DoorBlock.OPEN) == open) continue
            (state.block as DoorBlock).setOpen(by, world, state, pos, open)
            changed++
        }
        return changed
    }
}
