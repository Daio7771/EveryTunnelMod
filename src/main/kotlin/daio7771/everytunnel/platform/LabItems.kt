package daio7771.everytunnel.platform

import com.google.common.collect.ImmutableMultimap
import com.google.common.collect.Multimap
import daio7771.everytunnel.config.ConfigManager
import net.minecraft.block.BlockState
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttribute
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.sound.SoundCategory
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * Level 5's wrench: a heavy weapon. A full blow takes `wrench.damage` and comes about once a second
 * (`wrench.attacks_per_second`); every blow that lands rings out in metal (`item.wrench.hit`). It never
 * wears out. Held like a sword, it breaks no blocks in Creative either.
 */
class WrenchItem(settings: Settings) : Item(settings) {

    private val modifiers: Multimap<EntityAttribute, EntityAttributeModifier> by lazy {
        val wrench = ConfigManager.gameplay.wrench
        ImmutableMultimap.builder<EntityAttribute, EntityAttributeModifier>()
            // A player's own attack damage is 1 and speed 4: these bring them to the wrench's.
            .put(EntityAttributes.GENERIC_ATTACK_DAMAGE, EntityAttributeModifier(ATTACK_DAMAGE_MODIFIER_ID, "Wrench", wrench.damage - 1.0, EntityAttributeModifier.Operation.ADDITION))
            .put(EntityAttributes.GENERIC_ATTACK_SPEED, EntityAttributeModifier(ATTACK_SPEED_MODIFIER_ID, "Wrench", wrench.attacksPerSecond - 4.0, EntityAttributeModifier.Operation.ADDITION))
            .build()
    }

    override fun getAttributeModifiers(slot: EquipmentSlot): Multimap<EntityAttribute, EntityAttributeModifier> =
        if (slot == EquipmentSlot.MAINHAND) modifiers else super.getAttributeModifiers(slot)

    override fun postHit(stack: ItemStack, target: LivingEntity, attacker: LivingEntity): Boolean {
        val world = attacker.world
        world.playSound(null, target.x, target.y + target.height / 2, target.z, ModSounds.WRENCH_HIT, SoundCategory.PLAYERS, 1f, 0.9f + world.random.nextFloat() * 0.2f)
        return true
    }

    override fun canMine(state: BlockState, world: World, pos: BlockPos, miner: PlayerEntity): Boolean = !miner.isCreative
}
