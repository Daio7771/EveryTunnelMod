package daio7771.everytunnel.chapter2.level2.core

import kotlin.random.Random

/**
 * How the ceiling comes down on Stone Maw (phase 6.2, live since phase 9), as data: [plan] says which pieces fall, when and where, all
 * from `maw.death` and a seed (so a CONTINUE at the escape and the live fall leave the same mound), relative to a centre (Stone Maw's cell
 * when the last pillar breaks); [landings] when each lands, [heights] the mound left, [mound] the cells it fills. Plain Kotlin, unit tested;
 * `platform.Ch2L2Maw` spawns the falling blocks.
 *
 * A **piece** is a set of cells (dx, dz from the centre) that fall together at [Piece.delayTicks] after the collapse begins:
 * first [Ch2L2Maw.bitCount] single [Kind.BIT]s round the centre (the small bits), then the first [Kind.SLAB], 3 x 3 over the centre
 * itself, at [Ch2L2Maw.firstSlabDelayTicks] (it lands on the kneeling creature), then [Ch2L2Maw.slabCount] - 1 slabs of 2 x 2 or 3 x 3
 * each overlapping the first (the rubble piles up in the middle), over [Ch2L2Maw.slabSpreadTicks] ticks. Every cell is within [Ch2L2Maw.collapseRadius] of the centre
 * and no column is piled higher than [Ch2L2Maw.maxHeight] (a candidate that would is moved or dropped).
 */
object MawCollapse {

    enum class Kind { BIT, SLAB }

    data class Piece(val delayTicks: Int, val cells: List<Pair<Int, Int>>, val kind: Kind)

    /** The plan's seed: the live fall and a CONTINUE's rubble are the same mound. */
    const val SEED = 61

    /** Blocks between the floor cell and the top cell the pieces start in (the last of air under the ceiling). */
    const val TOP_CELL = 5

    private const val TRIES = 60

    fun plan(maw: Ch2L2Maw, seed: Int): List<Piece> {
        val random = Random(seed)
        val r = maw.collapseRadius
        val height = HashMap<Pair<Int, Int>, Int>()
        fun fits(cells: List<Pair<Int, Int>>) =
            cells.all { (dx, dz) -> dx * dx + dz * dz <= r * r && (height[dx to dz] ?: 0) < maw.maxHeight }
        fun take(cells: List<Pair<Int, Int>>) = cells.forEach { height.merge(it, 1, Int::plus) }

        // The first slab is over the centre itself: it must hit what kneels there.
        val firstCells = square(0, 0, 3)
        take(firstCells)

        val bits = ArrayList<Piece>()
        // The bits fall round the centre, not on the first slab's cells.
        val ring = (-r..r).flatMap { dx -> (-r..r).map { dz -> dx to dz } }
            .filter { (dx, dz) -> dx * dx + dz * dz <= r * r && (Math.abs(dx) > 1 || Math.abs(dz) > 1) }
        val near = ring.shuffled(random).take(maw.bitCount)
        for ((i, cell) in near.withIndex()) {
            val delay = ((i + 1) * (maw.firstSlabDelayTicks - 1).coerceAtLeast(0) / (near.size + 1))
            bits += Piece(delay, listOf(cell), Kind.BIT)
            take(listOf(cell))
        }

        val slabs = ArrayList<Piece>()
        slabs += Piece(maw.firstSlabDelayTicks, firstCells, Kind.SLAB)
        val others = maw.slabCount - 1
        for (i in 1..others.coerceAtLeast(0)) {
            val delay = maw.firstSlabDelayTicks + Math.round(maw.slabSpreadTicks.toDouble() * i / others).toInt()
            var placed: List<Pair<Int, Int>>? = null
            for (attempt in 0 until TRIES) {
                val size = if (random.nextBoolean()) 2 else 3
                val reach = (r - 1).coerceIn(0, 2)
                val cells = square(random.nextInt(-reach, reach + 1), random.nextInt(-reach, reach + 1), size)
                if (fits(cells) && cells.any { it in firstCells }) {
                    placed = cells
                    break
                }
            }
            if (placed != null) {
                take(placed)
                slabs += Piece(delay, placed, Kind.SLAB)
            }
        }
        return bits + slabs
    }

    /** The mound the pieces leave: a column's height is how many pieces cover it, at most [maxHeight]. */
    fun heights(plan: List<Piece>, maxHeight: Int): Map<Pair<Int, Int>, Int> {
        val height = HashMap<Pair<Int, Int>, Int>()
        for (piece in plan) for (cell in piece.cells) height.merge(cell, 1, Int::plus)
        return height.mapValues { it.value.coerceAtMost(maxHeight) }
    }

    /**
     * When each piece of [plan] reaches the stone below it, in ticks from the start of the collapse: its delay plus the fall from the top
     * cell to the column it lands on (the floor, or what earlier pieces piled there), at [startSpeed] blocks a tick to begin with.
     */
    fun landings(plan: List<Piece>, startSpeed: Double): List<Int> {
        val pile = HashMap<Pair<Int, Int>, Int>()
        return plan.map { piece ->
            val base = piece.cells.maxOf { pile[it] ?: 0 }
            for (cell in piece.cells) pile.merge(cell, 1, Int::plus)
            piece.delayTicks + fallTicks((TOP_CELL - base).toDouble(), startSpeed)
        }
    }

    /**
     * Ticks a falling block takes to drop [distance] blocks (vanilla's physics each tick: speed grows by 0.04, it moves, then 2% is lost),
     * thrown downwards at [startSpeed] blocks a tick to begin with.
     */
    fun fallTicks(distance: Double, startSpeed: Double): Int {
        var speed = startSpeed
        var fallen = 0.0
        var ticks = 0
        while (fallen < distance && ticks < MAX_FALL_TICKS) {
            speed += GRAVITY
            fallen += speed
            speed *= DRAG
            ticks++
        }
        return ticks
    }

    /** The cells (absolute, from a [centreX], [centreZ] the plan is relative to) the mound of [plan] fills, `maxHeight` high at most. */
    fun mound(plan: List<Piece>, centreX: Int, centreZ: Int): Set<Pair<Int, Int>> =
        plan.flatMap { it.cells }.mapTo(HashSet()) { (dx, dz) -> (centreX + dx) to (centreZ + dz) }

    private fun square(dx: Int, dz: Int, size: Int): List<Pair<Int, Int>> {
        val from = if (size == 3) -1 else 0
        return (from until from + size).flatMap { x -> (from until from + size).map { z -> (dx + x) to (dz + z) } }
    }

    private const val GRAVITY = 0.04
    private const val DRAG = 0.98
    private const val MAX_FALL_TICKS = 400
}
