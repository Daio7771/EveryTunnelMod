package daio7771.everytunnel.entities

import daio7771.everytunnel.core.level.Position
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ObserverBrainTest {

    private val settings = ObserverSettings()
    private val home = Position(0.0, 0.0, 0.0)
    private val point = Position(10.0, 0.0, 0.0)
    private val player = UUID.randomUUID()
    private var now = 1000L

    private fun ObserverBrain.step(
        at: Position = home,
        atHome: Boolean = true,
        seen: ObserverBrain.Seen? = null,
        hidden: ObserverBrain.Hidden? = null,
        alert: Position? = null,
    ): ObserverBrain.Decision = tick(ObserverBrain.Senses(now++, at, atHome, point, seen, hidden, alert))

    private fun ObserverBrain.run(ticks: Int, at: Position = home, atHome: Boolean = true): ObserverBrain.Decision {
        var last = step(at, atHome)
        repeat(ticks - 1) { last = step(at, atHome) }
        return last
    }

    @Test
    fun `he walks his patrol, then stands and looks round`() {
        val brain = ObserverBrain(settings)
        val first = brain.step()
        assertEquals(ObserverBrain.State.PATROL, first.state)
        assertEquals(ObserverBrain.Pace.WALK, first.pace)
        assertEquals(point, first.target)
        val there = brain.step(at = point)
        assertEquals(ObserverBrain.State.LOOK, there.state)
        assertEquals(ObserverBrain.Pace.STAND, there.pace)
        val looking = brain.run((settings.lookSweepSeconds * 20 / 4).toInt(), at = point)
        assertTrue((looking.lookOffset ?: 0.0) > settings.lookSweepDegrees * 0.9, "a quarter of a sweep turns his head fully: ${looking.lookOffset}")
        val states = List((settings.lookSeconds * 20).toInt()) { brain.step(at = point).state }
        assertTrue(ObserverBrain.State.PATROL in states, "after looking round he walks on")
    }

    @Test
    fun `a box on the shelf sends him running there, then he looks round and walks home`() {
        val brain = ObserverBrain(settings)
        brain.step()
        val shelf = Position(40.0, 0.0, 40.0)
        val alerted = brain.step(alert = shelf)
        assertEquals(ObserverBrain.State.ALERTED, alerted.state)
        assertEquals(ObserverBrain.Pace.ALERT_RUN, alerted.pace)
        assertEquals(shelf, alerted.target)
        val there = brain.step(at = shelf, atHome = false)
        assertEquals(ObserverBrain.State.INSPECT, there.state)
        val back = brain.run((settings.inspectSeconds * 20).toInt(), at = shelf, atHome = false)
        assertEquals(ObserverBrain.State.RETURN, back.state)
        assertEquals(ObserverBrain.Pace.WALK, back.pace)
        assertEquals(ObserverBrain.State.PATROL, brain.step(atHome = true).state)
    }

    @Test
    fun `a player he spots far off he walks to, and runs at once they stay in sight`() {
        val brain = ObserverBrain(settings)
        brain.step()
        val far = ObserverBrain.Seen(player, Position(15.0, 0.0, 0.0), 15.0)
        val spotted = brain.step(seen = far)
        assertEquals(ObserverBrain.State.CHASE, spotted.state)
        assertEquals(ObserverBrain.Pace.WALK, spotted.pace)
        var decision = spotted
        repeat((settings.spotSeconds * 20).toInt()) { decision = brain.step(seen = far) }
        assertEquals(ObserverBrain.Pace.RUN, decision.pace)
    }

    @Test
    fun `a player who ducks out of sight in time is only walked after`() {
        val brain = ObserverBrain(settings)
        brain.step()
        val far = ObserverBrain.Seen(player, Position(15.0, 0.0, 0.0), 15.0)
        repeat((settings.spotSeconds * 20).toInt() - 4) { brain.step(seen = far) }
        val ducked = brain.run((settings.lostSeconds * 20).toInt() + 2)
        assertEquals(ObserverBrain.State.SEARCH, ducked.state)
        assertEquals(ObserverBrain.Pace.WALK, ducked.pace)
    }

    @Test
    fun `seeing a player close he runs at once and catches them`() {
        val brain = ObserverBrain(settings)
        brain.step()
        val near = brain.step(seen = ObserverBrain.Seen(player, Position(5.0, 0.0, 0.0), 5.0))
        assertEquals(ObserverBrain.State.CHASE, near.state)
        assertEquals(ObserverBrain.Pace.RUN, near.pace)
        assertNull(near.catch)
        val close = brain.step(seen = ObserverBrain.Seen(player, Position(1.0, 0.0, 0.0), 1.0))
        assertEquals(player, close.catch)
    }

    @Test
    fun `out of sight he runs to where he saw them last, then looks round`() {
        val brain = ObserverBrain(settings)
        brain.step()
        val lastSeen = Position(12.0, 0.0, 3.0)
        brain.step(seen = ObserverBrain.Seen(player, lastSeen, 12.0))
        val still = brain.run((settings.lostSeconds * 20).toInt() - 2)
        assertEquals(ObserverBrain.State.CHASE, still.state)
        val lost = brain.run(3)
        assertEquals(ObserverBrain.State.SEARCH, lost.state)
        assertEquals(lastSeen, lost.target)
        assertEquals(ObserverBrain.State.INSPECT, brain.step(at = lastSeen).state)
    }

    @Test
    fun `a box does not stop a chase`() {
        val brain = ObserverBrain(settings)
        brain.step()
        brain.step(seen = ObserverBrain.Seen(player, Position(15.0, 0.0, 0.0), 15.0))
        val decision = brain.step(alert = Position(40.0, 0.0, 40.0))
        assertEquals(ObserverBrain.State.CHASE, decision.state)
    }

    @Test
    fun `seen getting into a locker, he goes to it and pulls them out`() {
        val brain = ObserverBrain(settings)
        brain.step()
        val locker = Position(8.0, 0.0, 2.0)
        val going = brain.step(hidden = ObserverBrain.Hidden(player, locker, 8.0))
        assertEquals(ObserverBrain.State.GRAB, going.state)
        assertEquals(locker, going.target)
        assertNull(going.grab)
        val there = brain.step(at = locker, hidden = ObserverBrain.Hidden(player, locker, 1.0))
        assertEquals(player, there.grab)
        assertNotNull(brain.step().state)
    }
}
