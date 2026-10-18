package daio7771.everytunnel.systems

import daio7771.everytunnel.config.FlashlightConfig
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * The light of a switched on flashlight: a cone from the player's eyes in the look
 * direction, plus a faint glow around the player.
 *
 * Pure math on plain Kotlin types. The client turns it into dynamic light through
 * LambDynamicLights (`client.compat`); later systems (e.g. LookDetection) can use
 * [lightAt] on the server to ask whether a position is lit by the flashlight.
 *
 * Light levels follow Minecraft's 0-15 scale:
 * - Along the beam the light dims from [brightness] at the lens to 0 at [range].
 * - Outside the cone it fades by [EDGE_FALLOFF] levels per block, so the edge is soft.
 * - The beam stops [PAST_HIT] blocks past [length], the distance to the first block
 *   it hits, so it lights the wall it points at but not the room behind it.
 * - The glow is a point light of [glow] around the eyes.
 */
data class FlashlightBeam(
    val originX: Double,
    val originY: Double,
    val originZ: Double,
    /** Look direction, normalized (see [create]). */
    val dirX: Double,
    val dirY: Double,
    val dirZ: Double,
    /** Distance from the origin to the first block the beam hits, at most [range]. */
    val length: Double,
    val range: Double,
    /** Half of the cone's width, in radians. */
    val halfAngle: Double,
    val brightness: Double,
    val glow: Double,
) {

    /** Inclusive block box that contains every position [lightAt] can light. */
    data class Bounds(val minX: Int, val minY: Int, val minZ: Int, val maxX: Int, val maxY: Int, val maxZ: Int)

    /**
     * Light level (0-15, fractional) that the flashlight gives the block whose center
     * is at ([x], [y], [z]). [falloff] scales how fast light fades with distance
     * (LambDynamicLights passes its own setting; 1 is normal).
     */
    fun lightAt(x: Double, y: Double, z: Double, falloff: Double = 1.0): Double {
        val vx = x - originX
        val vy = y - originY
        val vz = z - originZ
        val distanceSq = vx * vx + vy * vy + vz * vz
        val glowLight = glow - sqrt(distanceSq) * falloff

        var beamLight = 0.0
        val along = vx * dirX + vy * dirY + vz * dirZ
        if (along > 0.0 && along <= reach) {
            val fromAxis = sqrt(max(0.0, distanceSq - along * along))
            val outside = max(0.0, fromAxis - coneRadiusAt(along))
            val center = brightness * sqrt(max(0.0, 1.0 - along / range))
            beamLight = center - outside * EDGE_FALLOFF * falloff
        }
        return max(glowLight, beamLight).coerceIn(0.0, MAX_LIGHT)
    }

    /** Where the beam stops: just past the block it hits, never past [range]. */
    val reach: Double
        get() = min(length + PAST_HIT, range)

    fun coneRadiusAt(along: Double): Double = max(MIN_RADIUS, along * tan(halfAngle))

    /** Box around the cone and the glow (light outside the cone fades within brightness / [EDGE_FALLOFF] blocks). */
    fun bounds(): Bounds {
        val endX = originX + dirX * reach
        val endY = originY + dirY * reach
        val endZ = originZ + dirZ * reach
        val coneMargin = coneRadiusAt(reach) + brightness / EDGE_FALLOFF
        val glowMargin = max(glow, MIN_RADIUS + brightness / EDGE_FALLOFF)
        return Bounds(
            floor(min(min(originX, endX) - coneMargin, originX - glowMargin)).toInt(),
            floor(min(min(originY, endY) - coneMargin, originY - glowMargin)).toInt(),
            floor(min(min(originZ, endZ) - coneMargin, originZ - glowMargin)).toInt(),
            ceil(max(max(originX, endX) + coneMargin, originX + glowMargin)).toInt(),
            ceil(max(max(originY, endY) + coneMargin, originY + glowMargin)).toInt(),
            ceil(max(max(originZ, endZ) + coneMargin, originZ + glowMargin)).toInt(),
        )
    }

    /**
     * True if the light would look the same as [other]'s, so the lighting does not
     * need to be recomputed (recomputing rebuilds the chunk sections around the beam).
     */
    fun looksLike(other: FlashlightBeam): Boolean {
        val dx = originX - other.originX
        val dy = originY - other.originY
        val dz = originZ - other.originZ
        val cosAngle = dirX * other.dirX + dirY * other.dirY + dirZ * other.dirZ
        return dx * dx + dy * dy + dz * dz < SAME_POSITION * SAME_POSITION &&
            cosAngle > SAME_DIRECTION_COS &&
            abs(length - other.length) < SAME_LENGTH &&
            abs(brightness - other.brightness) < SAME_LIGHT &&
            abs(glow - other.glow) < SAME_LIGHT &&
            range == other.range &&
            halfAngle == other.halfAngle
    }

    companion object {
        const val MAX_LIGHT: Double = 15.0
        /** Light levels lost per block outside the cone. */
        const val EDGE_FALLOFF: Double = 3.0
        /** How far past the hit point the beam still lights (the air in front of the wall it hits). */
        const val PAST_HIT: Double = 1.0
        /** Smallest cone radius, so the beam is not a needle right at the lens. */
        const val MIN_RADIUS: Double = 0.75

        private const val SAME_POSITION = 0.25
        private val SAME_DIRECTION_COS = kotlin.math.cos(Math.toRadians(2.0))
        private const val SAME_LENGTH = 0.5
        private const val SAME_LIGHT = 0.25

        /**
         * A beam from the eye position along the (not necessarily normalized) look
         * vector. [hitDistance] is the distance to the first block in the way, or null
         * if nothing is within range. [dimming] (0-1) scales the light, for flicker.
         */
        fun create(
            eyeX: Double, eyeY: Double, eyeZ: Double,
            lookX: Double, lookY: Double, lookZ: Double,
            hitDistance: Double?,
            config: FlashlightConfig,
            dimming: Double = 1.0,
        ): FlashlightBeam {
            val lookLength = sqrt(lookX * lookX + lookY * lookY + lookZ * lookZ)
            require(lookLength > 0.0) { "look vector must not be zero" }
            val scale = dimming.coerceIn(0.0, 1.0)
            return FlashlightBeam(
                originX = eyeX, originY = eyeY, originZ = eyeZ,
                dirX = lookX / lookLength, dirY = lookY / lookLength, dirZ = lookZ / lookLength,
                length = (hitDistance ?: config.beamRange).coerceIn(0.0, config.beamRange),
                range = config.beamRange,
                halfAngle = Math.toRadians(config.beamWidthDegrees / 2.0),
                brightness = config.beamBrightness * scale,
                glow = config.glowBrightness * scale,
            )
        }
    }
}
