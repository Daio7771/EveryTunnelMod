package daio7771.everytunnel.mixin.client;

import daio7771.everytunnel.client.render.GuardUniform;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dresses every player as the night guard in worlds with levels (see GuardUniform): the
 * uniform as their skin, on the wide-armed model, and no cape. The skin texture and model
 * type also decide the first-person arms.
 */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {

    @Inject(method = "getSkinTexture", at = @At("HEAD"), cancellable = true)
    private void everyTunnel$uniform(CallbackInfoReturnable<Identifier> cir) {
        if (GuardUniform.applies()) cir.setReturnValue(GuardUniform.TEXTURE);
    }

    @Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
    private void everyTunnel$uniformModel(CallbackInfoReturnable<String> cir) {
        if (GuardUniform.applies()) cir.setReturnValue(GuardUniform.MODEL);
    }

    @Inject(method = "getCapeTexture", at = @At("HEAD"), cancellable = true)
    private void everyTunnel$noCape(CallbackInfoReturnable<Identifier> cir) {
        if (GuardUniform.applies()) cir.setReturnValue(null);
    }
}
