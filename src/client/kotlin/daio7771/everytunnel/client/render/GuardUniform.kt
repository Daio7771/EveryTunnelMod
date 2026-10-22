package daio7771.everytunnel.client.render

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.world.WorldRules
import net.minecraft.util.Identifier

/**
 * In worlds with levels every player is the night guard: their skin is the uniform
 * (`textures/entity/guard.png`, painted by `tools/entity/guard.py`) on the wide-armed model,
 * in third person and on the arms in first person, and capes are hidden. Applied by
 * `AbstractClientPlayerEntityMixin`; elsewhere players keep their own skins.
 */
object GuardUniform {

    @JvmField
    val TEXTURE = Identifier(EveryTunnel.MOD_ID, "textures/entity/guard.png")

    /** The model the uniform is painted for (wide arms). */
    const val MODEL = "default"

    @JvmStatic
    fun applies(): Boolean = WorldRules.active
}
