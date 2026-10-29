package daio7771.everytunnel.mixin.client;

import daio7771.everytunnel.client.effects.CameraShake;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shakes the camera ({@link CameraShake}) where the game tilts the view when hurt: that runs
 * every frame on the world's matrices, whatever the view-bobbing option.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "tiltViewWhenHurt", at = @At("TAIL"))
    private void everyTunnel$shake(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        CameraShake.apply(matrices, tickDelta);
    }
}
