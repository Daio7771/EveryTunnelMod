package daio7771.everytunnel.chapter2.level2.core

import daio7771.everytunnel.common.core.level.Position
import java.util.UUID
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * The rules of Stone Maw's last stand (phase 6.1, remade in phase 9), around phase 4's [VibrationCreatureBrain]: the brain is not rewritten,
 * it decides everything in [Mode.BRAIN] (rest, stalk, orient, charge, blow, search, return home, fed by the vibrations of the player's steps
 * and of pipes landing or falling), and the fight takes over from it on a hit:
 *
 * - **A hit** ([hit]; any impact of a thrown piece on the creature, unless it is dead) never kills it: it makes it [Mode.RAM_ORIENT] for
 *   the stage's `orient_seconds` (it faces the player, the click of its teeth is the tell), then [Mode.RAM]: a charge in a **straight
 *   line** at where the player was, turning at most the stage's `turn_degrees_per_second` towards them, **until it runs into something
 *   solid** (the caller says so with [Senses.wall], and with [Senses.pillar] if it was a pillar) or `ram.max_seconds` pass. A player within
 *   the creature's `contact_range` on the way is struck by phase 4's blow, for its `damage_fraction`, at most once every
 *   `hit_interval_seconds`; the ram goes on. Whatever it runs into stuns it ([Mode.STUNNED], still, for the stage's `stun_seconds`); a hit
 *   during the stun counts (and ends it: it comes for the player again). Afterwards it is handed back to the brain, searching round where it
 *   stands.
 * - **On its own**: in [Mode.BRAIN], when it turns to a noise ([VibrationCreatureBrain.Decision.oriented]) with a player in the arena and its
 *   cooldown over (`ram.auto_cooldown_seconds` after the last stun, and at the start), it goes into the same orient-and-ram as after a hit
 *   (no hit counted): the pipes only annoy it, a player who makes noise is charged at.
 * - **The pillars**: a charge that ends against one that still stands damages it ([Decision.pillarHit]); `pillars.hits` of them break it
 *   ([Decision.pillarBroken]); a wall, or the stump of a broken pillar, damages nothing. The **stage** is the number of pillars broken: each
 *   makes the ram faster and sharper and the stun shorter ([Ch2L2Maw.stage]). When the last one breaks, [Decision.collapse] is true once and the
 *   creature is [Mode.DEAD] (stunned against the pillar, under the ceiling the platform brings down): it decides nothing after.
 *
 * A miss is not this class's business: the platform feeds the clang to the brain as a loud pipe vibration. Plain Kotlin, unit tested;
 * `platform.Ch2L2Maw` runs it (with `VibrationCreatureEntity` as its body).
 */
class StoneMawFight(private val random: Random = Random.Default) {

    enum class Mode { BRAIN, RAM_ORIENT, RAM, STUNNED, DEAD }

    /** The player the creature has in mind: the nearest one. */
    data class Target(val id: UUID, val position: Position, val distance: Double)

    /**
     * What it senses this tick. [wall]: its last move ran into a block; [pillar]: the index of the pillar (in [Ch2L2Maw.pillars]) that block
     * is, if it is one (the platform decides it, by where the creature stands against it). [navigation]: about the brain's last target (as the
     * brain's own senses).
     */
    data class Senses(
        val now: Long,
        val position: Position,
        val home: Position,
        val centre: Position,
        val vibrations: List<VibrationCreatureBrain.Vibration> = emptyList(),
        val player: Target? = null,
        val navigation: VibrationCreatureBrain.Nav = VibrationCreatureBrain.Nav.MOVING,
        val wall: Boolean = false,
        val pillar: Int? = null,
    )

    /**
     * What to do this tick. [target]: where to go at [speed] blocks a second ([straight]: a charge in a line, no pathfinding, towards [heading],
     * a unit vector), or what to face when [speed] is 0. [state] is the brain state whose animation fits (the entity's pose).
     * [oriented]/[windup]/[struck]/[hit]: as the brain's (the click, the fists rising, the blow, who it hurts); [rammed]: it hit something
     * solid (the thud, the stun begins); [pillarHit]/[pillarBroken]: the pillar it damaged, and whether that broke it; [collapse]: the last one
     * broke, the ceiling comes down now.
     */
    data class Decision(
        val mode: Mode,
        val state: VibrationCreatureBrain.State,
        val target: Position?,
        val speed: Double,
        val straight: Boolean = false,
        val heading: Pair<Double, Double>? = null,
        val oriented: Boolean = false,
        val windup: Boolean = false,
        val struck: Boolean = false,
        val hit: UUID? = null,
        val rammed: Boolean = false,
        val pillarHit: Int? = null,
        val pillarBroken: Boolean = false,
        val collapse: Boolean = false,
    )

    /** What a blow to a pillar did: [index] damaged, [broken] if that was its last point, [collapse] if it was the last pillar standing. */
    data class PillarOutcome(val index: Int, val damage: Int, val broken: Boolean, val collapse: Boolean)

    private val brain = VibrationCreatureBrain(random)

    var mode: Mode = Mode.BRAIN
        private set

    /** Hits of a thrown piece taken so far (for the readout; they no longer decide anything). */
    var hits: Int = 0
        private set

    private var damage = IntArray(Ch2L2Maw.DEFAULT.pillars.size)
    private var needed = Ch2L2Maw.DEFAULT.pillarHits
    private var modeTicks = 0
    private var hx = 0.0
    private var hz = 1.0
    private var hitCooldown = 0
    private var pendingHit = false
    private var autoCooldown = 0

    /** The brain's own state (for tests and the debug readout). */
    val brainState: VibrationCreatureBrain.State get() = brain.state

    /** How many pillars stand no more: the fight's stage. */
    val broken: Int get() = damage.count { it >= needed }

    /** Damage points pillar [index] has taken (up to `pillars.hits`; the pillar is broken at the top). */
    fun pillarDamage(index: Int): Int = damage.getOrElse(index) { 0 }

    fun isBroken(index: Int): Boolean = pillarDamage(index) >= needed

    /** The fight begins again: every pillar whole, at rest, as if just out of the pipe. */
    fun reset(maw: Ch2L2Maw) {
        brain.reset()
        mode = Mode.BRAIN
        hits = 0
        damage = IntArray(maw.pillars.size)
        needed = maw.pillarHits
        modeTicks = 0
        hitCooldown = 0
        pendingHit = false
        autoCooldown = ticks(maw.autoRamCooldownSeconds)
    }

    /** Damages pillar [index] once, as a charge into it would (the debug command, and what a ram does). Null if it cannot be damaged (broken, dead, no such pillar). */
    fun damagePillar(index: Int): PillarOutcome? {
        if (mode == Mode.DEAD || index !in damage.indices || damage[index] >= needed) return null
        damage[index]++
        val broken = damage[index] >= needed
        val collapse = broken && damage.all { it >= needed }
        return PillarOutcome(index, damage[index], broken, collapse)
    }

    /**
     * The debug command's "kill": breaks every pillar still standing at once; the outcome of the last one has [PillarOutcome.collapse]. Null if
     * it is dead already or there are no pillars.
     */
    fun breakAll(): PillarOutcome? {
        var last: PillarOutcome? = null
        for (i in damage.indices) {
            while (damage[i] < needed) last = damagePillar(i) ?: break
        }
        if (last != null && last.collapse) mode = Mode.DEAD
        return last
    }

    /** A thrown piece struck it. False, and nothing counts, once it is dead. The next tick decides what it does with the player (orient and ram). */
    fun hit(): Boolean {
        if (mode == Mode.DEAD) return false
        hits++
        pendingHit = true
        return true
    }

    fun tick(senses: Senses, maw: Ch2L2Maw, creature: Ch2L2Creature): Decision {
        if (hitCooldown > 0) hitCooldown--
        if (autoCooldown > 0) autoCooldown--
        if (pendingHit) {
            pendingHit = false
            if (mode != Mode.DEAD) enter(Mode.RAM_ORIENT)
        }
        modeTicks++
        return when (mode) {
            Mode.BRAIN -> brainTick(senses, creature, maw)
            Mode.RAM_ORIENT -> ramOrient(senses, maw)
            Mode.RAM -> ram(senses, maw, creature)
            Mode.STUNNED -> stunned(senses, maw, creature)
            Mode.DEAD -> Decision(Mode.DEAD, VibrationCreatureBrain.State.RECOVER, null, 0.0)
        }
    }

    private fun enter(next: Mode) {
        mode = next
        modeTicks = 0
    }

    private fun brainTick(s: Senses, creature: Ch2L2Creature, maw: Ch2L2Maw): Decision {
        val d = brain.tick(
            VibrationCreatureBrain.Senses(
                s.now, s.position, s.home, s.vibrations, s.player?.let { VibrationCreatureBrain.NearbyPlayer(it.id, it.distance) }, s.navigation,
            ),
            creature,
        )
        if (d.oriented && s.player != null && autoCooldown == 0) {
            enter(Mode.RAM_ORIENT)
            return Decision(Mode.RAM_ORIENT, VibrationCreatureBrain.State.ORIENT, s.player.position, 0.0, oriented = true)
        }
        return Decision(Mode.BRAIN, d.state, d.target, d.speed, oriented = d.oriented, windup = d.windup, struck = d.struck, hit = d.hit)
    }

    private fun ramOrient(s: Senses, maw: Ch2L2Maw): Decision {
        val toward = s.player?.position ?: s.centre
        val stage = maw.stage(broken)
        if (modeTicks >= ticks(stage.orientSeconds)) {
            val dx = toward.x - s.position.x
            val dz = toward.z - s.position.z
            val length = hypot(dx, dz).coerceAtLeast(1e-6)
            hx = dx / length
            hz = dz / length
            enter(Mode.RAM)
            return Decision(Mode.RAM, VibrationCreatureBrain.State.CHARGE, ahead(s.position), stage.ramSpeed, straight = true, heading = hx to hz, oriented = true)
        }
        return Decision(Mode.RAM_ORIENT, VibrationCreatureBrain.State.ORIENT, toward, 0.0)
    }

    private fun ram(s: Senses, maw: Ch2L2Maw, creature: Ch2L2Creature): Decision {
        val stage = maw.stage(broken)
        s.player?.let { p ->
            // A small turn towards where they are now, never more than the limit.
            val want = atan2(p.position.z - s.position.z, p.position.x - s.position.x)
            val have = atan2(hz, hx)
            var diff = want - have
            while (diff > PI) diff -= 2 * PI
            while (diff < -PI) diff += 2 * PI
            val limit = Math.toRadians(stage.turnDegreesPerSecond) / 20.0
            val turn = diff.coerceIn(-limit, limit)
            hx = cos(have + turn)
            hz = sin(have + turn)
        }
        var struck = false
        var victim: UUID? = null
        val near = s.player
        if (near != null && near.distance <= creature.contactRange && hitCooldown == 0) {
            struck = true
            victim = near.id
            hitCooldown = VibrationCreatureBrain.ticks(creature.hitIntervalSeconds)
        }
        // The wall only counts once it has moved a little (a hit near a wall does not stun it at once).
        val walled = s.wall && modeTicks > MIN_RAM_TICKS
        if (walled || modeTicks >= ticks(maw.ramMaxSeconds)) {
            val outcome = if (walled) s.pillar?.let { damagePillar(it) } else null
            if (outcome != null && outcome.collapse) {
                enter(Mode.DEAD)
                return Decision(
                    Mode.DEAD, VibrationCreatureBrain.State.RECOVER, null, 0.0, struck = struck, hit = victim, rammed = true,
                    pillarHit = outcome.index, pillarBroken = true, collapse = true,
                )
            }
            enter(Mode.STUNNED)
            return Decision(
                Mode.STUNNED, VibrationCreatureBrain.State.RECOVER, null, 0.0, struck = struck, hit = victim, rammed = walled,
                pillarHit = outcome?.index, pillarBroken = outcome?.broken == true,
            )
        }
        return Decision(Mode.RAM, VibrationCreatureBrain.State.CHARGE, ahead(s.position), stage.ramSpeed, straight = true, heading = hx to hz, struck = struck, hit = victim)
    }

    private fun stunned(s: Senses, maw: Ch2L2Maw, creature: Ch2L2Creature): Decision {
        if (modeTicks >= ticks(maw.stage(broken).stunSeconds)) {
            brain.reset()
            brain.searchAround(s.position)
            enter(Mode.BRAIN)
            autoCooldown = ticks(maw.autoRamCooldownSeconds)
            return brainTick(s, creature, maw)
        }
        return Decision(Mode.STUNNED, VibrationCreatureBrain.State.RECOVER, null, 0.0)
    }

    /** A point far ahead on the heading (the entity charges at it without finding a path). */
    private fun ahead(from: Position) = Position(from.x + hx * AHEAD, from.y, from.z + hz * AHEAD)

    companion object {
        const val MIN_RAM_TICKS = 6
        const val AHEAD = 30.0

        private fun ticks(seconds: Double) = VibrationCreatureBrain.ticks(seconds)
    }
}
