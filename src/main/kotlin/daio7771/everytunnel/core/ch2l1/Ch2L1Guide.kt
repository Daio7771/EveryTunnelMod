package daio7771.everytunnel.core.ch2l1

import daio7771.everytunnel.core.level.Position

/**
 * Where the guide arrow of Chapter 2's Level 1 floats ([Ch2L1GuideSpec]; `client.render.TaskArrow` draws it): over the place of the
 * player's current step, so that the way is clear when the LAB is full of benches and machines. Plain Kotlin, unit tested.
 *
 * [targets] are the places the step can be done at, each lifted by the spec's height over its anchor: the time clock before the player
 * has clocked in; in the first task the sample tubes not taken yet and, once all are, the terminal; in the second the analyzer; in the third
 * the broom until it is taken and then the patches of dirt not swept yet. [nearest] is the one the arrow shows: the one closest to
 * the player. Later phases add the steps of Acts 2 and 3 here.
 */
object Ch2L1Guide {

    /** The places [state] points at, given the player's [counter]s (`Ch2L1Tasks.TUBES` and the rest), already lifted. */
    fun targets(state: Ch2L1State, tasks: Ch2L1Tasks, anchors: Ch2L1Anchors, counter: (String) -> Int): List<Position> {
        fun at(id: String): List<Position> = listOfNotNull(anchors.point(id)?.position)
        val places: List<Position> = when (state) {
            Ch2L1State.ACT1_CLOCKIN -> at("time_clock")
            Ch2L1State.ACT1_TASK1 -> {
                val taken = counter(Ch2L1Tasks.TUBES)
                val left = tasks.tubes.indices.filter { !Ch2L1Bits.has(taken, it) }
                if (left.isEmpty()) at(tasks.terminalAnchor) else left.flatMap { at(tasks.tubes[it].anchor) }
            }
            Ch2L1State.ACT1_TASK2 -> at(tasks.analyzerAnchor)
            Ch2L1State.ACT1_TASK3 ->
                if (counter(Ch2L1Tasks.BROOM) == 0) {
                    at(tasks.broomAnchor)
                } else {
                    val swept = counter(Ch2L1Tasks.SWEPT)
                    anchors.path(tasks.dirtPath)?.points?.filterIndexed { i, _ -> !Ch2L1Bits.has(swept, i) } ?: emptyList()
                }
            else -> emptyList()
        }
        return places.map { Position(it.x, it.y + tasks.guide.height, it.z) }
    }

    /** The place in [targets] nearest to [from], or null if there is none. */
    fun nearest(targets: List<Position>, from: Position): Position? =
        targets.minByOrNull { (it.x - from.x) * (it.x - from.x) + (it.y - from.y) * (it.y - from.y) + (it.z - from.z) * (it.z - from.z) }

    /** True if [from] is within [spec]'s hiding distance of [target], along the floor. */
    fun hidden(target: Position, from: Position, spec: Ch2L1GuideSpec): Boolean {
        val dx = target.x - from.x
        val dz = target.z - from.z
        return dx * dx + dz * dz < spec.hideWithin * spec.hideWithin
    }
}
