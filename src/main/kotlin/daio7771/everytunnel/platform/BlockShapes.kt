package daio7771.everytunnel.platform

import net.minecraft.block.Block
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes

/**
 * Outline and collision shapes for the mod's modelled blocks.
 *
 * Boxes are given in pixels (0..16), the way the block models are written
 * (`tools/blocks/generate.py`), for the model's base orientation. They are turned like a
 * blockstate turns the model: first [x] degrees around the X axis (90 tips the top towards
 * north), then [y] degrees around the vertical axis (90 turns north to east). Keeping the
 * same rotations as the blockstates keeps the hitbox on the visible model.
 */
object BlockShapes {

    /** A box in pixels: x1, y1, z1, x2, y2, z2. */
    class Box(val x1: Double, val y1: Double, val z1: Double, val x2: Double, val y2: Double, val z2: Double)

    fun box(x1: Number, y1: Number, z1: Number, x2: Number, y2: Number, z2: Number) =
        Box(x1.toDouble(), y1.toDouble(), z1.toDouble(), x2.toDouble(), y2.toDouble(), z2.toDouble())

    /** The boxes turned by [x] then [y] degrees (multiples of 90), as one shape. */
    fun rotated(boxes: List<Box>, x: Int, y: Int): VoxelShape =
        boxes.map { rotate(it, x, y) }
            .map { Block.createCuboidShape(it.x1, it.y1, it.z1, it.x2, it.y2, it.z2) }
            .fold(VoxelShapes.empty()) { a, b -> VoxelShapes.union(a, b) }

    /** Shapes for a model facing north in its base orientation, for the four horizontal facings. */
    fun horizontal(boxes: List<Box>): Map<Direction, VoxelShape> = mapOf(
        Direction.NORTH to rotated(boxes, 0, 0),
        Direction.EAST to rotated(boxes, 0, 90),
        Direction.SOUTH to rotated(boxes, 0, 180),
        Direction.WEST to rotated(boxes, 0, 270),
    )

    private fun rotate(b: Box, x: Int, y: Int): Box {
        var p1 = doubleArrayOf(b.x1 - 8, b.y1 - 8, b.z1 - 8)
        var p2 = doubleArrayOf(b.x2 - 8, b.y2 - 8, b.z2 - 8)
        repeat(Math.floorMod(x, 360) / 90) {
            p1 = quarterX(p1)
            p2 = quarterX(p2)
        }
        repeat(Math.floorMod(y, 360) / 90) {
            p1 = quarterY(p1)
            p2 = quarterY(p2)
        }
        return Box(
            minOf(p1[0], p2[0]) + 8, minOf(p1[1], p2[1]) + 8, minOf(p1[2], p2[2]) + 8,
            maxOf(p1[0], p2[0]) + 8, maxOf(p1[1], p2[1]) + 8, maxOf(p1[2], p2[2]) + 8,
        )
    }

    /** 90 degrees around X: up turns to north (-z), north turns to down. */
    private fun quarterX(p: DoubleArray) = doubleArrayOf(p[0], p[2], -p[1])

    /** 90 degrees around Y, clockwise seen from above: north (-z) turns to east (+x). */
    private fun quarterY(p: DoubleArray) = doubleArrayOf(-p[2], p[1], p[0])
}
