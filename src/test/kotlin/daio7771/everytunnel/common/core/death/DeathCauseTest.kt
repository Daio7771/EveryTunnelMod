package daio7771.everytunnel.common.core.death

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeathCauseTest {

    @Test
    fun `the killer is named by its entity`() {
        assertEquals(DeathCause.PALE_MAN, DeathCause.of(2, "every_tunnel:pale_man", "minecraft:mob_attack"))
        assertEquals(DeathCause.OBSERVER, DeathCause.of(3, "every_tunnel:observer", "minecraft:mob_attack"))
        assertEquals(DeathCause.HUNTERS, DeathCause.of(5, "every_tunnel:hunter", "minecraft:mob_attack"))
        assertEquals(DeathCause.CLOUD, DeathCause.of(5, "every_tunnel:distortion_cloud", "minecraft:indirect_magic"))
    }

    @Test
    fun `without a killer the damage names it, or nothing does`() {
        assertEquals(DeathCause.THIRST, DeathCause.of(2, null, "minecraft:dry_out"))
        assertEquals(DeathCause.FALL, DeathCause.of(5, null, "minecraft:fall"))
        assertEquals(DeathCause.DEEP_WATER, DeathCause.of(5, null, "minecraft:drown"))
        assertEquals(DeathCause.OTHER, DeathCause.of(3, null, "minecraft:lava"))
        assertEquals(DeathCause.OTHER, DeathCause.of(3, "minecraft:zombie", "minecraft:mob_attack"))
    }

    @Test
    fun `levels 1 and 4 name only thirst`() {
        assertEquals(DeathCause.THIRST, DeathCause.of(1, null, "minecraft:dry_out"))
        assertEquals(DeathCause.THIRST, DeathCause.of(4, null, "minecraft:dry_out"))
        assertNull(DeathCause.of(4, null, "minecraft:fall"))
        assertNull(DeathCause.of(1, "every_tunnel:pale_man", "minecraft:mob_attack"))
    }

    @Test
    fun `a later chapter's level says itself whether it is quiet`() {
        assertEquals(DeathCause.HUNTERS, DeathCause.of(quiet = false, "every_tunnel:hunter", "minecraft:mob_attack"))
        assertEquals(DeathCause.DEEP_WATER, DeathCause.of(quiet = false, null, "minecraft:drown"))
        assertNull(DeathCause.of(quiet = true, null, "minecraft:drown"))
        assertEquals(DeathCause.THIRST, DeathCause.of(quiet = true, null, "minecraft:dry_out"))
    }

    @Test
    fun `causes round-trip by id`() {
        for (cause in DeathCause.entries) assertEquals(cause, DeathCause.byId(cause.id))
        assertNull(DeathCause.byId("nobody"))
    }
}
