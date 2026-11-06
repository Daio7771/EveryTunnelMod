package daio7771.everytunnel.entities

import java.util.UUID
import kotlin.math.roundToInt

/**
 * One Distortion Cloud's clock: how long each player has been looking its way ([Gaze.inFront], with
 * nothing in between), and how long nobody has.
 *
 * Looking its way for [graceTicks] in a row kills; looking away, even for a tick, starts that over.
 * Once nobody has looked its way for [leaveTicks] in a row, it goes.
 */
class CloudClock(private val graceTicks: Int, private val leaveTicks: Int) {

    /** This tick: the players it kills now, whether it goes, and how near the longest look is to killing (0 to 1). */
    data class Tick(val kill: Set<UUID>, val leave: Boolean, val stare: Double)

    private val looking = HashMap<UUID, Int>()
    private var unseen = 0

    fun tick(lookers: Set<UUID>): Tick {
        looking.keys.retainAll(lookers)
        for (id in lookers) looking[id] = (looking[id] ?: 0) + 1
        unseen = if (lookers.isEmpty()) unseen + 1 else 0
        val kill = looking.filterValues { it >= graceTicks }.keys.toSet()
        val longest = looking.values.maxOrNull() ?: 0
        return Tick(kill, unseen >= leaveTicks, (longest.toDouble() / graceTicks).coerceIn(0.0, 1.0))
    }

    companion object {
        fun of(graceSeconds: Double, leaveSeconds: Double) =
            CloudClock((graceSeconds * 20).roundToInt().coerceAtLeast(1), (leaveSeconds * 20).roundToInt().coerceAtLeast(1))
    }
}

/**
 * One Distortion Junior's clock: staring at its eye ([Gaze.onTarget]) for [stareTicks] in a row ends
 * it; looking away, even for a tick, starts that over.
 */
class JuniorClock(private val stareTicks: Int) {

    /** This tick: whether it is done for, and how near the longest stare is to that (0 to 1). */
    data class Tick(val done: Boolean, val stare: Double)

    private val staring = HashMap<UUID, Int>()

    fun tick(starers: Set<UUID>): Tick {
        staring.keys.retainAll(starers)
        for (id in starers) staring[id] = (staring[id] ?: 0) + 1
        val longest = staring.values.maxOrNull() ?: 0
        return Tick(longest >= stareTicks, (longest.toDouble() / stareTicks).coerceIn(0.0, 1.0))
    }

    companion object {
        fun of(stareSeconds: Double) = JuniorClock((stareSeconds * 20).roundToInt().coerceAtLeast(1))
    }
}

/**
 * When the next Cloud comes to one player: after a random wait between [minTicks] and [maxTicks],
 * counted only while they are in a zone where Clouds come and none is about. Once [due], it stays due
 * until one has [appeared] (a place for it may take a few tries).
 */
class CloudSchedule(private val minTicks: Int, private val maxTicks: Int, private val random: () -> Double) {

    private var waited = 0
    private var wait = next()

    val due: Boolean get() = waited >= wait

    /** [ticks] ticks went by: in a Cloud zone ([inCloudZone]) with no Cloud about ([cloudAbout] false) they count. True if one should come now. */
    fun tick(inCloudZone: Boolean, cloudAbout: Boolean, ticks: Int = 1): Boolean {
        if (inCloudZone && !cloudAbout) waited = minOf(wait, waited + ticks)
        return due && inCloudZone && !cloudAbout
    }

    /** One came (by the schedule or not): the wait starts over. */
    fun appeared() {
        waited = 0
        wait = next()
    }

    private fun next(): Int = minTicks + (random() * (maxTicks - minTicks).coerceAtLeast(0)).roundToInt()

    companion object {
        fun of(minSeconds: Double, maxSeconds: Double, random: () -> Double) =
            CloudSchedule((minSeconds * 20).roundToInt(), (maxOf(minSeconds, maxSeconds) * 20).roundToInt(), random)
    }
}
