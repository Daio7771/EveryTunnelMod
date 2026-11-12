package daio7771.everytunnel.core.cutscene

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A camera's place and aim, Minecraft's way: [yaw] in degrees, 0 looking south (+z), growing towards west;
 * [pitch] in degrees, positive looking down.
 */
data class CameraPose(val x: Double, val y: Double, val z: Double, val yaw: Double, val pitch: Double) {

    /** This pose's yaw moved by whole turns to lie within half a turn of [other]'s, so a blend between them goes the short way. */
    fun yawNear(other: Double): CameraPose {
        var y = yaw
        while (y - other > 180.0) y -= 360.0
        while (y - other < -180.0) y += 360.0
        return copy(yaw = y)
    }

    companion object {
        /** The pose at ([x], [y], [z]) looking at ([tx], [ty], [tz]). */
        fun looking(x: Double, y: Double, z: Double, tx: Double, ty: Double, tz: Double): CameraPose {
            val dx = tx - x
            val dy = ty - y
            val dz = tz - z
            return CameraPose(x, y, z, Math.toDegrees(atan2(-dx, dz)), -Math.toDegrees(atan2(dy, hypot(dx, dz))))
        }

        /** [a] towards [b] by [t] (0..1), the yaw the short way round. */
        fun blend(a: CameraPose, b: CameraPose, t: Double): CameraPose {
            val c = b.yawNear(a.yaw)
            return CameraPose(lerp(a.x, c.x, t), lerp(a.y, c.y, t), lerp(a.z, c.z, t), lerp(a.yaw, c.yaw, t), lerp(a.pitch, c.pitch, t))
        }
    }
}

/**
 * Where a scene happens: a point on the floor ([x], [y], [z]) and the way someone standing there faces
 * ([yaw], Minecraft's). [at] turns a place given from him (so far [forward], so far to his [right], so far
 * [up]) into the world's.
 */
data class SceneFrame(val x: Double, val y: Double, val z: Double, val yaw: Double) {

    fun at(forward: Double, right: Double, up: Double): Triple<Double, Double, Double> {
        val r = Math.toRadians(yaw)
        val fx = -sin(r)
        val fz = cos(r)
        // His right hand, facing (fx, fz): facing south (+z), it is west (-x).
        return Triple(x + fx * forward - fz * right, y + up, z + fz * forward + fx * right)
    }
}

/** A place given from a [SceneFrame]'s someone: so far ahead of him, to his right, up from his floor. */
data class Local(val forward: Double, val right: Double, val up: Double)

/** What a shot's camera looks at, all through it. */
sealed interface Look {
    /** A place given from the scene's someone. */
    data class At(val place: Local) : Look

    /** The player's eyes, wherever they stand. */
    data object Player : Look
}

/** One shot: the camera glides from [from] to [to] (eased) over [ticks], looking at [look]. Shots meet in a cut. */
data class Shot(val ticks: Int, val from: Local, val to: Local, val look: Look)

/**
 * A cinematic laid out in ticks: black bands slide in ([BANDS_TICKS]) while the camera flies out of the
 * player's eyes ([flyInTicks]) to the first shot, the [shots] follow one another in cuts, the camera flies
 * back into the player's eyes ([flyOutTicks]), and the bands slide out. Plain Kotlin: the server counts
 * [totalTicks] to give the player back; the client asks [pose] and [bands] every frame. Unit tested.
 */
class Cutscene(val shots: List<Shot>, val flyInTicks: Int, val flyOutTicks: Int) {

    private val shotsTicks = shots.sumOf { it.ticks }

    /** From the start to the end of the last shot. */
    val shotsEnd: Int = flyInTicks + shotsTicks

    /** From the start to the bands gone: the whole scene. */
    val totalTicks: Int = shotsEnd + flyOutTicks + BANDS_TICKS

    /** The shot at [tick] (fractional), or -1 while flying in, [shots]' size while flying back and after. */
    fun shotAt(tick: Double): Int {
        if (tick < flyInTicks) return -1
        var t = tick - flyInTicks
        for ((i, shot) in shots.withIndex()) {
            if (t < shot.ticks) return i
            t -= shot.ticks
        }
        return shots.size
    }

    /** The camera at [tick] (fractional) in [frame], the player's [eyes] where they stood when it began, [playerEyes] now. */
    fun pose(tick: Double, frame: SceneFrame, eyes: CameraPose, playerEyes: Triple<Double, Double, Double>): CameraPose {
        if (tick < flyInTicks) return CameraPose.blend(eyes, shotPose(0, 0.0, frame, playerEyes), ease(tick / flyInTicks))
        var t = tick - flyInTicks
        for ((i, shot) in shots.withIndex()) {
            if (t < shot.ticks) return shotPose(i, t / shot.ticks, frame, playerEyes)
            t -= shot.ticks
        }
        if (t < flyOutTicks) return CameraPose.blend(shotPose(shots.size - 1, 1.0, frame, playerEyes), eyes, ease(t / flyOutTicks))
        return eyes
    }

    /** How far the bands are in at [tick]: 0 out, 1 all the way in. */
    fun bands(tick: Double): Double = when {
        tick < BANDS_TICKS -> ease(tick / BANDS_TICKS)
        tick < shotsEnd + flyOutTicks -> 1.0
        else -> 1.0 - ease(((tick - shotsEnd - flyOutTicks) / BANDS_TICKS).coerceAtMost(1.0))
    }

    private fun shotPose(i: Int, t: Double, frame: SceneFrame, playerEyes: Triple<Double, Double, Double>): CameraPose {
        val shot = shots[i]
        val e = ease(t)
        val (x, y, z) = frame.at(lerp(shot.from.forward, shot.to.forward, e), lerp(shot.from.right, shot.to.right, e), lerp(shot.from.up, shot.to.up, e))
        val (tx, ty, tz) = when (val look = shot.look) {
            is Look.At -> frame.at(look.place.forward, look.place.right, look.place.up)
            Look.Player -> playerEyes
        }
        return CameraPose.looking(x, y, z, tx, ty, tz)
    }

    companion object {
        /** The bands slide in and out in a second. */
        const val BANDS_TICKS = 20

        /** Slow at both ends (smoothstep). */
        fun ease(t: Double): Double {
            val c = t.coerceIn(0.0, 1.0)
            return c * c * (3.0 - 2.0 * c)
        }
    }
}

internal fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t
