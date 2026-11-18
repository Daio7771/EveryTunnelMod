package daio7771.everytunnel.chapter2.level2.core

/**
 * The Observant Guard's fight as a clock (Chapter 2 Level 2, phase 5), the state `level2.platform.Ch2L2Guard` plays out in the world.
 * Plain Kotlin; times are server ticks.
 *
 * ```
 * IDLE --begin--> CRACKING (crack) --> INTRO (intro) --> STARING (stare) --> HUNTING <--> STUNNED (stun) ... --4th stun--> DYING (death) --> DEAD
 * ```
 * [finish] is the one end of the fight, from any phase (also the debug `skip`): it is idempotent, true only the first time.
 *
 * A [stun] counts only if the Guard is hunting (not mid-stun, not before it hunts, not dying): "no new stun while one is active". The
 * [Ch2L2Guard.stunsToKill]th one starts its dying instead of a stun. Attacking: [think] says what the Guard does at [now] given how far the
 * player is: a blow starts a [Ch2L2Guard.windupSeconds] windup within the range and lands after it if the player is still in range, never
 * sooner than [Ch2L2Guard.hitIntervalSeconds] after the last landed one; a stun breaks a windup.
 */
class GuardFight(private val config: Ch2L2Guard) {

    enum class Phase { IDLE, CRACKING, INTRO, STARING, HUNTING, STUNNED, DYING, DEAD }

    /** What the Guard does this tick. */
    enum class Act { NONE, MOVE, WINDUP, HIT, MISS }

    var phase = Phase.IDLE
        private set
    var stuns = 0
        private set
    var finished = false
        private set

    private var until = 0L
    private var windupAt: Long? = null
    private var lastHit = Long.MIN_VALUE / 2

    private fun ticks(seconds: Double) = Math.round(seconds * 20.0)

    val stunActive: Boolean get() = phase == Phase.STUNNED
    val alive: Boolean get() = phase != Phase.IDLE && phase != Phase.DYING && phase != Phase.DEAD

    /** The wall starts to crack: false if the fight had begun. */
    fun begin(now: Long): Boolean {
        if (phase != Phase.IDLE || finished) return false
        phase = Phase.CRACKING
        until = now + ticks(config.crackSeconds)
        return true
    }

    /** Time passes to [now]: true on the tick the warning ends and the intro begins (the Guard is put in its nest). */
    fun tick(now: Long): Boolean {
        when (phase) {
            Phase.CRACKING -> if (now >= until) {
                phase = Phase.INTRO
                until = now + ticks(config.introSeconds)
                return true
            }
            Phase.INTRO -> if (now >= until) {
                phase = Phase.STARING
                until = now + ticks(config.stareSeconds)
            }
            Phase.STARING -> if (now >= until) phase = Phase.HUNTING
            Phase.STUNNED -> if (now >= until) phase = Phase.HUNTING
            Phase.DYING -> if (now >= until) phase = Phase.DEAD
            else -> Unit
        }
        return false
    }

    /** Ticks since the intro began (0 when it is not on). */
    fun introElapsed(now: Long): Long = if (phase == Phase.INTRO) (ticks(config.introSeconds) - (until - now)).coerceAtLeast(0L) else 0L

    /** The intro skipped: on to the stare. */
    fun skipIntro(now: Long) {
        if (phase != Phase.INTRO) return
        phase = Phase.STARING
        until = now + ticks(config.stareSeconds)
    }

    /** The Guard has been stunned at [now]: false if it does not count (see the class). */
    fun stun(now: Long): Boolean {
        if (phase != Phase.HUNTING) return false
        stuns++
        windupAt = null
        if (stuns >= config.stunsToKill) {
            phase = Phase.DYING
            until = now + ticks(config.deathSeconds)
        } else {
            phase = Phase.STUNNED
            until = now + ticks(config.stunSeconds)
        }
        return true
    }

    /** The dying is over (and the body gone): the caller runs the finish. */
    val dead: Boolean get() = phase == Phase.DEAD

    /** Ticks left of the build-up before the Guard appears (0 when it is not building up). */
    fun crackRemaining(now: Long): Long = if (phase == Phase.CRACKING) (until - now).coerceAtLeast(0L) else 0L

    /** Ticks left of the stun or the dying, 0 when neither. */
    fun remaining(now: Long): Long = if (phase == Phase.STUNNED || phase == Phase.DYING) (until - now).coerceAtLeast(0L) else 0L

    /** How far through its dying it is, 0 to 1 (0 when it is not dying). */
    fun dyingProgress(now: Long): Double {
        if (phase == Phase.DEAD) return 1.0
        if (phase != Phase.DYING) return 0.0
        val total = ticks(config.deathSeconds).coerceAtLeast(1L)
        return (1.0 - (until - now).toDouble() / total).coerceIn(0.0, 1.0)
    }

    /** What the Guard does at [now], the player [distance] blocks away (null: nobody to chase). */
    fun think(now: Long, distance: Double?): Act {
        if (phase != Phase.HUNTING) return Act.NONE
        val start = windupAt
        if (start != null) {
            if (now < start) return Act.WINDUP
            windupAt = null
            if (distance != null && distance <= config.hitRange && now - lastHit >= ticks(config.hitIntervalSeconds)) {
                lastHit = now
                return Act.HIT
            }
            return Act.MISS
        }
        if (distance == null) return Act.NONE
        if (distance <= config.hitRange && now - lastHit >= ticks(config.hitIntervalSeconds)) {
            windupAt = now + ticks(config.windupSeconds)
            return Act.WINDUP
        }
        return Act.MOVE
    }

    /** The end of the fight, from any phase: true the first time, false every time after. */
    fun finish(): Boolean {
        if (finished) return false
        finished = true
        phase = Phase.DEAD
        windupAt = null
        return true
    }

    /** Back to before it began (a death, CONTINUE, `reset`). */
    fun reset() {
        phase = Phase.IDLE
        stuns = 0
        finished = false
        until = 0L
        windupAt = null
        lastHit = Long.MIN_VALUE / 2
    }
}
