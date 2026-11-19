package daio7771.everytunnel.platform

import net.minecraft.entity.LivingEntity
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Where the game says a movement does nothing, chapter-neutral: a chapter registers a rule and the base game's mixin
 * (`LivingEntityJumpMixin`) asks it. Today only the jump: a place a level wants no one to climb out of (Chapter 2's Stone Maw arena)
 * makes [jumpBlocked] true for the player there. The rules run on both sides (the client moves its own player, the server checks), so a
 * rule looks at [LivingEntity.world]'s side itself.
 */
object MovementRules {

    private val jumpRules = CopyOnWriteArrayList<(LivingEntity) -> Boolean>()

    /** [rule] says whether [entity] may not jump now. */
    fun addJumpRule(rule: (LivingEntity) -> Boolean) {
        jumpRules += rule
    }

    /** True if any rule blocks [entity]'s jump. */
    @JvmStatic
    fun jumpBlocked(entity: LivingEntity): Boolean = jumpRules.any { it(entity) }
}
