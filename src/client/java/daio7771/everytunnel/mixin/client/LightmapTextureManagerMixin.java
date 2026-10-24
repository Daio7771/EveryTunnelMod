package daio7771.everytunnel.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import daio7771.everytunnel.client.ClientResources;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * In the levels (while the world rules apply to the player), the Brightness option counts as
 * Moody, whatever it is set to, so the dark is dark on every screen, with or without
 * shaders. The option itself is never changed: outside the levels it works as usual.
 * The second {@code SimpleOption.getValue()} in {@code update} is the gamma (the first is
 * the darkness effect scale).
 */
@Mixin(LightmapTextureManager.class)
public abstract class LightmapTextureManagerMixin {

    @ModifyExpressionValue(
        method = "update",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;", ordinal = 1)
    )
    private Object everyTunnel$moodyInLevels(Object gamma) {
        return ClientResources.INSTANCE.getCurrent().getActive() ? (Object) 0.0 : gamma;
    }
}
