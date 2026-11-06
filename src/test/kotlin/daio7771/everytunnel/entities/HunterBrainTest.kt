package daio7771.everytunnel.entities

import daio7771.everytunnel.config.HunterConfig
import daio7771.everytunnel.core.level.Position
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HunterBrainTest {

    private val config = HunterConfig()
    private val home = Position(0.0, 0.0, 0.0)
    private val point = Position(4.0, 0.0, 0.0)
    private val player = UUID.randomUUID()
    private var now = 1000L

    private fun HunterBrain.step(
        at: Position = home,
        seen: HunterBrain.Contact? = null,
        told: HunterBrain.Contact? = null,
        idle: Boolean = false,
    ): HunterBrain.Decision = tick(HunterBrain.Senses(now++, at, home, point, seen, told, idle))

    private fun HunterBrain.run(ticks: Int, at: Position = home, idle: Boolean = false): HunterBrain.Decision {
        var last = step(at, idle = idle)
        repeat(ticks - 1) { last = step(at, idle = idle) }
        return last
    }

    private fun contact(distance: Double, at: Position = Position(distance, 0.0, 0.0)) = HunterBrain.Contact(player, at, distance)

    private fun ticks(seconds: Double) = (seconds * 20).toInt()

    @Test
    fun `they stand a while, then walk to a point near home`() {
        val brain = HunterBrain(config)
        val first = brain.step()
        assertEquals(HunterBrain.State.WANDER, first.state)
        assertEquals(HunterBrain.Pace.STAND, first.pace)
        val walk = brain.run(ticks(config.wanderPauseSeconds))
        assertEquals(HunterBrain.Pace.WALK, walk.pace)
        assertEquals(point, walk.target)
        val there = brain.step(at = point)
        assertEquals(HunterBrain.Pace.STAND, there.pace)
    }

    @Test
    fun `seeing a player they charge, shrieking once`() {
        val brain = HunterBrain(config)
        brain.step()
        val first = brain.step(seen = contact(10.0))
        assertEquals(HunterBrain.State.CHASE, first.state)
        assertEquals(HunterBrain.Pace.RUN, first.pace)
        assertTrue(first.charged)
        assertEquals(Position(10.0, 0.0, 0.0), first.target)
        assertFalse(brain.step(seen = contact(9.0)).charged)
    }

    @Test
    fun `they hit a player they see close by, once a second`() {
        val brain = HunterBrain(config)
        assertEquals(player, brain.step(seen = contact(1.0)).attack)
        val between = (1 until ticks(config.attackSeconds)).map { brain.step(seen = contact(1.0)).attack }
        assertTrue(between.all { it == null })
        assertEquals(player, brain.step(seen = contact(1.0)).attack)
    }

    @Test
    fun `a player too far, or only told of, is not hit`() {
        val brain = HunterBrain(config)
        assertNull(brain.step(seen = contact(3.0)).attack)
        assertNull(brain.step(told = contact(1.0)).attack)
    }

    @Test
    fun `being told where a player is starts a chase too`() {
        val brain = HunterBrain(config)
        val decision = brain.step(told = contact(30.0))
        assertEquals(HunterBrain.State.CHASE, decision.state)
        assertTrue(decision.charged)
    }

    @Test
    fun `losing the player they search, then walk home and wander`() {
        val brain = HunterBrain(config)
        brain.step(seen = contact(10.0))
        val running = brain.run(ticks(config.lostSeconds) - 1, at = Position(5.0, 0.0, 0.0))
        assertEquals(HunterBrain.State.CHASE, running.state)
        val search = brain.step(at = Position(5.0, 0.0, 0.0))
        assertEquals(HunterBrain.State.SEARCH, search.state)
        assertEquals(HunterBrain.Pace.STAND, search.pace)
        val back = brain.run(ticks(config.searchSeconds), at = Position(5.0, 0.0, 0.0))
        assertEquals(HunterBrain.State.RETURN, back.state)
        assertEquals(HunterBrain.Pace.WALK, back.pace)
        assertEquals(home, back.target)
        assertEquals(HunterBrain.State.WANDER, brain.step(at = home).state)
    }

    @Test
    fun `reaching where the player was without finding them, they search at once`() {
        val brain = HunterBrain(config)
        brain.step(seen = contact(10.0))
        val there = brain.step(at = Position(10.0, 0.0, 0.0), idle = true)
        assertEquals(HunterBrain.State.SEARCH, there.state)
    }

    @Test
    fun `reset sends them back to wandering`() {
        val brain = HunterBrain(config)
        brain.step(seen = contact(1.0))
        brain.reset()
        val decision = brain.step()
        assertEquals(HunterBrain.State.WANDER, decision.state)
        assertEquals(player, brain.step(seen = contact(1.0)).attack)
    }
}
