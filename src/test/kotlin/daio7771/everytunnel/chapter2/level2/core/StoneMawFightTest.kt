package daio7771.everytunnel.chapter2.level2.core

import daio7771.everytunnel.chapter2.level2.core.StoneMawFight.Mode
import daio7771.everytunnel.common.core.level.Position
import java.util.UUID
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
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
    private var pillar: Int? = null

    private fun player(x: Double, z: Double = 0.0) = StoneMawFight.Target(id, Position(x, 0.0, z), Math.hypot(x - position.x, z - position.z))

    private fun tick(player: StoneMawFight.Target? = null, vibrations: List<VibrationCreatureBrain.Vibration> = emptyList()): StoneMawFight.Decision {
        val d = fight.tick(StoneMawFight.Senses(now, position, home, centre, vibrations, player, wall = wall, pillar = pillar), maw, creature)
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

    /** One ram that ends against [index] (a pillar), from the hit to the stop; returns the decision of the stop. */
    private fun ramInto(index: Int?, p: StoneMawFight.Target = player(-8.0)): StoneMawFight.Decision {
        hitAndRam(p)
        repeat(10) { tick(p) }
        wall = true
        pillar = index
        val d = tick(p)
        wall = false
        pillar = null
        return d
    }

    private fun waitOutStun() {
        var guard = 0
        while (fight.mode == Mode.STUNNED && guard++ < 400) tick()
    }

    @Test
    fun `at rest it does nothing, with every pillar whole`() {
        assertEquals(0, fight.broken)
        val d = tick()
        assertEquals(Mode.BRAIN, d.mode)
        assertEquals(VibrationCreatureBrain.State.REST, d.state)
    }

    @Test
    fun `a hit makes it orient on the player then ram in a straight line at the first stage's speed`() {
        val d = hitAndRam(player(-8.0, 3.0))
        assertEquals(1, fight.hits)
        assertEquals(Mode.RAM, d.mode)
        assertTrue(d.oriented)
        assertTrue(d.straight)
        assertEquals(maw.stage(0).ramSpeed, d.speed)
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
        assertTrue(Math.abs(degrees) <= maw.stage(0).turnDegreesPerSecond + 1.0, "turned $degrees")
        assertTrue(Math.abs(degrees) > 5.0, "it should turn some: $degrees")
    }

    @Test
    fun `a wall stuns it for the stun seconds and damages nothing, then it goes back to the brain searching`() {
        hitAndRam(player(-8.0))
        repeat(30) { assertEquals(Mode.RAM, tick(player(-8.0)).mode) }
        wall = true
        val d = tick(player(-8.0))
        assertEquals(Mode.STUNNED, d.mode)
        assertTrue(d.rammed)
        assertNull(d.pillarHit)
        assertEquals(0, (0..2).sumOf { fight.pillarDamage(it) })
        wall = false
        repeat(ticks(maw.stage(0).stunSeconds) - 2) { assertEquals(Mode.STUNNED, tick().mode) }
        var after = tick()
        var guard = 0
        while (after.mode == Mode.STUNNED && guard++ < 10) after = tick()
        assertEquals(Mode.BRAIN, after.mode)
        assertEquals(VibrationCreatureBrain.State.SEARCH, fight.brainState)
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
    fun `a hit while stunned has it come for the player again, and a hit never kills it`() {
        ramInto(null)
        assertEquals(Mode.STUNNED, fight.mode)
        assertTrue(fight.hit())
        assertEquals(Mode.RAM_ORIENT, tick(player(-8.0)).mode)
        repeat(40) {
            assertTrue(fight.hit())
            tick(player(-8.0))
        }
        assertNotEquals(Mode.DEAD, fight.mode)
        assertEquals(0, fight.broken)
    }

    @Test
    fun `a ram into a pillar cracks it, the second breaks it, and the stage goes up`() {
        val first = ramInto(1)
        assertEquals(Mode.STUNNED, first.mode)
        assertEquals(1, first.pillarHit)
        assertFalse(first.pillarBroken)
        assertFalse(first.collapse)
        assertEquals(1, fight.pillarDamage(1))
        assertEquals(0, fight.broken)
        waitOutStun()
        val second = ramInto(1)
        assertEquals(1, second.pillarHit)
        assertTrue(second.pillarBroken)
        assertFalse(second.collapse)
        assertTrue(fight.isBroken(1))
        assertEquals(1, fight.broken)
        // Stage 1: faster and sharper, and a shorter stun.
        waitOutStun()
        val ram = hitAndRam(player(-8.0))
        assertEquals(maw.stage(1).ramSpeed, ram.speed)
        assertTrue(maw.stage(1).ramSpeed > maw.stage(0).ramSpeed)
        assertTrue(maw.stage(1).stunSeconds < maw.stage(0).stunSeconds)
    }

    @Test
    fun `a ram into the stump of a broken pillar damages nothing, like a wall`() {
        repeat(maw.pillarHits) { ramInto(0); waitOutStun() }
        assertTrue(fight.isBroken(0))
        val d = ramInto(0)
        assertTrue(d.rammed)
        assertNull(d.pillarHit)
        assertEquals(maw.pillarHits, fight.pillarDamage(0))
        assertEquals(1, fight.broken)
    }

    @Test
    fun `only the third pillar to break brings the ceiling down, once, and then it is dead and nothing counts`() {
        var collapses = 0
        for (pillarIndex in listOf(2, 0, 1)) {
            repeat(maw.pillarHits) {
                val d = ramInto(pillarIndex)
                if (d.collapse) collapses++
                waitOutStun()
            }
        }
        assertEquals(1, collapses)
        assertEquals(3, fight.broken)
        assertEquals(Mode.DEAD, fight.mode)
        assertFalse(fight.hit())
        assertFalse(tick(player(-8.0)).collapse)
        assertNull(fight.damagePillar(0))
    }

    @Test
    fun `a ram that ends against nothing known damages no pillar even if it was told one`() {
        hitAndRam(player(-8.0))
        repeat(10) { tick(player(-8.0)) }
        pillar = 2
        val d = tick(player(-8.0))
        assertEquals(Mode.RAM, d.mode)
        assertEquals(0, fight.pillarDamage(2))
    }

    @Test
    fun `breakAll breaks what stands at once and ends in the collapse's outcome`() {
        assertTrue(fight.damagePillar(1) != null)
        val last = fight.breakAll()
        assertNotNull(last)
        assertTrue(last.collapse)
        assertEquals(3, fight.broken)
        assertEquals(Mode.DEAD, fight.mode)
        assertNull(fight.breakAll())
    }

    @Test
    fun `a miss is a loud pipe vibration the brain follows`() {
        val clang = VibrationCreatureBrain.Vibration(Position(5.0, 0.0, 5.0), now, VibrationCreatureBrain.Source.PIPE, VibrationCreatureBrain.Kind.LOUD)
        tick()
        val d = tick(vibrations = listOf(clang.copy(tick = now - 1)))
        assertEquals(VibrationCreatureBrain.State.ORIENT, d.state)
        assertEquals(Position(5.0, 0.0, 5.0), d.target)
        assertEquals(0, fight.hits)
    }

    @Test
    fun `it charges on its own at a noise when a player is in the arena, counts no hit, and waits its cooldown after a stun`() {
        fight.reset(maw)
        run(maw.autoRamCooldownSeconds)
        val clang = VibrationCreatureBrain.Vibration(Position(5.0, 0.0, 5.0), now, VibrationCreatureBrain.Source.PIPE, VibrationCreatureBrain.Kind.LOUD)
        tick(player(-8.0), listOf(clang))
        var d = tick(player(-8.0), listOf(clang.copy(tick = now - 1)))
        var guard = 0
        while (d.mode == Mode.BRAIN && guard++ < 20) d = tick(player(-8.0), listOf(clang.copy(tick = now - 1)))
        assertEquals(Mode.RAM_ORIENT, d.mode)
        assertEquals(0, fight.hits)
        // Ram into a wall, stun, and the next noise within the cooldown does not send it again.
        var n = 0
        while (fight.mode != Mode.RAM && n++ < 200) tick(player(-8.0))
        repeat(10) { tick(player(-8.0)) }
        wall = true
        tick(player(-8.0))
        wall = false
        waitOutStun()
        val again = clang.copy(tick = now)
        val after = tick(player(-8.0), listOf(again))
        assertEquals(Mode.BRAIN, after.mode)
    }

    @Test
    fun `without a player it never charges on its own`() {
        fight.reset(maw)
        run(maw.autoRamCooldownSeconds)
        val clang = VibrationCreatureBrain.Vibration(Position(5.0, 0.0, 5.0), now, VibrationCreatureBrain.Source.PIPE, VibrationCreatureBrain.Kind.LOUD)
        tick()
        repeat(30) { assertEquals(Mode.BRAIN, tick(vibrations = listOf(clang.copy(tick = now - 1))).mode) }
    }

    @Test
    fun `reset puts every pillar whole and it at rest`() {
        ramInto(0)
        fight.reset(maw)
        assertEquals(0, fight.broken)
        assertEquals(0, fight.pillarDamage(0))
        assertEquals(Mode.BRAIN, fight.mode)
        assertNotNull(tick())
        assertEquals(VibrationCreatureBrain.State.REST, fight.brainState)
    }
}
