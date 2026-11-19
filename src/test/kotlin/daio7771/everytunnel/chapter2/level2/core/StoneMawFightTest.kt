package daio7771.everytunnel.chapter2.level2.core

import daio7771.everytunnel.chapter2.level2.core.StoneMawFight.Mode
import daio7771.everytunnel.core.level.Position
import java.util.UUID
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoneMawFightTest {

    private val maw = Ch2L2Maw.DEFAULT
    private val creature = Ch2L2Creature.DEFAULT
    private val fight = StoneMawFight(Random(3))
    private val id = UUID.randomUUID()
    private val home = Position(0.0, 0.0, 0.0)
    private val centre = Position(10.0, 0.0, 0.0)
    private var now = 100L
    private var position = home
    private var wall = false

    private fun player(x: Double, z: Double = 0.0) = StoneMawFight.Target(id, Position(x, 0.0, z), Math.hypot(x - position.x, z - position.z))

    private fun tick(player: StoneMawFight.Target? = null, vibrations: List<VibrationCreatureBrain.Vibration> = emptyList()): StoneMawFight.Decision {
        val d = fight.tick(StoneMawFight.Senses(now, position, home, centre, vibrations, player, wall = wall), maw, creature)
        now++
        return d
    }

    private fun ticks(seconds: Double) = VibrationCreatureBrain.ticks(seconds)

    private fun run(seconds: Double, player: StoneMawFight.Target? = null) = repeat(ticks(seconds) + 1) { tick(player) }

    /** A hit, then the orient, until the ram begins; returns the ram's first decision. */
    private fun hitAndRam(p: StoneMawFight.Target?): StoneMawFight.Decision {
        assertTrue(fight.hit())
        var d = tick(p)
        var guard = 0
        while (d.mode == Mode.RAM_ORIENT && guard++ < 200) d = tick(p)
        return d
    }

    @Test
    fun `at rest it does nothing, with five hits to go`() {
        assertEquals(5, fight.hitsLeft)
        val d = tick()
        assertEquals(Mode.BRAIN, d.mode)
        assertEquals(VibrationCreatureBrain.State.REST, d.state)
    }

    @Test
    fun `a hit makes it orient on the player then ram in a straight line at the ram speed`() {
        val d = hitAndRam(player(-8.0, 3.0))
        assertEquals(4, fight.hitsLeft)
        assertEquals(Mode.RAM, d.mode)
        assertTrue(d.oriented)
        assertTrue(d.straight)
        assertEquals(maw.ramSpeed, d.speed)
        val (hx, hz) = d.heading!!
        assertEquals(1.0, Math.hypot(hx, hz), 1e-9)
        assertTrue(hx < 0.0 && hz > 0.0, "it heads for where the player is: $hx, $hz")
    }

    @Test
    fun `the ram turns only a little towards a player who moves`() {
        hitAndRam(player(-8.0, 0.0))
        // The player now stands to the side: after one second it has turned at most the limit.
        val sample = StoneMawFight.Senses(now, position, home, centre, player = player(0.0, 10.0))
        var heading = Pair(-1.0, 0.0)
        repeat(20) { heading = fight.tick(sample.copy(now = now++), maw, creature).heading ?: heading }
        val degrees = Math.toDegrees(Math.atan2(heading.second, heading.first)) - 180.0
        assertTrue(Math.abs(degrees) <= maw.ramTurnDegreesPerSecond + 1.0, "turned $degrees")
        assertTrue(Math.abs(degrees) > 5.0, "it should turn some: $degrees")
    }

    @Test
    fun `it rams on until a wall, then is stunned for the stun seconds and goes back to the brain searching`() {
        hitAndRam(player(-8.0))
        repeat(30) { assertEquals(Mode.RAM, tick(player(-8.0)).mode) }
        wall = true
        val d = tick(player(-8.0))
        assertEquals(Mode.STUNNED, d.mode)
        assertTrue(d.rammed)
        wall = false
        repeat(ticks(maw.stunSeconds) - 2) { assertEquals(Mode.STUNNED, tick().mode) }
        var after = tick()
        var guard = 0
        while (after.mode == Mode.STUNNED && guard++ < 10) after = tick()
        assertEquals(Mode.BRAIN, after.mode)
        assertEquals(VibrationCreatureBrain.State.SEARCH, fight.brainState)
        assertEquals(4, fight.hitsLeft)
    }

    @Test
    fun `a wall touched right at the start does not stun it`() {
        hitAndRam(player(-8.0))
        wall = true
        assertEquals(Mode.RAM, tick(player(-8.0)).mode)
    }

    @Test
    fun `with no wall the ram stops after its longest time`() {
        hitAndRam(player(-8.0))
        var d = tick()
        var n = 0
        while (d.mode == Mode.RAM && n++ < 400) d = tick()
        assertEquals(Mode.STUNNED, d.mode)
        assertFalse(d.rammed)
    }

    @Test
    fun `a player within contact range on the way is struck once, and the ram goes on`() {
        hitAndRam(player(-8.0))
        val close = StoneMawFight.Target(id, Position(-1.0, 0.0, 0.0), 1.0)
        val first = tick(close)
        assertTrue(first.struck)
        assertEquals(id, first.hit)
        assertEquals(Mode.RAM, first.mode)
        val second = tick(close)
        assertFalse(second.struck)
        assertNull(second.hit)
    }

    @Test
    fun `a hit while stunned counts and has it come for the player again`() {
        hitAndRam(player(-8.0))
        repeat(10) { tick(player(-8.0)) }
        wall = true
        tick(player(-8.0))
        wall = false
        assertEquals(Mode.STUNNED, fight.mode)
        assertTrue(fight.hit())
        assertEquals(3, fight.hitsLeft)
        assertEquals(Mode.RAM_ORIENT, tick(player(-8.0)).mode)
    }

    @Test
    fun `the last hit has it walk to the centre, crouch, and bring the ceiling down once, and no more hits count`() {
        repeat(4) { assertTrue(fight.hit()); tick(); tick() }
        assertTrue(fight.hit())
        assertEquals(0, fight.hitsLeft)
        var d = tick()
        assertEquals(Mode.WALK_TO_CENTRE, d.mode)
        assertEquals(centre, d.target)
        assertEquals(maw.deathWalkSpeed, d.speed)
        assertFalse(fight.hit())
        position = Position(9.5, 0.0, 0.0)
        d = tick()
        assertEquals(Mode.CROUCH, d.mode)
        var collapses = 0
        repeat(ticks(maw.crouchSeconds) + 5) { if (tick().collapse) collapses++ }
        assertEquals(1, collapses)
        assertEquals(Mode.DEAD, fight.mode)
        assertFalse(fight.hit())
        assertFalse(tick().collapse)
    }

    @Test
    fun `it gives up walking to the centre after the walk's longest time`() {
        fight.kill()
        var d = tick()
        var n = 0
        while (d.mode == Mode.WALK_TO_CENTRE && n++ < 1000) d = tick()
        assertEquals(Mode.CROUCH, d.mode)
        assertTrue(Math.abs(n - ticks(maw.deathWalkSeconds)) <= 2, "walked $n ticks")
    }

    @Test
    fun `a miss is a loud pipe vibration the brain follows`() {
        val clang = VibrationCreatureBrain.Vibration(Position(5.0, 0.0, 5.0), now, VibrationCreatureBrain.Source.PIPE, VibrationCreatureBrain.Kind.LOUD)
        tick()
        val d = tick(vibrations = listOf(clang.copy(tick = now - 1)))
        assertEquals(VibrationCreatureBrain.State.ORIENT, d.state)
        assertEquals(Position(5.0, 0.0, 5.0), d.target)
        assertEquals(5, fight.hitsLeft)
    }

    @Test
    fun `reset puts it back at five hits and at rest`() {
        hitAndRam(player(-8.0))
        fight.reset(maw)
        assertEquals(5, fight.hitsLeft)
        assertEquals(Mode.BRAIN, fight.mode)
        assertNotNull(tick())
        assertEquals(VibrationCreatureBrain.State.REST, fight.brainState)
    }
}
