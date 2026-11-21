package daio7771.everytunnel.chapter2.level2.core

import daio7771.everytunnel.common.core.cutscene.Cutscene
import daio7771.everytunnel.common.core.cutscene.Local
import daio7771.everytunnel.common.core.cutscene.Look
import daio7771.everytunnel.common.core.cutscene.Shot
import daio7771.everytunnel.common.core.cutscene.Timeline
import kotlin.math.exp
import kotlin.math.max

/**
 * The Observant Guard's entrance as a clock of ticks since the cinematic began (Chapter 2 Level 2, phase 5): the one list of moments that
 * the server's choreography (`platform.Ch2L2Guard`: when the animations start, when the wall breaks, when the chest is beaten) and the
 * client's camera effects ([shake], [flash]) both read, and that `tools/entity/chapter2/observant_guard.py`'s `punch`, `point` and `chest`
 * animations are written to. It is seen in **first person**, through the player's own eyes, with the bands of a cinematic:
 *
 * ```
 *   0 .. 10   the camera turns to the wall in the dark; the Guard, in its nest behind it, stands still
 *  10 .. 42   it punches the wall (`punch`, 1.6 s): the fist lands at [PUNCH_AT]: the lamps are back, the patch bursts
 *  42 .. 82   it walks out of the hole
 *  82 .. 94   it turns to the player
 *  94 ..138   it points at them (`point`, 2.2 s)
 * 138 ..198   it beats its chest with both fists, [CHEST_BLOWS] times (`chest`, 3.0 s)
 * ```
 * (200 ticks: `guard.intro_seconds`.) It has no voice and there is no subtitle: it speaks with signs. Plain Kotlin, unit tested.
 */
object GuardIntro {

    /** Ticks the camera takes to turn from where the player looks to the wall. */
    const val FLY_IN = 14

    /** When the `punch` animation starts, and when its fist lands (the animation's 1.0 s later). */
    const val PUNCH_START = 10L
    const val PUNCH_AT = PUNCH_START + 20L

    /** The walk out of the hole, the turn to the player, the pointing and the chest beating begin at these ticks. */
    const val WALK_FROM = PUNCH_START + 32L
    const val WALK_TO = 82L
    const val TURN_TO = 94L
    const val POINT_TO = 138L

    /** The chest is beaten [CHEST_BLOWS] times, the first [CHEST_AT] ticks after [POINT_TO] and then every [CHEST_EVERY] (the `chest` animation's 0.4 s and 0.55 s). */
    const val CHEST_AT = 8L
    const val CHEST_EVERY = 11L
    const val CHEST_BLOWS = 5

    /** Ticks of the camera's flight back into the player's eyes at the end; the bands take [Cutscene.BANDS_TICKS] more. */
    const val FLY_OUT = 10

    /** Where the Guard stands outside its hole once it has stepped out, in blocks ahead of its nest's anchor (the wall is 3 ahead of it). */
    const val OUT_FORWARD = 4.4

    /**
     * The entrance in **first person** for an intro of [total] ticks, from a frame at the Guard's nest facing the wall; [extras] are the
     * player's eyes in that frame (forward, right, up). The camera never leaves the player's eyes: it only turns, as their head would. It
     * whips round to the wall (in the dark) where the Guard punches through, follows it out of the hole, and stays on it as it points at the
     * player and beats its chest at them; the flight back into their head (which the server has turned to the Guard) is then no move at all.
     */
    fun timeline(total: Int, extras: DoubleArray): Timeline {
        val shots = (total - FLY_IN - FLY_OUT - Cutscene.BANDS_TICKS).coerceAtLeast(120)
        val eyes = Local(extras.getOrElse(0) { 8.0 }, extras.getOrElse(1) { 3.0 }, extras.getOrElse(2) { 1.62 })
        val hole = Local(3.0, 0.0, 1.7)
        // The shots follow the choreography: the wall (to the blow and the first bricks falling), the Guard stepping out, then on it.
        val a = Math.round(shots * 52 / 156.0).toInt()
        val b = Math.round(shots * 38 / 156.0).toInt()
        val c = Math.round(shots * 36 / 156.0).toInt()
        val d = shots - a - b - c
        return Timeline(
            Cutscene(
                listOf(
                    Shot(a, eyes, eyes, Look.At(hole)),
                    Shot(b, eyes, eyes, Look.At(hole), Look.At(Local(OUT_FORWARD, 0.0, 1.55))),
                    Shot(c, eyes, eyes, Look.At(Local(OUT_FORWARD, 0.0, 1.7)), Look.At(Local(OUT_FORWARD, 0.0, 1.9))),
                    Shot(d, eyes, eyes, Look.At(Local(OUT_FORWARD, 0.0, 1.8))),
                ),
                FLY_IN, FLY_OUT,
            ),
            emptyList(), emptyList(),
        )
    }

    /** The ticks of the chest's blows. */
    fun chestBlows(): List<Long> = List(CHEST_BLOWS) { POINT_TO + CHEST_AT + CHEST_EVERY * it }

    /**
     * How hard the first-person camera shakes at [tick] (1 is the cutscene view's full shake): a tremor growing in the dark before the
     * blow, a hard jolt at the punch that dies away in about half a second, and a smaller one with each blow of the chest.
     */
    fun shake(tick: Double): Double {
        var s = if (tick < PUNCH_AT) 0.25 * (tick / PUNCH_AT) * (tick / PUNCH_AT) else 1.5 * exp(-(tick - PUNCH_AT) / 6.0)
        for (blow in chestBlows()) if (tick >= blow) s = max(s, 0.5 * exp(-(tick - blow) / 3.0))
        return if (s < 0.01) 0.0 else s
    }

    /** A white flash (0 to 1) as the wall bursts and the lamps come back. */
    fun flash(tick: Double): Double {
        if (tick < PUNCH_AT) return 0.0
        val f = 0.5 * exp(-(tick - PUNCH_AT) / 3.0)
        return if (f < 0.01) 0.0 else f
    }
}
