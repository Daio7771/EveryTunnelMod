package daio7771.everytunnel.chapter2.level2.core

import daio7771.everytunnel.chapter2.level2.core.GuardFight.Act
import daio7771.everytunnel.chapter2.level2.core.GuardFight.Phase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuardFightTest {

    /** The slow-reveal timings (a crack, then a stare) so that the phases can be told apart; the shipped default is a jumpscare. */
    private val config = Ch2L2Guard.DEFAULT.copy(crackSeconds = 2.5, introSeconds = 0.0, stareSeconds = 1.5, deathSeconds = 10.0)
    private fun fight() = GuardFight(config)

    /** A fight already hunting at tick 0 (crack and stare over). */
    private fun hunting(): GuardFight {
        val f = fight()
        f.begin(0)
        assertTrue(f.tick(50))
        f.tick(50)
        f.tick(50 + 30)
        assertEquals(Phase.HUNTING, f.phase)
        return f
    }

    @Test
    fun `the shipped default warns five seconds, plays a ten second intro, stares half a second, then hunts`() {
        val f = GuardFight(Ch2L2Guard.DEFAULT)
        f.begin(0)
        assertEquals(100, f.crackRemaining(0))
        assertFalse(f.tick(99))
        assertTrue(f.tick(100))
        assertEquals(Phase.INTRO, f.phase)
        assertEquals(50, f.introElapsed(150))
        f.tick(299)
        assertEquals(Phase.INTRO, f.phase)
        f.tick(300)
        assertEquals(Phase.STARING, f.phase)
        f.tick(310)
        assertEquals(Phase.HUNTING, f.phase)
    }

    @Test
    fun `the intro can be skipped on to the stare, and only the intro`() {
        val f = GuardFight(Ch2L2Guard.DEFAULT)
        f.begin(0)
        f.skipIntro(5)
        assertEquals(Phase.CRACKING, f.phase)
        f.tick(100)
        f.skipIntro(120)
        assertEquals(Phase.STARING, f.phase)
        f.tick(130)
        assertEquals(Phase.HUNTING, f.phase)
    }

    @Test
    fun `it cracks, introduces itself, stares, then hunts`() {
        val f = fight()
        assertTrue(f.begin(0))
        assertFalse(f.begin(1))
        assertFalse(f.tick(49))
        assertEquals(Phase.CRACKING, f.phase)
        assertTrue(f.tick(50))
        assertEquals(Phase.INTRO, f.phase)
        f.tick(50)
        assertEquals(Phase.STARING, f.phase)
        f.tick(79)
        assertEquals(Phase.STARING, f.phase)
        f.tick(80)
        assertEquals(Phase.HUNTING, f.phase)
    }

    @Test
    fun `a stun lasts four seconds and a second one during it does not count`() {
        val f = hunting()
        assertTrue(f.stun(100))
        assertTrue(f.stunActive)
        assertFalse(f.stun(120))
        assertEquals(1, f.stuns)
        f.tick(179)
        assertTrue(f.stunActive)
        f.tick(180)
        assertEquals(Phase.HUNTING, f.phase)
    }

    @Test
    fun `no stun counts before it hunts`() {
        val f = fight()
        f.begin(0)
        assertFalse(f.stun(10))
        assertEquals(0, f.stuns)
    }

    @Test
    fun `four effective stuns kill it, even from the same trap, and the fourth starts a ten second death`() {
        val f = hunting()
        var now = 100L
        repeat(3) {
            assertTrue(f.stun(now))
            now += 80
            f.tick(now)
        }
        assertEquals(3, f.stuns)
        assertTrue(f.stun(now))
        assertEquals(Phase.DYING, f.phase)
        assertFalse(f.alive)
        f.tick(now + 199)
        assertFalse(f.dead)
        assertEquals(0.5, f.dyingProgress(now + 100), 0.01)
        f.tick(now + 200)
        assertTrue(f.dead)
    }

    @Test
    fun `finish runs once, from any phase`() {
        val f = fight()
        assertTrue(f.finish())
        assertFalse(f.finish())
        assertEquals(Phase.DEAD, f.phase)
        f.reset()
        assertTrue(f.finish())
    }

    @Test
    fun `it moves until it is in range, winds up, then hits`() {
        val f = hunting()
        assertEquals(Act.MOVE, f.think(100, 10.0))
        assertEquals(Act.WINDUP, f.think(101, 2.0))
        assertEquals(Act.WINDUP, f.think(105, 2.0))
        assertEquals(Act.HIT, f.think(113, 2.0))
    }

    @Test
    fun `a blow misses if the player got out of range during the windup`() {
        val f = hunting()
        f.think(100, 2.0)
        assertEquals(Act.MISS, f.think(113, 6.0))
    }

    @Test
    fun `at least two seconds pass between hits`() {
        val f = hunting()
        f.think(100, 2.0)
        assertEquals(Act.HIT, f.think(112, 2.0))
        // The next windup cannot start before 2 s after the hit.
        assertEquals(Act.MOVE, f.think(130, 2.0))
        assertEquals(Act.WINDUP, f.think(152, 2.0))
        assertEquals(Act.HIT, f.think(164, 2.0))
    }

    @Test
    fun `a stun breaks a windup and nothing happens while stunned`() {
        val f = hunting()
        f.think(100, 2.0)
        f.stun(105)
        assertEquals(Act.NONE, f.think(112, 2.0))
        f.tick(185)
        assertEquals(Act.MOVE, f.think(186, 10.0))
    }

    @Test
    fun `two hits kill at half the health each`() {
        assertEquals(1.0, config.damageFraction * 2, 0.0)
    }

    @Test
    fun `reset starts it over`() {
        val f = hunting()
        f.stun(100)
        f.reset()
        assertEquals(Phase.IDLE, f.phase)
        assertEquals(0, f.stuns)
        assertTrue(f.begin(500))
    }
}
