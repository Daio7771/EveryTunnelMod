package daio7771.everytunnel.chapter2.level2.core

import daio7771.everytunnel.core.level.Position
import java.util.UUID
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * The rules of Stone Maw's last stand (phase 6.1), around phase 4's [VibrationCreatureBrain]: the brain is not rewritten, it decides
 * everything in [Mode.BRAIN] (rest, stalk, orient, charge, blow, search, return home, fed by the vibrations of the player's steps and of
 * pipes landing or falling), and the fight takes over from it on a hit:
 *
 * - **A hit** ([hit]; any impact of a thrown piece on the creature, in any mode but the dying ones) takes one of [Ch2L2Maw.hits] away.
 *   With some left: [Mode.RAM_ORIENT] for `ram.orient_seconds` (it faces the player, the click of its teeth is the tell), then
 *   [Mode.RAM]: a charge in a **straight line** at where the player was, turning at most `ram.turn_degrees_per_second` towards them,
 *   **until it hits a wall** (the caller says so with [Senses.wall]) or `ram.max_seconds` pass. A player within the creature's
 *   `contact_range` on the way is struck by phase 4's blow, for its `damage_fraction`, at most once every `hit_interval_seconds`; the
 *   ram goes on. The wall (or the time) stuns it: [Mode.STUNNED] for `stun_seconds`, still; a hit during the stun counts (and ends it:
 *   it comes for the player again). Afterwards it is handed back to the brain, searching round where it stands.
 * - **The last hit** ([Mode.WALK_TO_CENTRE]): it limps to the centre of the arena (at `death.walk_speed`, at most `death.walk_seconds`),
 *   [Mode.CROUCH]es (kneels) for `death.crouch_seconds`, and then [Decision.collapse] is true once (the platform's death scene, on the clock of
 *   `MawDeathScene`, brings the ceiling down on it).
 *   [Mode.DEAD] after: it decides nothing.
 *
 * A miss is not this class's business: the platform feeds the clang to the brain as a loud pipe vibration. Plain Kotlin, unit tested;
 * `platform.Ch2L2Maw` runs it (with `VibrationCreatureEntity` as its body).
 */
class StoneMawFight(private val random: Random = Random.Default) {

    enum class Mode { BRAIN, RAM_ORIENT, RAM, STUNNED, WALK_TO_CENTRE, CROUCH, DEAD }

    /** The player the creature has in mind: the nearest one. */
    data class Target(val id: UUID, val position: Position, val distance: Double)

    /**
     * What it senses this tick. [wall]: its last move ran into a block. [navigation]: about the brain's last target (as the brain's own
     * senses).
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
    )

    /**
     * What to do this tick. [target]: where to go at [speed] blocks a second ([straight]: a charge in a line, no pathfinding, towards [heading],
     * a unit vector), or what to face when [speed] is 0. [state] is the brain state whose animation fits (the entity's pose).
     * [oriented]/[windup]/[struck]/[hit]: as the brain's (the click, the fists rising, the blow, who it hurts); [rammed]: it hit a wall
     * (the huge thud, the stun begins); [collapse]: the ceiling comes down now.
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
        val collapse: Boolean = false,
    )

    private val brain = VibrationCreatureBrain(random)

    var mode: Mode = Mode.BRAIN
        private set
    var hitsLeft: Int = Ch2L2Maw.DEFAULT.hits
        private set

    private var modeTicks = 0
    private var hx = 0.0
    private var hz = 1.0
    private var hitCooldown = 0
    private var pendingHit = false

    /** The brain's own state (for tests and the debug readout). */
    val brainState: VibrationCreatureBrain.State get() = brain.state

    /** The fight begins again: five hits to go, at rest, as if just out of the pipe. */
    fun reset(maw: Ch2L2Maw) {
        brain.reset()
        mode = Mode.BRAIN
        hitsLeft = maw.hits
        modeTicks = 0
        hitCooldown = 0
        pendingHit = false
    }

    /** It dies as if hit as many times as it takes (the debug command); a no-op if it is dying or dead. */
    fun kill() {
        if (mode == Mode.WALK_TO_CENTRE || mode == Mode.CROUCH || mode == Mode.DEAD) return
        hitsLeft = 1
        hit()
    }

    /**
     * A thrown piece struck it. False, and nothing counts, once it is dying. Otherwise one hit is taken away; the next tick decides what
     * it does with the player (orient and ram), or, at the last, starts the walk to the centre.
     */
    fun hit(): Boolean {
        if (mode == Mode.WALK_TO_CENTRE || mode == Mode.CROUCH || mode == Mode.DEAD) return false
        hitsLeft--
        pendingHit = true
        return true
    }

    fun tick(senses: Senses, maw: Ch2L2Maw, creature: Ch2L2Creature): Decision {
        if (hitCooldown > 0) hitCooldown--
        if (pendingHit) {
            pendingHit = false
            enter(if (hitsLeft <= 0) Mode.WALK_TO_CENTRE else Mode.RAM_ORIENT)
        }
        modeTicks++
        return when (mode) {
            Mode.BRAIN -> brainTick(senses, creature)
            Mode.RAM_ORIENT -> ramOrient(senses, maw)
            Mode.RAM -> ram(senses, maw, creature)
            Mode.STUNNED -> stunned(senses, maw, creature)
            Mode.WALK_TO_CENTRE -> walkToCentre(senses, maw)
            Mode.CROUCH -> crouch(senses, maw)
            Mode.DEAD -> Decision(Mode.DEAD, VibrationCreatureBrain.State.RECOVER, null, 0.0)
        }
    }

    private fun enter(next: Mode) {
        mode = next
        modeTicks = 0
    }

    private fun brainTick(s: Senses, creature: Ch2L2Creature): Decision {
        val d = brain.tick(
            VibrationCreatureBrain.Senses(
                s.now, s.position, s.home, s.vibrations, s.player?.let { VibrationCreatureBrain.NearbyPlayer(it.id, it.distance) }, s.navigation,
            ),
            creature,
        )
        return Decision(Mode.BRAIN, d.state, d.target, d.speed, oriented = d.oriented, windup = d.windup, struck = d.struck, hit = d.hit)
    }

    private fun ramOrient(s: Senses, maw: Ch2L2Maw): Decision {
        val toward = s.player?.position ?: s.centre
        if (modeTicks >= ticks(maw.ramOrientSeconds)) {
            val dx = toward.x - s.position.x
            val dz = toward.z - s.position.z
            val length = hypot(dx, dz).coerceAtLeast(1e-6)
            hx = dx / length
            hz = dz / length
            enter(Mode.RAM)
            return Decision(Mode.RAM, VibrationCreatureBrain.State.CHARGE, ahead(s.position), maw.ramSpeed, straight = true, heading = hx to hz, oriented = true)
        }
        return Decision(Mode.RAM_ORIENT, VibrationCreatureBrain.State.ORIENT, toward, 0.0)
    }

    private fun ram(s: Senses, maw: Ch2L2Maw, creature: Ch2L2Creature): Decision {
        s.player?.let { p ->
            // A small turn towards where they are now, never more than the limit.
            val want = atan2(p.position.z - s.position.z, p.position.x - s.position.x)
            val have = atan2(hz, hx)
            var diff = want - have
            while (diff > PI) diff -= 2 * PI
            while (diff < -PI) diff += 2 * PI
            val limit = Math.toRadians(maw.ramTurnDegreesPerSecond) / 20.0
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
            enter(Mode.STUNNED)
            return Decision(Mode.STUNNED, VibrationCreatureBrain.State.RECOVER, null, 0.0, struck = struck, hit = victim, rammed = walled)
        }
        return Decision(Mode.RAM, VibrationCreatureBrain.State.CHARGE, ahead(s.position), maw.ramSpeed, straight = true, heading = hx to hz, struck = struck, hit = victim)
    }

    private fun stunned(s: Senses, maw: Ch2L2Maw, creature: Ch2L2Creature): Decision {
        if (modeTicks >= ticks(maw.stunSeconds)) {
            brain.reset()
            brain.searchAround(s.position)
            enter(Mode.BRAIN)
            return brainTick(s, creature)
        }
        return Decision(Mode.STUNNED, VibrationCreatureBrain.State.RECOVER, null, 0.0)
    }

    private fun walkToCentre(s: Senses, maw: Ch2L2Maw): Decision {
        val there = VibrationCreatureBrain.horizontalDistance(s.position, s.centre) <= ARRIVE
        if (there || modeTicks >= ticks(maw.deathWalkSeconds)) {
            enter(Mode.CROUCH)
            return Decision(Mode.CROUCH, VibrationCreatureBrain.State.RECOVER, null, 0.0)
        }
        return Decision(Mode.WALK_TO_CENTRE, VibrationCreatureBrain.State.SEARCH, s.centre, maw.deathWalkSpeed)
    }

    private fun crouch(s: Senses, maw: Ch2L2Maw): Decision {
        if (modeTicks >= ticks(maw.crouchSeconds)) {
            enter(Mode.DEAD)
            return Decision(Mode.DEAD, VibrationCreatureBrain.State.RECOVER, null, 0.0, collapse = true)
        }
        return Decision(Mode.CROUCH, VibrationCreatureBrain.State.RECOVER, null, 0.0)
    }

    /** A point far ahead on the heading (the entity charges at it without finding a path). */
    private fun ahead(from: Position) = Position(from.x + hx * AHEAD, from.y, from.z + hz * AHEAD)

    companion object {
        /** Close enough to the centre to count as there, blocks. */
        const val ARRIVE = 1.0
        const val MIN_RAM_TICKS = 6
        const val AHEAD = 30.0

        private fun ticks(seconds: Double) = VibrationCreatureBrain.ticks(seconds)
    }
}
