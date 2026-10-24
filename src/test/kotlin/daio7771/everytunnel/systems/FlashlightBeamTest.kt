package daio7771.everytunnel.systems

import daio7771.everytunnel.config.FlashlightConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlashlightBeamTest {

    private val config = FlashlightConfig(beamRange = 20.0, beamWidthDegrees = 40.0, beamBrightness = 15.0, glowBrightness = 4.0)

    /** Beam from the origin along +X; [hit] is the distance to a wall, null for open space. */
    private fun beam(hit: Double? = null, dimming: Double = 1.0) =
        FlashlightBeam.create(0.0, 0.0, 0.0, 2.0, 0.0, 0.0, hit, config, dimming)

    @Test
    fun `the beam dims along its length and ends at the range`() {
        val b = beam()
        val near = b.lightAt(2.0, 0.0, 0.0)
        val middle = b.lightAt(10.0, 0.0, 0.0)
        val far = b.lightAt(18.0, 0.0, 0.0)
        assertTrue(near > middle && middle > far && far > 0.0, "$near > $middle > $far > 0")
        assertEquals(0.0, b.lightAt(21.0, 0.0, 0.0))
        assertTrue(near <= FlashlightBeam.MAX_LIGHT)
    }

    @Test
    fun `light fades outside the cone and only the glow is behind the player`() {
        val b = beam()
        // At 10 blocks a 40 degree cone has a radius of about 3.6 blocks.
        val center = b.lightAt(10.0, 0.0, 0.0)
        val insideEdge = b.lightAt(10.0, 3.0, 0.0)
        val outside = b.lightAt(10.0, 6.0, 0.0)
        assertEquals(center, insideEdge, 1e-9, "full light anywhere inside the cone")
        assertTrue(outside < center - 6.0, "soft edge: $outside")
        assertEquals(0.0, b.lightAt(10.0, 12.0, 0.0), "far outside the cone")

        assertEquals(3.0, b.lightAt(-1.0, 0.0, 0.0), 1e-9, "glow only: 4 - 1 block")
        assertEquals(0.0, b.lightAt(-6.0, 0.0, 0.0))
    }

    @Test
    fun `the beam stops just past the wall it hits`() {
        val b = beam(hit = 5.0)
        assertTrue(b.lightAt(5.5, 0.0, 0.0) > 0.0, "air in front of the wall and the wall itself are lit")
        assertEquals(0.0, b.lightAt(7.0, 0.0, 0.0), "the room behind the wall stays dark")
    }

    @Test
    fun `bounds contain every lit position`() {
        val b = beam(hit = 12.0)
        val box = b.bounds()
        for (x in -20..20) for (y in -20..20) for (z in -20..20) {
            if (b.lightAt(x + 0.5, y + 0.5, z + 0.5) > 0.0) {
                assertTrue(
                    x in box.minX..box.maxX && y in box.minY..box.maxY && z in box.minZ..box.maxZ,
                    "($x, $y, $z) is lit but outside $box",
                )
            }
        }
    }

    @Test
    fun `small movements do not count as a change, dimming does`() {
        val b = beam()
        assertTrue(b.looksLike(FlashlightBeam.create(0.1, 0.0, 0.0, 2.0, 0.01, 0.0, null, config)))
        assertFalse(b.looksLike(FlashlightBeam.create(0.0, 0.0, 0.0, 2.0, 0.5, 0.0, null, config)), "turned about 14 degrees")
        assertFalse(b.looksLike(beam(hit = 8.0)), "the wall moved closer")
        assertFalse(b.looksLike(beam(dimming = 0.25)), "flicker")
    }

    @Test
    fun `with shaders the beam is narrower, brightest in the middle, with less glow`() {
        val plain = beam()
        val shaped = FlashlightBeam.create(0.0, 0.0, 0.0, 2.0, 0.0, 0.0, null, config, forShaders = true)
        assertTrue(shaped.halfAngle < plain.halfAngle)
        assertTrue(shaped.glow < plain.glow)
        // On the axis the same; halfway to the cone's edge, dimmer.
        assertEquals(plain.lightAt(5.0, 0.0, 0.0), shaped.lightAt(5.0, 0.0, 0.0), 1e-9)
        val half = shaped.coneRadiusAt(5.0) / 2
        assertTrue(shaped.lightAt(5.0, half, 0.0) < shaped.lightAt(5.0, 0.0, 0.0))
        assertFalse(plain.looksLike(shaped, coarse = true), "switching shaders recomputes the light")
    }

    @Test
    fun `without shaders bigger changes count as the same, but not big turns or flicker`() {
        val b = beam()
        val turned5 = FlashlightBeam.create(0.0, 0.0, 0.0, 2.0, 0.175, 0.0, null, config)
        assertFalse(b.looksLike(turned5), "about 5 degrees is a change with shaders")
        assertTrue(b.looksLike(turned5, coarse = true), "but not without")
        assertTrue(b.looksLike(FlashlightBeam.create(0.5, 0.0, 0.0, 2.0, 0.0, 0.0, null, config), coarse = true), "half a step")
        assertFalse(b.looksLike(FlashlightBeam.create(0.0, 0.0, 0.0, 2.0, 0.5, 0.0, null, config), coarse = true), "turned about 14 degrees")
        assertFalse(b.looksLike(beam(dimming = 0.25), coarse = true), "flicker")
    }
}
