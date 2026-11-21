package daio7771.everytunnel.common.platform

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.Identifier

/**
 * What a chapter changes of the base game's rules for a player who plays it, asked by the base game's hooks so that they never
 * name a later chapter: the flashlight ([FlashlightHooks]), hiding in lockers ([HidingHooks]), being hurt ([SafeLevelHooks]),
 * the noise of their steps ([NoiseHooks]), the status bars (`common.client.hud.StatusHud`) and who they are (`common.client.render.GuardUniform`).
 *
 * A chapter [register]s a [Source] that gives, for a player, the [Rules] that apply to them now, or null when none of its own do
 * (they are not playing it). The first source with an answer wins; with none, the base game's ([Rules.BASE]). Sources are asked on
 * both sides (the client asks for the status bars), so they read what both sides know (synced progress).
 */
object PlayerRules {

    data class Rules(
        /** The flashlight can be used. */
        val flashlight: Boolean = true,
        /** A locker hides them. */
        val hiding: Boolean = true,
        /** Nothing hurts them, as in a safe level (`/kill` and the void still do). */
        val safe: Boolean = false,
        /** Their steps make noise that the creatures hear. */
        val noise: Boolean = true,
        /** The mod's status bars (stamina, thirst, battery) are drawn. */
        val statusBars: Boolean = true,
        /** Their skin (a 64 x 64 texture for the wide-armed model); null: the night guard's uniform. */
        val skin: Identifier? = null,
    ) {
        companion object {
            val BASE = Rules()
        }
    }

    fun interface Source {
        fun rulesFor(player: PlayerEntity): Rules?
    }

    private val sources = mutableListOf<Source>()

    fun register(source: Source) {
        sources += source
    }

    fun of(player: PlayerEntity): Rules = sources.firstNotNullOfOrNull { it.rulesFor(player) } ?: Rules.BASE
}
