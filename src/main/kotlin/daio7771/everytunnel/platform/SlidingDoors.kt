package daio7771.everytunnel.platform

import daio7771.everytunnel.core.door.SlidingDoor
import daio7771.everytunnel.core.level.BlockBox
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.block.Blocks
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction

/**
 * Level 5's sliding doors in the world: the airlock's ([LabHooks]) and the three locked ones ([LabDoorHooks]).
 * A door fills a zone's box, one block thick: its columns run along the box's long side, two leaves of
 * [AirlockDoorBlock] meeting in the middle, the lower half's sliding towards the lower coordinates. How much
 * of each column is left at each step is [SlidingDoor]'s. [open] slides one open by itself over time, with
 * the airlock's sound.
 */
object SlidingDoors {

    /** A door sliding open by itself: [open] quarters so far, one more every [stepTicks]. */
    private class Opening(val world: ServerWorld, val box: BlockBox, val stepTicks: Int, var open: Int = 0, var ticks: Int = 0)

    // Server thread only.
    private val opening = mutableListOf<Opening>()

    fun register() {
        ServerLifecycleEvents.SERVER_STOPPED.register { opening.clear() }
        ServerTickEvents.END_SERVER_TICK.register(::tick)
    }

    /** Starts [box]'s door sliding open, a quarter of a block every [stepTicks], heard from its middle. */
    fun open(world: ServerWorld, box: BlockBox, stepTicks: Int) {
        if (opening.any { it.box == box }) return
        world.playSound(null, centre(box), ModSounds.AIRLOCK_OPEN, SoundCategory.BLOCKS, 1.5f, 1f)
        opening += Opening(world, box, stepTicks)
    }

    /** Doors stop where they are (a new game: the level is built again anyway). */
    fun forgetAll() {
        opening.clear()
    }

    private fun tick(server: MinecraftServer) {
        val iterator = opening.iterator()
        while (iterator.hasNext()) {
            val door = iterator.next()
            if (++door.ticks % door.stepTicks != 0) continue
            door.open++
            set(door.world, door.box, door.open)
            if (door.open >= steps(door.box)) iterator.remove()
        }
    }

    /** Sets [box]'s door [open] quarters open (0 shut, [steps] open). */
    fun set(world: ServerWorld, box: BlockBox, open: Int) {
        val alongX = box.maxX > box.minX
        val first = if (alongX) box.minX else box.minZ
        val count = columns(box)
        val widths = SlidingDoor.widths(count, open)
        for (i in 0 until count) {
            val low = i < count / 2
            val facing = if (alongX) (if (low) Direction.WEST else Direction.EAST) else (if (low) Direction.NORTH else Direction.SOUTH)
            val state = if (widths[i] == 0) {
                Blocks.AIR.defaultState
            } else {
                ModBlocks.AIRLOCK_DOOR.defaultState.with(AirlockDoorBlock.FACING, facing).with(AirlockDoorBlock.WIDTH, widths[i])
            }
            for (y in box.minY..box.maxY) {
                world.setBlockState(if (alongX) BlockPos(first + i, y, box.minZ) else BlockPos(box.minX, y, first + i), state)
            }
        }
    }

    /** Quarter steps from shut to open. */
    fun steps(box: BlockBox): Int = SlidingDoor.steps(columns(box))

    fun playersIn(world: ServerWorld, box: BlockBox): Boolean =
        world.getEntitiesByClass(PlayerEntity::class.java, toBox(box)) { !it.isSpectator }.isNotEmpty()

    fun toBox(box: BlockBox) =
        Box(box.minX.toDouble(), box.minY.toDouble(), box.minZ.toDouble(), box.maxX + 1.0, box.maxY + 1.0, box.maxZ + 1.0)

    fun centre(box: BlockBox) = BlockPos((box.minX + box.maxX) / 2, box.minY + 1, (box.minZ + box.maxZ) / 2)

    private fun columns(box: BlockBox): Int = maxOf(box.maxX - box.minX, box.maxZ - box.minZ) + 1
}
