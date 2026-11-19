package daio7771.everytunnel.chapter2.core

/**
 * Looking at something without moving for [needed] ticks ([Ch2Achievement.FAMILIAR_FACE]): [tick] is told every tick whether the player
 * looks at it and where they stand; looking away, or moving more than [tolerance] blocks from where the gaze began, starts it again.
 * Plain Kotlin.
 */
class StillGaze(private val needed: Int, private val tolerance: Double = 0.3) {

    private var ticks = 0
    private var x = 0.0
    private var y = 0.0
    private var z = 0.0

    /** True on the tick the gaze has lasted [needed] ticks (once; it starts over after). */
    fun tick(looking: Boolean, px: Double, py: Double, pz: Double): Boolean {
        if (!looking) {
            ticks = 0
            return false
        }
        if (ticks == 0 || kotlin.math.abs(px - x) > tolerance || kotlin.math.abs(py - y) > tolerance || kotlin.math.abs(pz - z) > tolerance) {
            ticks = 0
            x = px
            y = py
            z = pz
        }
        ticks++
        if (ticks < needed) return false
        ticks = 0
        return true
    }

    fun reset() {
        ticks = 0
    }
}
