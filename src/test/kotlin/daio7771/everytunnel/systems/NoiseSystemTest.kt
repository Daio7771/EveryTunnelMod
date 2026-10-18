package daio7771.everytunnel.systems

import daio7771.everytunnel.config.NoiseConfig
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NoiseSystemTest {

    private val config = NoiseConfig(sneakRadius = 2.0, walkRadius = 6.0, sprintRadius = 14.0, landRadius = 10.0)
    private val overworld = "minecraft:overworld"

    private fun noise(source: NoiseSource, x: Double, radius: Double, tick: Long, dimension: String = overworld) =
        NoiseEvent(source, dimension, x, 0.0, 0.0, radius, tick)

    @AfterTest
    fun cleanUp() {
        NoiseSystem.clear()
    }

    @Test
    fun `noises are heard within their radius times the hearing, newest first`() {
        val walk = noise(NoiseSource.WALK, 0.0, 6.0, tick = 10)
        val sprint = noise(NoiseSource.SPRINT, 0.0, 14.0, tick = 20)
        NoiseSystem.emit(walk)
        NoiseSystem.emit(sprint)

        assertEquals(listOf(sprint, walk), NoiseSystem.audibleAt(overworld, 5.0, 0.0, 0.0, nowTick = 30))
        assertEquals(listOf(sprint), NoiseSystem.audibleAt(overworld, 10.0, 0.0, 0.0, nowTick = 30))
        assertEquals(listOf(sprint, walk), NoiseSystem.audibleAt(overworld, 10.0, 0.0, 0.0, nowTick = 30, hearing = 2.0))
        assertEquals(emptyList(), NoiseSystem.audibleAt("minecraft:the_nether", 1.0, 0.0, 0.0, nowTick = 30))
    }

    @Test
    fun `old noises are forgotten and silent ones are never recorded`() {
        NoiseSystem.emit(noise(NoiseSource.WALK, 0.0, 6.0, tick = 0))
        NoiseSystem.emit(noise(NoiseSource.JUMP, 0.0, 0.0, tick = 0))
        assertEquals(1, NoiseSystem.recent().size)
        assertEquals(1, NoiseSystem.audibleAt(overworld, 0.0, 0.0, 0.0, nowTick = 100).size)
        assertEquals(0, NoiseSystem.audibleAt(overworld, 0.0, 0.0, 0.0, nowTick = 100, maxAgeTicks = 20).size)
        NoiseSystem.prune(nowTick = 101)
        assertEquals(0, NoiseSystem.recent().size)
    }

    @Test
    fun `listeners hear each noise as it happens`() {
        val heard = mutableListOf<NoiseSource>()
        val listener = NoiseSystem.Listener { heard += it.source }
        NoiseSystem.register(listener)
        NoiseSystem.emit(noise(NoiseSource.LAND, 0.0, 10.0, tick = 0))
        NoiseSystem.unregister(listener)
        NoiseSystem.emit(noise(NoiseSource.LAND, 0.0, 10.0, tick = 1))
        assertEquals(listOf(NoiseSource.LAND), heard)
    }

    @Test
    fun `movement noise depends on sneaking and sprinting, sneaking wins`() {
        assertEquals(2.0, NoiseSystem.movementRadius(config, sneaking = true, sprinting = true))
        assertEquals(14.0, NoiseSystem.movementRadius(config, sneaking = false, sprinting = true))
        assertEquals(6.0, NoiseSystem.movementRadius(config, sneaking = false, sprinting = false))
    }

    @Test
    fun `landing noise scales with the fall between half and twice`() {
        assertEquals(5.0, NoiseSystem.landingRadius(config, 0.8), 1e-9)
        assertEquals(6.25, NoiseSystem.landingRadius(config, 1.25), 1e-9, "landing from a normal jump")
        assertEquals(10.0, NoiseSystem.landingRadius(config, 2.0), 1e-9)
        assertEquals(15.0, NoiseSystem.landingRadius(config, 3.0), 1e-9)
        assertEquals(20.0, NoiseSystem.landingRadius(config, 30.0), 1e-9)
    }

    @Test
    fun `one footstep per block walked`() {
        val steps = NoiseSystem.StepCounter()
        assertEquals(listOf(0, 0, 1, 0, 2), listOf(0.4, 0.4, 0.4, 0.4, 1.6).map { steps.add(it) })
    }
}
