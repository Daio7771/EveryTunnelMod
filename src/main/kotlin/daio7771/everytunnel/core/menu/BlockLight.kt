package daio7771.everytunnel.core.menu

/**
 * Block light for a small box of blocks with no world around it (the main menu's scene,
 * `client.menu.MenuScene`), spread the way Minecraft spreads it: a block that emits light
 * has that level, and light moves to the six neighbours losing max(1, the neighbour's
 * opacity) per step, so a fully opaque block (opacity 15) stops it.
 *
 * Cells are x, y, z from 0 to the sizes; outside the box the light is 0. Set [opacity] and
 * [emission], then [compute]. Plain Kotlin, unit tested.
 */
class BlockLight(val sizeX: Int, val sizeY: Int, val sizeZ: Int) {

    private val opacity = IntArray(sizeX * sizeY * sizeZ)
    private val emission = IntArray(sizeX * sizeY * sizeZ)
    private val level = IntArray(sizeX * sizeY * sizeZ)

    fun inside(x: Int, y: Int, z: Int): Boolean = x in 0 until sizeX && y in 0 until sizeY && z in 0 until sizeZ

    fun setOpacity(x: Int, y: Int, z: Int, value: Int) {
        opacity[index(x, y, z)] = value.coerceIn(0, MAX)
    }

    fun setEmission(x: Int, y: Int, z: Int, value: Int) {
        emission[index(x, y, z)] = value.coerceIn(0, MAX)
    }

    /** The light level after the last [compute]; 0 outside the box. */
    fun levelAt(x: Int, y: Int, z: Int): Int = if (inside(x, y, z)) level[index(x, y, z)] else 0

    /** Spreads the light of every emitting block from scratch. */
    fun compute() {
        val queue = ArrayDeque<Int>()
        for (i in level.indices) {
            level[i] = emission[i]
            if (level[i] > 0) queue.addLast(i)
        }
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            val x = i % sizeX
            val y = i / sizeX % sizeY
            val z = i / (sizeX * sizeY)
            for ((dx, dy, dz) in NEIGHBOURS) {
                val nx = x + dx
                val ny = y + dy
                val nz = z + dz
                if (!inside(nx, ny, nz)) continue
                val j = index(nx, ny, nz)
                val value = level[i] - maxOf(1, opacity[j])
                if (value > level[j]) {
                    // Brighter than before: spread again from here (a cell may be queued twice).
                    level[j] = value
                    queue.addLast(j)
                }
            }
        }
    }

    private fun index(x: Int, y: Int, z: Int): Int = (z * sizeY + y) * sizeX + x

    companion object {
        const val MAX = 15
        private val NEIGHBOURS = listOf(
            Triple(1, 0, 0), Triple(-1, 0, 0), Triple(0, 1, 0), Triple(0, -1, 0), Triple(0, 0, 1), Triple(0, 0, -1),
        )
    }
}
