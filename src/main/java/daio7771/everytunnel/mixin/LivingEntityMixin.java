package daio7771.everytunnel.mixin;

import daio7771.everytunnel.platform.ModItems;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No vanilla drinking sound (a sip every 4 ticks) for the water bottle: it plays its own
 * gulps from the CC0 recording, about one every half second (see WaterBottleItem).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Shadow
    public abstract ItemStack getActiveItem();

    @Inject(method = "shouldSpawnConsumptionEffects", at = @At("HEAD"), cancellable = true)
    private void everyTunnel$quietWaterBottle(CallbackInfoReturnable<Boolean> cir) {
        if (getActiveItem().isOf(ModItems.WATER_BOTTLE)) cir.setReturnValue(false);
    }
}
