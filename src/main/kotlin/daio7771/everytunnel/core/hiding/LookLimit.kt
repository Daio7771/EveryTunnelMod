package daio7771.everytunnel.core.hiding

/**
 * How far a player hiding in a locker may turn their head (plain Kotlin, unit tested): at most
 * [MAX_YAW] degrees either side of the way the locker faces, and [MAX_PITCH] up or down. The
 * locker's seat (`platform.LockerSeatEntity`) applies it every time the mouse turns the head,
 * as a boat limits its passengers.
 *
 * Minecraft's yaw is in degrees and not wrapped (a player who has turned round twice is at
 * 720), so the limit works on the difference to the locker's facing, wrapped to -180..180.
 */
object LookLimit {

    const val MAX_YAW = 30f
    const val MAX_PITCH = 15f

    /** The difference [yaw] - [centre], wrapped to -180 (exclusive) .. 180. */
    fun wrappedDelta(yaw: Float, centre: Float): Float {
        var delta = (yaw - centre) % 360f
        if (delta >= 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta
    }

    /**
     * How much to add to [yaw] to bring it within [MAX_YAW] of [centre] (0 if it already is).
     * Adding keeps the player's own unwrapped yaw, so the turn does not jump by 360.
     */
    fun yawCorrection(yaw: Float, centre: Float): Float {
        val delta = wrappedDelta(yaw, centre)
        return delta.coerceIn(-MAX_YAW, MAX_YAW) - delta
    }

    fun pitch(pitch: Float): Float = pitch.coerceIn(-MAX_PITCH, MAX_PITCH)
}
