package daio7771.everytunnel.entities

import daio7771.everytunnel.core.level.Position
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Where a player looks, for the Distortions (Minecraft's angles: yaw 0 faces south (+z), 90 west;
 * pitch 90 looks straight down). Walls are the caller's business.
 *
 * - [inFront]: the Cloud kills whoever looks its way: it is anywhere in front of them, within 90
 *   degrees either side of where they face, however far up or down they look.
 * - [onTarget]: the Junior's eye is under the crosshair: within [aimDegrees][onTarget] of where they
 *   look, or more when so close that the eye fills more than that.
 */
object Gaze {

    /** Degrees between where a viewer at [from] faces ([yaw]) and [target], on the ground: 0 to 180. */
    fun turnTo(from: Position, yaw: Double, target: Position): Double {
        val dx = target.x - from.x
        val dz = target.z - from.z
        if (dx * dx + dz * dz < 1e-9) return 0.0
        val toTarget = Math.toDegrees(atan2(-dx, dz))
        return abs(wrap(toTarget - yaw))
    }

    /** [target] is in front of a viewer at [from] facing [yaw] (looking up or down does not matter). */
    fun inFront(from: Position, yaw: Double, target: Position): Boolean = turnTo(from, yaw, target) < 90.0

    /** Degrees between where an [eye] looks ([yaw], [pitch]) and [target]: 0 to 180. */
    fun angleTo(eye: Position, yaw: Double, pitch: Double, target: Position): Double {
        val y = Math.toRadians(yaw)
        val p = Math.toRadians(pitch)
        val lx = -sin(y) * cos(p)
        val ly = -sin(p)
        val lz = cos(y) * cos(p)
        val dx = target.x - eye.x
        val dy = target.y - eye.y
        val dz = target.z - eye.z
        val length = sqrt(dx * dx + dy * dy + dz * dz)
        if (length < 1e-9) return 0.0
        return Math.toDegrees(acos(((lx * dx + ly * dy + lz * dz) / length).coerceIn(-1.0, 1.0)))
    }

    /**
     * The crosshair is on [target], something [radius] blocks across its middle: within [aimDegrees]
     * of it, or of its edge when that is wider (close up).
     */
    fun onTarget(eye: Position, yaw: Double, pitch: Double, target: Position, radius: Double, aimDegrees: Double): Boolean {
        val distance = sqrt((target.x - eye.x).let { it * it } + (target.y - eye.y).let { it * it } + (target.z - eye.z).let { it * it })
        val edge = if (distance < 1e-9) 90.0 else Math.toDegrees(atan(radius / distance))
        return angleTo(eye, yaw, pitch, target) <= max(aimDegrees, edge)
    }

    /** Minecraft's yaw (degrees) of the way from [from] to [to]. */
    fun yawTowards(from: Position, to: Position): Double = Math.toDegrees(atan2(-(to.x - from.x), to.z - from.z))

    /** An angle in degrees, brought into -180..180. */
    fun wrap(degrees: Double): Double {
        var d = degrees % 360.0
        if (d >= 180.0) d -= 360.0
        if (d < -180.0) d += 360.0
        return d
    }
}
