package daio7771.everytunnel.chapter2.level2.core

/**
 * The Guard's four traps (Chapter 2 Level 2, phase 5), reusable forever. Each is a plate and a breaker: a [Phase.SPENT] trap is pulled
 * ([pull]) and charges for [rearmTicks] ([Phase.CHARGING]), then stands [Phase.ARMED]; the Guard stepping on an armed plate is one
 * **activation**, rolled once ([step]) and kept: [Outcome.STUN] or, [failChance] of the time, [Outcome.FAIL]. Either way the trap is spent
 * again, to be pulled again. Plain Kotlin; the clock is the caller's (ticks).
 *
 * What counts as stepping on is the **edge**, not the level: the roll is made on entering the plate, so standing on it never rolls again,
 * and to fire again the Guard must step off, the trap be rearmed and the Guard step on again. A plate entered while its trap is not armed
 * does nothing and is not remembered (stepping off and on again after arming fires). A plate entered while a stun is still active
 * ([step]'s `stunActive`) does nothing either, and the trap is **not** consumed: it stays armed for the next entry.
 */
class TrapBoard(val ids: List<String>, private val rearmTicks: Long, private val failChance: Double) {

    enum class Phase { SPENT, CHARGING, ARMED }

    enum class Outcome { NONE, STUN, FAIL }

    private val phases = ids.associateWith { Phase.SPENT }.toMutableMap()
    private val readyAt = HashMap<String, Long>()
    private val inside = HashMap<String, Boolean>()

    fun phase(id: String): Phase = phases[id] ?: Phase.SPENT

    /** The breaker of [id] is pulled at [now]: a spent trap starts charging. False if it was not spent (nothing happens). */
    fun pull(id: String, now: Long): Boolean {
        if (phases[id] != Phase.SPENT) return false
        phases[id] = Phase.CHARGING
        readyAt[id] = now + rearmTicks
        return true
    }

    /** The traps whose charge ends at [now]: they are armed now. */
    fun tick(now: Long): List<String> {
        val armed = ArrayList<String>()
        for (id in ids) {
            if (phases[id] == Phase.CHARGING && now >= (readyAt[id] ?: 0L)) {
                phases[id] = Phase.ARMED
                armed += id
            }
        }
        return armed
    }

    /**
     * The Guard is [inside] the plate of [id] this tick ([roll]: a number from 0 up to 1, drawn by the caller, used only on an entry that
     * fires). See the class for what fires.
     */
    fun step(id: String, inside: Boolean, roll: Double, stunActive: Boolean): Outcome {
        val was = this.inside[id] == true
        this.inside[id] = inside
        if (!inside || was) return Outcome.NONE
        if (phases[id] != Phase.ARMED || stunActive) return Outcome.NONE
        phases[id] = Phase.SPENT
        return if (roll < failChance) Outcome.FAIL else Outcome.STUN
    }

    /** Every trap spent and nobody on any plate. */
    fun reset() {
        for (id in ids) phases[id] = Phase.SPENT
        readyAt.clear()
        inside.clear()
    }
}
