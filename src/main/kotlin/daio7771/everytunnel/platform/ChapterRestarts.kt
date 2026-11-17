package daio7771.everytunnel.platform

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier

/**
 * Where a later chapter's player comes back after dying, asked by [DeathHooks] so that it never names a chapter. Chapter 1's levels
 * restart from their start or their restart point; a chapter whose levels are not Chapter 1's ([LevelManager][daio7771.everytunnel.core.LevelManager]
 * knows none of them) [register]s a [Source] that gives, for a player who plays it, the [Restart] of the place they are at, or null when
 * none of its own applies (they are not playing it). The first source with an answer wins, before Chapter 1's levels are looked at.
 *
 * The dying itself is [DeathHooks]'s, the same for every chapter: the fade, the death screen, full health and resources (the flashlight
 * off), nothing hurts meanwhile. The chapter's [Restart.reset] then puts its level back as the place wants it (its state, its enemies).
 * Server thread only.
 */
object ChapterRestarts {

    class Restart(
        /** The dimension of the place ("minecraft:overworld"). */
        val world: Identifier,
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
        val pitch: Float,
        /** No enemies there: the death screen names only thirst ([daio7771.everytunnel.core.death.DeathCause]). */
        val quiet: Boolean = false,
        /** Once the player stands there again, in the dark: the chapter's own restart. */
        val reset: (ServerPlayerEntity) -> Unit = {},
    )

    fun interface Source {
        fun restartFor(player: ServerPlayerEntity): Restart?
    }

    private val sources = mutableListOf<Source>()

    fun register(source: Source) {
        sources += source
    }

    /** The restart of the chapter [player] plays, or null: Chapter 1's levels decide. */
    fun of(player: ServerPlayerEntity): Restart? = sources.firstNotNullOfOrNull { it.restartFor(player) }
}
