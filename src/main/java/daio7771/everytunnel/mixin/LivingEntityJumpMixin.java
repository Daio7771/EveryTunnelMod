package daio7771.everytunnel.mixin;

import daio7771.everytunnel.platform.MovementRules;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A jump a level has forbidden does nothing ({@link MovementRules}); Fabric API has no event for it. It runs on the client (which
 * moves the player) and on the server (which can also be asked to jump), and every rule is registered by a chapter, so vanilla worlds
 * and every other level are untouched.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityJumpMixin {

    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    private void everyTunnel$noJumpWhereForbidden(CallbackInfo ci) {
        if (MovementRules.jumpBlocked((LivingEntity) (Object) this)) ci.cancel();
    }
}
