package daio7771.everytunnel.core.camera

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * Where a security camera's head points (Level 5's anteroom), in degrees: [yaw] round the vertical (0 = north, the
 * way Minecraft's rotations about +Y turn a north-facing model: towards -x as it grows), [pitch] up (negative:
 * looking down). Each frame it turns towards a target ([turnTowards]) no faster than a servo can, the shorter way
 * round. Plain Kotlin, unit tested.
 */
class CameraAim(yaw: Double, pitch: Double) {

    var yaw = yaw
        private set
    var pitch = pitch
        private set

    /**
     * Turns towards ([targetYaw], [targetPitch]) for [seconds] at no more than [degreesPerSecond] (yaw and pitch
     * together, along the straight way between); returns how many degrees it turned.
     */
    fun turnTowards(targetYaw: Double, targetPitch: Double, seconds: Double, degreesPerSecond: Double): Double {
        val dYaw = wrap(targetYaw - yaw)
        val dPitch = targetPitch - pitch
        val distance = hypot(dYaw, dPitch)
        if (distance < 1e-6) return 0.0
        val step = min(distance, degreesPerSecond * seconds)
        yaw = wrap(yaw + dYaw * step / distance)
        pitch += dPitch * step / distance
        return step
    }

    companion object {
        /** The yaw that points a north-facing head along ([dx], [dz]). */
        fun yawTo(dx: Double, dz: Double): Double = Math.toDegrees(atan2(-dx, -dz))

        /** The pitch that points it at something [dy] higher, [horizontal] away. */
        fun pitchTo(dy: Double, horizontal: Double): Double = Math.toDegrees(atan2(dy, horizontal))

        /** [degrees] into -180..180. */
        fun wrap(degrees: Double): Double {
            var d = degrees % 360.0
            if (d > 180.0) d -= 360.0
            if (d < -180.0) d += 360.0
            return d
        }

        /** True if [a] and [b] (degrees) are within [tolerance] of each other, round the circle. */
        fun near(a: Double, b: Double, tolerance: Double): Boolean = abs(wrap(a - b)) <= tolerance
    }
}
